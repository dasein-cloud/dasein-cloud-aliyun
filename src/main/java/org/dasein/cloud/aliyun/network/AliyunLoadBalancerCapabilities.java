package org.dasein.cloud.aliyun.network;

import org.dasein.cloud.*;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.network.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Created by jwang7 on 5/19/2015.
 */
public class AliyunLoadBalancerCapabilities extends AbstractCapabilities<Aliyun> implements LoadBalancerCapabilities {

    public AliyunLoadBalancerCapabilities(@Nonnull Aliyun provider) {
        super(provider);
    }

    @Nonnull
    @Override
    public LoadBalancerAddressType getAddressType() throws CloudException, InternalException {
        return null;
    }

    @Override
    public int getMaxPublicPorts() throws CloudException, InternalException {
        return 0;
    }

    @Nonnull
    @Override
    public String getProviderTermForLoadBalancer(@Nonnull Locale locale) {
        return "Server Load Balancer (SLB)";
    }

    @Nullable
    @Override
    public VisibleScope getLoadBalancerVisibleScope() {
        return null; //TODO unknown
    }

    @Override
    public boolean healthCheckRequiresLoadBalancer() throws CloudException, InternalException {
        return false;
    }

    @Override
    public Requirement healthCheckRequiresName() throws CloudException, InternalException {
        return null;
    }

    @Nonnull
    @Override
    public Requirement identifyEndpointsOnCreateRequirement() throws CloudException, InternalException {
        return null;
    }

    @Nonnull
    @Override
    public Requirement identifyListenersOnCreateRequirement() throws CloudException, InternalException {
        return null;
    }

    @Nonnull
    @Override
    public Requirement identifyVlanOnCreateRequirement() throws CloudException, InternalException {
        return null;
    }

    @Nonnull
    @Override
    public Requirement identifyHealthCheckOnCreateRequirement() throws CloudException, InternalException {
        return null;
    }

    @Override
    public boolean isAddressAssignedByProvider() throws CloudException, InternalException {
        return false;
    }

    /**
     * Aliyun doesn't support data center for load balancer
     * @return
     * @throws CloudException
     * @throws InternalException
     */
    @Override
    public boolean isDataCenterLimited() throws CloudException, InternalException {
        return false;
    }

    @Nonnull
    @Override
    public Iterable<LbAlgorithm> listSupportedAlgorithms() throws CloudException, InternalException {
        return null;
    }

    @Nonnull
    @Override
    public Iterable<LbEndpointType> listSupportedEndpointTypes() throws CloudException, InternalException {
        return null;
    }

    @Nonnull
    @Override
    public Iterable<IPVersion> listSupportedIPVersions() throws CloudException, InternalException {
        return null;
    }

    @Nonnull
    @Override
    public Iterable<LbPersistence> listSupportedPersistenceOptions() throws CloudException, InternalException {
        return null;
    }

    @Nonnull
    @Override
    public Iterable<LbProtocol> listSupportedProtocols() throws CloudException, InternalException {
        return null;
    }

    @Override
    public boolean supportsAddingEndpoints() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean supportsMonitoring() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean supportsMultipleTrafficTypes() throws CloudException, InternalException {
        return false;
    }
}
