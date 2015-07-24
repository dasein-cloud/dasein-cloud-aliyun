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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.log4j.Logger;
import org.dasein.cloud.AbstractProviderService;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.aliyun.util.requester.AliyunHttpClientBuilderFactory;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestBuilder;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestExecutor;
import org.dasein.cloud.aliyun.util.requester.AliyunResponseHandlerWithMapper;
import org.dasein.cloud.aliyun.util.requester.AliyunValidateJsonResponseHandler;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.platform.CDNCapabilities;
import org.dasein.cloud.platform.CDNSupport;
import org.dasein.cloud.platform.Distribution;
import org.dasein.cloud.util.APITrace;
import org.dasein.cloud.util.requester.DriverToCoreMapper;
import org.dasein.cloud.util.requester.streamprocessors.StreamToJSONObjectProcessor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by Jane Wang on 7/22/2015.
 * 
 * @author Jane Wang
 * @since 2015.05.01
 */
public class AliyunCdn extends AbstractProviderService<Aliyun> implements CDNSupport {

	private static final Logger stdLogger = Aliyun.getStdLogger(AliyunCdn.class);
	
    private volatile transient AliyunCdnCapabilities capabilities;
    
    private static final int DefaultPageSize = 20;
    
    public AliyunCdn(Aliyun provider) {
    	super(provider);
    }
    
	@Override
	@Deprecated
	public String[] mapServiceAction(ServiceAction action) {
		return null;
	}

	/**
	 * If the client want to apply aliases for the domain, then the client need to enable the mapping from the DNS provider side.
	 * @param origin 	origin/location of the resources, can be ip address (separate by ','), domain name or oss bucket.
	 * @param name		domain name
	 * @param active	on-op
	 * @param aliases	cnames: unused in Aliyun
	 * @return domain name
	 * @throws InternalException
	 * @throws CloudException
	 */
	@Override
	public String create(String origin, String name, boolean active,
			String... aliases) throws InternalException, CloudException {
		APITrace.begin(getProvider(), "Cdn.create");
		try {
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("DomainName", name);
			params.put("CdnType", "web");
			params.put("SourceType", parseSourceType(origin.split(",")[0]).name());
			params.put("Sources", origin);
			
			HttpUriRequest request = AliyunRequestBuilder.post()
					.provider(getProvider())
					.category(AliyunRequestBuilder.Category.CDN)
					.parameter("Action", "AddCdnDomain")
					.entity(params)
					.build();
			
			new AliyunRequestExecutor<Void>(getProvider(),
					AliyunHttpClientBuilderFactory.newHttpClientBuilder(), request,
					new AliyunValidateJsonResponseHandler(getProvider())).execute();
			
			return name;
		} finally {
			APITrace.end();
		}
	}
	
	private static enum SourceType { ipaddr, domain, oss};
	private SourceType parseSourceType(String source) {
		final String ipAddressRexp = "((?:(?:25[0-5]|2[0-4]\\d|((1\\d{2})|([1-9]?\\d)))\\.){3}(?:25[0-5]|2[0-4]\\d|((1\\d{2})|([1-9]?\\d))))";
		final String domainNameRexp = "^((?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,6}$";
		String segment = source.split(",")[0].trim();
		if (Pattern.compile(ipAddressRexp).matcher(segment).matches()) { //check ip address first
			return SourceType.ipaddr;
		} else if (Pattern.compile(domainNameRexp).matcher(segment).matches()) {	//check domain name
			return SourceType.domain;
		} else {
			return SourceType.oss;
		}
	}
	
	@Override
	public void delete(String distributionId) throws InternalException,
			CloudException {
		APITrace.begin(getProvider(), "Cdn.delete");
		try {
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("DomainName", distributionId);
			
			HttpUriRequest request = AliyunRequestBuilder.post()
					.provider(getProvider())
					.category(AliyunRequestBuilder.Category.CDN)
					.parameter("Action", "DeleteCdnDomain")
					.entity(params)
					.build();
	
			new AliyunRequestExecutor<Void>(getProvider(),
					AliyunHttpClientBuilderFactory.newHttpClientBuilder(), request,
					new AliyunValidateJsonResponseHandler(getProvider())).execute();
		} finally {
			APITrace.end();
		}
	}

	@Override
	public CDNCapabilities getCapabilities() throws InternalException,
			CloudException {
		if (capabilities == null) {
			capabilities = new AliyunCdnCapabilities(getProvider());
		}
		return capabilities;
	}

	@Override
	public Distribution getDistribution(String distributionId)
			throws InternalException, CloudException {
		APITrace.begin(getProvider(), "Cdn.getDistribution");
		try {
			HttpUriRequest request = AliyunRequestBuilder.post()
					.provider(getProvider())
					.category(AliyunRequestBuilder.Category.CDN)
					.parameter("Action", "DescribeCdnDomainDetail")
					.parameter("DomainName", distributionId)
					.build();
			
			final String accountNumber = getContext().getAccountNumber();
			ResponseHandler<Distribution> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, Distribution>(
					new StreamToJSONObjectProcessor(),
					new DriverToCoreMapper<JSONObject, Distribution>() {
						@Override
						public Distribution mapFrom(JSONObject json) {
							JSONObject getDomainDetailModel;
							try {
								getDomainDetailModel = json.getJSONObject("GetDomainDetailModel");
								Distribution distribution = new Distribution();
								distribution.setProviderDistributionId(getDomainDetailModel.getString("DomainName"));
								distribution.setDnsName(getDomainDetailModel.getString("DomainName"));
								distribution.setProviderOwnerId(accountNumber);
								if (getProvider().isEmpty(getDomainDetailModel.getString("Cname"))) {
									distribution.setAliases(new String[]{getDomainDetailModel.getString("Cname")});
								}
								if (getProvider().isEmpty(getDomainDetailModel.getString("Sources"))) {
									distribution.setLocation(getDomainDetailModel.getString("Sources"));
								}
								String logFullPath = getLatestLog(getDomainDetailModel.getString("DomainName"));
								distribution.setLogDirectory(parseLogDirectory(logFullPath));
								distribution.setLogName(parseLogName(logFullPath));
								if (getDomainDetailModel.getString("DomainStatus").equals("online") ||
										getDomainDetailModel.getString("DomainStatus").equals("configuring")) {
									distribution.setActive(true);
									if (getDomainDetailModel.getString("DomainStatus").equals("online")) {
										distribution.setDeployed(true);
									}
								}
								return distribution;
							} catch (JSONException e) {
								stdLogger.error("Parse distribution from response failed", e);
								throw new RuntimeException(e);
							} catch (InternalException e) {
								stdLogger.error("Parse log directory failed", e);
								throw new RuntimeException(e);
							} catch (CloudException e) {
								stdLogger.error("Parse log directory failed", e);
								throw new RuntimeException(e);
							}
						}
					}, JSONObject.class);
			
			return new AliyunRequestExecutor<Distribution>(getProvider(),
					AliyunHttpClientBuilderFactory.newHttpClientBuilder(), request,
					responseHandler).execute();
		} finally {
			APITrace.end();
		}
	}

	@Override
	public String getProviderTermForDistribution(Locale locale) {
		return capabilities.getProviderTermForDistribution(locale);
	}

	@Override
	public boolean isSubscribed() throws InternalException, CloudException {
		return true;
	}

	@Override
	public Collection<Distribution> list() throws InternalException,
			CloudException {
		List<Distribution> distributions = new ArrayList<Distribution>();
		Iterator<ResourceStatus> resourceStatuses = listDistributionStatus().iterator();
		while (resourceStatuses.hasNext()) {
			ResourceStatus resourceStatus = resourceStatuses.next();
			distributions.add(getDistribution(resourceStatus.getProviderResourceId()));
		}
		return distributions;
	}

	@Override
	public Iterable<ResourceStatus> listDistributionStatus()
			throws InternalException, CloudException {
		APITrace.begin(getProvider(), "Cdn.listDistributionStatus");
		try {
			List<ResourceStatus> allDistributionStatus = new ArrayList<ResourceStatus>();
	        final AtomicInteger totalPageNumber = new AtomicInteger(1);
	        final AtomicInteger currentPageNumber = new AtomicInteger(1);
			
	        ResponseHandler<List<ResourceStatus>> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, List<ResourceStatus>>(
					new StreamToJSONObjectProcessor(),
					new DriverToCoreMapper<JSONObject, List<ResourceStatus>>() {
						@Override
						public List<ResourceStatus> mapFrom(JSONObject json) {
							List<ResourceStatus> distributionStatuses = new ArrayList<ResourceStatus>();
							JSONArray cdnDomains;
							try {
								cdnDomains = json.getJSONObject("Domains").getJSONArray("CDNDomain");
								for (int i = 0; i < cdnDomains.length(); i++) {
									JSONObject cdnDomain = cdnDomains.getJSONObject(i);
									distributionStatuses.add(
											new ResourceStatus(
													cdnDomain.getString("DomainName"), 
													cdnDomain.getString("DomainStatus")));
								}
								totalPageNumber.addAndGet(json.getInt("TotalCount") / DefaultPageSize +
		                                json.getInt("TotalCount") % DefaultPageSize > 0 ? 1 : 0);
		                        currentPageNumber.incrementAndGet();
		                        return distributionStatuses;
							} catch (JSONException e) {
								stdLogger.error("Parse Domains->CDNDomain from response failed", e);
								throw new RuntimeException(e);
							}
						}
					}, JSONObject.class);
	        
	        do {
				HttpUriRequest request = AliyunRequestBuilder.post()
						.provider(getProvider())
						.category(AliyunRequestBuilder.Category.CDN)
						.parameter("Action", "DescribeUserDomains")
						.build();
				
				allDistributionStatus.addAll(new AliyunRequestExecutor<List<ResourceStatus>>(getProvider(),
		                    AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
		                    request,
		                    responseHandler).execute());
	        } while (currentPageNumber.intValue() < totalPageNumber.intValue());
			
	        return allDistributionStatus;
		} finally {
			APITrace.end();
		}
	}

	/**
	 * use this update as state change, start or stop cdn service
	 * @param distributionId 	domain name
	 * @param name				domain name
	 * @param active			true -> start(to online state); false -> stop(to offline state)
	 * @param aliases			unused
	 * @throws InternalException
	 * @throws CloudException
	 */
	@Override
	public void update(String distributionId, String name, boolean active,
			String... aliases) throws InternalException, CloudException {
		APITrace.begin(getProvider(), "Cdn.update");
		try {
			AliyunRequestBuilder builder = AliyunRequestBuilder
					.post()
					.provider(getProvider())
					.category(AliyunRequestBuilder.Category.CDN)
					.parameter("DomainName", distributionId);
			if (active) { //start CDN service
				builder = builder.parameter("Action", "StartCdnDomain");
			} else {	//stop CDN service
				builder = builder.parameter("Action", "StopCdnDomain");
			}
			HttpUriRequest request = builder.build();
			
			new AliyunRequestExecutor<Void>(
					getProvider(),
					AliyunHttpClientBuilderFactory.newHttpClientBuilder(), 
					request,
					new AliyunValidateJsonResponseHandler(getProvider())
					).execute();
		} finally {
			APITrace.end();
		}
	}
	
	private String getLatestLog(String name) throws InternalException, CloudException {
		APITrace.begin(getProvider(), "Cdn.getLatestLog");
		try {
			HttpUriRequest request = AliyunRequestBuilder.post()
					.provider(getProvider())
					.category(AliyunRequestBuilder.Category.CDN)
					.parameter("Action", "DescribeCdnDomainLogs")
					.parameter("DomainName", name)
					.build();
			
			 ResponseHandler<String> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, String>(
						new StreamToJSONObjectProcessor(),
						new DriverToCoreMapper<JSONObject, String>() {
							@Override
							public String mapFrom(JSONObject json) {
								Date latestDate = null;
								String latestLogPath = null;
								JSONArray domainLogs;
								try {
									domainLogs = json.getJSONObject("DomainLogModel").getJSONObject("Items").getJSONArray("DomainLogDetail");
									for (int i = 0; i < domainLogs.length(); i++) {
										JSONObject domainLog = domainLogs.getJSONObject(i);
										Date currentDate = new SimpleDateFormat("YYYY-MM-DD'T'hh:mm:ss'Z'").parse(domainLog.getString("EndTime"));
										if (latestDate == null || latestDate.before(currentDate)) {
											latestDate = currentDate;
											latestLogPath = domainLog.getString("LogPath");
										}
									}
									return latestLogPath;
								} catch (JSONException e) {
									stdLogger.error("Parse DomainLogModel->Items->DomainLogDetail failed", e);
									throw new RuntimeException(e);
								} catch (ParseException e) {
									stdLogger.error("Parse EndTime with format 'YYYY-MM-DD'T'hh:mm:ss'Z'' failed", e);
									throw new RuntimeException(e);
								}
							}
						}, JSONObject.class);
			
			 return new AliyunRequestExecutor<String>(
						getProvider(),
						AliyunHttpClientBuilderFactory.newHttpClientBuilder(), 
						request,
						responseHandler
						).execute();
		} finally {
			APITrace.end();
		}
	}
	
	private String parseLogDirectory(String logFullPath) {
		if (!getProvider().isEmpty(logFullPath)) {
			return logFullPath.substring(0, logFullPath.lastIndexOf("/"));
		}
		return null;
	}
	
	private String parseLogName(String logFullPath) {
		if (!getProvider().isEmpty(logFullPath)) {
			return logFullPath.substring(logFullPath.lastIndexOf("/") + 1, logFullPath.indexOf("?") - 1);
		}
		return null;
	}

}
