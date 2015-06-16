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
import org.dasein.cloud.network.IPVersion;
import org.dasein.cloud.network.VLANCapabilities;
import org.dasein.cloud.util.NamingConstraints;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Collections;
import java.util.Locale;


/**
 * Created by Jane Wang on 5/13/2015.
 *
 * @author Jane Wang
 * @since 2015.05.01
 */
public class AliyunVlanCapabilities extends AbstractCapabilities<Aliyun> implements VLANCapabilities {

    public AliyunVlanCapabilities(@Nonnull Aliyun provider) {
        super(provider);
    }

    @Override
    public boolean allowsNewNetworkInterfaceCreation() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean allowsNewVlanCreation() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean allowsNewRoutingTableCreation() throws CloudException, InternalException {
        //will be create one routing table during creating the vlan/vpc, not allow create by the customer.
        return false;
    }

    @Override
    public boolean allowsNewSubnetCreation() throws CloudException, InternalException {
        //will be create one subnet/vswitch during creating the vlan/vpc, not allow create by the customer.
        return true;
    }

    @Override
    public boolean allowsMultipleTrafficTypesOverSubnet() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean allowsMultipleTrafficTypesOverVlan() throws CloudException, InternalException {
        return false;
    }

    @Override
    public int getMaxNetworkInterfaceCount() throws CloudException, InternalException {
        return 0;
    }

    @Override
    public int getMaxVlanCount() throws CloudException, InternalException {
        return Integer.MAX_VALUE;
    }

    @Nonnull
    @Override
    public String getProviderTermForNetworkInterface(@Nonnull Locale locale) {
        return null;
    }

    @Nonnull
    @Override
    public String getProviderTermForSubnet(@Nonnull Locale locale) {
        return "VSwitch";
    }

    @Nonnull
    @Override
    public String getProviderTermForVlan(@Nonnull Locale locale) {
        return "VPC";
    }

    @Nonnull
    @Override
    public Requirement getRoutingTableSupport() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Nonnull
    @Override
    public Requirement getSubnetSupport() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Nullable
    @Override
    public VisibleScope getVLANVisibleScope() {
        return VisibleScope.ACCOUNT_REGION;
    }

    @Nonnull
    @Override
    public Requirement identifySubnetDCRequirement() throws CloudException, InternalException {
        return Requirement.REQUIRED;
    }

    @Override
    public boolean isNetworkInterfaceSupportEnabled() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isSubnetDataCenterConstrained() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean isVlanDataCenterConstrained() throws CloudException, InternalException {
        return false;
    }

    @Nonnull
    @Override
    public Iterable<IPVersion> listSupportedIPVersions() throws CloudException, InternalException {
        return Collections.singletonList(IPVersion.IPV4);
    }

    @Override
    public boolean supportsInternetGatewayCreation() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean supportsRawAddressRouting() throws CloudException, InternalException {
        return false;
    }

	@Override
	public NamingConstraints getVlanNamingConstraints() {
		return NamingConstraints.getAlphaNumeric(2, 128).withRegularExpression(
                "^((?!http)[a-zA-Z\u4e00-\u9fa5])[\u4e00-\u9fa5a-zA-Z0-9_\\-]{1,127}$").withNoSpaces();
	}
	
	
}
