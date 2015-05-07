package org.dasein.cloud.aliyun.network;

import org.bouncycastle.util.IPAddress;
import org.dasein.cloud.AbstractCapabilities;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.network.IPAddressCapabilities;
import org.dasein.cloud.network.IPVersion;
import org.dasein.cloud.util.requester.fluent.Requester;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Locale;

/**
 * Created by jwang7 on 5/7/2015.
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
        return true; //TODO VPC Platform
    }
}
