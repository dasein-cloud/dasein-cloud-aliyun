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

import org.dasein.cloud.*;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.network.*;
import org.dasein.cloud.util.NamingConstraints;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

/**
 * Created by Jane Wang on 5/19/2015.
 *
 * @author Jane Wang
 * @since 2015.05.01
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
        return 10;
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
        return VisibleScope.ACCOUNT_REGION;
    }

    @Override
    public boolean healthCheckRequiresLoadBalancer() throws CloudException, InternalException {
        return true;
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
        return Requirement.OPTIONAL;
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
        //SLB support SUBNET type for RAW_TCP, it is enabled by default and cannot disable
        return Collections.unmodifiableList(Arrays.asList(LbPersistence.NONE, LbPersistence.COOKIE));
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

	@Override
	public boolean healthCheckRequiresListener() throws CloudException,
			InternalException {
		return true;
	}

	@Override
	public boolean supportsSslCertificateStore() throws CloudException,
			InternalException {
		return true;
	}

	@Override
	public NamingConstraints getLoadBalancerNamingConstraints()
			throws CloudException, InternalException {
		return NamingConstraints.getAlphaNumeric(1, 80).withRegularExpression(
                "^[a-zA-Z0-9_/\\-\\.]{1,80}").withNoSpaces();
	}

}
