package org.dasein.cloud.aliyun.network;

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.aliyun.AliyunMethod;
import org.dasein.cloud.network.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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

            //TODO HealthCheck, common for all protocol
            params.put("HealthCheckConnectPort", "");
            params.put("HealthyThreshold", "");
            params.put("UnhealthyThreshold", "");
            params.put("HealthCheckTimeout", "");
            params.put("HealthCheckInterval", "");

            if (listener.getNetworkProtocol().equals(LbProtocol.RAW_TCP)) {
                //CreateLoadBalancerTCPListener
                params.put("PersistenceTimeout", AliyunNetworkCommon.DefaultPersistenceTimeout);
                AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "CreateLoadBalancerTCPListener", params);
                method.post();
                continue;
            }

            //TODO HealthCheck, common to http and https
            params.put("HealthCheck", AliyunNetworkCommon.AliyunLbSwitcher.ON.name().toLowerCase());
            params.put("HealthCheckDomain", "$_ip");
            params.put("HealthCheckURI", "");
            params.put("HealthCheckHttpCode", "");

            //persistence and cookie
            if (listener.getPersistence() != null && listener.getPersistence().equals(LbPersistence.COOKIE)) {
                //TODO
                params.put("StickySession", AliyunNetworkCommon.AliyunLbSwitcher.ON.name().toLowerCase());
                params.put("Cookie", listener.getCookie());
                params.put("StickySessionType", AliyunNetworkCommon.AliyunLbPersistenceType.INSERT.name().toLowerCase());
                params.put("CookieTimeout", AliyunNetworkCommon.DefaultPersistenceTimeout);
            } else {
                throw new OperationNotSupportedException("Aliyun supports Cookie as the load balancer persistence type only!");
            }

            if (listener.getNetworkProtocol().equals(LbProtocol.HTTP)) {
                //CreateLoadBalancerHTTPListener
                AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.SLB, "CreateLoadBalancerHTTPListener", params);
                method.post();
            } else if (listener.getNetworkProtocol().equals(LbProtocol.HTTPS)) {
                //CreateLoadBalancerHTTPSListener
                params.put("ServerCertificateId", getSSLCertificateIdByName(listener.getSslCertificateName()));
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
        return super.createSSLCertificate(options);
    }

    @Nonnull
    @Override
    public LoadBalancerAddressType getAddressType() throws CloudException, InternalException {
        return super.getAddressType();
    }

    @Override
    public LoadBalancer getLoadBalancer(@Nonnull String loadBalancerId) throws CloudException, InternalException {
        return super.getLoadBalancer(loadBalancerId);
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        return true;
    }

    @Override
    public int getMaxPublicPorts() throws CloudException, InternalException {
        return super.getMaxPublicPorts();
    }

    @Nullable
    @Override
    public SSLCertificate getSSLCertificate(@Nonnull String certificateName) throws CloudException, InternalException {
        return super.getSSLCertificate(certificateName);
    }

    @Nonnull
    @Override
    public Iterable<LoadBalancer> listLoadBalancers() throws CloudException, InternalException {
        return super.listLoadBalancers();
    }

    @Nonnull
    @Override
    public Iterable<ResourceStatus> listLoadBalancerStatus() throws CloudException, InternalException {
        return super.listLoadBalancerStatus();
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
    public void removeDataCenters(@Nonnull String fromLoadBalancerId, @Nonnull String... dataCenterIdsToRemove) throws CloudException, InternalException {
        super.removeDataCenters(fromLoadBalancerId, dataCenterIdsToRemove);
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
    public LoadBalancerHealthCheck createLoadBalancerHealthCheck(@Nullable String name, @Nullable String description, @Nullable String host, @Nullable LoadBalancerHealthCheck.HCProtocol protocol, int port, @Nullable String path, int interval, int timeout, int healthyCount, int unhealthyCount) throws CloudException, InternalException {
        return super.createLoadBalancerHealthCheck(name, description, host, protocol, port, path, interval, timeout, healthyCount, unhealthyCount);
    }

    @Override
    public LoadBalancerHealthCheck createLoadBalancerHealthCheck(@Nonnull HealthCheckOptions options) throws CloudException, InternalException {
        return super.createLoadBalancerHealthCheck(options);
    }

    @Override
    public void attachHealthCheckToLoadBalancer(@Nonnull String providerLoadBalancerId, @Nonnull String providerLBHealthCheckId) throws CloudException, InternalException {
        super.attachHealthCheckToLoadBalancer(providerLoadBalancerId, providerLBHealthCheckId);
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
    public void detatchHealthCheck(String loadBalancerId, String heathcheckId) throws CloudException, InternalException {
        super.detatchHealthCheck(loadBalancerId, heathcheckId);
    }

    @Override
    public void setFirewalls(@Nonnull String providerLoadBalancerId, @Nonnull String... firewallIds) throws CloudException, InternalException {
        super.setFirewalls(providerLoadBalancerId, firewallIds);
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
        super.attachLoadBalancerToSubnets(toLoadBalancerId, subnetIdsToAdd);
    }

    @Override
    public void detachLoadBalancerFromSubnets(@Nonnull String fromLoadBalancerId, @Nonnull String... subnetIdsToDelete) throws CloudException, InternalException {
        super.detachLoadBalancerFromSubnets(fromLoadBalancerId, subnetIdsToDelete);
    }

    private String getSSLCertificateIdByName (String name) {
        return null; //TODO
    }
}
