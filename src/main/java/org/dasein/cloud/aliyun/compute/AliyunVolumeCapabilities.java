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
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.compute.Platform;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.compute.VolumeCapabilities;
import org.dasein.cloud.compute.VolumeFormat;
import org.dasein.cloud.util.NamingConstraints;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Storage;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Created by Jeffrey Yan on 5/13/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.05.1
 */
public class AliyunVolumeCapabilities extends AbstractCapabilities<Aliyun> implements VolumeCapabilities {
    public AliyunVolumeCapabilities(@Nonnull Aliyun provider) {
        super(provider);
    }

    @Override
    public boolean canAttach(VmState vmState) throws InternalException, CloudException {
        return (VmState.RUNNING.equals(vmState) || VmState.STOPPED.equals(vmState));
    }

    @Override
    public boolean canDetach(VmState vmState) throws InternalException, CloudException {
        return (VmState.RUNNING.equals(vmState) || VmState.STOPPED.equals(vmState));
    }

    @Override
    public int getMaximumVolumeCount() throws InternalException, CloudException {
        return Capabilities.LIMIT_UNKNOWN;
    }

    @Override
    public int getMaximumVolumeSizeIOPS() throws InternalException, CloudException {
        return 0;
    }

    @Override
    public int getMinimumVolumeSizeIOPS() throws InternalException, CloudException {
        return 0;
    }

    @Override
    public @Nonnull Storage<Gigabyte> getMaximumVolumeSize() throws InternalException, CloudException {
        return new Storage<Gigabyte>(2000, Storage.GIGABYTE);
    }

    @Override
    public @Nonnull Storage<Gigabyte> getMinimumVolumeSize() throws InternalException, CloudException {
        return new Storage<Gigabyte>(5, Storage.GIGABYTE);
    }

    @Override
    public @Nonnull NamingConstraints getVolumeNamingConstraints() throws CloudException, InternalException {
        return NamingConstraints.getAlphaNumeric(2, 128).withRegularExpression(
                "^((?!http)[a-z])[a-zA-Z0-9_\\-\\.]{1,127}").withNoSpaces();
    }

    @Override
    public @Nonnull String getProviderTermForVolume(@Nonnull Locale locale) {
        return "disk";
    }

    @Override
    public @Nonnull Requirement getVolumeProductRequirement() throws InternalException, CloudException {
        return Requirement.NONE;
    }

    @Nonnull
    @Override
    public Requirement getDeviceIdOnAttachRequirement() throws InternalException, CloudException {
        return Requirement.OPTIONAL;
    }

    @Override
    public boolean supportsIOPSVolumes() throws InternalException, CloudException {
        return false;
    }

    @Override
    public boolean isVolumeSizeDeterminedByProduct() throws InternalException, CloudException {
        return false;
    }

    @Override
    public @Nonnull Iterable<String> listPossibleDeviceIds(@Nonnull Platform platform)
            throws InternalException, CloudException {
        if (platform.isWindows()) {
            return Collections.emptyList();
        } else {
            List<String> result = new ArrayList<String>();
            for (int i = 98; i <= 122; i++) { //b-z
                result.add("/dev/xvd" + (char) i);
            }
            return result;
        }
    }

    @Override
    public @Nonnull Iterable<VolumeFormat> listSupportedFormats() throws InternalException, CloudException {
        return Collections.singletonList(VolumeFormat.BLOCK);
    }

    @Override
    public @Nonnull Requirement requiresVMOnCreate() throws InternalException, CloudException {
        return Requirement.NONE;
    }

    @Override
    public boolean supportsAttach() {
        return true;
    }

    @Override
    public boolean supportsDetach() {
        return true;
    }
}
