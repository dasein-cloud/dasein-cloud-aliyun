package org.dasein.cloud.aliyun.network;

import org.dasein.cloud.*;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.network.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
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
        return LoadBalancerAddressType.IP;
    }

    @Override
    public int getMaxPublicPorts() throws CloudException, InternalException {
        return Integer.MAX_VALUE;
    }

    @Nonnull
    @Override
    public String getProviderTermForLoadBalancer(@Nonnull Locale locale)
    {
        return "SLB";
    }

    @Nullable
    @Override
    public VisibleScope getLoadBalancerVisibleScope() {
        return VisibleScope.ACCOUNT_GLOBAL; //TODO: not sure GLOBAL:classic, DATACENTER:vswitch
    }

    @Override
    public boolean healthCheckRequiresLoadBalancer() throws CloudException, InternalException {
        return false;
    }

    @Override
    public Requirement healthCheckRequiresName() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Nonnull
    @Override
    public Requirement identifyEndpointsOnCreateRequirement() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Nonnull
    @Override
    public Requirement identifyListenersOnCreateRequirement() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Nonnull
    @Override
    public Requirement identifyVlanOnCreateRequirement() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Nonnull
    @Override
    public Requirement identifyHealthCheckOnCreateRequirement() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Override
    public boolean isAddressAssignedByProvider() throws CloudException, InternalException {
        return true;
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
        return Collections.unmodifiableList(Arrays.asList(LbAlgorithm.ROUND_ROBIN, LbAlgorithm.LEAST_CONN));
    }

    @Nonnull
    @Override
    public Iterable<LbEndpointType> listSupportedEndpointTypes() throws CloudException, InternalException {
        return Collections.singletonList(LbEndpointType.VM);
    }

    @Nonnull
    @Override
    public Iterable<IPVersion> listSupportedIPVersions() throws CloudException, InternalException {
        return Collections.singletonList(IPVersion.IPV4);
    }

    @Nonnull
    @Override
    public Iterable<LbPersistence> listSupportedPersistenceOptions() throws CloudException, InternalException {
        return Collections.unmodifiableList(Arrays.asList(LbPersistence.NONE, LbPersistence.COOKIE, LbPersistence.SUBNET));
    }

    @Nonnull
    @Override
    public Iterable<LbProtocol> listSupportedProtocols() throws CloudException, InternalException {
        return Collections.unmodifiableList(Arrays.asList(LbProtocol.HTTP, LbProtocol.HTTPS, LbProtocol.RAW_TCP));
    }

    @Override
    public boolean supportsAddingEndpoints() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean supportsMonitoring() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean supportsMultipleTrafficTypes() throws CloudException, InternalException {
        return false;
    }
}
