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
     * Note: Aliyun create health check during the creation of listners, however Dasein create health check and then create listener.
     * Solution: 1. If the load balancer doesn't have health check: turn off the health check for the listener.
     *           2. If the load balancer already has health check: get the health check and set it as the health check of this new listener.
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

            LoadBalancerHealthCheck healthCheck = null;
            try {
                healthCheck = getLoadBalancerHealthCheckByProtocol(toLoadBalancerId, protocol);
            } catch (JSONException e) {
                stdLogger.error("An exception occurs during get health check for load balancer " + toLoadBalancerId + " by protocol " + protocol.name());
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
                if (listener.getPersistence() != null && listener.getPersistence().equals(LbPersistence.COOKIE)) {
                    params.put("StickySession", AliyunNetworkCommon.AliyunLbSwitcher.ON.name().toLowerCase());
                    params.put("Cookie", listener.getCookie());
                    params.put("StickySessionType", AliyunNetworkCommon.AliyunLbPersistenceType.INSERT.name().toLowerCase()); //TODO check
                    params.put("CookieTimeout", AliyunNetworkCommon.DefaultPersistenceTimeout);
                } else if (listener.getPersistence() == null) {
                    params.put("StickySession", AliyunNetworkCommon.AliyunLbSwitcher.OFF.name().toLowerCase());
                } else {
                    throw new InternalException("Aliyun supports Cookie as the load balancer persistence type only!");
                }
            } else {
                params.put("PersistenceTimeout", AliyunNetworkCommon.DefaultPersistenceTimeout);
            }

            //health check
            if (healthCheck != null) {

                params.put("HealthCheckConnectPort", healthCheck.getPort());
                params.put("HealthyThreshold", healthCheck.getHealthyCount());
                params.put("UnhealthyThreshold", healthCheck.getUnhealthyCount());
                params.put("HealthCheckConnectTimeout", healthCheck.getTimeout());
                params.put("HealthCheckInterval", healthCheck.getInterval());

                if (listener.getNetworkProtocol().equals(LbProtocol.HTTP) || listener.getNetworkProtocol().equals(LbProtocol.HTTPS)) {
                    params.put("HealthCheck", AliyunNetworkCommon.AliyunLbSwitcher.ON.name().toLowerCase());
                    params.put("HealthCheckDomain", "$_ip"); //TODO check
                    params.put("HealthCheckURI", healthCheck.getPath());
                    params.put("HealthCheckHttpCode", "http_2xx"); //TODO check, default value

                }
            } else {
                if (listener.getNetworkProtocol().equals(LbProtocol.HTTP) || listener.getNetworkProtocol().equals(LbProtocol.HTTPS)) {
                    params.put("HealthCheck", AliyunNetworkCommon.AliyunLbSwitcher.OFF.name().toLowerCase());
                }
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
        }
    }

    @Override
    public void removeListeners(@Nonnull String toLoadBalancerId, @Nullable LbListener[] listeners) throws CloudException, InternalException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("LoadBalancerId", toLoadBalancerId);
        for (int i = 0; i < listeners.length; i++) {
            LbListener listener = listeners[i];
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
                jsonObject.put("Weight", AliyunNetworkCommon.DefaultWeight);
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
        if (options.getType() != null) {
            params.put("AddressType", options.getType().name().toLowerCase());
        }
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "CreateLoadBalancer", params);
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
        //TODO check, set the current timestamp as the ServerCertificate
        long certificateDateTime = new Date().getTime();
        params.put("ServerCertificate", certificateDateTime);
        if (!AliyunNetworkCommon.isEmpty(options.getCertificateName())) {
            params.put("ServerCertificateName", options.getCertificateName());
        }
        params.put("PrivateKey", options.getPrivateKey());
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "UploadServerCertificate", params);
        try {
            JSONObject response = method.post().asJson();
            return SSLCertificate.getInstance(response.getString("ServerCertificateName"),
                    response.getString("ServerCertificateId"), certificateDateTime, null, null, null);
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
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "DescribeServerCertificates", params);
        JSONObject response = method.get().asJson();
        try {
            JSONArray serverCertificates = response.getJSONObject("ServerCertificates").getJSONArray("ServerCertificate");
            for (int i = 0; i < serverCertificates.length(); i++) {
                JSONObject sslCertificate = serverCertificates.getJSONObject(i);
                if (sslCertificate.getString("ServerCertificateName").trim().equals(certificateName.trim())) {
                    return toSSLCertificate(sslCertificate);
                }
            }
            return null;
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during get ssl certificate!", e);
            throw new InternalException(e);
        }
    }

    @Nonnull
    @Override
    public Iterable<LoadBalancer> listLoadBalancers() throws CloudException, InternalException {
        try {
            List<LoadBalancer> loadBalancers = listSimpleLoadBalancers();
            for (LoadBalancer loadBalancer : loadBalancers) {
                //retrieve complete field for load balancer
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
            List<LoadBalancerEndpoint> loadBalancerEndpoints = new ArrayList<LoadBalancerEndpoint>();
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("LoadBalancerId", forLoadBalancerId);
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "DescribeLoadBalancerAttribute", params);
            JSONObject response = method.get().asJson();

            JSONArray listenerPorts = response.getJSONObject("ListenerPorts").getJSONArray("ListenerPorts");
            //search out state by listener port and retrieve associated backend servers
            for (int i = 0; i < listenerPorts.length(); i++) {
                params = new HashMap<String, Object>();
                params.put("LoadBalancerId", forLoadBalancerId);
                params.put("ListenerPort", listenerPorts.getInt(i));
                method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "DescribeHealthStatus", params);
                response = method.get().asJson();

                JSONArray backendServers = response.getJSONObject("BackendServers").getJSONArray("BackendServer");
                for (int j = 0; j < backendServers.length(); j++) {
                    JSONObject backendServer = backendServers.getJSONObject(j);
                    LbEndpointState state = LbEndpointState.INACTIVE;
                    if (backendServer.getString("ServerHealthStatus").toUpperCase().equals(
                            AliyunNetworkCommon.AliyunLbEndpointState.NORMAL.name().toUpperCase())) {
                        state = LbEndpointState.ACTIVE;
                    }
                    String serverId = response.getString("ServerId");
                    loadBalancerEndpoints.add(LoadBalancerEndpoint.getInstance(LbEndpointType.VM, serverId, state));
                }
            }
            return loadBalancerEndpoints;
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
        Iterable<SSLCertificate> certificates = listSSLCertificates();
        Iterator<SSLCertificate> certificateIterator = certificates.iterator();
        while (certificateIterator.hasNext()) {
            SSLCertificate certificate = certificateIterator.next();
            if (certificate.getCertificateName().equals(certificateName)) {
                Map<String, Object> params = new HashMap<String, Object>();
                params.put("RegionId", getContext().getRegionId());
                params.put("ServerCertificateId", certificate.getProviderCertificateId());
                AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "DeleteServerCertificate", params);
                JSONObject response = method.post().asJson();
                getProvider().validateResponse(response);
                return;
            }
        }
        stdLogger.warn("Not find ssl certificate for removing whose name is " + certificateName);
        throw new InternalException("Not find ssl certificate for removing whose name is " + certificateName);
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
        throw new OperationNotSupportedException("Aliyun doesn't support set SSL certificate!");
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

            List<LbListener> listeners = getLbListenersByLoadBalancerAndProtocol(loadBalancer, protocol);
            for (LbListener listener : listeners) {

                Map<String, Object> params = new HashMap<String, Object>();

                params.put("LoadBalancerId", options.getProviderLoadBalancerId());
                params.put("ListenerPort", listener.getPublicPort());

                params.put("HealthCheckConnectPort", options.getPort());
                params.put("HealthyThreshold", options.getHealthyCount());
                params.put("UnhealthyThreshold", options.getUnhealthyCount());
                params.put("HealthCheckConnectTimeout", options.getTimeout());
                params.put("HealthCheckInterval", options.getInterval());

                if (options.getProtocol().equals(LoadBalancerHealthCheck.HCProtocol.TCP)) {
                    AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, methodName, params);
                    getProvider().validateResponse(method.post().asJson());
                    continue;
                }

                params.put("HealthCheck", AliyunNetworkCommon.AliyunLbSwitcher.ON.name().toLowerCase());
                params.put("HealthCheckDomain", "$_ip"); //TODO check
                params.put("HealthCheckURI", options.getPath());
                params.put("HealthCheckHttpCode", "http_2xx"); //TODO default value

                AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, methodName, params);
                getProvider().validateResponse(method.post().asJson());
            }
            return LoadBalancerHealthCheck.getInstance(options.getProtocol(), options.getPort(), options.getPath(), options.getInterval(),
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

    /**
     * Since all the listeners for one load balancer have the same health check settings,
     * so get Load Balancer Health Check will return the first one found from HTTP, HTTPS, TCP listeners.
     * @param providerLBHealthCheckId no use
     * @param providerLoadBalancerId load balancer id
     * @return health check instance
     * @throws CloudException
     * @throws InternalException
     */
    @Override
    public LoadBalancerHealthCheck getLoadBalancerHealthCheck(@Nonnull String providerLBHealthCheckId, @Nullable String providerLoadBalancerId) throws CloudException, InternalException {
        //TODO check
        throw new OperationNotSupportedException("Aliyun doesn't support retrieve health check by id since there is no id for health check!");
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
            List<LbListener> listeners = getLbListenersByLoadBalancerAndProtocol(loadBalancer, protocol);
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
                healthChecks.add(toHealthCheck(response, hcProtocol));
            }
        }
        return healthChecks;
    }

    @Override
    public LoadBalancerHealthCheck modifyHealthCheck(@Nonnull String providerLBHealthCheckId, @Nonnull HealthCheckOptions options) throws InternalException, CloudException {
        throw new OperationNotSupportedException("Aliyun doesn't support health check id, so modify health check by id and options is invalid!");
    }

    /**
     * Set OFF the health check for http and https health check.
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
                listeners = getLbListenersByLoadBalancerAndProtocol(loadBalancer, protocol);
                for (LbListener listener : listeners) {
                    Map<String, Object> params = new HashMap<String, Object>();
                    params.put("LoadBalancerId", providerLoadBalancerId);
                    params.put("ListenerPort", listener.getPublicPort());
                    params.put("HealthCheckConnectPort", 0);
                    params.put("HealthyThreshold", 0);
                    params.put("UnhealthyThreshold", 0);
                    params.put("HealthCheckConnectTimeout", 0);
                    params.put("HealthCheckInterval", 0);
                    String methodName = "SetLoadBalancerTCPListenerAttribute";
                    if (protocol.equals(LbProtocol.HTTP)) {
                        methodName = "SetLoadBalancerHTTPListenerAttribute";
                        params.put("HealthCheck", AliyunNetworkCommon.AliyunLbSwitcher.OFF.name().toLowerCase());
                    } else if (protocol.equals(LbProtocol.HTTPS)) {
                        methodName = "SetLoadBalancerHTTPSListenerAttribute";
                        params.put("HealthCheck", AliyunNetworkCommon.AliyunLbSwitcher.OFF.name().toLowerCase());
                    }
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

    @Override
    public void modifyLoadBalancerAttributes(@Nonnull String id, @Nonnull LbAttributesOptions options) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Aliyun doesn't support modify load balancer attributes!");
    }

    @Override
    public LbAttributesOptions getLoadBalancerAttributes(@Nonnull String id) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Aliyun doesn't support get load balancer attributes!");
    }

    @Override
    public void attachLoadBalancerToSubnets(@Nonnull String toLoadBalancerId, @Nonnull String... subnetIdsToAdd) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Aliyun doesn't support attach load balancer to subnets operation, " +
                "subnet should be defined during creation of load balancer!");
    }

    @Override
    public void detachLoadBalancerFromSubnets(@Nonnull String fromLoadBalancerId, @Nonnull String... subnetIdsToDelete) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Aliyun doesn't support dettach load balancer to subnets operation, " +
                "subnet should be defined during creation of load balancer and couldn't be dettached!");
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

    private LbListener getLbListenerByLbAlgorithm (String loadBalancerId, int listenerPort, LbProtocol protocol)
            throws JSONException, CloudException, InternalException {

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("LoadBalancerId", loadBalancerId);
        params.put("ListenerPort", listenerPort);

        String methodName = null;
        if (protocol.equals(LbProtocol.HTTP)) {
            methodName = "DescribeLoadBalancerHTTPListenerAttribute";
        } else if (protocol.equals(LbProtocol.HTTPS)) {
            methodName = "DescribeLoadBalancerHTTPSListenerAttribute";
        } else if (protocol.equals(LbProtocol.RAW_TCP)) {
            methodName = "DescribeLoadBalancerTCPListenerAttribute";
        } else {
            throw new InternalException("Aliyun supports HTTP, HTTPS, RAW TCP as the load balancer protocol only!");
        }

        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, methodName, params);
        JSONObject response = method.get().asJson();
        if (response != null && !AliyunNetworkCommon.isEmpty(response.getString("ListenerPort"))) {
            int publicPort = response.getInt("ListenerPort");
            int privatePort = response.getInt("BackendServerPort");
            String status = response.getString("Status"); //TODO check status if it affects the result
            LbAlgorithm algorithm = LbAlgorithm.ROUND_ROBIN;
            if (!AliyunNetworkCommon.isEmpty(response.getString("Scheduler")) && response.getString("Scheduler").toUpperCase().equals(
                    AliyunNetworkCommon.AliyunLbScheduleAlgorithm.WLC.name().toUpperCase())) {
                algorithm = LbAlgorithm.LEAST_CONN;
            }
            if (!AliyunNetworkCommon.isEmpty(response.getString("StickySession"))
                    && response.getString("StickySession").toUpperCase().equals(AliyunNetworkCommon.AliyunLbSwitcher.ON.name().toUpperCase())) {
                return LbListener.getInstance(algorithm, LbPersistence.COOKIE, protocol, publicPort, privatePort);
            } else {
                //TODO check RAW_TCP
                return LbListener.getInstance(algorithm, LbPersistence.NONE, protocol, publicPort, privatePort);
            }
        }
        return null;
    }

    private List<LbListener> getLbListenersByLoadBalancerAndProtocol (LoadBalancer loadBalancer, LbProtocol protocol)
            throws JSONException, CloudException, InternalException {
        List<LbListener> listeners = new ArrayList<LbListener>();
        for (int i = 0; i < loadBalancer.getPublicPorts().length; i++) {
            LbListener listener = getLbListenerByLbAlgorithm(
                    loadBalancer.getProviderLoadBalancerId(), loadBalancer.getPublicPorts()[i], LbProtocol.HTTP);
            if (listener == null) {
                listener = getLbListenerByLbAlgorithm(
                        loadBalancer.getProviderLoadBalancerId(), loadBalancer.getPublicPorts()[i], LbProtocol.HTTPS);
            }
            if (listener == null) {
                listener = getLbListenerByLbAlgorithm(
                        loadBalancer.getProviderLoadBalancerId(), loadBalancer.getPublicPorts()[i], LbProtocol.RAW_TCP);
            }
            if (listener != null && (protocol == null || listener.getNetworkProtocol().equals(protocol))) {
                listeners.add(listener);
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

    private LoadBalancer toLoadBalancer (JSONObject response) throws JSONException, CloudException, InternalException {
        //TODO status of Dasein and Aliyun doesn't match
        LoadBalancerState status = LoadBalancerState.PENDING;
        if (!AliyunNetworkCommon.isEmpty(response.getString("LoadBalancerStatus"))) {
            if (response.getString("LoadBalancerStatus").toUpperCase().equals(LoadBalancerState.ACTIVE.name().toUpperCase())) {
                status = LoadBalancerState.ACTIVE;
            }
        }
        LbType lbType = LbType.EXTERNAL;
        if (!AliyunNetworkCommon.isEmpty(response.getString("AddressType"))) {
            lbType = LbType.valueOf(response.getString("AddressType").toUpperCase());
        }
        List<Integer> publicPorts = new ArrayList<Integer>();
        for (int i = 0; i < response.getJSONObject("ListenerPorts").getJSONArray("ListenerPort").length(); i++) {
            publicPorts.add(response.getJSONObject("ListenerPorts").getJSONArray("ListenerPort").getInt(i));
        }
        int[] ports = new int[publicPorts.size()];
        for (int i = 0; i < publicPorts.size(); i++) {
            ports[i] = publicPorts.get(i);
        }
        //TODO check addressType, setServerIds has been deprecated (API not support)
        LoadBalancer loadBalancer = LoadBalancer.getInstance(getContext().getAccountNumber(), getContext().getRegionId(), response.getString("LoadBalancerId"),
                status, response.getString("LoadBalancerName"), null, lbType, getAddressType(), response.getString("Address"), ports);
        //Listeners, include HTTP, HTTPS, TCP type listeners
        List<LbListener> listeners = new ArrayList<LbListener>();
        List<LbListener> protocolListeners = getLbListenersByLoadBalancerAndProtocol(loadBalancer, LbProtocol.HTTP);
        if (!AliyunNetworkCommon.isEmpty(protocolListeners)) {
            listeners.addAll(protocolListeners);
        }
        protocolListeners = getLbListenersByLoadBalancerAndProtocol(loadBalancer, LbProtocol.HTTPS);
        if (!AliyunNetworkCommon.isEmpty(protocolListeners)) {
            listeners.addAll(protocolListeners);
        }
        protocolListeners = getLbListenersByLoadBalancerAndProtocol(loadBalancer, LbProtocol.RAW_TCP);
        if (!AliyunNetworkCommon.isEmpty(protocolListeners)) {
            listeners.addAll(protocolListeners);
        }
        loadBalancer.withListeners(listeners.toArray(new LbListener[listeners.size()]));
        return loadBalancer;
    }

    private SSLCertificate toSSLCertificate (JSONObject response) throws JSONException, InternalException, CloudException {
        return SSLCertificate.getInstance(response.getString("ServerCertificateName"), response.getString("ServerCertificateId"), null, null, null, null);
    }

    private LoadBalancerHealthCheck toHealthCheck (JSONObject response, LoadBalancerHealthCheck.HCProtocol protocol) throws JSONException, InternalException, CloudException {
        LoadBalancerHealthCheck healthCheck = LoadBalancerHealthCheck.getInstance(protocol, response.getInt("HealthCheckConnectPort"),
                null, response.getInt("HealthCheckInterval"), response.getInt("HealthCheckTimeout"),
                response.getInt("HealthyThreshold"), response.getInt("UnhealthyThreshold"));
        if (protocol.equals(LoadBalancerHealthCheck.HCProtocol.HTTP) || protocol.equals(LoadBalancerHealthCheck.HCProtocol.HTTPS)) {
            healthCheck.setPath(response.getString("HealthCheckURI"));
        }
        return healthCheck;
    }

}
