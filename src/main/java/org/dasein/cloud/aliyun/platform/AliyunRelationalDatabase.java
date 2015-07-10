package org.dasein.cloud.aliyun.platform;

import static org.dasein.cloud.platform.DatabaseLicenseModel.BRING_YOUR_OWN_LICENSE;
import static org.dasein.cloud.platform.DatabaseLicenseModel.LICENSE_INCLUDED;
import static org.dasein.cloud.platform.DatabaseLicenseModel.POSTGRESQL_LICENSE;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.dasein.cloud.aliyun.platform.model.DatabaseProvider;
import org.dasein.cloud.aliyun.util.requester.AliyunHttpClientBuilderFactory;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestBuilder;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestExecutor;
import org.dasein.cloud.aliyun.util.requester.AliyunResponseHandlerWithMapper;
import org.dasein.cloud.aliyun.util.requester.AliyunValidateJsonResponseHandler;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.platform.AbstractRelationalDatabaseSupport;
import org.dasein.cloud.platform.ConfigurationParameter;
import org.dasein.cloud.platform.Database;
import org.dasein.cloud.platform.DatabaseBackup;
import org.dasein.cloud.platform.DatabaseBackupState;
import org.dasein.cloud.platform.DatabaseConfiguration;
import org.dasein.cloud.platform.DatabaseEngine;
import org.dasein.cloud.platform.DatabaseProduct;
import org.dasein.cloud.platform.DatabaseState;
import org.dasein.cloud.platform.RelationalDatabaseCapabilities;
import org.dasein.cloud.util.requester.DriverToCoreMapper;
import org.dasein.cloud.util.requester.streamprocessors.StreamToJSONObjectProcessor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class AliyunRelationalDatabase extends
		AbstractRelationalDatabaseSupport<Aliyun> {
	
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
		return true;
	}

	@Override
	public String[] mapServiceAction(ServiceAction action) {
		return new String[0]; 
	}
	
	@Override
	public DatabaseConfiguration getConfiguration(String providerConfigurationId)
			throws CloudException, InternalException {
		Iterator<DatabaseConfiguration> configurationIter = listConfigurations().iterator();
		while (configurationIter.hasNext()) {
			DatabaseConfiguration configuration = configurationIter.next();
			if (configuration.getProviderConfigurationId().equals(providerConfigurationId)) {
				return configuration;
			}
		}
		return null;
	}

	@Override
	public Iterable<DatabaseConfiguration> listConfigurations()
			throws CloudException, InternalException {
		
		List<DatabaseConfiguration> configurations = new ArrayList<DatabaseConfiguration>();
		Iterator<DatabaseEngine> engineIter = getDatabaseEngines().iterator();
		while (engineIter.hasNext()) {
			DatabaseEngine engine = engineIter.next();
			configurations.add(new DatabaseConfiguration(
					this,
					engine,
					"config:" + engine.name(),
					"config:" + engine.name(),
					"Database configuration for engine " + engine.name()));
		}
		return configurations;
	}

	@Override
	public Collection<ConfigurationParameter> listParameters(
			String forProviderConfigurationId) throws CloudException,
			InternalException {
		
		DatabaseConfiguration configuration = getConfiguration(forProviderConfigurationId);
		
		String engineName = null;
		if (configuration.getEngine().equals(DatabaseEngine.MYSQL)) {
			engineName = "MySQL";
		} else if (configuration.getEngine().equals(DatabaseEngine.SQLSERVER_EE)) {
			engineName = "SQLServer";
		} else if (configuration.getEngine().equals(DatabaseEngine.POSTGRES)) {
			engineName = "PostgreSQL";
		} else {
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
								parameter.setValidation(templateRecord.getString("CheckingCode"));	//TODO check if it needs to be shown to the user
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
		
	}

	@Override
	public void removeConfiguration(String providerConfigurationId)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Aliyun doesn't support remove configuration!");
	}

	@Override
	public void resetConfiguration(String providerConfigurationId,
			String... parameters) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Aliyun doesn't support reset configuration!");
	}

	@Override
	public void updateConfiguration(String providerConfigurationId,
			ConfigurationParameter... parameters) throws CloudException,
			InternalException {
		throw new OperationNotSupportedException("Aliyun doesn't support update configuration and parameters!");
	}
	
	@Override
	public void addAccess(String providerDatabaseId, String sourceCidr)
			throws CloudException, InternalException {
		
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
		
		AliyunNetworkCommon.executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS, 
				"ModifySecurityIps", AliyunNetworkCommon.RequestMethod.POST, false, 
				new AliyunValidateJsonResponseHandler(getProvider()));
		
	}

	/**
	 * alter following items:
	 * - productSize
	 * - storageInGigabytes
	 * - newAdminUser, newAdminPassword
	 * - preferredMaintenanceWindow
	 * - preferredBackupWindow
	 */
	@Override
	public void alterDatabase(String providerDatabaseId,
			boolean applyImmediately, String productSize,
			int storageInGigabytes, String configurationId,
			String newAdminUser, String newAdminPassword, int newPort,
			int snapshotRetentionInDays, TimeWindow preferredMaintenanceWindow,
			TimeWindow preferredBackupWindow) throws CloudException,
			InternalException {
		
		DatabaseId databaseId = new DatabaseId(providerDatabaseId);
		
		Map<String, Object> params = null;
		
		//alter db instance attribute
		if (!getProvider().isEmpty(productSize) || storageInGigabytes >= 5) {
			params = new HashMap<String, Object>();
			params.put("DBInstanceId", databaseId.getDatabaseInstanceId());
			params.put("PayType", "Postpaid");
			if (!getProvider().isEmpty(productSize)) {
				params.put("DBInstanceClass", productSize);	//TODO check
			}
			if (storageInGigabytes >= 5) {
				params.put("DBInstanceStorage", storageInGigabytes);
			}
			
			AliyunNetworkCommon.executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS, 
					"ModifyDBInstanceSpec", AliyunNetworkCommon.RequestMethod.POST, false, 
					new AliyunValidateJsonResponseHandler(getProvider()));
		}
		
		//alter newAdminUser and newAdminPassword
		if (!getProvider().isEmpty(newAdminUser) && !getProvider().isEmpty(newAdminPassword)) {
			alterDBInstanceAccount(databaseId.getDatabaseInstanceId(), databaseId.getDatabaseName(), newAdminUser, newAdminPassword);
		}
		
		//alter preferredMaintenanceWindow
		if (preferredMaintenanceWindow != null) {
			params = new HashMap<String, Object>();
			params.put("DBInstanceId", databaseId.getDatabaseInstanceId());
			params.put("MaintainTime", parseTimeWindow(preferredMaintenanceWindow));
			
			AliyunNetworkCommon.executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS, 
					"ModifyDBInstanceMaintainTime", AliyunNetworkCommon.RequestMethod.POST, false, 
					new AliyunValidateJsonResponseHandler(getProvider()));
		}
		
		//alter preferredBackupWindow
		if (preferredBackupWindow != null) {
			params = new HashMap<String, Object>();
			params.put("DBInstanceId", databaseId.getDatabaseInstanceId());
			params.put("PreferredBackupTime", parseTimeWindow(preferredBackupWindow));
			if (!preferredBackupWindow.getStartDayOfWeek().equals(preferredBackupWindow.getEndDayOfWeek())) {
				throw new InternalException("Backup window setting wrong: the start day and the end day should be the same!");
			} else {
				params.put("PreferredBackupPeriod", getProvider().capitalize(preferredBackupWindow.getStartDayOfWeek().name()));
			}
			AliyunNetworkCommon.executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS, 
					"ModifyBackupPolicy", AliyunNetworkCommon.RequestMethod.POST, false, 
					new AliyunValidateJsonResponseHandler(getProvider()));
		}
	}

	/**
	 * Create DB instance, database and admin user for the instance
	 * @param databaseName
	 * @param product
	 * @param databaseVersion
	 * @param withAdminUser
	 * @param withAdminPassword
	 * @param hostPort
	 * @return database instance id
	 * @throws CloudException
	 * @throws InternalException
	 */
	@Override
	public String createFromScratch(String databaseName,
			DatabaseProduct product, String databaseVersion,
			String withAdminUser, String withAdminPassword, int hostPort)
			throws CloudException, InternalException {
		
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
		String instanceId = (String) AliyunNetworkCommon.executeDefaultRequest(getProvider(), params, 
				AliyunRequestBuilder.Category.RDS, "CreateDBInstance", AliyunNetworkCommon.RequestMethod.POST, true, 
				AliyunNetworkCommon.getResponseMapHandler(getProvider(), "DBInstanceId")).get("DBInstanceId");
		
		//create database
		params = new HashMap<String, Object>();
		params.put("DBInstanceId", instanceId);
		params.put("DBName", databaseName);
		if (product.getEngine().equals(DatabaseEngine.MYSQL)) {
			params.put("CharacterSetName", "utf8");
		} else if (product.getEngine().equals(DatabaseEngine.POSTGRES)) {
			params.put("CharacterSetName", "UTF8");
		} else if (product.getEngine().equals(DatabaseEngine.SQLSERVER_EE)) {
			params.put("CharacterSetName", "Chinese_PRC_CI_AS");
		}
		
		AliyunNetworkCommon.executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS, 
				"CreateDatabase", AliyunNetworkCommon.RequestMethod.POST, false, 
				new AliyunValidateJsonResponseHandler(getProvider()));
		
		//create database account
		createDBInstanceAccount(instanceId, databaseName, withAdminUser, withAdminPassword);
		
		return instanceId;
	}

	@Override
	public Database getDatabase(String providerDatabaseId)
			throws CloudException, InternalException {
		
		final DatabaseId databaseId = new DatabaseId(providerDatabaseId);
		final JSONObject dbInstanceAttribute = getDBInstanceAttribute(databaseId.getDatabaseInstanceId());
		
		HttpUriRequest request = AliyunRequestBuilder.get()
				.provider(getProvider())
				.category(AliyunRequestBuilder.Category.RDS)
				.parameter("Action", "DescribeDBInstances")
				.parameter("DBInstanceId", databaseId.getDatabaseInstanceId())
				.parameter("DBName", databaseId.getDatabaseName())
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
								database.setName(dbInstanceAttribute.getString("DBInstanceClass"));
								database.setProductSize(dbInstanceAttribute.getString("DBInstanceClass")); //TODO check
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
	}

	@Override
	public Iterable<DatabaseEngine> getDatabaseEngines() throws CloudException,
			InternalException {
		return Arrays.asList(
				DatabaseEngine.MYSQL,
				DatabaseEngine.SQLSERVER_EE,
				DatabaseEngine.POSTGRES);
	}

	@Override
	public String getDefaultVersion(DatabaseEngine forEngine)
			throws CloudException, InternalException {
		if (forEngine.equals(DatabaseEngine.MYSQL)) {
			return "5.5";
		} else if (forEngine.equals(DatabaseEngine.SQLSERVER_EE)) {
			return "2008r2";
		} else if (forEngine.equals(DatabaseEngine.POSTGRES)) {
			return "9.4";
		} else {
			throw new OperationNotSupportedException("Aliyun doesn't support database engine " + forEngine.name());
		}
	}

	@Override
	public Iterable<String> getSupportedVersions(DatabaseEngine forEngine)
			throws CloudException, InternalException {
		if (forEngine.equals(DatabaseEngine.MYSQL)) {
			return Arrays.asList("5.5", "5.6");
		} else if (forEngine.equals(DatabaseEngine.SQLSERVER_EE)) {
			return Arrays.asList("2008r2");
		} else if (forEngine.equals(DatabaseEngine.POSTGRES)) {
			return Arrays.asList("9.4");
		} else {
			throw new OperationNotSupportedException("Aliyun doesn't support database engine " + forEngine.name());
		}
	}

	@Override
	public Iterable<DatabaseProduct> listDatabaseProducts(
			DatabaseEngine forEngine) throws CloudException, InternalException {
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
			stdLogger.error("not support database engine " + forEngine.name());
			throw new OperationNotSupportedException("Aliyun doesn't support database engine " + forEngine.name());
		}
		org.dasein.cloud.aliyun.platform.model.DatabaseEngine engine = provider.findEngine(engineName);
		for (org.dasein.cloud.aliyun.platform.model.DatabaseRegion region : engine.getRegions()) {
			if (region.getName().equals(getContext().getRegionId())) {
				
				for (org.dasein.cloud.aliyun.platform.model.DatabaseProduct product : region.getProducts()) {
					DatabaseProduct retProduct = new DatabaseProduct(engineName);
					retProduct.setCurrency(product.getCurrency());
					retProduct.setName(product.getName());
					retProduct.setProductSize(product.getName()); //TODO check
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
	public Iterable<String> listAccess(String toProviderDatabaseId)
			throws CloudException, InternalException {
		
		DatabaseId databaseId = new DatabaseId(toProviderDatabaseId);
		String databaseInstanceId = databaseId.getDatabaseInstanceId();
		
		JSONObject databaseInstanceAttribute = getDBInstanceAttribute(databaseInstanceId);	
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
			stdLogger.error("parsing security ip list from database instance " + databaseInstanceId + " failed", e);
			throw new InternalException(e);
		}
	}

	@Override
	public Iterable<ResourceStatus> listDatabaseStatus() throws CloudException,
			InternalException {
		List<ResourceStatus> statuses = new ArrayList<ResourceStatus>();
		Iterator<Database> databaseIter = listDatabases().iterator();
		while (databaseIter.hasNext()) {
			Database database = databaseIter.next();
			statuses.add(new ResourceStatus(database.getProviderDatabaseId(), database.getCurrentState().name()));
		}
		return statuses;
	}

	@Override
	public Iterable<Database> listDatabases() throws CloudException,
			InternalException {
		
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
									database.setName(dbInstanceAttribute.getString("DBInstanceClass"));
									database.setProductSize(dbInstanceAttribute.getString("DBInstanceClass")); //TODO check
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
	}

	@Override
	public void removeDatabase(String providerDatabaseId)
			throws CloudException, InternalException {
		
		DatabaseId databaseId = new DatabaseId(providerDatabaseId);
		
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("DBInstanceId", databaseId.getDatabaseInstanceId());
		params.put("DBName", databaseId.getDatabaseName());
		
		AliyunNetworkCommon.executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS, 
				"DeleteDatabase", AliyunNetworkCommon.RequestMethod.POST, false, 
				new AliyunValidateJsonResponseHandler(getProvider()));
	}

	@Override
	public void restart(String providerDatabaseId, boolean blockUntilDone)
			throws CloudException, InternalException {
		
		DatabaseId databaseId = new DatabaseId(providerDatabaseId);
		
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("DBInstanceId", databaseId.getDatabaseInstanceId());
		
		AliyunNetworkCommon.executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS, 
				"RestartDBInstance", AliyunNetworkCommon.RequestMethod.POST, false, 
				new AliyunValidateJsonResponseHandler(getProvider()));
	}

	@Override
	public void revokeAccess(String providerDatabaseId, String sourceCidr)
			throws CloudException, InternalException {

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
		
		AliyunNetworkCommon.executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS, 
				"ModifySecurityIps", AliyunNetworkCommon.RequestMethod.POST, false, 
				new AliyunValidateJsonResponseHandler(getProvider()));
	}

	@Override
	public DatabaseBackup getUsableBackup(String providerDbId,
			String beforeTimestamp) throws CloudException, InternalException {
		
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
	public Iterable<DatabaseBackup> listBackups(
			String forOptionalProviderDatabaseId) throws CloudException,
			InternalException {
		
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
	}

	@Override
	public void createFromBackup(DatabaseBackup backup,
			String databaseCloneToName) throws CloudException,
			InternalException {
		throw new OperationNotSupportedException("Cannot create a db instance from backup by name!");
	}

	@Override
	public void removeBackup(DatabaseBackup backup) throws CloudException,
			InternalException {
		throw new OperationNotSupportedException("Remove backup is not supported by aliyun!");
	}

	@Override
	public void restoreBackup(DatabaseBackup backup) throws CloudException,
			InternalException {
		throw new OperationNotSupportedException("Cannot restore backup to the original db instance!");
	}
	
	/**
	 * get backup time window by database instance id
	 * @param databaseInstanceId
	 * @return backup time window
	 * @throws InternalException
	 * @throws CloudException
	 */
	private TimeWindow getBackupTimeWindow(String databaseInstanceId) throws InternalException, CloudException {
		
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("DBInstanceId", databaseInstanceId);
		
		Map<String, Object> backupTimeWindow = AliyunNetworkCommon.executeDefaultRequest(getProvider(), 
				params, AliyunRequestBuilder.Category.RDS, "DescribeBackupPolicy", 
				AliyunNetworkCommon.RequestMethod.POST, false, 
				AliyunNetworkCommon.getResponseMapHandler(getProvider(), "PreferredBackupTime", "PreferredBackupPeriod"));
		
		TimeWindow timeWindow = new TimeWindow();
		SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm'Z'");
		String[] segments = ((String) backupTimeWindow.get("PreferredBackupTime")).split("-");
		try {
			Calendar cal = Calendar.getInstance();
			cal.setTime(timeFormatter.parse(segments[0].trim()));
			
			timeWindow.setStartHour(cal.get(Calendar.HOUR_OF_DAY));
			timeWindow.setStartMinute(cal.get(Calendar.MINUTE));
			cal.setTime(timeFormatter.parse(segments[1].trim()));
			
			timeWindow.setEndHour(cal.get(Calendar.HOUR_OF_DAY));
			timeWindow.setEndMinute(cal.get(Calendar.MINUTE));
			
			timeWindow.setStartDayOfWeek(DayOfWeek.valueOf(backupTimeWindow.get("PreferredBackupPeriod").toString().toUpperCase()));
			timeWindow.setEndDayOfWeek(DayOfWeek.valueOf(backupTimeWindow.get("PreferredBackupPeriod").toString().toUpperCase()));
			
			return timeWindow;
		} catch (ParseException e) {
			stdLogger.error("parsing start and end hour/minutes failed for backup time", e);
			throw new InternalException(e);
		}
		
	}
	
	/**
	 * if current time is closer to the base time than then old time do, return true, otherwise, return false
	 * @param baseTime
	 * @param currentTime
	 * @param oldTime
	 * @return
	 */
	private boolean isCurrentTimeCloser(Date baseTime, Date currentTime, Date oldTime) {
		if (Math.abs(currentTime.getTime() - baseTime.getTime()) < Math.abs(oldTime.getTime() - baseTime.getTime())) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * get DB instance attribute JSON
	 * @param databaseInstanceId
	 * @return DB instance attribute JSON
	 * @throws InternalException
	 * @throws CloudException
	 */
	private JSONObject getDBInstanceAttribute(String databaseInstanceId) throws InternalException, CloudException {
		
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
	
	/**
	 * alter db instance admin account, remove old and add new
	 * @param dbInstanceId
	 * @param newAdminUser
	 * @param newAdminPassword
	 * @throws CloudException
	 * @throws InternalException
	 */
	private void alterDBInstanceAccount (String dbInstanceId, String dbName, String newAdminUser, String newAdminPassword) 
			throws CloudException, InternalException {
		String oldAccountName = getDBInstanceAccount(dbInstanceId);
		createDBInstanceAccount(dbInstanceId, dbName, newAdminUser, newAdminPassword);
		removeDBInstanceAccount(dbInstanceId, oldAccountName);
	}
	
	/**
	 * get db instance account name
	 * @param dbInstanceId
	 * @return account name
	 * @throws CloudException
	 * @throws InternalException
	 */
	private String getDBInstanceAccount (final String dbInstanceId) 
			throws CloudException, InternalException {
		
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
                        	} else {
                        		return null;
                        	}
                        } catch (JSONException e) {
                        	stdLogger.error("Failed to parse account for db instance " + dbInstanceId, e);
                            throw new RuntimeException(e.getMessage());
						} 
                    }
                },
                JSONObject.class);
		
		return new AliyunRequestExecutor<String>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                responseHandler).execute();
	}
	
	/**
	 * create db instance account, and authorize read-write privilege for the user to a specific database.
	 * @param dbInstanceId
	 * @param dbName
	 * @param adminUser
	 * @param adminPassword
	 * @throws InternalException
	 * @throws CloudException
	 */
	private void createDBInstanceAccount (String dbInstanceId, String dbName, String adminUser, String adminPassword) throws InternalException, CloudException {
		
		//create db account
		HashMap<String, Object> params = new HashMap<String, Object>();
		params.put("DBInstanceId", dbInstanceId);
		params.put("AccountName", adminUser);
		params.put("AccountPassword", adminPassword);
		params.put("AccountDescription", "User " + adminUser + " for DB Instance " + dbInstanceId);
		
		AliyunNetworkCommon.executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS, 
				"CreateAccount", AliyunNetworkCommon.RequestMethod.POST, false, 
				new AliyunValidateJsonResponseHandler(getProvider()));
		
		//authorize READ&WRITE privilege for the account
		params = new HashMap<String, Object>();
		params.put("DBInstanceId", dbInstanceId);
		params.put("AccountName", adminUser);
		params.put("DBName", dbName);
		params.put("AccountpPrivilege", "ReadWrite");
		
		AliyunNetworkCommon.executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS, 
				"GrantAccountPrivilege", AliyunNetworkCommon.RequestMethod.POST, false, 
				new AliyunValidateJsonResponseHandler(getProvider()));
	}
	
	/**
	 * remove db instance account
	 * @param dbInstanceId
	 * @param adminUser
	 * @throws InternalException
	 * @throws CloudException
	 */
	private void removeDBInstanceAccount (String dbInstanceId, String adminUser) throws InternalException, CloudException {
		
		HashMap<String, Object> params = new HashMap<String, Object>();
		params.put("DBInstanceId", dbInstanceId);
		params.put("AccountName", adminUser);
		
		AliyunNetworkCommon.executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS, 
				"DeleteAccount", AliyunNetworkCommon.RequestMethod.POST, false, 
				new AliyunValidateJsonResponseHandler(getProvider()));
	}
	
	
}
