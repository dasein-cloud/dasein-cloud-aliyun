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
     *       Also since the healthcheck is part of listener, it will not set on during add listeners, and can be open when invoke create healthcheck
     *       (healthcheck will be set to on and params will be filled).
     * @param toLoadBalancerId
     * @param listeners
     * @throws CloudException
     * @throws InternalException
     */
    @Override
    public void addListeners(@Nonnull String toLoadBalancerId, @Nullable LbListener[] listeners) throws CloudException, InternalException {

        for (int i = 0; i < listeners.length; i++) {

            LbListener listener = listeners[i];

            //validate network protocol
            if (listener.getNetworkProtocol() == null || !listener.getNetworkProtocol().equals(LbProtocol.HTTP) ||
                    !listener.getNetworkProtocol().equals(LbProtocol.HTTPS) || !listener.getNetworkProtocol().equals(LbProtocol.RAW_TCP)) {
                throw new OperationNotSupportedException("Aliyun supports HTTP, HTTPS and RAW TCP as the load balancer algorithms only!");
            }

            Map<String, Object> params = new HashMap<String, Object>();
            params.put("LoadBalancerId", toLoadBalancerId);
            params.put("ListenerPort", listener.getPublicPort());
            params.put("BackendServerPort", listener.getPrivatePort());

            if (listener.getAlgorithm() != null) {
                if (listener.getAlgorithm().equals(LbAlgorithm.ROUND_ROBIN)) {
                    params.put("Scheduler", AliyunNetworkCommon.AliyunLbScheduleAlgorithm.WRR.name().toLowerCase());
                } else if (listener.getAlgorithm().equals(LbAlgorithm.LEAST_CONN)) {
                    params.put("Scheduler", AliyunNetworkCommon.AliyunLbScheduleAlgorithm.WLC.name().toLowerCase());
                } else {
                    throw new OperationNotSupportedException("Aliyun supports weighted round robin and weighted least connection scheduler algorithms only!");
                }
            }

            if (listener.getNetworkProtocol().equals(LbProtocol.RAW_TCP)) {
                //CreateLoadBalancerTCPListener
                AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "CreateLoadBalancerTCPListener", params);
                method.post();
                continue;
            }

            //TODO HealthCheck, common to http and https, turn off the health check will set when invoke create health check
            params.put("HealthCheck", AliyunNetworkCommon.AliyunLbSwitcher.OFF.name().toLowerCase());

            //persistence and cookie
            if (listener.getPersistence() != null && listener.getPersistence().equals(LbPersistence.COOKIE)) {
                params.put("StickySession", AliyunNetworkCommon.AliyunLbSwitcher.ON.name().toLowerCase());
                params.put("Cookie", listener.getCookie());
                params.put("StickySessionType", AliyunNetworkCommon.AliyunLbPersistenceType.INSERT.name().toLowerCase()); //TODO check
                params.put("CookieTimeout", AliyunNetworkCommon.DefaultPersistenceTimeout);
            } else if (listener.getPersistence() == null) {
                params.put("StickySession", AliyunNetworkCommon.AliyunLbSwitcher.OFF.name().toLowerCase());
            } else {
                throw new OperationNotSupportedException("Aliyun supports Cookie as the load balancer persistence type only!");
            }

            if (listener.getNetworkProtocol().equals(LbProtocol.HTTP)) {
                //CreateLoadBalancerHTTPListener
                AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "CreateLoadBalancerHTTPListener", params);
                method.post();
            } else if (listener.getNetworkProtocol().equals(LbProtocol.HTTPS)) {
                //CreateLoadBalancerHTTPSListener
                params.put("ServerCertificateId", getSSLCertificate(listener.getSslCertificateName()).getProviderCertificateId());
                AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "CreateLoadBalancerHTTPSListener", params);
                method.post();
            }
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
            method.post();
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
            method.post();
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
                throw new OperationNotSupportedException("Aliyun supports add load balancer to only one subnet!");
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
        //TODO check
        params.put("ServerCertificate", new Date().getTime());
        if (!AliyunNetworkCommon.isEmpty(options.getCertificateName())) {
            params.put("ServerCertificateName", options.getCertificateName());
        }
        params.put("PrivateKey", options.getPrivateKey());
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "UploadServerCertificate", params);
        try {
            JSONObject response = method.post().asJson();
            return SSLCertificate.getInstance(response.getString("ServerCertificateName"),
                    response.getString("ServerCertificateId"), null, null, null, null);
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
        return toLoadBalancer(response);
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
            for (int i = 0; i < response.getJSONObject("ServerCertificates").getJSONArray("ServerCertificate").length(); i++) {
                JSONObject sslCertificate = response.getJSONObject("ServerCertificates").getJSONArray("ServerCertificate").getJSONObject(i);
                if (sslCertificate.getString("ServerCertificateName").trim().equals(certificateName.trim())) {
                    return SSLCertificate.getInstance(sslCertificate.getString("ServerCertificateName"),
                            sslCertificate.getString("ServerCertificateId"), null, null, null, null);
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
        List<LoadBalancer> loadBalancers = listSimpleLoadBalancers();
        for (LoadBalancer loadBalancer : loadBalancers) {
            //retrieve complete field for load balancer
            loadBalancers.add(getLoadBalancer(loadBalancer.getProviderLoadBalancerId()));
        }
        return loadBalancers;
    }

    @Nonnull
    @Override
    public Iterable<ResourceStatus> listLoadBalancerStatus() throws CloudException, InternalException {
        List<ResourceStatus> resourceStatuses = new ArrayList<ResourceStatus>();
        List<LoadBalancer> loadBalancers = listSimpleLoadBalancers();
        for (LoadBalancer loadBalancer : loadBalancers) {
            resourceStatuses.add(new ResourceStatus(loadBalancer.getProviderLoadBalancerId(), loadBalancer.getCurrentState()));
        }
        return resourceStatuses;
    }

    @Nonnull
    @Override
    public Iterable<LoadBalancerEndpoint> listEndpoints(@Nonnull String forLoadBalancerId) throws CloudException, InternalException {
        return super.listEndpoints(forLoadBalancerId);
    }

    @Nonnull
    @Override
    public Iterable<LoadBalancerEndpoint> listEndpoints(@Nonnull String forLoadBalancerId, @Nonnull LbEndpointType type, @Nonnull String... endpoints) throws CloudException, InternalException {
        return super.listEndpoints(forLoadBalancerId, type, endpoints);
    }

    @Nonnull
    @Override
    public Iterable<SSLCertificate> listSSLCertificates() throws CloudException, InternalException {
        return super.listSSLCertificates();
    }

    @Override
    public void removeIPEndpoints(@Nonnull String fromLoadBalancerId, @Nonnull String... addresses) throws CloudException, InternalException {
        super.removeIPEndpoints(fromLoadBalancerId, addresses);
    }

    @Override
    public void removeLoadBalancer(@Nonnull String loadBalancerId) throws CloudException, InternalException {
        super.removeLoadBalancer(loadBalancerId);
    }

    @Override
    public void removeSSLCertificate(@Nonnull String certificateName) throws CloudException, InternalException {
        super.removeSSLCertificate(certificateName);
    }

    @Override
    public void removeServers(@Nonnull String fromLoadBalancerId, @Nonnull String... serverIdsToRemove) throws CloudException, InternalException {
        super.removeServers(fromLoadBalancerId, serverIdsToRemove);
    }

    @Override
    public void setSSLCertificate(@Nonnull SetLoadBalancerSSLCertificateOptions options) throws CloudException, InternalException {
        super.setSSLCertificate(options);
    }

    @Override
    public LoadBalancerHealthCheck createLoadBalancerHealthCheck(@Nullable String name, @Nullable String description,
                                                                 @Nullable String host, @Nullable LoadBalancerHealthCheck.HCProtocol protocol,
                                                                 int port, @Nullable String path, int interval, int timeout, int healthyCount,
                                                                 int unhealthyCount) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Aliyun doesn't support create health check without associated load balancer!");
    }

    /**
     * Change load balancer associated listeners healthcheck settings. add this healthcheck to all listeners.
     * @param options health check creation options
     * @return
     * @throws CloudException
     * @throws InternalException
     */
    @Override
    public LoadBalancerHealthCheck createLoadBalancerHealthCheck(@Nonnull HealthCheckOptions options) throws CloudException, InternalException {

        //retrieve load balancer by id
        LoadBalancer loadBalancer = getLoadBalancer(options.getProviderLoadBalancerId());

        List<LbListener> listeners = getLbListenersByLoadBalancer(loadBalancer);
        for (LbListener listener : listeners) {

            Map<String, Object> params = new HashMap<String, Object>();
            params.put("HealthCheckConnectPort", options.getPort());
            params.put("HealthyThreshold", options.getHealthyCount());
            params.put("UnhealthyThreshold", options.getUnhealthyCount());
            params.put("HealthCheckConnectTimeout", options.getTimeout());
            params.put("HealthCheckInterval", options.getInterval());

            String methodName = null;
            if (listener.getNetworkProtocol().equals(LbProtocol.HTTP)) {
                methodName = "SetLoadBalancerHTTPListenerAttribute";
            } else if (listener.getNetworkProtocol().equals(LbProtocol.HTTPS)) {
                methodName = "SetLoadBalancerHTTPSListenerAttribute";
//                params.put("ServerCertificateId", ""); TODO check it
            } else if (listener.getNetworkProtocol().equals(LbProtocol.RAW_TCP)) {
                methodName = "SetLoadBalancerTCPListenerAttribute";
//                params.put("PersistenceTimeout", "");
                AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, methodName, params);
                method.post();
                continue;
            } else {
                throw new OperationNotSupportedException("Aliyun supports HTTP, HTTPS and RAW TCP as the load balancer protocol only!");
            }

            params.put("HealthCheck", AliyunNetworkCommon.AliyunLbSwitcher.ON.name().toLowerCase());
            params.put("HealthCheckDomain", "$_ip"); //TODO
            params.put("HealthCheckURI", options.getPath());
//            params.put("HealthCheckHttpCode", "")
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, methodName, params);
            method.post();
        }
        return LoadBalancerHealthCheck.getInstance(options.getProtocol(), options.getPort(), options.getPath(), options.getInterval(),
                options.getTimeout(), options.getHealthyCount(), options.getUnhealthyCount());
    }

    @Override
    public Iterable<LoadBalancerHealthCheck> listLBHealthChecks(@Nullable HealthCheckFilterOptions opts) throws CloudException, InternalException {
        return super.listLBHealthChecks(opts);
    }

    @Override
    public LoadBalancerHealthCheck getLoadBalancerHealthCheck(@Nonnull String providerLBHealthCheckId, @Nullable String providerLoadBalancerId) throws CloudException, InternalException {
        return super.getLoadBalancerHealthCheck(providerLBHealthCheckId, providerLoadBalancerId);
    }

    @Override
    public LoadBalancerHealthCheck modifyHealthCheck(@Nonnull String providerLBHealthCheckId, @Nonnull HealthCheckOptions options) throws InternalException, CloudException {
        return super.modifyHealthCheck(providerLBHealthCheckId, options);
    }

    @Override
    public void removeLoadBalancerHealthCheck(@Nonnull String providerLoadBalancerId) throws CloudException, InternalException {
        super.removeLoadBalancerHealthCheck(providerLoadBalancerId);
    }

    @Override
    public void modifyLoadBalancerAttributes(@Nonnull String id, @Nonnull LbAttributesOptions options) throws CloudException, InternalException {
        super.modifyLoadBalancerAttributes(id, options);
    }

    @Override
    public LbAttributesOptions getLoadBalancerAttributes(@Nonnull String id) throws CloudException, InternalException {
        return super.getLoadBalancerAttributes(id);
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
    private List<LoadBalancer> listSimpleLoadBalancers() throws CloudException, InternalException {
        List<LoadBalancer> loadBalancers = new ArrayList<LoadBalancer>();
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "DescribeLoadBalancers", params);
        JSONObject response = method.get().asJson();
        try {
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
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during list load balancers!", e);
            throw new InternalException(e);
        }
    }

    private LbListener getLbListenerByLbAlgorithm (String loadBalancerId, int listenerPort, LbProtocol protocol) throws CloudException, InternalException {

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
            throw new OperationNotSupportedException("Aliyun supports HTTP, HTTPS, RAW TCP as the load balancer protocol only!");
        }

        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, methodName, params);
        JSONObject response = method.get().asJson();
        try {
            if (response != null && !AliyunNetworkCommon.isEmpty(response.getString("ListenerPort"))) {
                int publicPort = response.getInt("ListenerPort");
                int privatePort = response.getInt("BackendServerPort");
                String status = response.getString("Status"); //TODO check status if it affects the result
                LbAlgorithm algorithm = LbAlgorithm.ROUND_ROBIN;
                if (!AliyunNetworkCommon.isEmpty(response.getString("Scheduler")) && response.getString("Scheduler").toUpperCase().equals(
                        AliyunNetworkCommon.AliyunLbScheduleAlgorithm.WLC.name().toUpperCase())) {
                    algorithm = LbAlgorithm.LEAST_CONN;
                }
                if (!AliyunNetworkCommon.isEmpty(response.getString("StickySession")) && response.getString("StickySession").toUpperCase().equals(AliyunNetworkCommon.AliyunLbSwitcher.ON.name().toUpperCase())) {
                    return LbListener.getInstance(algorithm, LbPersistence.COOKIE, protocol, publicPort, privatePort);
                } else {
                    //TODO check RAW_TCP
                    return LbListener.getInstance(algorithm, LbPersistence.NONE, protocol, publicPort, privatePort);
                }
            }
            return null;
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during get listener for load balancer", e);
            throw new InternalException(e);
        }
    }

    private List<LbListener> getLbListenersByLoadBalancer(LoadBalancer loadBalancer) throws CloudException, InternalException {
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
            if (listener != null) {
                listeners.add(listener);
            }
        }
        return listeners;
    }

    private LoadBalancer toLoadBalancer (JSONObject response) throws  CloudException, InternalException {
        try {
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
            //TODO check addressType, setServerIds has been deprecated
            LoadBalancer loadBalancer = LoadBalancer.getInstance(getContext().getAccountNumber(), getContext().getRegionId(), response.getString("LoadBalancerId"),
                    status, response.getString("LoadBalancerName"), null, lbType, getAddressType(), response.getString("Address"), ports);
            //Listeners
            List<LbListener> listeners = getLbListenersByLoadBalancer(loadBalancer);
            loadBalancer.withListeners(listeners.toArray(new LbListener[listeners.size()]));
            return loadBalancer;
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during parsing response to load balancer instance!", e);
            throw new InternalException(e);
        }
    }
}
