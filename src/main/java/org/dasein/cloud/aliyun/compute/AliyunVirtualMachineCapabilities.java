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

package org.dasein.cloud.aliyun.compute;

import org.dasein.cloud.AbstractCapabilities;
import org.dasein.cloud.Capabilities;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.VisibleScope;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.compute.Architecture;
import org.dasein.cloud.compute.ImageClass;
import org.dasein.cloud.compute.Platform;
import org.dasein.cloud.compute.VMScalingCapabilities;
import org.dasein.cloud.compute.VirtualMachineCapabilities;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.util.NamingConstraints;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

/**
 * Created by Jeffrey Yan on 5/15/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.05.1
 */
public class AliyunVirtualMachineCapabilities extends AbstractCapabilities<Aliyun>
        implements VirtualMachineCapabilities {

    public AliyunVirtualMachineCapabilities(@Nonnull Aliyun provider) {
        super(provider);
    }

    @Override
    public boolean canAlter(@Nonnull VmState fromState) throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean canClone(@Nonnull VmState fromState) throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean canPause(@Nonnull VmState fromState) throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean canReboot(@Nonnull VmState fromState) throws CloudException, InternalException {
        return VmState.RUNNING.equals(fromState);
    }

    @Override
    public boolean canResume(@Nonnull VmState fromState) throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean canStart(@Nonnull VmState fromState) throws CloudException, InternalException {
        return VmState.STOPPED.equals(fromState);
    }

    @Override
    public boolean canStop(@Nonnull VmState fromState) throws CloudException, InternalException {
        return VmState.RUNNING.equals(fromState);
    }

    @Override
    public boolean canSuspend(@Nonnull VmState fromState) throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean canTerminate(@Nonnull VmState fromState) throws CloudException, InternalException {
        return VmState.STOPPED.equals(fromState);
    }

    @Override
    public boolean canUnpause(@Nonnull VmState fromState) throws CloudException, InternalException {
        return false;
    }

    @Override
    public int getMaximumVirtualMachineCount() throws CloudException, InternalException {
        return Capabilities.LIMIT_UNKNOWN;
    }

    @Override
    public int getCostFactor(@Nonnull VmState state) throws CloudException, InternalException {
        return (state.equals(VmState.TERMINATED) ? 0 : 100);
    }

    @Override
    public @Nonnull String getProviderTermForVirtualMachine(@Nonnull Locale locale)
            throws CloudException, InternalException {
        return "instance";
    }

    @Override
    public @Nullable VMScalingCapabilities getVerticalScalingCapabilities()
            throws CloudException, InternalException {
        return VMScalingCapabilities.getInstance(false, false, false);
    }

    @Override
    public @Nonnull NamingConstraints getVirtualMachineNamingConstraints()
            throws CloudException, InternalException {
        return NamingConstraints.getAlphaNumeric(2, 128).withRegularExpression(
                "^((?!http)[a-zA-Z])[a-zA-Z0-9_\\-\\.]{1,127}").withNoSpaces();
    }

    @Override
    public @Nullable VisibleScope getVirtualMachineVisibleScope() {
        return VisibleScope.ACCOUNT_DATACENTER;
    }

    @Override
    public @Nullable VisibleScope getVirtualMachineProductVisibleScope() {
        return VisibleScope.ACCOUNT_GLOBAL;
    }

    @Nonnull
    @Override
    public String[] getVirtualMachineReservedUserNames() {
        return new String[0];
    }

    @Override
    public @Nonnull Requirement identifyDataCenterLaunchRequirement()
            throws CloudException, InternalException {
        return Requirement.OPTIONAL;
    }

    @Override
    public @Nonnull Requirement identifyImageRequirement(@Nonnull ImageClass cls)
            throws CloudException, InternalException {
        return (cls.equals(ImageClass.MACHINE) ? Requirement.REQUIRED : Requirement.NONE);
    }

    @Override
    public @Nonnull Requirement identifyPasswordRequirement(Platform platform)
            throws CloudException, InternalException {
        return Requirement.OPTIONAL;
    }

    @Override
    public @Nonnull Requirement identifyRootVolumeRequirement() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Override
    public @Nonnull Requirement identifyShellKeyRequirement(Platform platform)
            throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Override
    public @Nonnull Requirement identifyStaticIPRequirement() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Override
    public @Nonnull Requirement identifySubnetRequirement() throws CloudException, InternalException {
        return Requirement.OPTIONAL;
    }

    @Override
    public @Nonnull Requirement identifyVlanRequirement() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Override
    public boolean isAPITerminationPreventable() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isBasicAnalyticsSupported() throws CloudException, InternalException {
        //Aliyun's analytics has only average value, no max/min value
        //refer http://docs.aliyun.com/?spm=5176.100054.3.1.Ym5tBh#/ecs/open-api/monitor&describeinstancemonitordata
        return false;
    }

    @Override
    public boolean isExtendedAnalyticsSupported() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isUserDataSupported() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isUserDefinedPrivateIPSupported() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean isRootPasswordSSHKeyEncrypted() throws CloudException, InternalException {
        return false;
    }

    @Override
    public @Nonnull Iterable<Architecture> listSupportedArchitectures()
            throws InternalException, CloudException {
        //no architecture argument when launch VM, although image has two architecture type
        return Collections.unmodifiableList(Arrays.asList(Architecture.I64));
    }

    @Override
    public boolean supportsSpotVirtualMachines() throws InternalException, CloudException {
        return false;
    }

    @Override
    public boolean supportsClientRequestToken() throws InternalException, CloudException {
        return false; //we will handle client token in dasein driver for aliyun
    }

    @Override
    public boolean supportsCloudStoredShellKey() throws InternalException, CloudException {
        return false;
    }

    @Override
    public boolean isVMProductDCConstrained() throws InternalException, CloudException {
        return false;
    }

    @Override
    public boolean supportsAlterVM() {
        return false;
    }

    @Override
    public boolean supportsClone() {
        return false;
    }

    @Override
    public boolean supportsPause() {
        return false;
    }

    @Override
    public boolean supportsReboot() {
        return true;
    }

    @Override
    public boolean supportsResume() {
        return false;
    }

    @Override
    public boolean supportsStart() {
        return true;
    }

    @Override
    public boolean supportsStop() {
        return true;
    }

    @Override
    public boolean supportsSuspend() {
        return false;
    }

    @Override
    public boolean supportsTerminate() {
        return true;
    }

    @Override
    public boolean supportsUnPause() {
        return false;
    }
}
