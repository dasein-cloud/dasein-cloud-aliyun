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

import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.compute.AbstractComputeServices;
import org.dasein.cloud.compute.ComputeServices;
import org.dasein.cloud.compute.MachineImageSupport;
import org.dasein.cloud.compute.SnapshotSupport;
import org.dasein.cloud.compute.VirtualMachineSupport;
import org.dasein.cloud.compute.VolumeSupport;

import javax.annotation.Nullable;

/**
 * Created by Jeffrey Yan on 5/8/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.05.1
 */
public class AliyunCompute extends AbstractComputeServices<Aliyun> implements ComputeServices{

    public AliyunCompute(Aliyun provider) {
        super(provider);
    }

    @Override
    public @Nullable MachineImageSupport getImageSupport() {
        return new AliyunImage(getProvider());
    }

    @Override
    public @Nullable SnapshotSupport getSnapshotSupport() {
        return new AliyunSnapshot(getProvider());
    }

    @Override
    public @Nullable VirtualMachineSupport getVirtualMachineSupport() {
        return new AliyunVirtualMachine(getProvider());
    }

    @Override
    public @Nullable VolumeSupport getVolumeSupport() {
        return new AliyunVolume(getProvider());
    }

}
