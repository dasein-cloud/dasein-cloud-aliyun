/**
 * Copyright (C) 2009-2015 Dell, Inc.
 * See annotations for authorship information
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */
package org.dasein.cloud.aliyun.platform;

import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.DayOfWeek;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.TimeWindow;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.aliyun.network.AliyunNetworkCommon;
import org.dasein.cloud.aliyun.network.AliyunNetworkCommon.RequestMethod;
import org.dasein.cloud.aliyun.platform.model.DatabaseProvider;
import org.dasein.cloud.aliyun.util.requester.AliyunHttpClientBuilderFactory;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestBuilder;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestExecutor;
import org.dasein.cloud.aliyun.util.requester.AliyunResponseHandlerWithMapper;
import org.dasein.cloud.aliyun.util.requester.AliyunValidateJsonResponseHandler;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.platform.AbstractRelationalDatabaseSupport;
import org.dasein.cloud.platform.Database;
import org.dasein.cloud.platform.DatabaseBackup;
import org.dasein.cloud.platform.DatabaseBackupState;
import org.dasein.cloud.platform.DatabaseEngine;
import org.dasein.cloud.platform.DatabaseProduct;
import org.dasein.cloud.platform.DatabaseState;
import org.dasein.cloud.platform.RelationalDatabaseCapabilities;
import org.dasein.cloud.platform.RelationalDatabaseSupport;
import org.dasein.cloud.util.APITrace;
import org.dasein.cloud.util.requester.DriverToCoreMapper;
import org.dasein.cloud.util.requester.streamprocessors.StreamToJSONObjectProcessor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.dasein.cloud.platform.DatabaseLicenseModel.BRING_YOUR_OWN_LICENSE;
import static org.dasein.cloud.platform.DatabaseLicenseModel.LICENSE_INCLUDED;
import static org.dasein.cloud.platform.DatabaseLicenseModel.POSTGRESQL_LICENSE;

/**
 * Created by Jane Wang on 7/10/2015.
 * 
 * @author Jane Wang
 * @since 2015.05.01
 */
public class AliyunRelationalDatabase extends AbstractRelationalDatabaseSupport<Aliyun>
		implements RelationalDatabaseSupport {

	private static final Logger stdLogger = Aliyun.getStdLogger(AliyunRelationalDatabase.class);

    private transient volatile AliyunRelationalDatabaseCapabilities capabilities;
	
	public AliyunRelationalDatabase(Aliyun provider) {
		super(provider);
	}
	
	@Override
	public RelationalDatabaseCapabilities getCapabilities()
			throws InternalException, CloudException {
		if (capabilities == null) {
			capabilities = new AliyunRelationalDatabaseCapabilities(getProvider());
		}
		return capabilities;
	}

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		String regionId = getContext().getRegionId();
		if (regionId == null) {
			throw new InternalException("No region was set for this request");
		}

		if (Arrays.asList("cn-hangzhou", "cn-qingdao", "cn-beijing", "cn-hongkong").contains(regionId)) {
			return true;
		} else {
			return false;
		}
	}

	/*
	@Override
	public Collection<ConfigurationParameter> listParameters(
			String forProviderConfigurationId) throws CloudException,
			InternalException {
		APITrace.begin(getProvider(), "RelationalDatabase.listParameters");
		try {
			
			DatabaseConfiguration configuration = getConfiguration(forProviderConfigurationId);
			String engineName = null;
			if (configuration.getEngine().equals(DatabaseEngine.MYSQL)) {
				engineName = "MySQL";
			} else if (configuration.getEngine().equals(DatabaseEngine.SQLSERVER_EE)) {
				engineName = "SQLServer";
			} else if (configuration.getEngine().equals(DatabaseEngine.POSTGRES)) {
				engineName = "PostgreSQL";
			} else {
				stdLogger.error("Database engine " + configuration.getEngine().name() + " is not supported!");
				throw new OperationNotSupportedException("Database engine " + configuration.getEngine().name() + " is not supported!");
			}
		
			HttpUriRequest request = AliyunRequestBuilder.get()
					.provider(getProvider())
					.category(AliyunRequestBuilder.Category.RDS)
					.parameter("Action", "DescribeParameterTemplates")
					.parameter("Engine", engineName)
					.parameter("EngineVersion", getDefaultVersion(configuration.getEngine()))
					.build();
			
			ResponseHandler<List<ConfigurationParameter>> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, List<ConfigurationParameter>>(
					new StreamToJSONObjectProcessor(),
					new DriverToCoreMapper<JSONObject, List<ConfigurationParameter>>() {
						@Override
						public List<ConfigurationParameter> mapFrom(JSONObject json) {
							try {
								List<ConfigurationParameter> parameters = new ArrayList<ConfigurationParameter>();
								JSONArray templateRecords = json.getJSONObject("Parameters").getJSONArray("TemplateRecord");
								for (int i = 0; i < templateRecords.length(); i++ ) {
									JSONObject templateRecord = templateRecords.getJSONObject(i);
									ConfigurationParameter parameter = new ConfigurationParameter();
									if ("True".equals(templateRecord.getString("ForceRestart"))) {
										parameter.setApplyImmediately(false);
									} else {
										parameter.setApplyImmediately(true);
									}
									parameter.setDescription(templateRecord.getString("ParameterDescription"));
									parameter.setKey(templateRecord.getString("ParameterName"));
									if ("True".equals(templateRecord.getString("ForceModify"))) {
										parameter.setModifiable(true);
									} else {
										parameter.setModifiable(false);
									}
									parameter.setParameter(templateRecord.getString("ParameterValue"));
									parameter.setValidation(templateRecord.getString("CheckingCode"));
									parameters.add(parameter);
								}
								return parameters;
							} catch (JSONException e) {
								stdLogger.error("parsing db instance attribute failed", e);
								throw new RuntimeException(e);
							}
						}
					}, JSONObject.class);
			
			return new AliyunRequestExecutor<List<ConfigurationParameter>>(getProvider(),
					AliyunHttpClientBuilderFactory.newHttpClientBuilder(), request,
					responseHandler).execute();
		} finally {
			APITrace.end();
		}
	}
	*/
	@Override
	public Iterable<DatabaseEngine> getDatabaseEngines() throws CloudException, InternalException {
		return Arrays.asList(
				DatabaseEngine.MYSQL,
				DatabaseEngine.SQLSERVER_EE,
				DatabaseEngine.POSTGRES);
	}

	@Override
	public String getDefaultVersion(DatabaseEngine forEngine) throws CloudException, InternalException {
		if (forEngine.equals(DatabaseEngine.MYSQL)) {
			return "5.5";
		} else if (forEngine.equals(DatabaseEngine.SQLSERVER_EE)) {
			return "2008r2";
		} else if (forEngine.equals(DatabaseEngine.POSTGRES)) {
			return "9.4";
		} else {
			stdLogger.error("Aliyun doesn't support database engine " + forEngine.name());
			throw new OperationNotSupportedException("Aliyun doesn't support database engine " + forEngine.name());
		}
	}

	@Override
	public Iterable<String> getSupportedVersions(DatabaseEngine forEngine) throws CloudException, InternalException {
		if (forEngine.equals(DatabaseEngine.MYSQL)) {
			return Arrays.asList("5.5", "5.6");
		} else if (forEngine.equals(DatabaseEngine.SQLSERVER_EE)) {
			return Arrays.asList("2008r2");
		} else if (forEngine.equals(DatabaseEngine.POSTGRES)) {
			return Arrays.asList("9.4");
		} else {
			stdLogger.error("Aliyun doesn't support database engine " + forEngine.name());
			throw new OperationNotSupportedException("Aliyun doesn't support database engine " + forEngine.name());
		}
	}

	@Override
	public Iterable<DatabaseProduct> listDatabaseProducts(DatabaseEngine forEngine)
			throws CloudException, InternalException {
		List<DatabaseProduct> retProducts = new ArrayList<DatabaseProduct>();
		DatabaseProvider provider = DatabaseProvider.fromFile("/platform/dbproducts.json", "Aliyun");
		String engineName = null;
		if (forEngine.equals(DatabaseEngine.MYSQL)) {
			engineName = "MySQL";
		} else if (forEngine.equals(DatabaseEngine.POSTGRES)) {
			engineName = "PostgreSQL";
		} else if (forEngine.equals(DatabaseEngine.SQLSERVER_EE)) {
			engineName = "SQLServer";
		} else {
			stdLogger.error("Aliyun doesn't support database engine " + forEngine.name());
			throw new OperationNotSupportedException("Aliyun doesn't support database engine " + forEngine.name());
		}
		org.dasein.cloud.aliyun.platform.model.DatabaseEngine engine = provider.findEngine(engineName);
		for (org.dasein.cloud.aliyun.platform.model.DatabaseRegion region : engine.getRegions()) {
			if (region.getName().equals(getContext().getRegionId())) {

				for (org.dasein.cloud.aliyun.platform.model.DatabaseProduct product : region.getProducts()) {
					DatabaseProduct retProduct = new DatabaseProduct(engineName);
					retProduct.setCurrency(product.getCurrency());
					retProduct.setName(String.format("class:%s, memory %dMB, max iops %d, max connection %d",
							product.getName(), product.getMemory(), product.getMaxIops(), product.getMaxConnection()));
					retProduct.setProductSize(product.getName());
					retProduct.setStandardHourlyRate(product.getHourlyPrice());
					retProduct.setStorageInGigabytes(product.getStorageInGigabytes());
					if( "included".equalsIgnoreCase(product.getLicense())) {
						retProduct.setLicenseModel(LICENSE_INCLUDED);
					} else if( "byol".equalsIgnoreCase(product.getLicense())) {
						retProduct.setLicenseModel(BRING_YOUR_OWN_LICENSE);
					} else if( "postgres".equalsIgnoreCase(product.getLicense())) {
						retProduct.setLicenseModel(POSTGRESQL_LICENSE);
					}
					retProducts.add(retProduct);
				}
			}
		}
		return retProducts;
	}

	@Override
	public String createFromScratch(String databaseName, DatabaseProduct product, String databaseVersion,
			String withAdminUser, String withAdminPassword, int hostPort) throws CloudException, InternalException {
		APITrace.begin(getProvider(), "RelationalDatabase.createFromScratch");
		try {
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("RegionId", getContext().getRegionId());

			if (product.getEngine().equals(DatabaseEngine.MYSQL)) {
				params.put("Engine", "MySQL");
			} else if (product.getEngine().equals(DatabaseEngine.POSTGRES)) {
				params.put("Engine", "PostgreSQL");
			} else if (product.getEngine().equals(DatabaseEngine.SQLSERVER_EE)) {
				params.put("Engine", "SQLServer");
			} else {
				stdLogger.error("not support database engine " + product.getEngine().name());
				throw new OperationNotSupportedException("Aliyun doesn't support database engine " + product.getEngine().name());
			}

			if (!getProvider().isEmpty(databaseVersion)) {
				params.put("EngineVersion", databaseVersion);
			} else {
				params.put("EngineVersion", this.getDefaultVersion(product.getEngine()));
			}

			params.put("DBInstanceClass", product.getName());
			params.put("DBInstanceStorage", product.getStorageInGigabytes());
			params.put("DBInstanceNetType", "Internet");
			params.put("DBInstanceDescription", "DB Instance - " + product.getName() + " " + product.getStorageInGigabytes());
			params.put("SecurityIPList", "0.0.0.0/0");
			params.put("PayType", "Postpaid");
			if (!getProvider().isEmpty(product.getProviderDataCenterId())) {
				params.put("ZoneId", product.getProviderDataCenterId());
			}

			//create DB instance
			ResponseHandler<String> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, String>(
					new StreamToJSONObjectProcessor(),
					new DriverToCoreMapper<JSONObject, String>() {
						@Override
						public String mapFrom(JSONObject json ) {
							try {
								return json.getString("DBInstanceId" );
							} catch (JSONException e ) {
								stdLogger.error("parse SecurityGroupId from response failed", e);
								throw new RuntimeException(e);
							}
						}
					}, JSONObject. class);

			String instanceId = (String) executeDefaultRequest(getProvider(), params,
					AliyunRequestBuilder.Category.RDS, "CreateDBInstance", AliyunNetworkCommon.RequestMethod.POST,
					true, responseHandler);

			//create database
			params = new HashMap<String, Object>();
			params.put("DBInstanceId", instanceId);
			params.put("DBName", databaseName);
			if (product.getEngine().equals(DatabaseEngine.MYSQL)) {
				params.put("CharacterSetName", "utf8");
			} else if (product.getEngine().equals(DatabaseEngine.POSTGRES)) {
				params.put("CharacterSetName", "UTF8");
			} else if (product.getEngine().equals(DatabaseEngine.SQLSERVER_EE)) {
				params.put("CharacterSetName", "Chinese_PRC_CI_AS"); //TODO, use global charset, not Chinese
			}

			executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS,
					"CreateDatabase", AliyunNetworkCommon.RequestMethod.POST, false,
					new AliyunValidateJsonResponseHandler(getProvider()));

			//create database account
			createDBInstanceAccount(instanceId, databaseName, withAdminUser, withAdminPassword);

			return instanceId;
		} finally {
			APITrace.end();
		}
	}

	/*
	 * alter following items:
	 * - productSize, storageInGigabytes
	 * - newAdminUser, newAdminPassword
	 * - preferredMaintenanceWindow
	 * - preferredBackupWindow
	 */
	@Override
	public void alterDatabase(String providerDatabaseId, boolean applyImmediately, String productSize, int storageInGigabytes,
			String configurationId, String newAdminUser, String newAdminPassword, int newPort, int snapshotRetentionInDays,
			TimeWindow preferredMaintenanceWindow, TimeWindow preferredBackupWindow) throws CloudException, InternalException {
		DatabaseId databaseId = new DatabaseId(providerDatabaseId);
		if (!getProvider().isEmpty(productSize) || storageInGigabytes >= 5) {
			alterDBInstanceAttribute(databaseId.getDatabaseInstanceId(), productSize, storageInGigabytes);
		}

		if (!getProvider().isEmpty(newAdminUser) && !getProvider().isEmpty(newAdminPassword)) {
			alterDBInstanceAccount(databaseId.getDatabaseInstanceId(), databaseId.getDatabaseName(), newAdminUser, newAdminPassword);
		}

		if (preferredMaintenanceWindow != null) {
			alterPreferredMaintenanceWindow(databaseId.getDatabaseInstanceId(), preferredMaintenanceWindow);
		}

		if (preferredBackupWindow != null) {
			alterPreferredBackupWindow(databaseId.getDatabaseInstanceId(), preferredBackupWindow);
		}
	}

	@Override
	public void removeDatabase(String providerDatabaseId) throws CloudException, InternalException {
		APITrace.begin(getProvider(), "RelationalDatabase.removeDatabase");
		try {
			DatabaseId databaseId = new DatabaseId(providerDatabaseId);

			Map<String, Object> params = new HashMap<String, Object>();
			params.put("DBInstanceId", databaseId.getDatabaseInstanceId());
			params.put("DBName", databaseId.getDatabaseName());

			executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS,
					"DeleteDatabase", AliyunNetworkCommon.RequestMethod.POST, false,
					new AliyunValidateJsonResponseHandler(getProvider()));
			//TODO: remove instance if there is no Database in instance
		} finally {
			APITrace.end();
		}
	}

	@Override
	public void restart(String providerDatabaseId, boolean blockUntilDone) throws CloudException, InternalException {
		APITrace.begin(getProvider(), "RelationalDatabase.restart");
		try {
			DatabaseId databaseId = new DatabaseId(providerDatabaseId);

			Map<String, Object> params = new HashMap<String, Object>();
			params.put("DBInstanceId", databaseId.getDatabaseInstanceId());

			executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS,
					"RestartDBInstance", AliyunNetworkCommon.RequestMethod.POST, false,
					new AliyunValidateJsonResponseHandler(getProvider()));
		} finally {
			APITrace.end();
		}
	}

	@Override
	public Database getDatabase(String providerDatabaseId)
			throws CloudException, InternalException {
		APITrace.begin(getProvider(), "RelationalDatabase.getDatabase");
		try {

			final DatabaseId databaseId = new DatabaseId(providerDatabaseId);
			final JSONObject dbInstanceAttribute = getDBInstanceAttribute(databaseId.getDatabaseInstanceId());

			HttpUriRequest request = AliyunRequestBuilder.get()
					.provider(getProvider())
					.category(AliyunRequestBuilder.Category.RDS)
					.parameter("Action", "DescribeDBInstances")
					.parameter("DBInstanceId", databaseId.getDatabaseInstanceId())
					.parameter("DBName", databaseId.getDatabaseName()) //TODO, I didn't find DBName parameter in API document! You should get all Database and then get the right one. And need to remove the duplicate code with listDatabases(0
					.build();

			ResponseHandler<Database> dbResponseHandler = new AliyunResponseHandlerWithMapper<JSONObject, Database>(
					new StreamToJSONObjectProcessor(),
					new DriverToCoreMapper<JSONObject, Database>() {
						@Override
						public Database mapFrom(JSONObject json) {
							try {
								JSONArray databaseArr = json.getJSONObject("Databases").getJSONArray("Database");
								if (databaseArr.length() > 0) {

									JSONObject respDatabase = databaseArr.getJSONObject(0);
									Database database = new Database();

									database.setAdminUser(respDatabase.getJSONObject("Accounts").getJSONArray("AccountPrivilegeInfo").getJSONObject(0).getString("Account"));
									database.setAllocatedStorageInGb(dbInstanceAttribute.getInt("DBInstanceStorage"));
									database.setBackupWindow(getBackupTimeWindow(databaseId.getDatabaseInstanceId()));
									database.setEngineVersion(dbInstanceAttribute.getString("EngineVersion"));
									database.setHostName(dbInstanceAttribute.getString("ConnectionString"));
									database.setHostPort(dbInstanceAttribute.getInt("Port"));
									database.setCreationTimestamp(new SimpleDateFormat("YYYY-MM-DD'T'hh:mm:ss'Z'").parse(dbInstanceAttribute.getString("CreationTime")).getTime());
									database.setMaintenanceWindow(formatTimeWindow(dbInstanceAttribute.getString("MaintainTime")));
									database.setName(respDatabase.getString("DBName"));
									database.setProductSize(dbInstanceAttribute.getString("DBInstanceClass"));
									database.setProviderDatabaseId(new DatabaseId(databaseId.getDatabaseInstanceId(), respDatabase.getString("DBName")).getDatabaseId());
									database.setProviderOwnerId(getContext().getAccountNumber());
									database.setProviderRegionId(getContext().getRegionId());

									if ("Creating".equals(respDatabase.getString("DBStatus"))) {
										database.setCurrentState(DatabaseState.PENDING);
									} else if ("Running".equals(respDatabase.getString("DBStatus"))) {
										database.setCurrentState(DatabaseState.AVAILABLE);
									} else if ("Deleting".equals(respDatabase.getString("DBStatus"))) {
										database.setCurrentState(DatabaseState.DELETING);
									} else {
										database.setCurrentState(DatabaseState.UNKNOWN);
									}

									if ("MySQL".equals(dbInstanceAttribute.getString("Engine"))) {
										database.setEngine(DatabaseEngine.MYSQL);
									} else if ("SQLServer".equals(dbInstanceAttribute.getString("Engine"))) {
										database.setEngine(DatabaseEngine.SQLSERVER_EE);
									} else if ("PostgreSQL".equals(dbInstanceAttribute.getString("Engine"))) {
										database.setEngine(DatabaseEngine.POSTGRES);
									} else {
										throw new InternalException("Database engine " + dbInstanceAttribute.getString("Engine") + " is not supported!");
									}

									return database;
								}
								return null;
							} catch (JSONException e) {
								stdLogger.error("parsing db instance attribute failed", e);
								throw new RuntimeException(e);
							} catch (InternalException e) {
								stdLogger.error("get datbase by instance id failed", e);
								throw new RuntimeException(e);
							} catch (CloudException e) {
								stdLogger.error("get datbase by instance id failed", e);
								throw new RuntimeException(e);
							} catch (ParseException e) {
								stdLogger.error("parsing time window failed", e);
								throw new RuntimeException(e);
							}
						}
					}, JSONObject.class);

			return new AliyunRequestExecutor<Database>(getProvider(),
					AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
					request,
					dbResponseHandler).execute();
		} finally {
			APITrace.end();
		}
	}

	@Override
	public Iterable<Database> listDatabases() throws CloudException, InternalException {
		APITrace.begin(getProvider(), "RelationalDatabase.listDatabases");
		try {
			//query all db instance ids
			List<String> allDbInstanceIds = new ArrayList<String>();
			final AtomicInteger totalPageNumber = new AtomicInteger(1);
			final AtomicInteger currentPageNumber = new AtomicInteger(1);

			ResponseHandler<List<String>> dbInstResponseHandler = new AliyunResponseHandlerWithMapper<JSONObject, List<String>>(
					new StreamToJSONObjectProcessor(),
					new DriverToCoreMapper<JSONObject, List<String>>() {
						@Override
						public List<String> mapFrom(JSONObject json) {
							try {
								List<String> dbInstanceIds = new ArrayList<String>();
								JSONArray dbInstances = json.getJSONObject("Items").getJSONArray("DBInstance");
								for (int i = 0; i < dbInstances.length(); i++) {
									dbInstanceIds.add(dbInstances.getJSONObject(i).getString("DBInstanceId"));

								}
								totalPageNumber.addAndGet(json.getInt("TotalRecordCount") / AliyunNetworkCommon.DefaultPageSize +
										json.getInt("TotalRecordCount") % AliyunNetworkCommon.DefaultPageSize > 0 ? 1 : 0);
								currentPageNumber.incrementAndGet();
								return dbInstanceIds;
							} catch (JSONException e) {
								stdLogger.error("parsing db instance attribute failed", e);
								throw new RuntimeException(e);
							}
						}
					}, JSONObject.class);

			do {
				HttpUriRequest request = AliyunRequestBuilder.get()
						.provider(getProvider())
						.category(AliyunRequestBuilder.Category.RDS)
						.parameter("Action", "DescribeDBInstances")
						.parameter("RegionId", getContext().getRegionId())
						.parameter("PageSize", AliyunNetworkCommon.DefaultPageSize)
						.parameter("PageNumber", currentPageNumber)
						.build();

				allDbInstanceIds.addAll(new AliyunRequestExecutor<List<String>>(getProvider(),
						AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
						request,
						dbInstResponseHandler).execute());

			} while (currentPageNumber.intValue() < totalPageNumber.intValue());

			//query databases by instance ids
			List<Database> allDatabases = new ArrayList<Database>();
			for (final String dbInstanceId : allDbInstanceIds) {

				final JSONObject dbInstanceAttribute = getDBInstanceAttribute(dbInstanceId);

				HttpUriRequest request = AliyunRequestBuilder.get()
						.provider(getProvider())
						.category(AliyunRequestBuilder.Category.RDS)
						.parameter("Action", "DescribeDBInstances")
						.parameter("DBInstanceId", dbInstanceId)
						.build();

				//TODO: remove duplicate code with getDatabase method
				ResponseHandler<List<Database>> dbResponseHandler = new AliyunResponseHandlerWithMapper<JSONObject, List<Database>>(
						new StreamToJSONObjectProcessor(),
						new DriverToCoreMapper<JSONObject, List<Database>>() {
							@Override
							public List<Database> mapFrom(JSONObject json) {
								try {
									List<Database> databases = new ArrayList<Database>();
									JSONArray databaseArr = json.getJSONObject("Databases").getJSONArray("Database");
									for (int i = 0; i < databaseArr.length(); i++) {
										JSONObject respDatabase = databaseArr.getJSONObject(i);
										Database database = new Database();

										database.setAdminUser(respDatabase.getJSONObject("Accounts").getJSONArray("AccountPrivilegeInfo").getJSONObject(0).getString("Account"));
										database.setAllocatedStorageInGb(dbInstanceAttribute.getInt("DBInstanceStorage"));
										database.setBackupWindow(getBackupTimeWindow(dbInstanceId));
										database.setEngineVersion(dbInstanceAttribute.getString("EngineVersion"));
										database.setHostName(dbInstanceAttribute.getString("ConnectionString"));
										database.setHostPort(dbInstanceAttribute.getInt("Port"));
										database.setCreationTimestamp(new SimpleDateFormat("YYYY-MM-DD'T'hh:mm:ss'Z'").parse(dbInstanceAttribute.getString("CreationTime")).getTime());
										database.setMaintenanceWindow(formatTimeWindow(dbInstanceAttribute.getString("MaintainTime")));
										database.setName(respDatabase.getString("DBName"));
										database.setProductSize(dbInstanceAttribute.getString("DBInstanceClass"));
										database.setProviderDatabaseId(new DatabaseId(dbInstanceId, respDatabase.getString("DBName")).getDatabaseId());
										database.setProviderOwnerId(getContext().getAccountNumber());
										database.setProviderRegionId(getContext().getRegionId());

										if ("Creating".equals(respDatabase.getString("DBStatus"))) {
											database.setCurrentState(DatabaseState.PENDING);
										} else if ("Running".equals(respDatabase.getString("DBStatus"))) {
											database.setCurrentState(DatabaseState.AVAILABLE);
										} else if ("Deleting".equals(respDatabase.getString("DBStatus"))) {
											database.setCurrentState(DatabaseState.DELETING);
										} else {
											database.setCurrentState(DatabaseState.UNKNOWN);
										}

										if ("MySQL".equals(dbInstanceAttribute.getString("Engine"))) {
											database.setEngine(DatabaseEngine.MYSQL);
										} else if ("SQLServer".equals(dbInstanceAttribute.getString("Engine"))) {
											database.setEngine(DatabaseEngine.SQLSERVER_EE);
										} else if ("PostgreSQL".equals(dbInstanceAttribute.getString("Engine"))) {
											database.setEngine(DatabaseEngine.POSTGRES);
										}

										databases.add(database);
									}
									return databases;
								} catch (JSONException e) {
									stdLogger.error("parsing db instance attribute failed", e);
									throw new RuntimeException(e);
								} catch (InternalException e) {
									stdLogger.error("get datbase by instance id failed", e);
									throw new RuntimeException(e);
								} catch (CloudException e) {
									stdLogger.error("get datbase by instance id failed", e);
									throw new RuntimeException(e);
								} catch (ParseException e) {
									stdLogger.error("parsing time window failed", e);
									throw new RuntimeException(e);
								}
							}
						}, JSONObject.class);

				allDatabases.addAll(new AliyunRequestExecutor<List<Database>>(getProvider(),
						AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
						request,
						dbResponseHandler).execute());

			}

			return allDatabases;
		} finally {
			APITrace.end();
		}
	}

	@Override
	public Iterable<ResourceStatus> listDatabaseStatus() throws CloudException, InternalException {
		List<ResourceStatus> statuses = new ArrayList<ResourceStatus>();
		for (Database database : listDatabases()) {
			statuses.add(new ResourceStatus(database.getProviderDatabaseId(), database.getCurrentState().name()));
		}
		return statuses;
	}

	@Override
	public void addAccess(String providerDatabaseId, String sourceCidr)
			throws CloudException, InternalException {
		APITrace.begin(getProvider(), "RelationalDatabase.addAccess");
		try {
			Map<String, Object> params = new HashMap<String, Object>();
			
			DatabaseId id = new DatabaseId(providerDatabaseId);
			params.put("DBInstanceId", id.getDatabaseInstanceId());
			
			StringBuilder accessBuilder = new StringBuilder();
			Iterator<String> access = listAccess(providerDatabaseId).iterator();
			while (access.hasNext()) {
				accessBuilder.append(access.next() + ",");
			}
			accessBuilder.append(sourceCidr);
			params.put("SecurityIps", accessBuilder.toString());
			
			executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS, 
					"ModifySecurityIps", AliyunNetworkCommon.RequestMethod.POST, false, 
					new AliyunValidateJsonResponseHandler(getProvider()));
		} finally {
			APITrace.end();
		}
	}

	@Override
	public void revokeAccess(String providerDatabaseId, String sourceCidr) throws CloudException, InternalException {
		APITrace.begin(getProvider(), "RelationalDatabase.revokeAccess");
		try {
			Map<String, Object> params = new HashMap<String, Object>();
			
			DatabaseId id = new DatabaseId(providerDatabaseId);
			params.put("DBInstanceId", id.getDatabaseInstanceId());
			
			StringBuilder accessBuilder = new StringBuilder();
			Iterator<String> access = listAccess(providerDatabaseId).iterator();
			while (access.hasNext()) {
				String cidr = access.next();
				if (!cidr.equals(sourceCidr)) {
					accessBuilder.append(access.next() + ",");
				}
			}
			accessBuilder.deleteCharAt(accessBuilder.length() - 1);
			params.put("SecurityIps", accessBuilder.toString());
			
			executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS, 
					"ModifySecurityIps", AliyunNetworkCommon.RequestMethod.POST, false, 
					new AliyunValidateJsonResponseHandler(getProvider()));
		} finally {
			APITrace.end();
		}
	}

	@Override
	public Iterable<String> listAccess(String toProviderDatabaseId) throws CloudException, InternalException {
		DatabaseId databaseId = new DatabaseId(toProviderDatabaseId);

		JSONObject databaseInstanceAttribute = getDBInstanceAttribute(databaseId.getDatabaseInstanceId());
		try {
			String securityIPList = databaseInstanceAttribute.getString("SecurityIPList");

			List<String> accessList = new ArrayList<String>();
			if (!getProvider().isEmpty(securityIPList)) {

				String[] segments = securityIPList.split(",");
				if (segments.length > 0) {
					Collections.addAll(accessList, segments);
				}
			}

			return accessList;
		} catch (JSONException e) {
			stdLogger.error("parsing security ip list from database instance " + databaseId.getDatabaseInstanceId() + " failed", e);
			throw new InternalException(e);
		}
	}

	@Override
	public DatabaseBackup getUsableBackup(String providerDbId, String beforeTimestamp)
			throws CloudException, InternalException {
		
		SimpleDateFormat daseinFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		Date baseDate = null;
		try {
			baseDate = daseinFormatter.parse(beforeTimestamp);
		} catch (ParseException e) {
			stdLogger.error("parse beforeTimestamp failed", e);
			throw new RuntimeException(e);
		}
		
		SimpleDateFormat aliyunFormatter = new SimpleDateFormat("YYYY-MM-DD'T'hh:mm:ss'Z'");
		DatabaseBackup closestBackup = null;
		Iterator<DatabaseBackup> backupIter = listBackups(providerDbId).iterator();
		while (backupIter.hasNext()) {
			try {
				DatabaseBackup backup = backupIter.next();
				if (backup.getCurrentState().equals(DatabaseBackupState.AVAILABLE)) {
					if (closestBackup == null || isCurrentTimeCloser(baseDate, aliyunFormatter.parse(backup.getStartTime()), 
							aliyunFormatter.parse(closestBackup.getStartTime()))) {
						closestBackup = backup;
					}
				}
			} catch (ParseException e) {
				stdLogger.error("parse backup start time failed", e);
				throw new RuntimeException(e);
			}
		}
		return closestBackup;
	}

	@Override
	public Iterable<DatabaseBackup> listBackups(String forOptionalProviderDatabaseId)
			throws CloudException, InternalException {
		APITrace.begin(getProvider(), "RelationalDatabase.listBackups");
		try {
			List<DatabaseBackup> allBackups = new ArrayList<DatabaseBackup>();
	        final AtomicInteger totalPageNumber = new AtomicInteger(1);
	        final AtomicInteger currentPageNumber = new AtomicInteger(1);
	        final DatabaseId databaseId = new DatabaseId(forOptionalProviderDatabaseId);
	        
			ResponseHandler<List<DatabaseBackup>> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, List<DatabaseBackup>>(
					new StreamToJSONObjectProcessor(),
					new DriverToCoreMapper<JSONObject, List<DatabaseBackup>>() {
						@Override
						public List<DatabaseBackup> mapFrom(JSONObject json) {
							try {
								List<DatabaseBackup> backups = new ArrayList<DatabaseBackup>();
								JSONArray respBackups = json.getJSONObject("Items").getJSONArray("Backup");
								for (int i = 0; i < respBackups.length(); i++) {
									JSONObject respBackup = respBackups.getJSONObject(i);
									DatabaseBackup backup = new DatabaseBackup();
									backup.setCurrentState(DatabaseBackupState.ERROR);
									if ("Success".equals(respBackup.getString("BackupStatus"))) {
										backup.setCurrentState(DatabaseBackupState.AVAILABLE);
									}
									backup.setStartTime(respBackup.getString("BackupStartTime"));
									backup.setEndTime(respBackup.getString("BackupEndTime"));
									backup.setProviderBackupId(String.valueOf(respBackup.getInt("BackupId")));
									backup.setProviderDatabaseId(databaseId.getDatabaseId());
									backup.setProviderOwnerId(getContext().getAccountNumber());
									backup.setProviderRegionId(getContext().getRegionId());
									backup.setStorageInGigabytes((int)(respBackup.getLong("storageInGigabytes") / 1024l * 1024l * 1024l));
									backups.add(backup);
								}
								totalPageNumber.addAndGet(json.getInt("TotalRecordCount") / AliyunNetworkCommon.DefaultPageSize +
	                                    json.getInt("TotalRecordCount") % AliyunNetworkCommon.DefaultPageSize > 0 ? 1 : 0);
	                            currentPageNumber.incrementAndGet();
								return backups;
							} catch (JSONException e) {
								stdLogger.error("parsing db instance attribute failed", e);
								throw new RuntimeException(e);
							} catch (InternalException e) {
								stdLogger.error("get accont number or region id from context failed", e);
								throw new RuntimeException(e);
							}
						}
					}, JSONObject.class);
			
			do {
	            HttpUriRequest request = AliyunRequestBuilder.get()
	    				.provider(getProvider())
	    				.category(AliyunRequestBuilder.Category.RDS)
	    				.parameter("Action", "DescribeBackups")
	    				.parameter("DBInstanceId", databaseId.getDatabaseInstanceId())
	    				.parameter("PageSize", AliyunNetworkCommon.DefaultPageSize)
	            		.parameter("PageNumber", currentPageNumber)
	    				.build();
	            
	            allBackups.addAll(new AliyunRequestExecutor<List<DatabaseBackup>>(getProvider(),
	                    AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
	                    request,
	                    responseHandler).execute());
	        } while (currentPageNumber.intValue() < totalPageNumber.intValue());
			
			return allBackups;
		} finally {
			APITrace.end();
		}
	}

	private TimeWindow getBackupTimeWindow(String databaseInstanceId) throws InternalException, CloudException {
		APITrace.begin(getProvider(), "RelationalDatabase.getBackupTimeWindow");
		try {
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("DBInstanceId", databaseInstanceId);
			
			ResponseHandler<TimeWindow> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, TimeWindow>(
	            new StreamToJSONObjectProcessor(),
	                  new DriverToCoreMapper<JSONObject, TimeWindow>() {
	                     @Override
	                     public TimeWindow mapFrom(JSONObject json ) {
	                        try {
	                            TimeWindow timeWindow = new TimeWindow();
	                      		SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm'Z'");
	                      		String[] segments = json.getString("PreferredBackupTime").split("-");
	                      		
                      			Calendar cal = Calendar.getInstance();
                      			cal.setTime(timeFormatter.parse(segments[0].trim()));
                      			
                      			timeWindow.setStartHour(cal.get(Calendar.HOUR_OF_DAY));
                      			timeWindow.setStartMinute(cal.get(Calendar.MINUTE));
                      			cal.setTime(timeFormatter.parse(segments[1].trim()));
                      			
                      			timeWindow.setEndHour(cal.get(Calendar.HOUR_OF_DAY));
                      			timeWindow.setEndMinute(cal.get(Calendar.MINUTE));
                      			
                      			timeWindow.setStartDayOfWeek(
										DayOfWeek.valueOf(json.getString("PreferredBackupPeriod").toUpperCase()));
                      			timeWindow.setEndDayOfWeek(
										DayOfWeek.valueOf(json.getString("PreferredBackupPeriod").toUpperCase()));
                      			
                      			return timeWindow;
	                        } catch (JSONException e ) {
	                        	stdLogger.error("parse SecurityGroupId from response failed", e);
	                        	throw new RuntimeException(e);
	                        } catch (ParseException e) {
	                        	stdLogger.error("parsing start and end hour/minutes failed for backup time", e);
	                        	throw new RuntimeException(e);
							}
	                    }
	                 }, JSONObject. class);  
	
			
			return executeDefaultRequest(getProvider(), 
					params, AliyunRequestBuilder.Category.RDS, "DescribeBackupPolicy", 
					AliyunNetworkCommon.RequestMethod.POST, false, 
					responseHandler);
		} finally {
			APITrace.end();
		}
	}
	
	/*
	 * if current time is closer to the base time than then old time do, return true, otherwise, return false
	 */
	private boolean isCurrentTimeCloser(Date baseTime, Date currentTime, Date oldTime) {
		if (Math.abs(currentTime.getTime() - baseTime.getTime()) < Math.abs(oldTime.getTime() - baseTime.getTime())) {
			return true;
		} else {
			return false;
		}
	}

	private JSONObject getDBInstanceAttribute(String databaseInstanceId) throws InternalException, CloudException {
		APITrace.begin(getProvider(), "RelationalDatabase.getDBInstanceAttribute");
		try {
			HttpUriRequest request = AliyunRequestBuilder.get()
					.provider(getProvider())
					.category(AliyunRequestBuilder.Category.RDS)
					.parameter("Action", "DescribeDBInstanceAttribute")
					.parameter("DBInstanceId", databaseInstanceId)
					.build();
			
			ResponseHandler<JSONObject> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, JSONObject>(
					new StreamToJSONObjectProcessor(),
					new DriverToCoreMapper<JSONObject, JSONObject>() {
						@Override
						public JSONObject mapFrom(JSONObject json) {
							try {
								return json.getJSONObject("Items").getJSONArray("DBInstanceAttribute").getJSONObject(0);
							} catch (JSONException e) {
								stdLogger.error("parsing db instance attribute failed", e);
								throw new RuntimeException(e);
							}
						}
					}, JSONObject.class);
			
			return new AliyunRequestExecutor<JSONObject>(getProvider(),
					AliyunHttpClientBuilderFactory.newHttpClientBuilder(), request,
					responseHandler).execute();
		} finally {
			APITrace.end();
		}
	}
	
	private TimeWindow formatTimeWindow (String timespan) throws ParseException {
		SimpleDateFormat format = new SimpleDateFormat("HH:mm'Z'");
		String[] segments = timespan.split("-");
		
		TimeWindow timeWindow = new TimeWindow();
		Calendar cal = Calendar.getInstance();
		
		cal.setTime(format.parse(segments[0].trim()));
		timeWindow.setStartHour(cal.get(Calendar.HOUR_OF_DAY));
		timeWindow.setStartMinute(cal.get(Calendar.MINUTE));
		
		cal.setTime(format.parse(segments[1].trim()));
		timeWindow.setEndHour(cal.get(Calendar.HOUR_OF_DAY));
		timeWindow.setEndMinute(cal.get(Calendar.MINUTE));
		
		return timeWindow;
	}
	
	/**
	 * format startTime("HH:mmZ")-endTime("HH:mmZ")
	 * @param timeWindow
	 * @return parsed timewindow
	 */
	private String parseTimeWindow(TimeWindow timeWindow) {
		StringBuilder timeSpan = new StringBuilder();
		SimpleDateFormat format = new SimpleDateFormat("HH:mm'Z'");
		
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.HOUR_OF_DAY, timeWindow.getStartHour());
		cal.set(Calendar.MINUTE, timeWindow.getStartMinute());
		timeSpan.append(format.format(cal.getTime()));
		
		timeSpan.append("-");
		
		cal.set(Calendar.HOUR_OF_DAY, timeWindow.getEndHour());
		cal.set(Calendar.MINUTE, timeWindow.getEndMinute());
		timeSpan.append(format.format(cal.getTime()));
		
		return timeSpan.toString();
	}
	
	/*
	 * replace the first db account with the new one
	 */
	private void alterDBInstanceAccount (String dbInstanceId, String dbName, String newAdminUser, String newAdminPassword) 
			throws CloudException, InternalException {
		//TODO, this behavior need to be changed. We cannot change any account of the instance, we should change the account who has the database privilege
		//Suggestion:
		//1. find an account who has ReadWrite privilege to only target database(no privilege to other database)
		//2. remove the account if found, otherwise don't change current accounts
		//3. create new account
		String oldAccountName = getDBInstanceAccount(dbInstanceId);
		createDBInstanceAccount(dbInstanceId, dbName, newAdminUser, newAdminPassword);
		removeDBInstanceAccount(dbInstanceId, oldAccountName);
	}
	
	/*
	 * get the first account name of DB instance
	 */
	private String getDBInstanceAccount(final String dbInstanceId) throws CloudException, InternalException {
		APITrace.begin(getProvider(), "RelationalDatabase.getDBInstanceAccount");
		try {
			HttpUriRequest request = AliyunRequestBuilder.get()
					.provider(getProvider())
					.category(AliyunRequestBuilder.Category.RDS)
					.parameter("Action", "DescribeAccounts")
					.parameter("DBInstanceId", dbInstanceId)
					.build();
			
			ResponseHandler<String> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, String>(
	        		new StreamToJSONObjectProcessor(),
	        		new DriverToCoreMapper<JSONObject, String>() {
	                    @Override
	                    public String mapFrom(JSONObject json) {
	                        try {
	                        	JSONArray accounts = json.getJSONObject("Accounts").getJSONArray("DBInstanceAccount");
	                        	if (accounts.length() > 0) {
	                        		return accounts.getJSONObject(0).getString("AccountName");
	                        	} 
	                        	return null;
	                        } catch (JSONException e) {
	                        	stdLogger.error("Failed to parse account for db instance " + dbInstanceId, e);
	                            throw new RuntimeException(e);
							} 
	                    }
	                },
	                JSONObject.class);
			
			return new AliyunRequestExecutor<String>(getProvider(),
	                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
	                request,
	                responseHandler).execute();
		} finally {
			APITrace.end();
		}
	}
	
	private void removeDBInstanceAccount (String dbInstanceId, String adminUser) throws InternalException, CloudException {
		APITrace.begin(getProvider(), "RelationalDatabase.removeDBInstanceAccount");
		try {
			HashMap<String, Object> params = new HashMap<String, Object>();
			params.put("DBInstanceId", dbInstanceId);
			params.put("AccountName", adminUser);
			executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS,
					"DeleteAccount", AliyunNetworkCommon.RequestMethod.POST, false,
					new AliyunValidateJsonResponseHandler(getProvider()));
		} finally {
			APITrace.end();
		}
	}

	/*
	 * create db instance account, and authorize read-write privilege for the user to a specific database.
	 */
	private void createDBInstanceAccount(String dbInstanceId, String dbName, String adminUser, String adminPassword)
			throws InternalException, CloudException {
		APITrace.begin(getProvider(), "RelationalDatabase.createDBInstanceAccount");
		try {
			//create db account
			HashMap<String, Object> params = new HashMap<String, Object>();
			params.put("DBInstanceId", dbInstanceId);
			params.put("AccountName", adminUser);
			params.put("AccountPassword", adminPassword);
			params.put("AccountDescription", "User " + adminUser + " for DB Instance " + dbInstanceId);
			executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS,
					"CreateAccount", AliyunNetworkCommon.RequestMethod.POST, false,
					new AliyunValidateJsonResponseHandler(getProvider()));

			//authorize READ&WRITE privilege for the account
			params = new HashMap<String, Object>();
			params.put("DBInstanceId", dbInstanceId);
			params.put("AccountName", adminUser);
			params.put("DBName", dbName);
			params.put("AccountpPrivilege", "ReadWrite");
			executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS,
					"GrantAccountPrivilege", AliyunNetworkCommon.RequestMethod.POST, false,
					new AliyunValidateJsonResponseHandler(getProvider()));
		} finally {
			APITrace.end();
		}
	}

	private void alterPreferredBackupWindow(String databaseInstanceId, TimeWindow preferredBackupWindow) 
			throws InternalException, CloudException {
		APITrace.begin(getProvider(), "RelationalDatabase.alterPreferredBackupWindow");
		try {
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("DBInstanceId", databaseInstanceId);
			params.put("PreferredBackupTime", parseTimeWindow(preferredBackupWindow));
			if (!preferredBackupWindow.getStartDayOfWeek().equals(preferredBackupWindow.getEndDayOfWeek())) {
				throw new InternalException("Backup window setting wrong: the start day and the end day should be the same!");
			} else {
				params.put("PreferredBackupPeriod", getProvider().capitalize(preferredBackupWindow.getStartDayOfWeek().name()));
			}
			executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS, 
					"ModifyBackupPolicy", AliyunNetworkCommon.RequestMethod.POST, false, 
					new AliyunValidateJsonResponseHandler(getProvider()));
		} finally {
			APITrace.end();
		}
	}
	
	private void alterPreferredMaintenanceWindow(String databaseInstanceId, TimeWindow preferredMaintenanceWindow) 
			throws InternalException, CloudException {
		APITrace.begin(getProvider(), "RelationalDatabase.alterPreferredMaintenanceWindow");
		try {
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("DBInstanceId", databaseInstanceId);
			params.put("MaintainTime", parseTimeWindow(preferredMaintenanceWindow));
			
			executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS, 
					"ModifyDBInstanceMaintainTime", AliyunNetworkCommon.RequestMethod.POST, false, 
					new AliyunValidateJsonResponseHandler(getProvider()));
		} finally {
			APITrace.end();
		}
	}
	
	private void alterDBInstanceAttribute(String databaseInstanceId, String productSize, int storageInGigabytes) 
			throws InternalException, CloudException {
		APITrace.begin(getProvider(), "RelationalDatabase.alterDBInstanceAttribute");
		try {
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("DBInstanceId", databaseInstanceId);
			params.put("PayType", "Postpaid");
			if (!getProvider().isEmpty(productSize)) {
				params.put("DBInstanceClass", productSize);
			}
			if (storageInGigabytes >= 5) {
				params.put("DBInstanceStorage", storageInGigabytes);
			}
			
			executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS, 
					"ModifyDBInstanceSpec", AliyunNetworkCommon.RequestMethod.POST, false, 
					new AliyunValidateJsonResponseHandler(getProvider()));
		} finally {
			APITrace.end();
		}
	}	
	
	private static <V> V executeDefaultRequest(Aliyun provider, Map<String, Object> params, 
			AliyunRequestBuilder.Category category, String action, RequestMethod requestMethod, 
			boolean clientToken, ResponseHandler<V> handler) throws InternalException, CloudException {
		
		AliyunRequestBuilder builder = null;
		if (requestMethod.equals(RequestMethod.GET)) { 
			builder = AliyunRequestBuilder.get();
			for (String key : params.keySet()) {
				builder = builder.parameter(key, params.get(key));
			}
		} else if (requestMethod.equals(RequestMethod.POST)) {
			builder = AliyunRequestBuilder.post();
			builder = builder.entity(params);
		} else {
			stdLogger.error("Not supported request method " + requestMethod.name());
			throw new InternalException("Not supported request method " + requestMethod.name());
		}
		builder = builder.provider(provider).category(category).parameter("Action", action);
		if (clientToken) {
			builder = builder.clientToken(true);
		}

		return new AliyunRequestExecutor<V>(
				provider,
				AliyunHttpClientBuilderFactory.newHttpClientBuilder(), 
				builder.build(),
				handler).execute();
	}

	@Override
	public String[] mapServiceAction(ServiceAction action) {
		return new String[0];
	}

	class DatabaseId {

		private String databaseInstanceId;
		private String databaseName;

		public DatabaseId(String databaseInstanceId, String databaseName) {
			this.databaseInstanceId = databaseInstanceId;
			this.databaseName = databaseName;
		}

		public DatabaseId(String databaseId) {
			String[] segments = databaseId.split(":");
			if (segments.length == 3) {
				this.databaseInstanceId = segments[1];
				this.databaseName = segments[2];
			}
		}

		public String getDatabaseInstanceId() {
			return databaseInstanceId;
		}

		public void setDatabaseInstanceId(String databaseInstanceId) {
			this.databaseInstanceId = databaseInstanceId;
		}

		public String getDatabaseName() {
			return databaseName;
		}

		public void setDatabaseName(String databaseName) {
			this.databaseName = databaseName;
		}

		public String getDatabaseId() {
			return "db:" + databaseInstanceId + ":" + databaseName;
		}

	}
}
