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
package org.dasein.cloud.aliyun.network;

import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.log4j.Logger;
import org.dasein.cloud.*;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.aliyun.util.requester.AliyunHttpClientBuilderFactory;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestBuilder;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestExecutor;
import org.dasein.cloud.aliyun.util.requester.AliyunResponseHandlerWithMapper;
import org.dasein.cloud.aliyun.util.requester.AliyunValidateJsonResponseHandler;
import org.dasein.cloud.network.*;
import org.dasein.cloud.util.APITrace;
import org.dasein.cloud.util.requester.DriverToCoreMapper;
import org.dasein.cloud.util.requester.streamprocessors.StreamToJSONObjectProcessor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.*;

/**
 * Created by Jane Wang on 5/19/2015.
 *
 * @author Jane Wang
 * @since 2015.05.01
 */
public class AliyunLoadBalancer extends AbstractLoadBalancerSupport<Aliyun> {

	private static final Logger stdLogger = Aliyun
			.getStdLogger(AliyunLoadBalancer.class);

	private transient volatile AliyunLoadBalancerCapabilities capabilities;

	protected AliyunLoadBalancer(Aliyun provider) {
		super(provider);
	}

	@Nonnull
	@Override
	public LoadBalancerCapabilities getCapabilities() throws CloudException,
			InternalException {
		if (capabilities == null) {
			capabilities = new AliyunLoadBalancerCapabilities(getProvider());
		}
		return capabilities;
	}

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}

	@Nonnull
	@Override
	public String createLoadBalancer(@Nonnull LoadBalancerCreateOptions options)
			throws CloudException, InternalException {
		APITrace.begin(getProvider(), "createLoadBalancer");
		try {
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("RegionId", getContext().getRegionId());
			getProvider().addValueIfNotEmpty(params, "LoadBalancerName",
					options.getName());
			if (options.getProviderSubnetIds() != null
					&& options.getProviderSubnetIds().length > 0) {
				if (options.getProviderSubnetIds().length == 1) {
					params.put("VSwitchId", options.getProviderSubnetIds()[0]);
				} else {
					throw new InternalException(
							"Aliyun supports add load balancer to only one subnet!");
				}
			}
			if (options.getType() != null
					&& options.getType().equals(LbType.INTERNAL)) {
				params.put("AddressType",
						AliyunNetworkCommon.LoadBalancerAddressType.intranet.name());
			}
			params.put("InternetChargeType",
					AliyunNetworkCommon.InternetChargeType.PayByTraffic.name()
							.toLowerCase());
			params.put("Bandwidth", 1000);
			
			ResponseHandler<String> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, String>(
	            new StreamToJSONObjectProcessor(),
	              new DriverToCoreMapper<JSONObject, String>() {
	                 @Override
	                 public String mapFrom(JSONObject json ) {
	                      try {
	                          return json.getString("LoadBalancerId" );
	                      } catch (JSONException e ) {
	                              stdLogger.error("parse LoadBalancerId from response failed", e);
	                          throw new RuntimeException(e);
	                      }
	                }
	             }, JSONObject. class);      
	
			
			String loadBalancerId = (String) AliyunNetworkCommon.executeDefaultRequest(getProvider(), params, 
					AliyunRequestBuilder.Category.SLB, "CreateLoadBalancer", 
					AliyunNetworkCommon.RequestMethod.POST, true, responseHandler);
	
			if (loadBalancerId != null) {
	
				for (LbListener listener : options.getListeners()) {
					createListener(loadBalancerId, listener);
				}
	
				createLoadBalancerHealthCheck(options.getHealthCheckOptions());
	
				for (LbListener listener : options.getListeners()) {
					// Listener is Stopped by default, need to start
					startListener(loadBalancerId, listener);
				}
			}
	
			return loadBalancerId;
		} finally {
			APITrace.end();
		}
	}

	@Override
	public void removeLoadBalancer(@Nonnull String loadBalancerId)
			throws CloudException, InternalException {
		APITrace.begin(getProvider(), "removeLoadBalancer");
		try {
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("LoadBalancerId", loadBalancerId);
	
			AliyunNetworkCommon.executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.SLB, 
					"DeleteLoadBalancer", AliyunNetworkCommon.RequestMethod.POST, false, 
					new AliyunValidateJsonResponseHandler(getProvider()));
		} finally {
			APITrace.end();
		}
	}

	@Nonnull
	@Override
	public Iterable<LoadBalancer> listLoadBalancers() throws CloudException,
			InternalException {
		try {
			List<LoadBalancer> loadBalancers = describeLoadBalancersBriefInfo();
			for (LoadBalancer loadBalancer : loadBalancers) {
				loadBalancers.add(getLoadBalancer(loadBalancer
						.getProviderLoadBalancerId()));
			}
			return loadBalancers;
		} catch (JSONException e) {
			stdLogger.error("An exception occurs during listLoadBalancers!", e);
			throw new InternalException(e);
		}
	}

	@Nonnull
	@Override
	public Iterable<ResourceStatus> listLoadBalancerStatus()
			throws CloudException, InternalException {
		List<ResourceStatus> resourceStatuses = new ArrayList<ResourceStatus>();
		try {
			List<LoadBalancer> loadBalancers = describeLoadBalancersBriefInfo();
			for (LoadBalancer loadBalancer : loadBalancers) {
				resourceStatuses.add(new ResourceStatus(loadBalancer
						.getProviderLoadBalancerId(), loadBalancer
						.getCurrentState()));
			}
			return resourceStatuses;
		} catch (JSONException e) {
			stdLogger.error(
					"An exception occurs during listLoadBalancerStatus!", e);
			throw new InternalException(e);
		}
	}

	@Override
	public LoadBalancer getLoadBalancer(@Nonnull String loadBalancerId)
			throws CloudException, InternalException {
		return describeLoadBalancer(loadBalancerId, true);
	}

	@Override
	public void addListeners(@Nonnull String toLoadBalancerId,
			@Nullable LbListener[] listeners) throws CloudException,
			InternalException {
		if (listeners == null) {
			return;
		}
		for (LbListener listener : listeners) {
			createListener(toLoadBalancerId, listener);
			// Listener is Stopped by default, need to start
			startListener(toLoadBalancerId, listener);
		}
	}

	private void startListener(@Nonnull String toLoadBalancerId,
			LbListener listener) throws CloudException, InternalException {
		APITrace.begin(getProvider(), "startListener");
		try {
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("LoadBalancerId", toLoadBalancerId);
			params.put("ListenerPort", listener.getPublicPort());
	
			AliyunNetworkCommon.executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.SLB, 
					"StartLoadBalancerListener", AliyunNetworkCommon.RequestMethod.POST, false, 
					new AliyunValidateJsonResponseHandler(getProvider()));
		} finally {
			APITrace.end();
		}
	}

	private void createListener(@Nonnull String toLoadBalancerId,
			LbListener listener) throws CloudException, InternalException {
		APITrace.begin(getProvider(), "createListener");
		try {
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("LoadBalancerId", toLoadBalancerId);
			params = appendParamsByListener(params, listener);
	
			if (listener.getNetworkProtocol() == LbProtocol.HTTP
					|| listener.getNetworkProtocol() == LbProtocol.HTTPS) {
				// health check of TCP_RAW can not be disabled
				params.put("HealthCheck", "off");
			}
	
			String methodName = null;
			if (listener.getNetworkProtocol().equals(LbProtocol.HTTP)) {
				methodName = "CreateLoadBalancerHTTPListener";
			} else if (listener.getNetworkProtocol().equals(LbProtocol.HTTPS)) {
				methodName = "CreateLoadBalancerHTTPSListener";
			} else if (listener.getNetworkProtocol().equals(LbProtocol.RAW_TCP)) {
				methodName = "CreateLoadBalancerTCPListener";
			}
	
			AliyunNetworkCommon.executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.SLB, 
					methodName, AliyunNetworkCommon.RequestMethod.POST, false, 
					new AliyunValidateJsonResponseHandler(getProvider()));
		} finally {
			APITrace.end();
		}
	}

	@Override
	public void removeListeners(@Nonnull String toLoadBalancerId,
			@Nullable LbListener[] listeners) throws CloudException,
			InternalException {
		APITrace.begin(getProvider(), "removeListeners");
		try {
			for (LbListener listener : listeners) {
	
				Map<String, Object> params = new HashMap<String, Object>();
				params.put("LoadBalancerId", toLoadBalancerId);
				params.put("ListenerPort", listener.getPublicPort());
	
				AliyunNetworkCommon.executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.SLB, 
						"DeleteLoadBalancerListener", AliyunNetworkCommon.RequestMethod.POST, false, 
						new AliyunValidateJsonResponseHandler(getProvider()));
			}
		} finally {
			APITrace.end();
		}
	}

	@Override
	public void addServers(@Nonnull String toLoadBalancerId,
			@Nonnull String... serverIdsToAdd) throws CloudException,
			InternalException {
		APITrace.begin(getProvider(), "addServers");
		try {
			JSONArray jsonArray = new JSONArray();
			for (String serverIdToAdd : serverIdsToAdd) {
				JSONObject jsonObject = new JSONObject();
				jsonObject.put("ServerId", serverIdToAdd);
				jsonObject.put("Weight",
						AliyunNetworkCommon.DefaultServerWeight);
				jsonArray.put(jsonObject);
			}
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("LoadBalancerId", toLoadBalancerId);
			params.put("BackendServers", jsonArray.toString());

			AliyunNetworkCommon.executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.SLB, 
					"AddBackendServers", AliyunNetworkCommon.RequestMethod.POST, false, 
					new AliyunValidateJsonResponseHandler(getProvider()));
		} catch (JSONException e) {
			stdLogger.error("An exception occurs during add backend servers!",
					e);
			throw new InternalException(e);
		} finally {
			APITrace.end();
		}
	}

	@Override
	public void removeServers(@Nonnull String fromLoadBalancerId,
			@Nonnull String... serverIdsToRemove) throws CloudException,
			InternalException {
		APITrace.begin(getProvider(), "removeServers");
		try {
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("LoadBalancerId", fromLoadBalancerId);
			JSONArray servers = new JSONArray();
			for (int i = 0; i < serverIdsToRemove.length; i++) {
				try {
					servers.put(i, serverIdsToRemove[i]);
				} catch (JSONException e) {
					stdLogger
							.error("An exception occurs during remove servers!", e);
					throw new InternalException(e);
				}
			}
			params.put("BackendServers", servers);
			
			AliyunNetworkCommon.executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.SLB, 
					"RemoveBackendServers", AliyunNetworkCommon.RequestMethod.POST, false, 
					new AliyunValidateJsonResponseHandler(getProvider()));
		} finally {
			APITrace.end();
		}
	}

	@Nonnull
	@Override
	public Iterable<LoadBalancerEndpoint> listEndpoints(
			@Nonnull String forLoadBalancerId) throws CloudException,
			InternalException {
		APITrace.begin(getProvider(), "listEndpoints");
		try {
			final Map<String, LoadBalancerEndpoint> allLoadBalancerEndpointMap = new HashMap<String, LoadBalancerEndpoint>();
			int[] listenerPorts = describeLoadBalancer(forLoadBalancerId, false).getPublicPorts();
			for (int i = 0; i < listenerPorts.length; i++) {
	
				HttpUriRequest request = AliyunRequestBuilder.get()
						.provider(getProvider())
						.category(AliyunRequestBuilder.Category.SLB)
						.parameter("Action", "DescribeHealthStatus")
						.parameter("LoadBalancerId", forLoadBalancerId)
						.parameter("ListenerPort", listenerPorts[i])
						.build();
				
				ResponseHandler<Map<String, LoadBalancerEndpoint>> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, Map<String, LoadBalancerEndpoint>>(
	            		new StreamToJSONObjectProcessor(),
	            		new DriverToCoreMapper<JSONObject, Map<String, LoadBalancerEndpoint>>() {
	                        @Override
	                        public Map<String, LoadBalancerEndpoint> mapFrom(JSONObject json) {
	                            try {
	                            	Map<String, LoadBalancerEndpoint> loadBalancerEndpointMap = new HashMap<String, LoadBalancerEndpoint>();
	                            	JSONArray backendServers = json.getJSONObject(
	                						"BackendServers").getJSONArray("BackendServer");
	                            	for (int j = 0; j < backendServers.length(); j++) {
	                					JSONObject backendServer = backendServers.getJSONObject(j);
	                					String serverId = backendServer.getString("ServerId");
	                					LbEndpointState state = LbEndpointState.INACTIVE;
	                					if (backendServer.getString("ServerHealthStatus").equals(
	                							AliyunNetworkCommon.AliyunLbEndpointState.normal
	                									.name())) {
	                						state = LbEndpointState.ACTIVE;
	                					}
	                					if (!loadBalancerEndpointMap.containsKey(serverId)) { // not
	                																			// contain
	                						loadBalancerEndpointMap.put(serverId,
	                								LoadBalancerEndpoint.getInstance(
	                										LbEndpointType.VM, serverId, state));
	                					} else { // already contains
	                						LoadBalancerEndpoint endpoint = loadBalancerEndpointMap
	                								.get(serverId);
	                						if (state.equals(LbEndpointState.INACTIVE)
	                								&& endpoint.getCurrentState().equals(
	                										LbEndpointState.ACTIVE)) {
	                							loadBalancerEndpointMap.remove(serverId);
	                							loadBalancerEndpointMap.put(serverId,
	                									LoadBalancerEndpoint.getInstance(
	                											LbEndpointType.VM, serverId,
	                											LbEndpointState.INACTIVE));
	                						}
	                					}
	                				}
	                            	return loadBalancerEndpointMap;
	                            } catch (JSONException e) {
	                            	stdLogger.error("Failed to parse routing table", e);
	                                throw new RuntimeException(e.getMessage());
								} 
	                        }
	                    },
	                    JSONObject.class);
				
				allLoadBalancerEndpointMap.putAll(new AliyunRequestExecutor<Map<String, LoadBalancerEndpoint>>(getProvider(),
						AliyunHttpClientBuilderFactory.newHttpClientBuilder(), request,
						responseHandler).execute());
			}
			
			return allLoadBalancerEndpointMap.values();
		} finally {
			APITrace.end();
		}
	}

	@Override
	public SSLCertificate createSSLCertificate(
			@Nonnull SSLCertificateCreateOptions options)
			throws CloudException, InternalException {
		APITrace.begin(getProvider(), "createSSLCertificate");
		try {
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("RegionId", getContext().getRegionId());
			params.put("ServerCertificate", options.getCertificateBody());
			if (!getProvider().isEmpty(options.getCertificateName())) {
				params.put("ServerCertificateName", options.getCertificateName());
			}
			params.put("PrivateKey", options.getPrivateKey());
			
			HttpUriRequest request = AliyunRequestBuilder.post()
					.provider(getProvider())
					.category(AliyunRequestBuilder.Category.SLB)
					.parameter("Action", "UploadServerCertificate")
					.entity(params)
					.build();
			
			ResponseHandler<SSLCertificate> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, SSLCertificate>(
	        		new StreamToJSONObjectProcessor(),
	        		new DriverToCoreMapper<JSONObject, SSLCertificate>() {
	                    @Override
	                    public SSLCertificate mapFrom(JSONObject json) {
	                        try {
	                			return toSSLCertificate(json);
	                        } catch (JSONException e) {
	                        	stdLogger.error("Failed to parse routing table", e);
	                            throw new RuntimeException(e.getMessage());
							} catch (InternalException e) {
								stdLogger.error("Failed build request", e);
	                            throw new RuntimeException(e.getMessage());
							} catch (CloudException e) {
								stdLogger.error("Failed to parse sslcertificate", e);
	                            throw new RuntimeException(e.getMessage());
							} 
	                    }
	                },
	                JSONObject.class);
			
			return new AliyunRequestExecutor<SSLCertificate>(getProvider(),
	                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
	                request,
	                responseHandler).execute();
		} finally {
			APITrace.end();
		}
	}

	@Nullable
	@Override
	public SSLCertificate getSSLCertificate(@Nonnull String certificateName)
			throws CloudException, InternalException {
		Iterable<SSLCertificate> sslCertificates = listSSLCertificates();
		Iterator<SSLCertificate> sslCertificateIterator = sslCertificates.iterator();
		while (sslCertificateIterator.hasNext()) {
			SSLCertificate certificate = sslCertificateIterator.next();
			if (certificate.getCertificateName().equals(certificateName)) {
				return certificate;
			}
		}
		return null;
	}

	@Nonnull
	@Override
	public Iterable<SSLCertificate> listSSLCertificates()
			throws CloudException, InternalException {
		APITrace.begin(getProvider(), "listSSLCertificates");
		try {
			HttpUriRequest request = AliyunRequestBuilder.get()
					.provider(getProvider())
					.category(AliyunRequestBuilder.Category.SLB)
					.parameter("Action", "DescribeServerCertificates")
					.parameter("RegionId", getContext().getRegionId())
					.build();
			
			ResponseHandler<List<SSLCertificate>> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, List<SSLCertificate>>(
	        		new StreamToJSONObjectProcessor(),
	        		new DriverToCoreMapper<JSONObject, List<SSLCertificate>>() {
	                    @Override
	                    public List<SSLCertificate> mapFrom(JSONObject json) {
	                        try {
	                        	List<SSLCertificate> sslCertificates = new ArrayList<SSLCertificate>();
	                        	JSONArray sslCertificatesResponse = json.getJSONObject(
	                					"ServerCertificates").getJSONArray("ServerCertificate");
	                			for (int i = 0; i < sslCertificatesResponse.length(); i++) {
	                				sslCertificates.add(toSSLCertificate(sslCertificatesResponse
	                						.getJSONObject(i)));
	                			}
	                			return sslCertificates;
	                        } catch (JSONException e) {
	                        	stdLogger.error("Failed to parse routing table", e);
	                            throw new RuntimeException(e.getMessage());
							} catch (InternalException e) {
								stdLogger.error("Failed build request", e);
	                            throw new RuntimeException(e.getMessage());
							} catch (CloudException e) {
								stdLogger.error("Failed to parse sslcertificate", e);
	                            throw new RuntimeException(e.getMessage());
							} 
	                    }
	                },
	                JSONObject.class);
			
			return new AliyunRequestExecutor<List<SSLCertificate>>(getProvider(),
					AliyunHttpClientBuilderFactory.newHttpClientBuilder(), request,
					responseHandler).execute();
		} finally {
			APITrace.end();
		}
	}

	@Override
	public void removeSSLCertificate(@Nonnull String certificateName)
			throws CloudException, InternalException {
		APITrace.begin(getProvider(), "removeSSLCertificate");
		try {
			SSLCertificate certificate = getSSLCertificate(certificateName);
			if (certificate != null) {
				Map<String, Object> params = new HashMap<String, Object>();
				params.put("RegionId", getContext().getRegionId());
				params.put("ServerCertificateId",
						certificate.getProviderCertificateId());
				AliyunNetworkCommon.executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.SLB, 
						"DeleteServerCertificate", AliyunNetworkCommon.RequestMethod.POST, false, 
						new AliyunValidateJsonResponseHandler(getProvider()));
			}
		} finally {
			APITrace.end();
		}
	}

	@Override
	public void setSSLCertificate(
			@Nonnull SetLoadBalancerSSLCertificateOptions options)
			throws CloudException, InternalException {
		APITrace.begin(getProvider(), "setSSLCertificate");
		try {
			SSLCertificate certificate = getSSLCertificate(options
					.getSslCertificateName());
			LoadBalancer loadBalancer = describeLoadBalancer(options
					.getLoadBalancerName());

			Map<String, Object> params = new HashMap<String, Object>();
			params.put("LoadBalancerId",
					loadBalancer.getProviderLoadBalancerId());
			params.put("ListenerPort", options.getSslCertificateAssignToPort());
			params.put("Bandwidth",
					AliyunNetworkCommon.DefaultLoadBalancerBandwidth);
			params.put("ServerCertificateId",
					certificate.getProviderCertificateId());
			
			AliyunNetworkCommon.executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.SLB, 
					"SetLoadBalancerHTTPSListenerAttribute", AliyunNetworkCommon.RequestMethod.POST, false, 
					new AliyunValidateJsonResponseHandler(getProvider()));
		} catch (JSONException e) {
			stdLogger.error("An exception occurs during setSSLCertificate", e);
			throw new InternalException(e);
		} finally {
			APITrace.end();
		}
	}

	private LoadBalancerHealthCheck setHealthCheckAttribute(
			HealthCheckOptions options, LbListener lbListener)
			throws CloudException, InternalException {
		APITrace.begin(getProvider(), "setHealthCheckAttribute");
		try {	
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("LoadBalancerId", options.getProviderLoadBalancerId());
			params = appendParamsByListener(params, lbListener);
	
			params.put("HealthCheckConnectPort", options.getPort());
			params.put("HealthyThreshold", options.getHealthyCount());
			params.put("UnhealthyThreshold", options.getUnhealthyCount());
			params.put("HealthCheckInterval", options.getInterval());
			if (options.getProtocol() == LoadBalancerHealthCheck.HCProtocol.HTTP
					|| options.getProtocol() == LoadBalancerHealthCheck.HCProtocol.HTTPS) {
				params.put("HealthCheck",
						AliyunNetworkCommon.AliyunLbSwitcher.on.name());
				params.put("HealthCheckURI", options.getPath());
				if (getProvider().isEmpty(options.getHost())) {
					params.put("HealthCheckDomain", "$_ip");
				} else {
					params.put("HealthCheckDomain", options.getHost());
				}
	
				params.put("HealthCheckTimeout", options.getTimeout());
			} else {
				params.put("HealthCheckConnectTimeout", options.getTimeout());
			}
	
			String methodName;
			if (options.getProtocol().equals(
					LoadBalancerHealthCheck.HCProtocol.HTTP)) {
				methodName = "SetLoadBalancerHTTPListenerAttribute";
			} else if (options.getProtocol().equals(
					LoadBalancerHealthCheck.HCProtocol.HTTPS)) {
				methodName = "SetLoadBalancerHTTPSListenerAttribute";
			} else if (options.getProtocol().equals(
					LoadBalancerHealthCheck.HCProtocol.TCP)) {
				methodName = "SetLoadBalancerTCPListenerAttribute";
			} else {
				throw new InternalException(
						"Aliyun supports HTTP, HTTPS and TCP as the health check protocol only!");
			}
			
			AliyunNetworkCommon.executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.SLB, 
					methodName, AliyunNetworkCommon.RequestMethod.POST, false, 
					new AliyunValidateJsonResponseHandler(getProvider()));
			
	
			String healthCheckId = new LbListenerHealthCheckIdentity(
					options.getProviderLoadBalancerId(),
					lbListener.getNetworkProtocol(), lbListener.getPublicPort())
					.toString();
			LoadBalancerHealthCheck healthCheck = LoadBalancerHealthCheck
					.getInstance(healthCheckId, null, null, options.getHost(),
							options.getProtocol(), options.getPort(),
							options.getPath(), options.getInterval(),
							options.getTimeout(), options.getHealthyCount(),
							options.getUnhealthyCount());
			healthCheck.addListener(lbListener);
			
			return healthCheck;
		} finally {
			APITrace.end();
		}
	}

	@Override
	public LoadBalancerHealthCheck createLoadBalancerHealthCheck(
			@Nonnull HealthCheckOptions options) throws CloudException,
			InternalException {
		if (options == null) {
			return null;
		}
		return setHealthCheckAttribute(options, options.getListener());
	}

	@Override
	public LoadBalancerHealthCheck modifyHealthCheck(
			@Nonnull String providerLBHealthCheckId,
			@Nonnull HealthCheckOptions options) throws InternalException,
			CloudException {
		LbListenerHealthCheckIdentity healthCheckIdentity = new LbListenerHealthCheckIdentity(
				providerLBHealthCheckId);
		if (!healthCheckIdentity.getLoadBalancerId().equals(
				options.getProviderLoadBalancerId())
				|| healthCheckIdentity.getListenerPort() != options
						.getListener().getPublicPort()
				|| healthCheckIdentity.getListenerProtocol() != options
						.getListener().getNetworkProtocol()) {
			throw new InternalException("Input arguments are not consistent.");
		}

		try {
			LbListener lbListener = describeListener(
					healthCheckIdentity.getLoadBalancerId(),
					healthCheckIdentity.getListenerProtocol(),
					healthCheckIdentity.getListenerPort());

			return setHealthCheckAttribute(options, lbListener);
		} catch (JSONException e) {
			stdLogger.error(
					"An exception occurs during remove health check by load balancer id "
							+ healthCheckIdentity.getLoadBalancerId(), e);
			throw new InternalException(e);
		}
	}

	@Override
	public void removeLoadBalancerHealthCheck(
			@Nonnull String providerLoadBalancerId) throws CloudException,
			InternalException {
		APITrace.begin(getProvider(), "removeLoadBalancerHealthCheck");
		try {
			List<LbListener> listeners = describeListeners(providerLoadBalancerId);
			for (LbListener listener : listeners) {
				LbProtocol lbProtocol = listener.getNetworkProtocol();
				if (lbProtocol == LbProtocol.RAW_TCP) {
					continue; // can not disable health check of TCP listener
				}

				String methodName = null;
				if (lbProtocol == LbProtocol.HTTP) {
					methodName = "SetLoadBalancerHTTPListenerAttribute";
				} else if (lbProtocol == LbProtocol.HTTPS) {
					methodName = "SetLoadBalancerHTTPSListenerAttribute";
				}

				Map<String, Object> params = new HashMap<String, Object>();
				params.put("LoadBalancerId", providerLoadBalancerId);
				params = appendParamsByListener(params, listener);

				params.put("HealthCheck",
						AliyunNetworkCommon.AliyunLbSwitcher.off.name());
				
				AliyunNetworkCommon.executeDefaultRequest(getProvider(), params, AliyunRequestBuilder.Category.SLB, 
						methodName, AliyunNetworkCommon.RequestMethod.POST, false, 
						new AliyunValidateJsonResponseHandler(getProvider()));
				
			}
		} catch (JSONException e) {
			stdLogger.error(
					"An exception occurs during remove health check by load balancer id "
							+ providerLoadBalancerId, e);
			throw new InternalException(e);
		} finally {
			APITrace.end();
		}
	}

	@Override
	public Iterable<LoadBalancerHealthCheck> listLBHealthChecks(
			@Nullable HealthCheckFilterOptions opts) throws CloudException,
			InternalException {
		List<LoadBalancerHealthCheck> healthChecks = new ArrayList<LoadBalancerHealthCheck>();
		try {
			List<LoadBalancer> loadBalancers = describeLoadBalancersBriefInfo();
			for (LoadBalancer loadBalancer : loadBalancers) {
				List<LoadBalancerHealthCheck> loadBalancerHealthChecks = describeHealthChecks(loadBalancer
						.getProviderLoadBalancerId());
				for (LoadBalancerHealthCheck healthCheck : loadBalancerHealthChecks) {
					if (opts == null || opts.matches(healthCheck)) {
						healthChecks.add(healthCheck);
					}
				}
			}
			return healthChecks;
		} catch (JSONException e) {
			stdLogger
					.error("An exception occurs during listLBHealthChecks with options",
							e);
			throw new InternalException(e);
		}
	}

	@Override
	public LoadBalancerHealthCheck getLoadBalancerHealthCheck(
			@Nonnull String providerLBHealthCheckId,
			@Nullable String providerLoadBalancerId) throws CloudException,
			InternalException {
		LbListenerHealthCheckIdentity healthCheckIdentity = new LbListenerHealthCheckIdentity(
				providerLoadBalancerId);
		LbProtocol lbProtocol = healthCheckIdentity.getListenerProtocol();
		int listenerPort = healthCheckIdentity.getListenerPort();
		JSONObject listenerJson = describeListenerAttribute(
				providerLoadBalancerId, lbProtocol, listenerPort);
		try {
			return toHealthCheck(providerLoadBalancerId, listenerJson,
					lbProtocol);
		} catch (JSONException e) {
			stdLogger.error(
					"An exception occurs during get health check by id: "
							+ providerLBHealthCheckId, e);
			throw new InternalException(e);
		}
	}

	private Map<String, Object> appendParamsByListener(
			Map<String, Object> params, LbListener listener)
			throws CloudException, InternalException {
		params.put("ListenerPort", listener.getPublicPort());
		params.put("BackendServerPort", listener.getPrivatePort());
		params.put("Bandwidth",
				AliyunNetworkCommon.DefaultLoadBalancerBandwidth);

		if (listener.getAlgorithm() != null) {
			if (listener.getAlgorithm().equals(LbAlgorithm.ROUND_ROBIN)) {
				params.put("Scheduler",
						AliyunNetworkCommon.AliyunLbScheduleAlgorithm.wrr
								.name());
			} else if (listener.getAlgorithm().equals(LbAlgorithm.LEAST_CONN)) {
				params.put("Scheduler",
						AliyunNetworkCommon.AliyunLbScheduleAlgorithm.wlc
								.name());
			} else {
				throw new InternalException(
						"Aliyun supports weighted round robin and weighted least connection scheduler algorithms only!");
			}
		}

		if (listener.getNetworkProtocol() == LbProtocol.HTTP
				|| listener.getNetworkProtocol() == LbProtocol.HTTPS) {
			if (listener.getPersistence().equals(LbPersistence.NONE)) {
				params.put("StickySession",
						AliyunNetworkCommon.AliyunLbSwitcher.off.name());
			} else if (listener.getPersistence().equals(LbPersistence.COOKIE)) {
				params.put("StickySession",
						AliyunNetworkCommon.AliyunLbSwitcher.on.name());
				if (getProvider().isEmpty(listener.getCookie())) {
					params.put("StickySessionType",
							AliyunNetworkCommon.AliyunLbPersistenceType.insert
									.name());
					params.put("CookieTimeout",
							AliyunNetworkCommon.DefaultPersistenceTimeout);
				} else {
					params.put("StickySessionType",
							AliyunNetworkCommon.AliyunLbPersistenceType.server
									.name());
					params.put("Cookie", listener.getCookie());
				}
			}
		} else {
			params.put("PersistenceTimeout",
					AliyunNetworkCommon.DefaultPersistenceTimeout);
		}

		if (listener.getNetworkProtocol() == LbProtocol.HTTPS) {
			SSLCertificate certificate = getSSLCertificate(listener
					.getSslCertificateName());
			if (certificate != null) {
				params.put("ServerCertificateId",
						certificate.getProviderCertificateId());
			} else {
				stdLogger
						.warn("SSL certificate is required to add HTTPS listener. However, ssl certificate with name "
								+ listener.getSslCertificateName()
								+ " cannot be found!");
			}
		}

		return params;
	}

	/*
	 * return load balancers contains only below information: - load balancer id
	 * - load balancer name - load balancer status
	 */
	private List<LoadBalancer> describeLoadBalancersBriefInfo()
			throws JSONException, InternalException, CloudException {
		
		APITrace.begin(getProvider(), "describeLoadBalancersBriefInfo");
		try {
			HttpUriRequest request = AliyunRequestBuilder.get()
					.provider(getProvider())
					.category(AliyunRequestBuilder.Category.SLB)
					.parameter("Action", "DescribeLoadBalancers")
					.parameter("RegionId", getContext().getRegionId())
					.build();
			
			ResponseHandler<List<LoadBalancer>> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, List<LoadBalancer>>(
					new StreamToJSONObjectProcessor(),
					new DriverToCoreMapper<JSONObject, List<LoadBalancer>>() {
						@Override
						public List<LoadBalancer> mapFrom(JSONObject json) {
							List<LoadBalancer> loadBalancers = new ArrayList<LoadBalancer>();
							try {
								for (int i = 0; i < json.getJSONObject("LoadBalancers")
										.getJSONArray("LoadBalancer").length(); i++) {
									JSONObject simpleLoadBalancer = json
											.getJSONObject("LoadBalancers")
											.getJSONArray("LoadBalancer").getJSONObject(i);
									LoadBalancerState status = LoadBalancerState.PENDING;
									if (!getProvider().isEmpty(
											simpleLoadBalancer.getString("LoadBalancerStatus"))
											&& simpleLoadBalancer
													.getString("LoadBalancerStatus")
													.toUpperCase()
													.equals(LoadBalancerState.ACTIVE.name()
															.toUpperCase())) {
										status = LoadBalancerState.ACTIVE;
									}
									loadBalancers.add(LoadBalancer.getInstance(getContext()
											.getAccountNumber(), getContext().getRegionId(),
											simpleLoadBalancer.getString("LoadBalancerId"), status,
											simpleLoadBalancer.getString("LoadBalancerName"), null,
											null, null));
								}
								return loadBalancers;
							} catch (JSONException e) {
								stdLogger.error("An exception occurs during parsing json for load balancers", e);
								throw new RuntimeException(e);
							} catch (InternalException e) {
								stdLogger.error("An exception occurs during parsing json for load balancers", e);
								throw new RuntimeException(e);
							}
						}
					}, JSONObject.class);
			
			return new AliyunRequestExecutor<List<LoadBalancer>>(getProvider(),
					AliyunHttpClientBuilderFactory.newHttpClientBuilder(), request,
					responseHandler).execute();
		} finally {
			APITrace.end();
		}
	}

	private LoadBalancer describeLoadBalancer(String name)
			throws JSONException, InternalException, CloudException {
		for (LoadBalancer loadBalancer : describeLoadBalancersBriefInfo()) {
			if (loadBalancer.getName().equals(name)) {
				return getLoadBalancer(loadBalancer.getProviderLoadBalancerId());
			}
		}
		return null;
	}

	private LoadBalancer describeLoadBalancer(@Nonnull final String loadBalancerId,
			final boolean cascade) throws CloudException, InternalException {
		APITrace.begin(getProvider(), "describeLoadBalancer");
		try {
			HttpUriRequest request = AliyunRequestBuilder.get()
					.provider(getProvider())
					.category(AliyunRequestBuilder.Category.SLB)
					.parameter("Action", "DescribeLoadBalancerAttribute")
					.parameter("LoadBalancerId", loadBalancerId)
					.build();
			
			ResponseHandler<LoadBalancer> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, LoadBalancer>(
					new StreamToJSONObjectProcessor(),
					new DriverToCoreMapper<JSONObject, LoadBalancer>() {
						@Override
						public LoadBalancer mapFrom(JSONObject json) {
							try {
								return toLoadBalancer(json, cascade);
							} catch (JSONException e) {
								stdLogger.error("An exception occurs during getLoadBalancer with id " + loadBalancerId, e);
								throw new RuntimeException(e);
							} catch (CloudException e) {
								stdLogger.error("An exception occurs during parsing json of load balancer with id " + loadBalancerId, e);
								throw new RuntimeException(e);
							} catch (InternalException e) {
								stdLogger.error("An exception occurs during parsing json of load balancer with id " + loadBalancerId, e);
								throw new RuntimeException(e);
							}
						}
					}, JSONObject.class);
			
			return new AliyunRequestExecutor<LoadBalancer>(getProvider(),
					AliyunHttpClientBuilderFactory.newHttpClientBuilder(), request,
					responseHandler).execute();
		} finally {
			APITrace.end();
		}
	}

	private List<LoadBalancerHealthCheck> describeHealthChecks(
			@Nonnull String loadBalancerId) throws InternalException,
			CloudException, JSONException {
		List<LoadBalancerHealthCheck> result = new ArrayList<LoadBalancerHealthCheck>();
		Map<JSONObject, LbProtocol> listenersJSON = describeListenersAttribute(loadBalancerId);
		for (Map.Entry<JSONObject, LbProtocol> listenerJSON : listenersJSON
				.entrySet()) {
			result.add(toHealthCheck(loadBalancerId, listenerJSON.getKey(),
					listenerJSON.getValue()));
		}
		return result;
	}

	private List<LbListener> describeListeners(String loadBalancerId)
			throws CloudException, InternalException, JSONException {
		List<LbListener> result = new ArrayList<LbListener>();
		Map<JSONObject, LbProtocol> listenersJSON = describeListenersAttribute(loadBalancerId);
		for (Map.Entry<JSONObject, LbProtocol> listenerJSON : listenersJSON
				.entrySet()) {
			result.add(toListener(loadBalancerId, listenerJSON.getKey(),
					listenerJSON.getValue()));
		}
		return result;
	}

	private LbListener describeListener(String loadBalancerId,
			LbProtocol lbProtocol, int listenerPort) throws CloudException,
			InternalException, JSONException {
		JSONObject listenerJSON = describeListenerAttribute(loadBalancerId,
				lbProtocol, listenerPort);
		return toListener(loadBalancerId, listenerJSON, lbProtocol);
	}

	private Map<JSONObject, LbProtocol> describeListenersAttribute(
			String loadBalancerId) throws CloudException, InternalException {
		Map<JSONObject, LbProtocol> result = new HashMap<JSONObject, LbProtocol>();

		List<LbProtocol> lbProtocols = Arrays.asList(LbProtocol.HTTP,
				LbProtocol.HTTPS, LbProtocol.RAW_TCP);
		int[] listenerPorts = describeLoadBalancer(loadBalancerId, false)
				.getPublicPorts();

		for (int listenerPort : listenerPorts) {
			JSONObject listenerJson = null;
			LbProtocol listenerProtocal = null;
			for (LbProtocol lbProtocol : lbProtocols) {
				try {
					listenerJson = describeListenerAttribute(loadBalancerId,
							lbProtocol, listenerPort);
					listenerProtocal = lbProtocol;
					break;
				} catch (CloudException cloudException) {
					if ("ListenerNotFound".equals(cloudException
							.getProviderCode())) {
						stdLogger.info(
								"Doesn't find listener with port "
										+ listenerPort + " and protocol "
										+ lbProtocol.name()
										+ " for load balancer with id "
										+ loadBalancerId, cloudException);
					} else {
						throw cloudException;
					}
				}
			}

			if (listenerJson == null) {
				throw new InternalException("Doesn't find listener with port "
						+ listenerPort + " for load balancer with id "
						+ loadBalancerId);
			} else {
				result.put(listenerJson, listenerProtocal);
			}
		}

		return result;
	}

	private JSONObject describeListenerAttribute(String loadBalancerId,
			LbProtocol lbProtocol, int listenerPort) throws CloudException,
			InternalException {
		APITrace.begin(getProvider(), "describeListenerAttribute");
		try {
			String methodName = null;
			if (lbProtocol.equals(LbProtocol.HTTP)) {
				methodName = "DescribeLoadBalancerHTTPListenerAttribute";
			} else if (lbProtocol.equals(LbProtocol.HTTPS)) {
				methodName = "DescribeLoadBalancerHTTPSListenerAttribute";
			} else if (lbProtocol.equals(LbProtocol.RAW_TCP)) {
				methodName = "DescribeLoadBalancerTCPListenerAttribute";
			}
			
			HttpUriRequest request = AliyunRequestBuilder.get()
					.provider(getProvider())
					.category(AliyunRequestBuilder.Category.SLB)
					.parameter("Action", methodName)
					.parameter("LoadBalancerId", loadBalancerId)
					.parameter("ListenerPort", listenerPort)
					.build();
			
			ResponseHandler<JSONObject> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, JSONObject>(
					new StreamToJSONObjectProcessor(),
					new DriverToCoreMapper<JSONObject, JSONObject>() {
						@Override
						public JSONObject mapFrom(JSONObject json) {
							return json;
						}
					}, JSONObject.class);
			
			return new AliyunRequestExecutor<JSONObject>(getProvider(),
					AliyunHttpClientBuilderFactory.newHttpClientBuilder(), request,
					responseHandler).execute();
		} finally {
			APITrace.end();
		}
	}

	private LoadBalancer toLoadBalancer(JSONObject response, boolean cascade)
			throws JSONException, CloudException, InternalException {
		String id = response.getString("LoadBalancerId");
		String region = response.getString("RegionId");
		String name = response.getString("LoadBalancerName");
		LoadBalancerState status = LoadBalancerState.PENDING;
		if (!getProvider().isEmpty(response.getString("LoadBalancerStatus"))
				&& response.getString("LoadBalancerStatus").equals(
						AliyunNetworkCommon.AliyunLbState.active.name())) {
			status = LoadBalancerState.ACTIVE;
		}
		String address = response.getString("Address");
		LoadBalancerAddressType addressType = getCapabilities()
				.getAddressType();
		LbType lbType = LbType.EXTERNAL;
		if (!getProvider().isEmpty(response.getString("AddressType"))
				&& response.getString("AddressType").equals(
						AliyunNetworkCommon.LoadBalancerAddressType.intranet
								.name())) {
			lbType = LbType.INTERNAL;
		}
		JSONArray portsJSONArray = response.getJSONObject("ListenerPorts")
				.getJSONArray("ListenerPort");
		int[] ports = new int[portsJSONArray.length()];
		for (int i = 0; i < portsJSONArray.length(); i++) {
			ports[i] = portsJSONArray.getInt(i);
		}
		String description = "load balancer " + id;
		LoadBalancer loadBalancer = LoadBalancer.getInstance(getContext()
				.getAccountNumber(), region, id, status, name, description,
				lbType, addressType, address, null,
				VisibleScope.ACCOUNT_REGION, ports);
		loadBalancer.createdAt(getProvider().parseIso8601Date(
				response.getString("CreateTime")).getTime());
		if ("vpc".equals(response.getString("NetworkType"))) {
			// TODO, need to refine VswitchId
			loadBalancer.withProviderSubnetIds(response.getString("VswitchId"));
			loadBalancer.forVlan(response.getString("VpcId"));
		}

		if (cascade) {
			List<LbListener> listeners = describeListeners(loadBalancer
					.getProviderLoadBalancerId());
			loadBalancer.withListeners(listeners
					.toArray(new LbListener[listeners.size()]));
		}
		loadBalancer.supportingTraffic(IPVersion.IPV4);
		return loadBalancer;
	}

	private SSLCertificate toSSLCertificate(JSONObject response)
			throws JSONException, InternalException, CloudException {
		return SSLCertificate.getInstance(
				response.getString("ServerCertificateName"),
				response.getString("ServerCertificateId"), null,
				response.getString("ServerCertificate"), null, null);
	}

	private LbListener toListener(String loadBalancerId, JSONObject response,
			LbProtocol lbProtocol) throws JSONException, InternalException,
			CloudException {
		int publicPort = response.getInt("ListenerPort");
		int privatePort = response.getInt("BackendServerPort");

		LbAlgorithm algorithm = LbAlgorithm.ROUND_ROBIN;
		if (AliyunNetworkCommon.AliyunLbScheduleAlgorithm.wlc.name()
				.equalsIgnoreCase(response.getString("Scheduler"))) {
			algorithm = LbAlgorithm.LEAST_CONN;
		}

		LbPersistence persistence = null;
		String cookie = null;
		if (lbProtocol == LbProtocol.HTTP || lbProtocol == LbProtocol.HTTPS) {
			if (AliyunNetworkCommon.AliyunLbSwitcher.on.name().equals(
					response.getString("StickySession"))) {
				persistence = LbPersistence.COOKIE;
				if ("server".equals(response.getString("StickySessionType"))) {
					cookie = response.getString("Cookie");
				}
			} else {
				persistence = LbPersistence.NONE;
			}
		}

		String certificateName = null;
		if (lbProtocol == LbProtocol.HTTPS) {
			if (!getProvider().isEmpty(
					response.getString("ServerCertificateId"))) {
				for (SSLCertificate certificate : listSSLCertificates()) {
					if (certificate.getProviderCertificateId().equals(
							response.getString("ServerCertificateId"))) {
						certificateName = certificate.getCertificateName();
					}
				}
			}
		}

		String lbHealthCheckId = null;
		if ("on".equals(response.getString("HealthCheck"))) {
			lbHealthCheckId = new LbListenerHealthCheckIdentity(loadBalancerId,
					lbProtocol, publicPort).toString();
		}

		LbListener lbListener;
		if (getProvider().isEmpty(cookie)) {
			lbListener = LbListener.getInstance(algorithm, persistence,
					lbProtocol, publicPort, privatePort);
		} else {
			lbListener = LbListener.getInstance(algorithm, cookie, lbProtocol,
					publicPort, privatePort);
		}
		lbListener.withSslCertificateName(certificateName);
		lbListener.withProviderLBHealthCheckId(lbHealthCheckId);
		return lbListener;
	}

	private LoadBalancerHealthCheck toHealthCheck(String loadBalancerId,
			JSONObject listenerJson, LbProtocol lbProtocol)
			throws JSONException, InternalException, CloudException {
		LoadBalancerHealthCheck.HCProtocol hcProtocol;
		if (lbProtocol.equals(LbProtocol.HTTP)) {
			hcProtocol = LoadBalancerHealthCheck.HCProtocol.HTTP;
		} else if (lbProtocol.equals(LbProtocol.HTTPS)) {
			hcProtocol = LoadBalancerHealthCheck.HCProtocol.HTTPS;
		} else if (lbProtocol.equals(LbProtocol.RAW_TCP)) {
			hcProtocol = LoadBalancerHealthCheck.HCProtocol.TCP;
		} else {
			stdLogger
					.error("Aliyun support HTTP, HTTPS and TCP protocol only!");
			throw new InternalException(
					"Aliyun support HTTP, HTTPS and TCP protocol only!");
		}

		if ((lbProtocol == LbProtocol.HTTP || lbProtocol == LbProtocol.HTTPS)
				&& !AliyunNetworkCommon.AliyunLbSwitcher.on.name().equals(
						listenerJson.getString("HealthCheck"))) {
			return null;
		}

		int listenerPort = listenerJson.getInt("ListenerPort");

		String httpDomain = null;
		String httpPath = null;
		if (lbProtocol == LbProtocol.HTTP || lbProtocol == LbProtocol.HTTPS) {
			httpDomain = listenerJson.getString("HealthCheckDomain");
			httpPath = listenerJson.getString("HealthCheckURI");
		}

		int healthCheckPort = listenerJson.getInt("HealthCheckConnectPort");
		int healthCheckInterval = listenerJson.getInt("HealthCheckInterval");
		int healthCheckTimeout = listenerJson.getInt("HealthCheckTimeout");
		int healthThreshold = listenerJson.getInt("HealthyThreshold");
		int unhealthyThreshold = listenerJson.getInt("UnhealthyThreshold");
		String healthCheckId = new LbListenerHealthCheckIdentity(
				loadBalancerId, lbProtocol, listenerPort).toString();
		String healthCheckName = "health check - " + healthCheckId;
		String healthCheckDescription = "health check for load balancer "
				+ loadBalancerId + " listener listens at port " + listenerPort;
		LoadBalancerHealthCheck healthCheck = LoadBalancerHealthCheck
				.getInstance(healthCheckId, healthCheckName,
						healthCheckDescription, httpDomain, hcProtocol,
						healthCheckPort, httpPath, healthCheckInterval,
						healthCheckTimeout, healthThreshold, unhealthyThreshold);
		healthCheck.addListener(toListener(loadBalancerId, listenerJson,
				lbProtocol));
		healthCheck.addProviderLoadBalancerId(loadBalancerId);
		return healthCheck;
	}

	private class LbListenerHealthCheckIdentity {
		private String loadBalancerId;
		private LbProtocol listenerProtocol;
		private int listenerPort;

		private LbListenerHealthCheckIdentity(String loadBalancerId,
				LbProtocol listenerProtocol, int listenerPort) {
			this.loadBalancerId = loadBalancerId;
			this.listenerProtocol = listenerProtocol;
			this.listenerPort = listenerPort;
		}

		private LbListenerHealthCheckIdentity(String id) {
			String[] parts = id.split(":");
			this.loadBalancerId = parts[0];
			this.listenerProtocol = LbProtocol.valueOf(parts[1]);
			this.listenerPort = Integer.parseInt(parts[2]);
		}

		public String getLoadBalancerId() {
			return loadBalancerId;
		}

		public LbProtocol getListenerProtocol() {
			return listenerProtocol;
		}

		public int getListenerPort() {
			return listenerPort;
		}

		@Override
		public String toString() {
			return loadBalancerId + ":" + listenerProtocol + ":" + listenerPort;
		}
	}
}
