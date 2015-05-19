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

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.aliyun.AliyunMethod;
import org.dasein.cloud.compute.AbstractVolumeSupport;
import org.dasein.cloud.compute.MachineImage;
import org.dasein.cloud.compute.Volume;
import org.dasein.cloud.compute.VolumeCapabilities;
import org.dasein.cloud.compute.VolumeCreateOptions;
import org.dasein.cloud.compute.VolumeFormat;
import org.dasein.cloud.compute.VolumeState;
import org.dasein.cloud.compute.VolumeSupport;
import org.dasein.cloud.compute.VolumeType;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Storage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Jeffrey Yan on 5/13/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.05.1
 */
public class AliyunVolume extends AbstractVolumeSupport<Aliyun> implements VolumeSupport {

    static private final Logger stdLogger = Aliyun.getStdLogger(AliyunVolume.class);

    protected AliyunVolume(Aliyun provider) {
        super(provider);
    }

    @Override
    public VolumeCapabilities getCapabilities() throws CloudException, InternalException {
        return new AliyunVolumeCapabilities(getProvider());
    }

    @Override
    public @Nonnull boolean isSubscribed() throws CloudException, InternalException {
        return true;
    }

    private @Nonnull Volume toVolume(@Nonnull JSONObject volumeJson) throws JSONException, InternalException {
        Volume result = new Volume();
        result.setCreationTimestamp(getProvider().parseIso8601Date(volumeJson.getString("CreationTime")).getTime());
        String status = volumeJson.getString("Status");
        if ("In_use".equals(status) || "Available".equals(status)) {
            result.setCurrentState(VolumeState.AVAILABLE);
        } else if ("Attaching".equals(status) || "Detaching".equals(status) || "Creating".equals(status) || "ReIniting"
                .equals(status)) {
            result.setCurrentState(VolumeState.PENDING);
        } else {
            result.setCurrentState(VolumeState.DELETED);
        }
        result.setProviderDataCenterId(volumeJson.getString("ZoneId"));
        result.setName(volumeJson.getString("DiskName"));
        result.setDescription(volumeJson.getString("Description"));
        result.setDeviceId(volumeJson.getString("Device"));
        result.setFormat(VolumeFormat.BLOCK);
        result.setProviderVolumeId(volumeJson.getString("DiskId"));
        result.setProviderRegionId(volumeJson.getString("RegionId"));
        result.setProviderVirtualMachineId(volumeJson.getString("InstanceId"));
        if ("system".equals(volumeJson.getString("Type"))) {
            result.setRootVolume(true);
        } else {
            result.setRootVolume(false);
        }
        result.setSize(new Storage<Gigabyte>(volumeJson.getInt("Size"), Storage.GIGABYTE));
        result.setProviderSnapshotId(volumeJson.getString("SourceSnapshotId"));
        String category = volumeJson.getString("Category");
        if ("cloud".equals(category) || "ephemeral".equals(category)) {
            result.setType(VolumeType.HDD);
        } else if ("ephemeral_ssd".equals(category)) {
            result.setType(VolumeType.SSD);
        }
        return result;
    }

    @Override
    public Volume getVolume(@Nonnull String volumeId) throws InternalException, CloudException {
        String regionId = getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("RegionId", regionId);
        parameters.put("DiskIds", "[\"" + volumeId + "\"]");
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeDisks", parameters);
        JSONObject json = method.get().asJson();

        try {
            JSONArray volumesJson = json.getJSONObject("Disks").getJSONArray("Disk");
            if (volumesJson.length() >= 1) {
                JSONObject volumeJson = volumesJson.getJSONObject(0);
                return toVolume(volumeJson);
            } else {
                return null;
            }
        } catch (JSONException jsonException) {
            stdLogger.error("Failed to parse JSON due to field not exist", jsonException);
            throw new InternalException(jsonException);
        }
    }

    @Override
    public @Nonnull Iterable<Volume> listVolumes() throws InternalException, CloudException {
        String regionId = getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }

        List<Volume> result = new ArrayList<Volume>();
        int pageNumber = 1;
        int processedCount = 0;
        while(true) {
            Map<String, Object> parameters = new HashMap<String, Object>();
            parameters.put("RegionId", regionId);
            parameters.put("PageNumber", pageNumber++);
            parameters.put("PageSize", 50);//max
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeDisks", parameters);
            JSONObject json = method.get().asJson();

            try {
                int totalCount = json.getInt("TotalCount");
                JSONArray volumesJson = json.getJSONObject("Images").getJSONArray("Image");
                for (int i = 0; i < volumesJson.length(); i++) {
                    JSONObject volumeJson = volumesJson.getJSONObject(i);
                    Volume volume = toVolume(volumeJson);
                    //TODO, should we filter Portal=false disks?
                    result.add(volume);
                    processedCount++;
                }
                if (processedCount >= totalCount) {
                    break;
                }
            } catch (JSONException jsonException) {
                stdLogger.error("Failed to parse JSON due to field not exist", jsonException);
                throw new InternalException(jsonException);
            }
        }
        return result;
    }

    @Override
    public @Nonnull String createVolume(@Nonnull VolumeCreateOptions options) throws InternalException, CloudException {
        String regionId = getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("RegionId", regionId);
        parameters.put("ZoneId", options.getDataCenterId());
        parameters.put("DiskName", options.getName());
        parameters.put("Description", options.getDescription());
        if (options.getVolumeSize() != null) {
            parameters.put("Size", options.getVolumeSize().getQuantity().intValue());
        }
        parameters.put("SnapshotId", options.getSnapshotId());
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "CreateDisk", parameters);
        JSONObject json = method.post().asJson();
        try {
            String diskId = json.getString("DiskId");
            return diskId;
        } catch (JSONException jsonException) {
            stdLogger.error("Failed to parse JSON due to field not exist", jsonException);
            throw new InternalException(jsonException);
        }
    }

    @Override
    public void remove(@Nonnull String volumeId) throws InternalException, CloudException {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("DiskId", volumeId);
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DeleteDisk", parameters);
        JSONObject json = method.post().asJson();
        getProvider().validateResponse(json);
    }

    @Override
    public void attach(@Nonnull String volumeId, @Nonnull String toServer, @Nonnull String deviceId) throws InternalException, CloudException {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("DiskId", volumeId);
        parameters.put("InstanceId", toServer);
        parameters.put("Device", deviceId);
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "AttachDisk", parameters);
        JSONObject json = method.post().asJson();
        getProvider().validateResponse(json);
    }

    @Override
    public void detach(@Nonnull String volumeId, boolean force) throws InternalException, CloudException {
        Volume volume = getVolume(volumeId);
        if (volume.getProviderVirtualMachineId() == null || volume.getProviderVirtualMachineId().isEmpty()) {
            throw new CloudException("Volume " + volumeId + " has not been attached to a instance");
        }

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("DiskId", volumeId);
        parameters.put("InstanceId", volume.getProviderVirtualMachineId());
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DetachDisk", parameters);
        JSONObject json = method.post().asJson();
        getProvider().validateResponse(json);
    }

}
