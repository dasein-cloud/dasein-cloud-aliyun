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

import org.apache.log4j.Logger;
import org.dasein.cloud.*;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.aliyun.AliyunMethod;
import org.dasein.cloud.network.*;
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

    private static final Logger stdLogger = Aliyun.getStdLogger(AliyunLoadBalancer.class);

    private transient volatile AliyunLoadBalancerCapabilities capabilities;

    protected AliyunLoadBalancer(Aliyun provider) {
        super(provider);
    }

    /**
     * Create and Start Load Balanacer Listener
     * Note: Aliyun create health check during the creation of listners, however Dasein create health check and then create listener.
     * Solution: 1. If the load balancer doesn't have health check (classified by type: HTTP, HTTPS and TCP): 
     * 				turn off the health check for the listener with the same type.
     *           2. If the load balancer already has health check (classified by type: HTTP, HTTPS and TCP): 
     *           	get the health check and set it as the health check of this new listener with the same type.
     * @param toLoadBalancerId load balancer id
     * @param listeners listeners
     * @throws CloudException
     * @throws InternalException
     */
    @Override
    public void addListeners(@Nonnull String toLoadBalancerId, @Nullable LbListener[] listeners) throws CloudException, InternalException {

        for (int i = 0; i < listeners.length; i++) {

            LbListener listener = listeners[i];

            //translate listener protocol to health check protocol
            LoadBalancerHealthCheck.HCProtocol protocol = null;
            if (listener.getNetworkProtocol().equals(LbProtocol.HTTP)) {
                protocol = LoadBalancerHealthCheck.HCProtocol.HTTP;
            } else if (listener.getNetworkProtocol().equals(LbProtocol.HTTPS)) {
                protocol = LoadBalancerHealthCheck.HCProtocol.HTTPS;
            } else if (listener.getNetworkProtocol().equals(LbProtocol.RAW_TCP)) {
                protocol = LoadBalancerHealthCheck.HCProtocol.TCP;
            } else {
                stdLogger.error("Aliyun supports HTTP, HTTPS and TCP as the health check protocol only!");
                throw new InternalException("Aliyun supports HTTP, HTTPS and TCP as the health check protocol only!");
            }

            //validate network protocol
            if (listener.getNetworkProtocol() == null || !listener.getNetworkProtocol().equals(LbProtocol.HTTP) ||
                    !listener.getNetworkProtocol().equals(LbProtocol.HTTPS) || !listener.getNetworkProtocol().equals(LbProtocol.RAW_TCP)) {
                throw new InternalException("Aliyun supports HTTP, HTTPS and RAW TCP as the load balancer algorithms only!");
            }

            Map<String, Object> params = new HashMap<String, Object>();

            //common
            params.put("LoadBalancerId", toLoadBalancerId);
            params.put("ListenerPort", listener.getPublicPort());
            params.put("BackendServerPort", listener.getPrivatePort());
            params.put("Bandwidth", AliyunNetworkCommon.DefaultLoadBalancerBandwidth);

            //scheduler
            if (listener.getAlgorithm() != null) {
                if (listener.getAlgorithm().equals(LbAlgorithm.ROUND_ROBIN)) {
                    params.put("Scheduler", AliyunNetworkCommon.AliyunLbScheduleAlgorithm.WRR.name().toLowerCase());
                } else if (listener.getAlgorithm().equals(LbAlgorithm.LEAST_CONN)) {
                    params.put("Scheduler", AliyunNetworkCommon.AliyunLbScheduleAlgorithm.WLC.name().toLowerCase());
                } else {
                    throw new InternalException("Aliyun supports weighted round robin and weighted least connection scheduler algorithms only!");
                }
            }

            //persistence and cookie
            if (listener.getNetworkProtocol().equals(LbProtocol.HTTP) || listener.getNetworkProtocol().equals(LbProtocol.HTTPS)){
                if (listener.getPersistence().equals(LbPersistence.NONE)) {
                    params.put("StickySession", AliyunNetworkCommon.AliyunLbSwitcher.OFF.name().toLowerCase());
                } else if (listener.getPersistence().equals(LbPersistence.COOKIE)) {
                    params.put("StickySession", AliyunNetworkCommon.AliyunLbSwitcher.ON.name().toLowerCase());
                    if (!AliyunNetworkCommon.isEmpty(listener.getCookie())) { //no cookie, slb insert
                        params.put("StickySessionType", AliyunNetworkCommon.AliyunLbPersistenceType.INSERT.name().toLowerCase());
                        params.put("CookieTimeout", AliyunNetworkCommon.DefaultPersistenceTimeout);
                    } else { //cookie, server insert
                        params.put("StickySessionType", AliyunNetworkCommon.AliyunLbPersistenceType.SERVER.name().toLowerCase());
                        params.put("Cookie", listener.getCookie());
                    }
                }
            } else {
                params.put("PersistenceTimeout", AliyunNetworkCommon.DefaultPersistenceTimeout);
            }

            //health check
            LoadBalancerHealthCheck healthCheck = null;
            try {
                healthCheck = getLoadBalancerHealthCheckByProtocol(toLoadBalancerId, protocol);
                if (healthCheck != null) {
                    params.put("HealthCheckConnectPort", healthCheck.getPort());
                    params.put("HealthyThreshold", healthCheck.getHealthyCount());
                    params.put("UnhealthyThreshold", healthCheck.getUnhealthyCount());
                    params.put("HealthCheckConnectTimeout", healthCheck.getTimeout());
                    params.put("HealthCheckInterval", healthCheck.getInterval());
                    if (listener.getNetworkProtocol().equals(LbProtocol.HTTP) || listener.getNetworkProtocol().equals(LbProtocol.HTTPS)) {
                        params.put("HealthCheck", AliyunNetworkCommon.AliyunLbSwitcher.ON.name().toLowerCase());
                        params.put("HealthCheckDomain", "$_ip");
                        params.put("HealthCheckURI", healthCheck.getPath());

                    }
                } else {
                    if (listener.getNetworkProtocol().equals(LbProtocol.HTTP) || listener.getNetworkProtocol().equals(LbProtocol.HTTPS)) {
                        params.put("HealthCheck", AliyunNetworkCommon.AliyunLbSwitcher.OFF.name().toLowerCase());
                    }
                }
            } catch (JSONException e) {
                stdLogger.error("An exception occurs during get health check for load balancer " + toLoadBalancerId + " by protocol " + protocol.name());
            }

            //invoke method name
            String methodName = null;
            if (listener.getNetworkProtocol().equals(LbProtocol.HTTP)) {
                methodName = "CreateLoadBalancerHTTPListener";
            } else if (listener.getNetworkProtocol().equals(LbProtocol.HTTPS)) {
                SSLCertificate certificate = getSSLCertificate(listener.getSslCertificateName());
                if (certificate != null) {
                    params.put("ServerCertificateId", certificate.getProviderCertificateId());
                } else {
                    stdLogger.warn("for add healthcheck for https listener, ssl certificate should be added. However, ssl certificate with name "
                            + listener.getSslCertificateName() + " cannot be found!");
                }
                methodName = "CreateLoadBalancerHTTPSListener";
            } else if (listener.getNetworkProtocol().equals(LbProtocol.RAW_TCP)) {
                methodName = "CreateLoadBalancerTCPListener";
            }
            
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, methodName, params);
            getProvider().validateResponse(method.post().asJson());
            
            //2. start listener
            params = new HashMap<String, Object>();
            params.put("LoadBalancerId", toLoadBalancerId);
            params.put("ListenerPort", listener.getPublicPort());
            method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "StartLoadBalancerListener", params);
            getProvider().validateResponse(method.post().asJson());
        }
    }

    /**
     * stop first and then remove listener
     * @param toLoadBalancerId
     * @param listeners
     * @throws CloudException
     * @throws InternalException
     */
    @Override
    public void removeListeners(@Nonnull String toLoadBalancerId, @Nullable LbListener[] listeners) throws CloudException, InternalException {
        for (int i = 0; i < listeners.length; i++) {
            LbListener listener = listeners[i];
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("LoadBalancerId", toLoadBalancerId);
            params.put("ListenerPort", listener.getPublicPort());
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "DeleteLoadBalancerListener", params);
            getProvider().validateResponse(method.post().asJson());
        }
    }

    @Override
    public void addServers(@Nonnull String toLoadBalancerId, @Nonnull String... serverIdsToAdd) throws CloudException, InternalException {
        try {
            JSONArray jsonArray = new JSONArray();
            for (String serverIdToAdd : serverIdsToAdd) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("ServerId", serverIdToAdd);
                jsonObject.put("Weight", AliyunNetworkCommon.DefaultServerWeight);
                jsonArray.put(jsonObject);
            }
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("LoadBalancerId", toLoadBalancerId);
            params.put("BackendServers", jsonArray.toString());
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "AddBackendServers", params);
            getProvider().validateResponse(method.post().asJson());
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during add backend servers!", e);
            throw new InternalException(e);
        }
    }

    @Nonnull
    @Override
    public String createLoadBalancer(@Nonnull LoadBalancerCreateOptions options) throws CloudException, InternalException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        if (!AliyunNetworkCommon.isEmpty(options.getName())) {
            params.put("LoadBalancerName", options.getName());
        }
        if (options.getProviderSubnetIds() != null && options.getProviderSubnetIds().length > 0) {
            if (options.getProviderSubnetIds().length == 1) {
                params.put("VSwitchId", options.getProviderSubnetIds()[0]);
            } else {
                throw new InternalException("Aliyun supports add load balancer to only one subnet!");
            }
        }
        if (options.getType() != null && options.getType().equals(LbType.INTERNAL)) {
            params.put("AddressType", AliyunNetworkCommon.LoadBalancerAddressType.INTRANET.name().toLowerCase());
        }
        params.put("InternetChargeType", AliyunNetworkCommon.InternetChargeType.PayByTraffic.name());
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "CreateLoadBalancer", params, true);
        JSONObject response = method.post().asJson();
        try {
            return response.getString("LoadBalancerId");
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during create load balancer!", e);
            throw new InternalException(e);
        }
    }

    @Nonnull
    @Override
    public LoadBalancerCapabilities getCapabilities() throws CloudException, InternalException {
        if (capabilities == null) {
            capabilities = new AliyunLoadBalancerCapabilities(getProvider());
        }
        return capabilities;
    }

    @Override
    public SSLCertificate createSSLCertificate(@Nonnull SSLCertificateCreateOptions options) throws CloudException, InternalException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        params.put("ServerCertificate", options.getCertificateBody());
        if (!AliyunNetworkCommon.isEmpty(options.getCertificateName())) {
            params.put("ServerCertificateName", options.getCertificateName());
        }
        params.put("PrivateKey", options.getPrivateKey());
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "UploadServerCertificate", params);
        try {
            JSONObject response = method.post().asJson();
            return toSSLCertificate(response);
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during create ssl certificate!", e);
            throw new InternalException(e);
        }
    }

    @Override
    public LoadBalancer getLoadBalancer(@Nonnull String loadBalancerId) throws CloudException, InternalException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("LoadBalancerId", loadBalancerId);
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "DescribeLoadBalancerAttribute", params);
        JSONObject response = method.get().asJson();
        try {
            return toLoadBalancer(response);
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during getLoadBalancer with id " + loadBalancerId, e);
            throw new InternalException(e);
        }
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        return true;
    }

    @Nullable
    @Override
    public SSLCertificate getSSLCertificate(@Nonnull String certificateName) throws CloudException, InternalException {
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
    public Iterable<LoadBalancer> listLoadBalancers() throws CloudException, InternalException {
        try {
            List<LoadBalancer> loadBalancers = listSimpleLoadBalancers();
            for (LoadBalancer loadBalancer : loadBalancers) {
                loadBalancers.add(getLoadBalancer(loadBalancer.getProviderLoadBalancerId()));
            }
            return loadBalancers;
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during listLoadBalancers!", e);
            throw new InternalException(e);
        }
    }

    @Nonnull
    @Override
    public Iterable<ResourceStatus> listLoadBalancerStatus() throws CloudException, InternalException {
        List<ResourceStatus> resourceStatuses = new ArrayList<ResourceStatus>();
        try {
            List<LoadBalancer> loadBalancers = listSimpleLoadBalancers();
            for (LoadBalancer loadBalancer : loadBalancers) {
                resourceStatuses.add(new ResourceStatus(loadBalancer.getProviderLoadBalancerId(), loadBalancer.getCurrentState()));
            }
            return resourceStatuses;
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during listLoadBalancerStatus!", e);
            throw new InternalException(e);
        }
    }

    @Nonnull
    @Override
    public Iterable<LoadBalancerEndpoint> listEndpoints(@Nonnull String forLoadBalancerId) throws CloudException, InternalException {
        try {
            Map<String, LoadBalancerEndpoint> loadBalancerEndpointMap = new HashMap<String, LoadBalancerEndpoint>();
            LoadBalancer loadBalancer = getLoadBalancer(forLoadBalancerId);
            int[] listenerPorts = loadBalancer.getPublicPorts();
            for (int i = 0; i < listenerPorts.length; i++) {
                Map<String, Object> params = new HashMap<String, Object>();
                params.put("LoadBalancerId", forLoadBalancerId);
                params.put("ListenerPort", listenerPorts[i]);
                AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "DescribeHealthStatus", params);
                JSONObject response = method.get().asJson();

                JSONArray backendServers = response.getJSONObject("BackendServers").getJSONArray("BackendServer");
                for (int j = 0; j < backendServers.length(); j++) {
                    JSONObject backendServer = backendServers.getJSONObject(j);
                    String serverId = backendServer.getString("ServerId");
                    LbEndpointState state = LbEndpointState.INACTIVE;
                    if (backendServer.getString("ServerHealthStatus").toUpperCase().equals(
                            AliyunNetworkCommon.AliyunLbEndpointState.NORMAL.name())) {
                        state = LbEndpointState.ACTIVE;
                    }
                    if (!loadBalancerEndpointMap.containsKey(serverId)) { //not contain
                        loadBalancerEndpointMap.put(serverId, LoadBalancerEndpoint.getInstance(LbEndpointType.VM, serverId, state));
                    } else { //already contains
                        LoadBalancerEndpoint endpoint = loadBalancerEndpointMap.get(serverId);
                        if (state.equals(LbEndpointState.INACTIVE) && endpoint.getCurrentState().equals(LbEndpointState.ACTIVE)) {
                            loadBalancerEndpointMap.remove(serverId);
                            loadBalancerEndpointMap.put(serverId, LoadBalancerEndpoint.getInstance(LbEndpointType.VM, serverId, LbEndpointState.INACTIVE));
                        }
                    }
                }
            }
            return loadBalancerEndpointMap.values();
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during listEndpoints for load balander with id " + forLoadBalancerId, e);
            throw new InternalException(e);
        }
    }

    @Nonnull
    @Override
    public Iterable<SSLCertificate> listSSLCertificates() throws CloudException, InternalException {
        List<SSLCertificate> sslCertificates = new ArrayList<SSLCertificate>();
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "DescribeServerCertificates", params);
        JSONObject response = method.get().asJson();
        try {
            JSONArray sslCertificatesResponse = response.getJSONObject("ServerCertificates").getJSONArray("ServerCertificate");
            for (int i = 0; i < sslCertificatesResponse.length(); i++) {
                sslCertificates.add(toSSLCertificate(sslCertificatesResponse.getJSONObject(i)));
            }
            return sslCertificates;
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during listSSLCertificates!", e);
            throw new InternalException(e);
        }
    }

    @Override
    public void removeLoadBalancer(@Nonnull String loadBalancerId) throws CloudException, InternalException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("LoadBalancerId", loadBalancerId);
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "DeleteLoadBalancer", params);
        JSONObject response = method.post().asJson();
        getProvider().validateResponse(response);
    }

    @Override
    public void removeSSLCertificate(@Nonnull String certificateName) throws CloudException, InternalException {
        SSLCertificate certificate = getSSLCertificate(certificateName);
        if (certificate != null) {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("RegionId", getContext().getRegionId());
            params.put("ServerCertificateId", certificate.getProviderCertificateId());
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "DeleteServerCertificate", params);
            JSONObject response = method.post().asJson();
            getProvider().validateResponse(response);
        }
    }

    @Override
    public void removeServers(@Nonnull String fromLoadBalancerId, @Nonnull String... serverIdsToRemove) throws CloudException, InternalException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("LoadBalancerId", fromLoadBalancerId);
        JSONArray servers = new JSONArray();
        for (int i = 0; i < serverIdsToRemove.length; i++) {
            try {
                servers.put(i, serverIdsToRemove[i]);
            } catch (JSONException e) {
                stdLogger.error("An exception occurs during remove servers!", e);
                throw new InternalException(e);
            }
        }
        params.put("BackendServers", servers);
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "RemoveBackendServers", params);
        JSONObject response = method.post().asJson();
        getProvider().validateResponse(response);
    }

    @Override
    public void setSSLCertificate(@Nonnull SetLoadBalancerSSLCertificateOptions options) throws CloudException, InternalException {
        SSLCertificate certificate = getSSLCertificate(options.getSslCertificateName());
        int port = options.getSslCertificateAssignToPort();
        LoadBalancer loadBalancer = null;
        try {
            loadBalancer = getLoadBalancerByName(options.getLoadBalancerName());
            List<LbListener> listeners = getLbListeners(loadBalancer.getProviderLoadBalancerId(), port, LbProtocol.HTTPS);
            //Only one listener listen at a specific port
            if (!AliyunNetworkCommon.isEmpty(listeners)) {
                Map<String, Object> params = new HashMap<String, Object>();
                params.put("LoadBalancerId", loadBalancer.getProviderLoadBalancerId());
                params.put("ListenerPort", listeners.get(0).getPublicPort());
                params.put("Bandwidth", AliyunNetworkCommon.DefaultLoadBalancerBandwidth);
                params.put("ServerCertificateId", certificate.getProviderCertificateId());
                AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "SetLoadBalancerHTTPSListenerAttribute", params);
                JSONObject response = method.post().asJson();
                getProvider().validateResponse(response);
            }
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during setSSLCertificate", e);
            throw new InternalException(e);
        }
    }

    @Override
    public LoadBalancerHealthCheck createLoadBalancerHealthCheck(@Nullable String name, @Nullable String description,
                                                                 @Nullable String host, @Nullable LoadBalancerHealthCheck.HCProtocol protocol,
                                                                 int port, @Nullable String path, int interval, int timeout, int healthyCount,
                                                                 int unhealthyCount) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Aliyun doesn't support create health check without associated load balancer!");
    }

    /**
     * New or Update health check for specific type of listener (HTTP, HTTPS and TCP), Aliyun support same protocol for listener and health check.
     * @return load balancer health check instance
     * @throws CloudException
     * @throws InternalException
     */
    @Override
    public LoadBalancerHealthCheck createLoadBalancerHealthCheck(@Nonnull HealthCheckOptions options) throws CloudException, InternalException {

        //retrieve load balancer by id
        LoadBalancer loadBalancer = getLoadBalancer(options.getProviderLoadBalancerId());
        try {

            //translate healtch check protocol to listener protocol
            LbProtocol protocol = null;
            String methodName = null;
            if (options.getProtocol().equals(LoadBalancerHealthCheck.HCProtocol.HTTP)) {
                protocol = LbProtocol.HTTP;
                methodName = "SetLoadBalancerHTTPListenerAttribute";
            } else if (options.getProtocol().equals(LoadBalancerHealthCheck.HCProtocol.HTTPS)) {
                protocol = LbProtocol.HTTPS;
                methodName = "SetLoadBalancerHTTPSListenerAttribute";
            } else if (options.getProtocol().equals(LoadBalancerHealthCheck.HCProtocol.TCP)) {
                protocol = LbProtocol.RAW_TCP;
                methodName = "SetLoadBalancerTCPListenerAttribute";
            } else {
                throw new InternalException("Aliyun supports HTTP, HTTPS and TCP as the health check protocol only!");
            }

            //find the same protocol listener and set the healthcheck ON and also the related fields.
            List<LbListener> listeners = getLbListeners(loadBalancer.getProviderLoadBalancerId(), 0, protocol);
            for (LbListener listener : listeners) {

                Map<String, Object> params = new HashMap<String, Object>();

                //listener common settings, keep it the same
                params.put("LoadBalancerId", loadBalancer.getProviderLoadBalancerId());
                params.put("ListenerPort", listener.getPublicPort());
                params.put("Bandwidth", AliyunNetworkCommon.DefaultLoadBalancerBandwidth);
                //algorithm
                AliyunNetworkCommon.AliyunLbScheduleAlgorithm algorithm = AliyunNetworkCommon.AliyunLbScheduleAlgorithm.WRR;
                if (listener.getAlgorithm().equals(LbAlgorithm.LEAST_CONN)) {
                    algorithm = AliyunNetworkCommon.AliyunLbScheduleAlgorithm.WLC;
                }
                //scheduler
                params.put("Scheduler", algorithm.name().toLowerCase());
                //sticky, cookie
                if (listener.getPersistence().equals(LbPersistence.NONE)) {
                    params.put("StickySession", AliyunNetworkCommon.AliyunLbSwitcher.OFF.name().toLowerCase());
                } else if (listener.getPersistence().equals(LbPersistence.COOKIE)) {
                    if (protocol.equals(LbProtocol.HTTP) || protocol.equals(LbProtocol.HTTPS)) {
                        params.put("StickySession", AliyunNetworkCommon.AliyunLbSwitcher.ON.name().toLowerCase());
                        params.put("StickySessionType", AliyunNetworkCommon.AliyunLbPersistenceType.INSERT.name().toLowerCase());
                        params.put("CookieTimeout", AliyunNetworkCommon.DefaultPersistenceTimeout);
                        params.put("Cookie", listener.getCookie());
                    }
                } else {
                    throw new InternalException("Aliyun support Cookie as the sticky session type only!");
                }
                //certificate
                if (protocol.equals(LbProtocol.HTTPS)) {
                    SSLCertificate sslCertificate = getSSLCertificate(listener.getSslCertificateName());
                    params.put("ServerCertificateId", sslCertificate.getProviderCertificateId());
                }

                //health check
                params.put("HealthCheckConnectPort", options.getPort());
                params.put("HealthyThreshold", options.getHealthyCount());
                params.put("UnhealthyThreshold", options.getUnhealthyCount());
                params.put("HealthCheckConnectTimeout", options.getTimeout());
                params.put("HealthCheckInterval", options.getInterval());

                if (protocol.equals(LbProtocol.HTTP) || protocol.equals(LbProtocol.HTTPS)) {
                    params.put("HealthCheck", AliyunNetworkCommon.AliyunLbSwitcher.ON.name().toLowerCase());
                    params.put("HealthCheckURI", options.getPath());
                    if (!AliyunNetworkCommon.isEmpty(options.getHost())) {
                        params.put("HealthCheckDomain", options.getHost());
                    } else {
                        params.put("HealthCheckDomain", "$_ip");
                    }
                }

                AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, methodName, params);
                getProvider().validateResponse(method.post().asJson());
            }
            return generateHealthCheck(loadBalancer.getProviderLoadBalancerId(), listeners.get(0).getPublicPort(),
                    options.getProtocol(), options.getPort(), options.getPath(), options.getInterval(),
                    options.getTimeout(), options.getHealthyCount(), options.getUnhealthyCount());
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during createLoadBalancerHealthCheck with options!", e);
            throw new InternalException(e);
        }
    }

    @Override
    public Iterable<LoadBalancerHealthCheck> listLBHealthChecks(@Nullable HealthCheckFilterOptions opts) throws CloudException, InternalException {
        List<LoadBalancerHealthCheck> healthChecks = new ArrayList<LoadBalancerHealthCheck>();
        try {
            List<LoadBalancer> loadBalancers = listSimpleLoadBalancers();
            for (LoadBalancer loadBalancer : loadBalancers) {
                List<LoadBalancerHealthCheck> loadBalancerHealthChecks = getLoadBalancerHealthChecks(loadBalancer.getProviderLoadBalancerId());
                for (LoadBalancerHealthCheck healthCheck : loadBalancerHealthChecks) {
                    if (healthCheck != null && (opts == null || opts.matches(healthCheck))) {
                        healthChecks.add(healthCheck);
                    }
                }
            }
            return healthChecks;
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during listLBHealthChecks with options", e);
            throw new InternalException(e);
        }
    }

    @Override
    public LoadBalancerHealthCheck getLoadBalancerHealthCheck(@Nonnull String providerLBHealthCheckId, @Nullable String providerLoadBalancerId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Aliyun doesn't support retrieve health check by id since there is no id for health check " +
                "and one load balancer can have more than one health check!");
    }

    /**
     * health check protocol cannot be changed
     * @param providerLBHealthCheckId
     * @param options
     * @return
     * @throws InternalException
     * @throws CloudException
     */
    @Override
    public LoadBalancerHealthCheck modifyHealthCheck(@Nonnull String providerLBHealthCheckId, @Nonnull HealthCheckOptions options)
            throws InternalException, CloudException {
        LbProtocol protocol = null;
        String methodName = null;
        if (options.getProtocol().equals(LoadBalancerHealthCheck.HCProtocol.HTTP)) {
            protocol = LbProtocol.HTTP;
            methodName = "SetLoadBalancerHTTPListenerAttribute";
        } else if (options.getProtocol().equals(LoadBalancerHealthCheck.HCProtocol.HTTPS)) {
            protocol = LbProtocol.HTTPS;
            methodName = "SetLoadBalancerHTTPSListenerAttribute";
        } else if (options.getProtocol().equals(LoadBalancerHealthCheck.HCProtocol.TCP)) {
            protocol = LbProtocol.RAW_TCP;
            methodName = "SetLoadBalancerTCPListenerAttribute";
        } else {
            stdLogger.error("Aliyun support HTTP, HTTPS and TCP protocol only!");
            throw new InternalException("Aliyun support HTTP, HTTPS and TCP protocol only!");
        }
        try {
            LoadBalancer loadBalancer = getLoadBalancer(options.getProviderLoadBalancerId());
            List<LbListener> listeners = getLbListeners(loadBalancer.getProviderLoadBalancerId(), 0, protocol);
            Map<String, Object> params = new HashMap<String, Object>();
            //health check
            if (options.getHealthyCount() >= 0) {
                params.put("HealthyThreshold", options.getHealthyCount());
            }
            if (options.getUnhealthyCount() >= 0) {
                params.put("UnhealthyThreshold", options.getUnhealthyCount());
            }
            if (options.getInterval() >= 0) {
                params.put("HealthCheckInterval", options.getInterval());
            }
            if (options.getTimeout() >= 0) {
                params.put("HealthCheckTimeout", options.getTimeout());
            }
            if (options.getPort() >= 0) {
                params.put("HealthCheckConnectPort", options.getPort());
            }
            //type specific params
            if (options.getProtocol().equals(LoadBalancerHealthCheck.HCProtocol.HTTP)
                    || options.getProtocol().equals(LoadBalancerHealthCheck.HCProtocol.HTTPS)) {
                if (!AliyunNetworkCommon.isEmpty(options.getPath())) {
                    params.put("HealthCheckURI", options.getPath());
                }
                if (!AliyunNetworkCommon.isEmpty(options.getHost())) {
                    params.put("HealthCheckDomain", options.getHost());
                } else {
                    params.put("HealthCheckDomain", "$_ip");
                }
                params.put("HealthCheck", AliyunNetworkCommon.AliyunLbSwitcher.ON.name().toLowerCase());
            }
            //modify attribute for all listeners
            for (LbListener listener : listeners) {
                //common params for listener should be configure also.
                params.put("LoadBalancerId", loadBalancer.getProviderLoadBalancerId());
                params = appendParamsByListener(params, listener);
                //change health check
                AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, methodName, params);
                JSONObject response = method.post().asJson();
                getProvider().validateResponse(response);
            }
            return getLoadBalancerHealthCheckByProtocol(loadBalancer.getProviderLoadBalancerId(), options.getProtocol());
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during modifyHealthCheck!", e);
            throw new InternalException(e);
        }
    }

    /**
     * Set OFF the health check for http and https health check. and also set default values for other fields.
     * @param providerLoadBalancerId
     * @throws CloudException
     * @throws InternalException
     */
    @Override
    public void removeLoadBalancerHealthCheck(@Nonnull String providerLoadBalancerId) throws CloudException, InternalException {
        LoadBalancer loadBalancer = getLoadBalancer(providerLoadBalancerId);
        List<LbListener> listeners = null;
        try {
            List<LbProtocol> protocols = Arrays.asList(LbProtocol.RAW_TCP, LbProtocol.HTTPS, LbProtocol.HTTP);
            for (LbProtocol protocol : protocols) {
                listeners = getLbListeners(loadBalancer.getProviderLoadBalancerId(), 0, protocol);
                for (LbListener listener : listeners) {
                    Map<String, Object> params = new HashMap<String, Object>();
                    params.put("LoadBalancerId", providerLoadBalancerId);
                    params.put("ListenerPort", listener.getPublicPort());
                    params.put("Bandwidth", AliyunNetworkCommon.DefaultLoadBalancerBandwidth);
                    //set to default
                    params.put("HealthCheckConnectPort", 1);
                    params.put("HealthyThreshold", 1);
                    params.put("UnhealthyThreshold", 1);
                    params.put("HealthCheckConnectTimeout", 1);
                    params.put("HealthCheckInterval", 1);
                    String methodName = null;
                    if (protocol.equals(LbProtocol.RAW_TCP)) {
                        methodName = "SetLoadBalancerTCPListenerAttribute";
                    } else {
                        params.put("HealthCheck", AliyunNetworkCommon.AliyunLbSwitcher.OFF.name().toLowerCase());
                        if (protocol.equals(LbProtocol.HTTP)) {
                            methodName = "SetLoadBalancerHTTPListenerAttribute";
                        } else if (protocol.equals(LbProtocol.HTTPS)) {
                            methodName = "SetLoadBalancerHTTPSListenerAttribute";
                        }
                    }
                    params = appendParamsByListener(params, listener);
                    AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, methodName, params);
                    JSONObject response = method.post().asJson();
                    getProvider().validateResponse(response);
                }
            }
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during remove health check by load balancer id " + providerLoadBalancerId, e);
            throw new InternalException(e);
        }
    }

    private Map<String, Object> appendParamsByListener (Map<String, Object> params, LbListener listener)
            throws JSONException, CloudException, InternalException{
        params.put("ListenerPort", listener.getPublicPort());
        params.put("Bandwidth", AliyunNetworkCommon.DefaultLoadBalancerBandwidth);
        //scheduler
        AliyunNetworkCommon.AliyunLbScheduleAlgorithm algorithm = AliyunNetworkCommon.AliyunLbScheduleAlgorithm.WRR;
        if (listener.getAlgorithm().equals(LbAlgorithm.LEAST_CONN)) {
            algorithm = AliyunNetworkCommon.AliyunLbScheduleAlgorithm.WLC;
        }
        params.put("Scheduler", algorithm.name().toLowerCase());
        //sticky, cookie
        if (listener.getNetworkProtocol().equals(LbProtocol.HTTP) || listener.getNetworkProtocol().equals(LbProtocol.HTTPS)) {
            if (listener.getPersistence().equals(LbPersistence.NONE)) {
                params.put("StickySession", AliyunNetworkCommon.AliyunLbSwitcher.OFF.name().toLowerCase());
            } else if (listener.getPersistence().equals(LbPersistence.COOKIE)) {
                params.put("StickySession", AliyunNetworkCommon.AliyunLbSwitcher.ON.name().toLowerCase());
                if (!AliyunNetworkCommon.isEmpty(listener.getCookie())) { //server insert
                    params.put("StickySessionType", AliyunNetworkCommon.AliyunLbPersistenceType.SERVER.name().toLowerCase());
                    params.put("Cookie", listener.getCookie());
                } else { //slb insert
                    params.put("StickySessionType", AliyunNetworkCommon.AliyunLbPersistenceType.INSERT.name().toLowerCase());
                    params.put("CookieTimeout", AliyunNetworkCommon.DefaultPersistenceTimeout);
                }
            }
        }
        //certificate
        if (listener.getNetworkProtocol().equals(LbProtocol.HTTPS)) {
            SSLCertificate certificate = getSSLCertificate(listener.getSslCertificateName());
            params.put("ServerCertificateId", certificate.getProviderCertificateId());
        }
        return params;
    }

    /**
     * One health check for each type (HTTP, HTTPS, TCP)
     * @param providerLoadBalancerId
     * @return
     * @throws InternalException
     * @throws CloudException
     * @throws JSONException
     */
    private List<LoadBalancerHealthCheck> getLoadBalancerHealthChecks (@Nonnull String providerLoadBalancerId)
            throws InternalException, CloudException, JSONException {
        List<LoadBalancerHealthCheck> healthChecks = new ArrayList<LoadBalancerHealthCheck>();
        LoadBalancer loadBalancer = getLoadBalancer(providerLoadBalancerId);
        List<LbProtocol> protocols = Arrays.asList(LbProtocol.HTTP, LbProtocol.HTTPS, LbProtocol.RAW_TCP);
        for (LbProtocol protocol : protocols) {
            List<LbListener> listeners = getLbListeners(loadBalancer.getProviderLoadBalancerId(), 0, protocol);
            if (!AliyunNetworkCommon.isEmpty(listeners)) {
                Map<String, Object> params = new HashMap<String, Object>();
                params.put("LoadBalancerId", loadBalancer.getProviderLoadBalancerId());
                params.put("ListenerPort", listeners.get(0).getPublicPort());
                String methodName = null;
                LoadBalancerHealthCheck.HCProtocol hcProtocol = null;
                if (protocol.equals(LbProtocol.HTTP)) {
                    methodName = "DescribeLoadBalancerHTTPListenerAttribute";
                    hcProtocol = LoadBalancerHealthCheck.HCProtocol.HTTP;
                } else if (protocol.equals(LbProtocol.HTTPS)) {
                    methodName = "DescribeLoadBalancerHTTPSListenerAttribute";
                    hcProtocol = LoadBalancerHealthCheck.HCProtocol.HTTPS;
                } else if (protocol.equals(LbProtocol.RAW_TCP)) {
                    methodName = "DescribeLoadBalancerTCPListenerAttribute";
                    hcProtocol = LoadBalancerHealthCheck.HCProtocol.TCP;
                } else {
                    stdLogger.error("Aliyun support HTTP, HTTPS and TCP protocol only!");
                    throw new InternalException("Aliyun support HTTP, HTTPS and TCP protocol only!");
                }
                AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, methodName, params);
                JSONObject response = method.get().asJson();
                healthChecks.add(toHealthCheck(response, hcProtocol, providerLoadBalancerId, listeners.get(0).getPublicPort()));
            }
        }
        return healthChecks;
    }

    /**
     * return load balancers contains simple information:
     *  - load balancer id
     *  - load balancer name
     *  - load balancer status
     * @return list of simple load balancers
     * @throws CloudException
     * @throws InternalException
     */
    private List<LoadBalancer> listSimpleLoadBalancers() throws JSONException, InternalException, CloudException {
        List<LoadBalancer> loadBalancers = new ArrayList<LoadBalancer>();
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "DescribeLoadBalancers", params);
        JSONObject response = method.get().asJson();
        for (int i = 0; i < response.getJSONObject("LoadBalancers").getJSONArray("LoadBalancer").length(); i++) {
            JSONObject simpleLoadBalancer = response.getJSONObject("LoadBalancers").getJSONArray("LoadBalancer").getJSONObject(i);
            //TODO check status
            LoadBalancerState status = LoadBalancerState.PENDING;
            if (!AliyunNetworkCommon.isEmpty(simpleLoadBalancer.getString("LoadBalancerStatus")) &&
                    simpleLoadBalancer.getString("LoadBalancerStatus").toUpperCase().equals(LoadBalancerState.ACTIVE.name().toUpperCase())) {
                status = LoadBalancerState.ACTIVE;
            }
            loadBalancers.add(LoadBalancer.getInstance(getContext().getAccountNumber(), getContext().getRegionId(),
                    simpleLoadBalancer.getString("LoadBalancerId"), status, simpleLoadBalancer.getString("LoadBalancerName"),
                    null, null, null));
        }
        return loadBalancers;
    }

    /**
     * Get listeners by load balancer id, port and protocol.
     * 1. If not specify listenerPort and protocol: then will return all the listeners associated with the load balancer.
     * 2. If not specify listenerPort: then will return all the listeners associated with the load balancer for the specific protocol.
     * 3. If not specify protocol: then will return all the listeners associated with the load balancer for the specific port. (unique)
     * @param loadBalancerId
     * @param listenerPort
     * @param protocol
     * @return
     * @throws JSONException
     * @throws InternalException
     * @throws CloudException
     */
    private List<LbListener> getLbListeners (String loadBalancerId, int listenerPort, LbProtocol protocol)
            throws JSONException, InternalException, CloudException {

        List<LbListener> listeners = new ArrayList<LbListener>();
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("LoadBalancerId", loadBalancerId);

        //for protocol equals to null: all supported; otherwise specific one.
        List<LbProtocol> protocolList = new ArrayList<LbProtocol>();
        if (protocol != null) {
            protocolList.add(protocol);
        } else {
            protocolList.addAll(Arrays.asList(LbProtocol.HTTP, LbProtocol.HTTPS, LbProtocol.RAW_TCP));
        }

        //for port list contains all or specific one.
        List<Integer> portList = new ArrayList<Integer>();
        if (listenerPort > 0) {
            portList.add(listenerPort);
        } else {
            LoadBalancer loadBalancer = getLoadBalancer(loadBalancerId);
            for (int publicPort : loadBalancer.getPublicPorts()) {
                portList.add(publicPort);
            }
        }

        for (LbProtocol lbProtocol : protocolList) {
            String methodName = null;
            if (lbProtocol.equals(LbProtocol.HTTP)) {
                methodName = "DescribeLoadBalancerHTTPListenerAttribute";
            } else if (lbProtocol.equals(LbProtocol.HTTPS)) {
                methodName = "DescribeLoadBalancerHTTPSListenerAttribute";
            } else if (lbProtocol.equals(LbProtocol.RAW_TCP)) {
                methodName = "DescribeLoadBalancerTCPListenerAttribute";
            }
            for (int port : portList) {
                params.put("ListenerPort", port);
                try {
                    AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, methodName, params);
                    JSONObject response = method.get().asJson();
                    listeners.add(toListener(response, protocol));
                } catch (CloudException e) {
                    stdLogger.warn("Not find listener with port " + port + " and protocol " + lbProtocol.name()
                            + " for load balancer with id " + loadBalancerId, e);
                } catch (InternalException e) {
                    stdLogger.warn("Not find listener with port " + port + " and protocol " + lbProtocol.name()
                            + " for load balancer with id " + loadBalancerId, e);
                }
            }
        }
        return listeners;
    }

    private LoadBalancerHealthCheck getLoadBalancerHealthCheckByProtocol (String loadBalancerId, LoadBalancerHealthCheck.HCProtocol protocol)
            throws InternalException, CloudException, JSONException {
        List<LoadBalancerHealthCheck> healthChecks = getLoadBalancerHealthChecks(loadBalancerId);
        for (LoadBalancerHealthCheck healthCheck : healthChecks) {
            if (healthCheck.getProtocol().equals(protocol)) {
                return healthCheck;
            }
        }
        return null;
    }

    private LoadBalancer getLoadBalancerByName (String name) throws JSONException, InternalException, CloudException {
        for(LoadBalancer loadBalancer : listSimpleLoadBalancers()) {
            if (loadBalancer.getName().equals(name)) {
                return getLoadBalancer(loadBalancer.getProviderLoadBalancerId());
            }
        }
        return null;
    }

    private LoadBalancer toLoadBalancer (JSONObject response) throws JSONException, CloudException, InternalException {
        //TODO check status mapping
        LoadBalancerState status = LoadBalancerState.PENDING;
        if (!AliyunNetworkCommon.isEmpty(response.getString("LoadBalancerStatus"))) {
            if (response.getString("LoadBalancerStatus").toUpperCase().equals(AliyunNetworkCommon.AliyunLbState.ACTIVE.name().toUpperCase())) {
                status = LoadBalancerState.ACTIVE;
            }
        }
        LbType lbType = LbType.EXTERNAL;
        if (!AliyunNetworkCommon.isEmpty(response.getString("AddressType"))
                && response.getString("AddressType").equals(AliyunNetworkCommon.LoadBalancerAddressType.INTRANET.name().toLowerCase())) {
            lbType = LbType.INTERNAL;
        }
        List<Integer> publicPorts = new ArrayList<Integer>();
        for (int i = 0; i < response.getJSONObject("ListenerPorts").getJSONArray("ListenerPort").length(); i++) {
            publicPorts.add(response.getJSONObject("ListenerPorts").getJSONArray("ListenerPort").getInt(i));
        }
        int[] ports = new int[publicPorts.size()];
        for (int i = 0; i < publicPorts.size(); i++) {
            ports[i] = publicPorts.get(i);
        }
        LoadBalancer loadBalancer = LoadBalancer.getInstance(getContext().getAccountNumber(), getContext().getRegionId(), response.getString("LoadBalancerId"),
                status, response.getString("LoadBalancerName"), null, lbType, getAddressType(), response.getString("Address"), ports);
        //Listeners
        List<LbListener> listeners = getLbListeners(loadBalancer.getProviderLoadBalancerId(), 0, null);
        loadBalancer.withListeners(listeners.toArray(new LbListener[listeners.size()]));
        return loadBalancer;
    }

    private LbListener toListener(JSONObject response, LbProtocol lbProtocol)
            throws JSONException, InternalException, CloudException {

        //ports
        int publicPort = response.getInt("ListenerPort");
        int privatePort = response.getInt("BackendServerPort");

        //algorithm
        LbAlgorithm algorithm = LbAlgorithm.ROUND_ROBIN;
        if (!AliyunNetworkCommon.isEmpty(response.getString("Scheduler"))
                && response.getString("Scheduler").toUpperCase().equals(AliyunNetworkCommon.AliyunLbScheduleAlgorithm.WLC.name().toUpperCase())) {
            algorithm = LbAlgorithm.LEAST_CONN;
        }

        //persistence/sticky and cookie
        LbPersistence persistence = LbPersistence.NONE;
        if (!AliyunNetworkCommon.isEmpty(response.getString("StickySession"))
                && response.getString("StickySession").toUpperCase().equals(AliyunNetworkCommon.AliyunLbSwitcher.ON.name().toUpperCase())) {
            persistence = LbPersistence.COOKIE;
        }

        //certificate
        String certificateName = null;
        if (!AliyunNetworkCommon.isEmpty(response.getString("ServerCertificateId"))) {
            for (SSLCertificate certificate : listSSLCertificates()) {
                if (certificate.getProviderCertificateId().equals(response.getString("ServerCertificateId"))) {
                    certificateName = certificate.getCertificateName();
                }
            }
        }
        return new LbListener(algorithm, persistence, lbProtocol, publicPort, privatePort, certificateName);
    }

    private SSLCertificate toSSLCertificate (JSONObject response) throws JSONException, InternalException, CloudException {
        return SSLCertificate.getInstance(response.getString("ServerCertificateName"), response.getString("ServerCertificateId"), null,
                response.getString("ServerCertificate"), null, null);
    }

    private LoadBalancerHealthCheck toHealthCheck (JSONObject response,
                                                   LoadBalancerHealthCheck.HCProtocol protocol, String loadBalancerId, int listenerPort)
            throws JSONException, InternalException, CloudException {
        if (!AliyunNetworkCommon.isEmpty(response.getString("HealthCheck"))
                && response.getString("HealthCheck").toUpperCase().equals(AliyunNetworkCommon.AliyunLbSwitcher.OFF.name().toUpperCase())) {
            return null;
        } else {
            String path = null;
            if (protocol.equals(LoadBalancerHealthCheck.HCProtocol.HTTP) || protocol.equals(LoadBalancerHealthCheck.HCProtocol.HTTPS)) {
                path = response.getString("HealthCheckURI");
            }
            String healthCheckId = generateLoadBalancerHealthCheckId(loadBalancerId, listenerPort, protocol,
                    response.getInt("HealthCheckConnectPort"), null, response.getInt("HealthCheckInterval"),
                    response.getInt("HealthCheckTimeout"), response.getInt("HealthyThreshold"), response.getInt("UnhealthyThreshold"));
            LoadBalancerHealthCheck healthCheck = LoadBalancerHealthCheck.getInstance(healthCheckId, null, null, null,
                    protocol, response.getInt("HealthCheckConnectPort"), path, response.getInt("HealthCheckInterval"),
                    response.getInt("HealthCheckTimeout"), response.getInt("HealthyThreshold"), response.getInt("UnhealthyThreshold"));
            if (protocol.equals(LoadBalancerHealthCheck.HCProtocol.HTTP) || protocol.equals(LoadBalancerHealthCheck.HCProtocol.HTTPS)) {
                healthCheck.setPath(response.getString("HealthCheckURI"));
            }
            return healthCheck;
        }
    }

    private LoadBalancerHealthCheck generateHealthCheck (String loadBalancerId, int listenerPort, LoadBalancerHealthCheck.HCProtocol protocol,
                                                         int port, String path, int interval, int timeout, int healthyCount, int unhealthyCount) {
        String healthCheckId = generateLoadBalancerHealthCheckId(loadBalancerId, listenerPort, protocol, port, path, interval, timeout, healthyCount, unhealthyCount);
        return LoadBalancerHealthCheck.getInstance(healthCheckId, null, null, null, protocol, port, path, interval, timeout, healthyCount, unhealthyCount);
    }

    //TODO see it can be removed
    private String generateLoadBalancerHealthCheckId (String loadBalancerId, int listenerPort, LoadBalancerHealthCheck.HCProtocol protocol, int port,
                                                      String path, int interval, int timeout, int healthyCount, int unhealthyCount) {
        return loadBalancerId + ":" + listenerPort + ":" + protocol.name().toUpperCase() + ":" +
                port + ":" + path + ":" + interval + ":" + timeout + ":" + healthyCount + ":" + unhealthyCount;
    }

}
