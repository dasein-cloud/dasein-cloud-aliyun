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

import org.dasein.cloud.AbstractCapabilities;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.network.IPAddressCapabilities;
import org.dasein.cloud.network.IPVersion;
import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Locale;

/**
 * Created by Jane Wang on 5/7/2015.
 *
 * @author Jane Wang
 * @since 2015.05.01
 */
public class AliyunIpAddressCapabilities extends AbstractCapabilities<Aliyun> implements IPAddressCapabilities {

    public AliyunIpAddressCapabilities(@Nonnull Aliyun provider) {
        super(provider);
    }

    @Nonnull
    public String getProviderTermForIpAddress(@Nonnull Locale locale) {
        return "Elastic IP Address";
    }

    @Nonnull
    public Requirement identifyVlanForVlanIPRequirement() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Nonnull
    public Requirement identifyVlanForIPRequirement() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Nonnull
    public Requirement identifyVMForPortForwarding() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    public boolean isAssigned(@Nonnull IPVersion version) throws CloudException, InternalException {
        return version.equals(IPVersion.IPV4);
    }

    public boolean canBeAssigned(@Nonnull VmState vmState) throws CloudException, InternalException {
        return vmState.equals(VmState.RUNNING) || vmState.equals(VmState.STOPPED);
    }

    public boolean isAssignablePostLaunch(@Nonnull IPVersion version) throws CloudException, InternalException {
        return version.equals(IPVersion.IPV4);
    }

    public boolean isForwarding(IPVersion version) throws CloudException, InternalException {
        return false;
    }

    public boolean isRequestable(@Nonnull IPVersion version) throws CloudException, InternalException {
        return version.equals(IPVersion.IPV4);
    }

    @Nonnull
    public Iterable<IPVersion> listSupportedIPVersions() throws CloudException, InternalException {
        return Collections.singletonList(IPVersion.IPV4);
    }

    public boolean supportsVLANAddresses(@Nonnull IPVersion ofVersion) throws InternalException, CloudException {
        return true;
    }
}
