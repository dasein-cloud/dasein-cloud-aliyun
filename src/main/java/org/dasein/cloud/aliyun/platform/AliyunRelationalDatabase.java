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
    private static final int DefaultPageSize = 30;
	
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

		if (Arrays.asList("cn-hangzhou", "cn-qingdao", "cn-beijing", "cn-hongkong", "cn-shenzhen", "us-west-1").contains(regionId)) {
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
								stdLogger.error("parsing database instance attribute failed", e);
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
			engineName = "postgres";
		} else if (forEngine.equals(DatabaseEngine.SQLSERVER_EE)) {
			engineName = "sqlserver-ex";
		} else {
			return retProducts;
		}
		org.dasein.cloud.aliyun.platform.model.DatabaseEngine engine = provider.findEngine(engineName);
		for (org.dasein.cloud.aliyun.platform.model.DatabaseRegion region : engine.getRegions()) {
			if (region.getName().contains(getContext().getRegionId())) {

				for (org.dasein.cloud.aliyun.platform.model.DatabaseProduct product : region.getProducts()) {
					DatabaseProduct retProduct = new DatabaseProduct(engineName);
					retProduct.setCurrency(product.getCurrency());
					retProduct.setName(String.format("class:%s, memory %.2fMB, max iops %d, max connection %d",
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
			params.put("DBInstanceDescription", "Database Instance - " + product.getName() + " " + product.getStorageInGigabytes());
			params.put("SecurityIPList", "0.0.0.0/0");
			params.put("PayType", "Postpaid");
			if (!getProvider().isEmpty(product.getProviderDataCenterId())) {
				params.put("ZoneId", product.getProviderDataCenterId());
			}

			//create database instance
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
				params.put("CharacterSetName", "Latin1_General_CI_AS");
			}

			executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS,
					"CreateDatabase", AliyunNetworkCommon.RequestMethod.POST, false,
					new AliyunValidateJsonResponseHandler(getProvider()));

			//create database account
			createAccount(instanceId, databaseName, withAdminUser, withAdminPassword);

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
		
		if (!getProvider().isEmpty(databaseId.getDatabaseInstanceId())) {
			if (!getProvider().isEmpty(productSize) || storageInGigabytes >= 5) {
				alterDatabaseInstanceAttribute(databaseId.getDatabaseInstanceId(), productSize, storageInGigabytes);
			}
	
			if (!getProvider().isEmpty(databaseId.getDatabaseName()) 
					&& !getProvider().isEmpty(newAdminUser) 
					&& !getProvider().isEmpty(newAdminPassword)) {
				alterDatabaseAccount(databaseId.getDatabaseInstanceId(), databaseId.getDatabaseName(), newAdminUser, newAdminPassword);
			}
	
			if (preferredMaintenanceWindow != null) {
				alterPreferredMaintenanceWindow(databaseId.getDatabaseInstanceId(), preferredMaintenanceWindow);
			}
	
			if (preferredBackupWindow != null) {
				alterPreferredBackupWindow(databaseId.getDatabaseInstanceId(), preferredBackupWindow);
			}
		}
	}

	@Override
	public void removeDatabase(String providerDatabaseId) throws CloudException, InternalException {
		APITrace.begin(getProvider(), "RelationalDatabase.removeDatabase");
		try {
			DatabaseId databaseId = new DatabaseId(providerDatabaseId);
			if (!getProvider().isEmpty(databaseId.getDatabaseInstanceId()) && !getProvider().isEmpty(databaseId.getDatabaseName())) {
				Map<String, Object> params = new HashMap<String, Object>();
				params.put("DBInstanceId", databaseId.getDatabaseInstanceId());
				params.put("DBName", databaseId.getDatabaseName());
	
				executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS,
						"DeleteDatabase", AliyunNetworkCommon.RequestMethod.POST, false,
						new AliyunValidateJsonResponseHandler(getProvider()));
				
				List<Database> databases = queryDatabase(databaseId.getDatabaseInstanceId(), null);
				if (getProvider().isEmpty(databases)) {
					removeDatabaseInstance(databaseId.getDatabaseInstanceId());
				}
			}
		} finally {
			APITrace.end();
		}
	}

	@Override
	public void restart(String providerDatabaseId, boolean blockUntilDone) throws CloudException, InternalException {
		APITrace.begin(getProvider(), "RelationalDatabase.restart");
		try {
			DatabaseId databaseId = new DatabaseId(providerDatabaseId);
			if (!getProvider().isEmpty(databaseId.getDatabaseInstanceId())) {
				Map<String, Object> params = new HashMap<String, Object>();
				params.put("DBInstanceId", databaseId.getDatabaseInstanceId());
				executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS,
						"RestartDBInstance", AliyunNetworkCommon.RequestMethod.POST, false,
						new AliyunValidateJsonResponseHandler(getProvider()));
			}
		} finally {
			APITrace.end();
		}
	}
	
	@Override
	public Database getDatabase(String providerDatabaseId)
			throws CloudException, InternalException {
		final DatabaseId databaseId = new DatabaseId(providerDatabaseId);
		if (!getProvider().isEmpty(databaseId.getDatabaseInstanceId()) && !getProvider().isEmpty(databaseId.getDatabaseName())) {
			List<Database> databaseList = queryDatabase(databaseId.getDatabaseInstanceId(), databaseId.getDatabaseName());
			if (databaseList.size() > 0) {
				return databaseList.get(0);
			}
		}
		return null;
	}

	@Override
	public Iterable<Database> listDatabases() throws CloudException, InternalException {
		List<String> allDatabaseInstanceIds = queryDatabaseInstanceIds();
		List<Database> allDatabases = new ArrayList<Database>();
		for (String instanceId : allDatabaseInstanceIds) {	
			allDatabases.addAll(queryDatabase(instanceId, null));
		}			
		return allDatabases;
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
			DatabaseId databaseId = new DatabaseId(providerDatabaseId);
			if (!getProvider().isEmpty(databaseId.getDatabaseInstanceId())) {
				
				Map<String, Object> params = new HashMap<String, Object>();
				params.put("DBInstanceId", databaseId.getDatabaseInstanceId());
				
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
			}
		} finally {
			APITrace.end();
		}
	}

	@Override
	public void revokeAccess(String providerDatabaseId, String sourceCidr) throws CloudException, InternalException {
		APITrace.begin(getProvider(), "RelationalDatabase.revokeAccess");
		try {
			DatabaseId databaseId = new DatabaseId(providerDatabaseId);
			if (!getProvider().isEmpty(databaseId.getDatabaseInstanceId())) {
				Map<String, Object> params = new HashMap<String, Object>();
				params.put("DBInstanceId", databaseId.getDatabaseInstanceId());
				
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
			}
		} finally {
			APITrace.end();
		}
	}

	@Override
	public Iterable<String> listAccess(String toProviderDatabaseId) throws CloudException, InternalException {
		List<String> accessList = new ArrayList<String>();
		DatabaseId databaseId = new DatabaseId(toProviderDatabaseId);
		if (!getProvider().isEmpty(databaseId.getDatabaseInstanceId())) {
			JSONObject databaseInstanceAttribute = getDatabaseInstanceAttribute(databaseId.getDatabaseInstanceId());
			try {
				String securityIPList = databaseInstanceAttribute.getString("SecurityIPList");
				
				if (!getProvider().isEmpty(securityIPList)) {
	
					String[] segments = securityIPList.split(",");
					if (segments.length > 0) {
						Collections.addAll(accessList, segments);
					}
				}
			} catch (JSONException e) {
				stdLogger.error("parsing security ip list from database instance " + databaseId.getDatabaseInstanceId() + " failed", e);
				throw new InternalException(e);
			}
		}
		return accessList;
	}
	
	private static final SimpleDateFormat daseinFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
	private static final SimpleDateFormat aliyunFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	private static final SimpleDateFormat aliyunFormatterWithoutSecond = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'"); 
	@Override
	public DatabaseBackup getUsableBackup(String providerDatabaseId, String beforeTimestamp)
			throws CloudException, InternalException {
		
		Date baseDate = null;
		try {
			baseDate = daseinFormatter.parse(beforeTimestamp);
		} catch (ParseException e) {
			stdLogger.error("parse beforeTimestamp failed", e);
			throw new RuntimeException(e);
		}
		
		DatabaseBackup closestBackup = null;
		Iterator<DatabaseBackup> backupIter = listBackups(providerDatabaseId).iterator();
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
	        
	        if (getProvider().isEmpty(databaseId.getDatabaseInstanceId())) {
	        	return allBackups;
	        }
	        
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
									backups.add(backup);
								}
								totalPageNumber.addAndGet(json.getInt("TotalRecordCount") / DefaultPageSize +
	                                    json.getInt("TotalRecordCount") % DefaultPageSize > 0 ? 1 : 0);
	                            currentPageNumber.incrementAndGet();
								return backups;
							} catch (JSONException e) {
								stdLogger.error("parsing database instance attribute failed", e);
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
	    				.parameter("StartTime", aliyunFormatterWithoutSecond.format(new Date(0)))
	    				.parameter("EndTime", aliyunFormatterWithoutSecond.format(new Date()))			//TODO check synchronized with server
	    				.parameter("PageSize", DefaultPageSize)
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
                      			
                      			//TODO: Aliyun returns Monday,Wednesday,Friday,Sunday. How to transfer to Dasein TimeWindow period
//                      			timeWindow.setStartDayOfWeek(
//										DayOfWeek.valueOf(json.getString("PreferredBackupPeriod").toUpperCase()));
//                      			timeWindow.setEndDayOfWeek(
//										DayOfWeek.valueOf(json.getString("PreferredBackupPeriod").toUpperCase()));
                      			
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

	private JSONObject getDatabaseInstanceAttribute(String databaseInstanceId) throws InternalException, CloudException {
		APITrace.begin(getProvider(), "RelationalDatabase.getDatabaseInstanceAttribute");
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
								stdLogger.error("parsing database instance attribute failed", e);
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
	 * Aliyun: 
	 * 	one instance can have multiple databases, 
	 *  one instance can have multiple accounts, 
	 *  one account can have specific privileges to multiple databases.
	 * Mapping to Dasein implementations: 
	 * 	one instance can have only one database
	 *  one instance can have only on account
	 *  this account have READ/WRITE privilege to the only database of this instance.
	 * This function:
	 *  1) change account password
	 *  2) replace account
	 *  3) create new account
	 */
	private void alterDatabaseAccount (String databaseInstanceId, String databaseName, String newAdminUser, String newAdminPassword) 
			throws CloudException, InternalException {
		List<String> oldAccountNames = getAccounts(databaseInstanceId, databaseName);
		if (getProvider().isEmpty(oldAccountNames)) { //create new
			createAccount(databaseInstanceId, databaseName, newAdminUser, newAdminPassword);
		} else {
			if (oldAccountNames.contains(newAdminUser)) {	//reset password
				resetAccountPassword(databaseInstanceId, newAdminUser, newAdminPassword);
			} else {	//replace old accounts with new account
				createAccount(databaseInstanceId, databaseName, newAdminUser, newAdminPassword);
				for (String oldAccountName: oldAccountNames) {
					removeAccount(databaseInstanceId, oldAccountName);
				}
			}
		}
	}
	
	private void resetAccountPassword (String databaseInstanceId, String accountName, String newAccountPassword) 
			throws InternalException, CloudException {
		APITrace.begin(getProvider(), "RelationalDatabase.resetAccountPassword");
		try {
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("DBInstanceId", databaseInstanceId);
			params.put("AccountName", accountName);
			params.put("AccountPassword", newAccountPassword);
	
			executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS,
					"ResetAccountPassword", AliyunNetworkCommon.RequestMethod.POST, false,
					new AliyunValidateJsonResponseHandler(getProvider()));
		} finally {
			APITrace.end();
		}
	}

	private List<String> getAccounts(final String databaseInstanceId, final String databaseName) throws CloudException, InternalException {
		APITrace.begin(getProvider(), "RelationalDatabase.getAccounts");
		try {
			HttpUriRequest request = AliyunRequestBuilder.get()
					.provider(getProvider())
					.category(AliyunRequestBuilder.Category.RDS)
					.parameter("Action", "DescribeAccounts")
					.parameter("DBInstanceId", databaseInstanceId)
					.build();
			
			ResponseHandler<List<String>> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, List<String>>(
	        		new StreamToJSONObjectProcessor(),
	        		new DriverToCoreMapper<JSONObject, List<String>>() {
	                    @Override
	                    public List<String> mapFrom(JSONObject json) {
	                        try {
	                        	List<String> accountNames = new ArrayList<String>();
	                        	JSONArray accounts = json.getJSONObject("Accounts").getJSONArray("DBInstanceAccount");
	                        	for (int i = 0; i < accounts.length(); i++) {
	                        		JSONObject account = accounts.getJSONObject(i);
	                        		JSONArray databasePrivileges = account.getJSONObject("DatabasePrivilegess").getJSONArray("DatabasePrivileges");
	                        		for (int j = 0; j < databasePrivileges.length(); j++) {
	                        			JSONObject databasePrivilege = databasePrivileges.getJSONObject(j);
	                        			if (databasePrivilege.getString("DBName").equals(databaseName) && 
	                        					databasePrivilege.getString("AccountPrivilege").equals("ReadWrite") &&
	                        					databasePrivilege.getString("AccountStatus").equals("Available")) {
	                        				accountNames.add(account.getString("AccountName"));
	                        			}
	                        		}
	                        	} 
	                        	return accountNames;
	                        } catch (JSONException e) {
	                        	stdLogger.error("Failed to parse account for database instance " + databaseInstanceId, e);
	                            throw new RuntimeException(e);
							} 
	                    }
	                },
	                JSONObject.class);
			
			return new AliyunRequestExecutor<List<String>>(getProvider(),
	                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
	                request,
	                responseHandler).execute();
		} finally {
			APITrace.end();
		}
	}
	
	private void removeAccount (String databaseInstanceId, String adminUser) throws InternalException, CloudException {
		APITrace.begin(getProvider(), "RelationalDatabase.removeAccount");
		try {
			HashMap<String, Object> params = new HashMap<String, Object>();
			params.put("DBInstanceId", databaseInstanceId);
			params.put("AccountName", adminUser);
			executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS,
					"DeleteAccount", AliyunNetworkCommon.RequestMethod.POST, false,
					new AliyunValidateJsonResponseHandler(getProvider()));
		} finally {
			APITrace.end();
		}
	}

	/*
	 * create database instance account, and authorize read-write privilege for the user to a specific database.
	 */
	private void createAccount(String databaseInstanceId, String databaseName, String adminUser, String adminPassword)
			throws InternalException, CloudException {
		APITrace.begin(getProvider(), "RelationalDatabase.createAccount");
		try {
			//create database instance account
			HashMap<String, Object> params = new HashMap<String, Object>();
			params.put("DBInstanceId", databaseInstanceId);
			params.put("AccountName", adminUser);
			params.put("AccountPassword", adminPassword);
			params.put("AccountDescription", "User " + adminUser + " for database instance " + databaseInstanceId);
			executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS,
					"CreateAccount", AliyunNetworkCommon.RequestMethod.POST, false,
					new AliyunValidateJsonResponseHandler(getProvider()));

			//authorize READ&WRITE privilege for the account
			params = new HashMap<String, Object>();
			params.put("DBInstanceId", databaseInstanceId);
			params.put("AccountName", adminUser);
			params.put("DBName", databaseName);
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
	
	private void alterDatabaseInstanceAttribute(String databaseInstanceId, String productSize, int storageInGigabytes) 
			throws InternalException, CloudException {
		APITrace.begin(getProvider(), "RelationalDatabase.alterDatabaseInstanceAttribute");
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
	
	private List<String> queryDatabaseInstanceIds() throws InternalException, CloudException {
		APITrace.begin(getProvider(), "RelationalDatabase.queryDatabaseInstanceIds");
		try {
			List<String> allDatabaseInstanceIds = new ArrayList<String>();
			final AtomicInteger totalPageNumber = new AtomicInteger(1);
			final AtomicInteger currentPageNumber = new AtomicInteger(1);
	
			ResponseHandler<List<String>> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, List<String>>(
					new StreamToJSONObjectProcessor(),
					new DriverToCoreMapper<JSONObject, List<String>>() {
						@Override
						public List<String> mapFrom(JSONObject json) {
							try {
								List<String> databaseInstanceIds = new ArrayList<String>();
								JSONArray databaseInstances = json.getJSONObject("Items").getJSONArray("DBInstance");
								for (int i = 0; i < databaseInstances.length(); i++) {
									databaseInstanceIds.add(databaseInstances.getJSONObject(i).getString("DBInstanceId"));
	
								}
								totalPageNumber.addAndGet(json.getInt("TotalRecordCount") / DefaultPageSize +
										json.getInt("TotalRecordCount") % DefaultPageSize > 0 ? 1 : 0);
								currentPageNumber.incrementAndGet();
								return databaseInstanceIds;
							} catch (JSONException e) {
								stdLogger.error("parsing database instance attribute failed", e);
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
						.parameter("DBInstanceType", "Primary")
						.parameter("PageSize", DefaultPageSize)
						.parameter("PageNumber", currentPageNumber)
						.build();
	
				allDatabaseInstanceIds.addAll(new AliyunRequestExecutor<List<String>>(getProvider(),
						AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
						request,
						responseHandler).execute());
	
			} while (currentPageNumber.intValue() < totalPageNumber.intValue());
			
			return allDatabaseInstanceIds;
		} finally {
			APITrace.end();
		}
	}
	
	/*
	 * If datbaseName is null, then get all the databases associated with databaseInstanceId.
	 * Otherwise, get the specific database.
	 */
	private List<Database> queryDatabase(final String databaseInstanceId, final String datbaseName) 
			throws InternalException, CloudException {

		APITrace.begin(getProvider(), "RelationalDatabase.queryDatabase");
		try {
			final JSONObject databaseInstanceAttribute = getDatabaseInstanceAttribute(databaseInstanceId);
			
			AliyunRequestBuilder builder = AliyunRequestBuilder.get()
					.provider(getProvider())
					.category(AliyunRequestBuilder.Category.RDS)
					.parameter("Action", "DescribeDatabases")
					.parameter("DBInstanceId", databaseInstanceId);
			
			if (!getProvider().isEmpty(datbaseName)) { //get specific database
				builder = builder.parameter("DBName", datbaseName);
			}
			HttpUriRequest request = builder.build();
			
			ResponseHandler<List<Database>> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, List<Database>>(
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
									database.setAllocatedStorageInGb(databaseInstanceAttribute.getInt("DBInstanceStorage"));
									database.setBackupWindow(getBackupTimeWindow(databaseInstanceId));
									database.setEngineVersion(databaseInstanceAttribute.getString("EngineVersion"));
									database.setHostName(databaseInstanceAttribute.getString("ConnectionString"));
									database.setHostPort(databaseInstanceAttribute.getInt("Port"));
									database.setCreationTimestamp(new SimpleDateFormat("YYYY-MM-DD'T'hh:mm:ss'Z'").parse(databaseInstanceAttribute.getString("CreationTime")).getTime());
									database.setMaintenanceWindow(formatTimeWindow(databaseInstanceAttribute.getString("MaintainTime")));
									database.setName(respDatabase.getString("DBName"));
									database.setProductSize(databaseInstanceAttribute.getString("DBInstanceClass"));
									database.setProviderDatabaseId(new DatabaseId(databaseInstanceId, respDatabase.getString("DBName")).getDatabaseId());
									database.setProviderOwnerId(getContext().getAccountNumber());
									database.setProviderRegionId(getContext().getRegionId());
									database.setHighAvailability(true);	
									
									if ("Creating".equals(respDatabase.getString("DBStatus"))) {
										database.setCurrentState(DatabaseState.PENDING);
									} else if ("Running".equals(respDatabase.getString("DBStatus"))) {
										database.setCurrentState(DatabaseState.AVAILABLE);
									} else if ("Deleting".equals(respDatabase.getString("DBStatus"))) {
										database.setCurrentState(DatabaseState.DELETING);
									} else {
										database.setCurrentState(DatabaseState.UNKNOWN);
									}
	
									if ("MySQL".equals(databaseInstanceAttribute.getString("Engine"))) {
										database.setEngine(DatabaseEngine.MYSQL);
									} else if ("SQLServer".equals(databaseInstanceAttribute.getString("Engine"))) {
										database.setEngine(DatabaseEngine.SQLSERVER_EE);
									} else if ("PostgreSQL".equals(databaseInstanceAttribute.getString("Engine"))) {
										database.setEngine(DatabaseEngine.POSTGRES);
									} else {
										throw new InternalException("Database engine " + databaseInstanceAttribute.getString("Engine") + " is not supported!");
									}
	
									databases.add(database);
								}
								return databases;
							} catch (JSONException e) {
								stdLogger.error("parsing database instance attribute failed", e);
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
			
			return new AliyunRequestExecutor<List<Database>>(getProvider(),
					AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
					request,
					responseHandler).execute();
		} finally {
			APITrace.end();
		}
	}
	
	private void removeDatabaseInstance(String databaseInstanceId) throws InternalException, CloudException {
		APITrace.begin(getProvider(), "RelationalDatabase.removeDatabaseInstance");
		try {
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("DBInstanceId", databaseInstanceId);
			
			executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS,
					"DeleteDatabase", AliyunNetworkCommon.RequestMethod.POST, false,
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
			return "database:" + databaseInstanceId + ":" + databaseName;
		}

	}
}
