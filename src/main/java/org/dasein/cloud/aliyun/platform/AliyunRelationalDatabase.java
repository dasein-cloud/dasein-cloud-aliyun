package org.dasein.cloud.aliyun.platform;

import static org.dasein.cloud.platform.DatabaseLicenseModel.BRING_YOUR_OWN_LICENSE;
import static org.dasein.cloud.platform.DatabaseLicenseModel.LICENSE_INCLUDED;
import static org.dasein.cloud.platform.DatabaseLicenseModel.POSTGRESQL_LICENSE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
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
import org.dasein.cloud.platform.DatabaseConfiguration;
import org.dasein.cloud.platform.DatabaseEngine;
import org.dasein.cloud.platform.DatabaseProduct;
import org.dasein.cloud.platform.RelationalDatabaseCapabilities;
import org.dasein.cloud.util.requester.DriverToCoreMapper;
import org.dasein.cloud.util.requester.streamprocessors.StreamToJSONObjectProcessor;
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
		
		AliyunNetworkCommon.executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS, "ModifySecurityIps");
		
	}

	@Override
	public void alterDatabase(String providerDatabaseId,
			boolean applyImmediately, String productSize,
			int storageInGigabytes, String configurationId,
			String newAdminUser, String newAdminPassword, int newPort,
			int snapshotRetentionInDays, TimeWindow preferredMaintenanceWindow,
			TimeWindow preferredBackupWindow) throws CloudException,
			InternalException {
		// TODO Auto-generated method stub
		super.alterDatabase(providerDatabaseId, applyImmediately, productSize,
				storageInGigabytes, configurationId, newAdminUser, newAdminPassword,
				newPort, snapshotRetentionInDays, preferredMaintenanceWindow,
				preferredBackupWindow);
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
		} else if (product.getEngine().equals(DatabaseEngine.SQLSERVER_EX)) {
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
		HttpUriRequest request = AliyunRequestBuilder.post()
				.provider(getProvider())
				.category(AliyunRequestBuilder.Category.RDS)
				.parameter("Action", "CreateDBInstance")
				.entity(params)
				.clientToken(true)
				.build();
		
		String instanceId = (String) new AliyunRequestExecutor<Map<String, Object>>(getProvider(),
				AliyunHttpClientBuilderFactory.newHttpClientBuilder(), request,
				AliyunNetworkCommon.getDefaultResponseHandler(getProvider(), "DBInstanceId")).execute().get("DBInstanceId");
		
		//create database
		params = new HashMap<String, Object>();
		params.put("DBInstanceId", instanceId);
		params.put("DBName", databaseName);
		if (product.getEngine().equals(DatabaseEngine.MYSQL)) {
			params.put("CharacterSetName", "utf8");
		} else if (product.getEngine().equals(DatabaseEngine.POSTGRES)) {
			params.put("CharacterSetName", "UTF8");
		} else if (product.getEngine().equals(DatabaseEngine.SQLSERVER_EX)) {
			params.put("CharacterSetName", "Chinese_PRC_CI_AS");
		}
		
		request = AliyunRequestBuilder.post()
				.provider(getProvider())
				.category(AliyunRequestBuilder.Category.RDS)
				.parameter("Action", "CreateDatabase")
				.entity(params)
				.build();
		
		new AliyunRequestExecutor<Void>(getProvider(),
				AliyunHttpClientBuilderFactory.newHttpClientBuilder(), request,
				new AliyunValidateJsonResponseHandler(getProvider())).execute();
		
		//create database account
		params = new HashMap<String, Object>();
		params.put("DBInstanceId", instanceId);
		params.put("AccountName", withAdminUser);
		params.put("AccountPassword", withAdminPassword);
		params.put("AccountDescription", "User " + withAdminUser + " for DB Instance " + instanceId);

		new AliyunRequestExecutor<Void>(getProvider(),
				AliyunHttpClientBuilderFactory.newHttpClientBuilder(), request,
				new AliyunValidateJsonResponseHandler(getProvider())).execute();
		
		//grant privileges for admin user
		params = new HashMap<String, Object>();
		params.put("DBInstanceId", instanceId);
		params.put("AccountName", withAdminUser);
		params.put("DBName", databaseName);
		params.put("AccountpPrivilege", "ReadWrite");
		
		new AliyunRequestExecutor<Void>(getProvider(),
				AliyunHttpClientBuilderFactory.newHttpClientBuilder(), request,
				new AliyunValidateJsonResponseHandler(getProvider())).execute();
		
		return instanceId;
	}

	@Override
	public DatabaseConfiguration getConfiguration(String providerConfigurationId)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return super.getConfiguration(providerConfigurationId);
	}

	@Override
	public Database getDatabase(String providerDatabaseId)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return super.getDatabase(providerDatabaseId);
	}

	@Override
	public Iterable<DatabaseEngine> getDatabaseEngines() throws CloudException,
			InternalException {
		return Arrays.asList(
				DatabaseEngine.MYSQL,
				DatabaseEngine.SQLSERVER_EX,
				DatabaseEngine.POSTGRES);
	}

	@Override
	public String getDefaultVersion(DatabaseEngine forEngine)
			throws CloudException, InternalException {
		if (forEngine.equals(DatabaseEngine.MYSQL)) {
			return "5.6";
		} else if (forEngine.equals(DatabaseEngine.SQLSERVER_EX)) {
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
		} else if (forEngine.equals(DatabaseEngine.SQLSERVER_EX)) {
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
		} else if (forEngine.equals(DatabaseEngine.SQLSERVER_EX)) {
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
	public Iterable<DatabaseConfiguration> listConfigurations()
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return super.listConfigurations();
	}

	@Override
	public Iterable<ResourceStatus> listDatabaseStatus() throws CloudException,
			InternalException {
		// TODO Auto-generated method stub
		return super.listDatabaseStatus();
	}

	@Override
	public Iterable<Database> listDatabases() throws CloudException,
			InternalException {
		// TODO Auto-generated method stub
		return super.listDatabases();
	}

	@Override
	public Collection<ConfigurationParameter> listParameters(
			String forProviderConfigurationId) throws CloudException,
			InternalException {
		// TODO Auto-generated method stub
		return super.listParameters(forProviderConfigurationId);
	}

	@Override
	public void removeConfiguration(String providerConfigurationId)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		super.removeConfiguration(providerConfigurationId);
	}

	@Override
	public void removeDatabase(String providerDatabaseId)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		super.removeDatabase(providerDatabaseId);
	}

	@Override
	public void resetConfiguration(String providerConfigurationId,
			String... parameters) throws CloudException, InternalException {
		// TODO Auto-generated method stub
		super.resetConfiguration(providerConfigurationId, parameters);
	}

	@Override
	public void restart(String providerDatabaseId, boolean blockUntilDone)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		super.restart(providerDatabaseId, blockUntilDone);
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
		
		AliyunNetworkCommon.executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.RDS, "ModifySecurityIps");
		
	}

	@Override
	public void updateConfiguration(String providerConfigurationId,
			ConfigurationParameter... parameters) throws CloudException,
			InternalException {
		// TODO Auto-generated method stub
		super.updateConfiguration(providerConfigurationId, parameters);
	}

	@Override
	public DatabaseBackup getUsableBackup(String providerDbId,
			String beforeTimestamp) throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return super.getUsableBackup(providerDbId, beforeTimestamp);
	}

	@Override
	public Iterable<DatabaseBackup> listBackups(
			String forOptionalProviderDatabaseId) throws CloudException,
			InternalException {
		// TODO Auto-generated method stub
		return super.listBackups(forOptionalProviderDatabaseId);
	}

	@Override
	public void createFromBackup(DatabaseBackup backup,
			String databaseCloneToName) throws CloudException,
			InternalException {
		// TODO Auto-generated method stub
		super.createFromBackup(backup, databaseCloneToName);
	}

	@Override
	public void removeBackup(DatabaseBackup backup) throws CloudException,
			InternalException {
		// TODO Auto-generated method stub
		super.removeBackup(backup);
	}

	@Override
	public void restoreBackup(DatabaseBackup backup) throws CloudException,
			InternalException {
		// TODO Auto-generated method stub
		super.restoreBackup(backup);
	}

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
	
	
}
