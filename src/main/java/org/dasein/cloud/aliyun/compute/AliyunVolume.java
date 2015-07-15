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

import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.aliyun.util.requester.AliyunHttpClientBuilderFactory;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestBuilder;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestExecutor;
import org.dasein.cloud.aliyun.util.requester.AliyunResponseHandlerWithMapper;
import org.dasein.cloud.aliyun.util.requester.AliyunValidateJsonResponseHandler;
import org.dasein.cloud.compute.AbstractVolumeSupport;
import org.dasein.cloud.compute.Volume;
import org.dasein.cloud.compute.VolumeCapabilities;
import org.dasein.cloud.compute.VolumeCreateOptions;
import org.dasein.cloud.compute.VolumeFormat;
import org.dasein.cloud.compute.VolumeState;
import org.dasein.cloud.compute.VolumeSupport;
import org.dasein.cloud.compute.VolumeType;
import org.dasein.cloud.util.requester.DriverToCoreMapper;
import org.dasein.cloud.util.requester.streamprocessors.StreamToJSONObjectProcessor;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Storage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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

        HttpUriRequest request = AliyunRequestBuilder.get()
                .provider(getProvider())
                .category(AliyunRequestBuilder.Category.ECS)
                .parameter("Action", "DescribeDisks")
                .parameter("RegionId", regionId)
                .parameter("DiskIds", "[\"" + volumeId + "\"]")
                .clientToken(true)
                .build();

        ResponseHandler<Volume> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, Volume>(
                new StreamToJSONObjectProcessor(),
                new DriverToCoreMapper<JSONObject, Volume>() {
                    @Override
                    public Volume mapFrom(JSONObject json) {
                        try {
                            JSONArray volumesJson = json.getJSONObject("Disks").getJSONArray("Disk");
                            if (volumesJson.length() >= 1) {
                                JSONObject volumeJson = volumesJson.getJSONObject(0);
                                return toVolume(volumeJson);
                            } else {
                                return null;
                            }
                        } catch (JSONException jsonException) {
                            stdLogger.error("Failed to parse JSON", jsonException);
                            throw new RuntimeException(jsonException);
                        } catch (InternalException internalException) {
                            stdLogger.error("Failed to parse JSON", internalException);
                            throw new RuntimeException(internalException.getMessage());
                        }
                    }
                },
                JSONObject.class);

        return new AliyunRequestExecutor<Volume>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                responseHandler).execute();
    }

    @Override
    public @Nonnull Iterable<Volume> listVolumes() throws InternalException, CloudException {
        String regionId = getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }

        final List<Volume> result = new ArrayList<Volume>();
        final AtomicInteger totalCount = new AtomicInteger(0);
        final AtomicInteger processedCount = new AtomicInteger(0);

        ResponseHandler<Void> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, Void>(
                new StreamToJSONObjectProcessor(),
                new DriverToCoreMapper<JSONObject, Void>() {
                    @Override
                    public Void mapFrom(JSONObject json) {
                        try {
                            totalCount.set(json.getInt("TotalCount"));

                            JSONArray volumesJson = json.getJSONObject("Disks").getJSONArray("Disk");
                            for (int i = 0; i < volumesJson.length(); i++) {
                                JSONObject volumeJson = volumesJson.getJSONObject(i);
                                Volume volume = toVolume(volumeJson);
                                //TODO, should we filter Portal=false disks?
                                result.add(volume);
                                processedCount.incrementAndGet();
                            }
                            return null;
                        } catch (JSONException jsonException) {
                            stdLogger.error("Failed to parse JSON", jsonException);
                            throw new RuntimeException(jsonException);
                        } catch (InternalException internalException) {
                            stdLogger.error("Failed to parse JSON", internalException);
                            throw new RuntimeException(internalException.getMessage());
                        }
                    }
                },
                JSONObject.class);

        int pageNumber = 1;
        while(true) {
            HttpUriRequest request = AliyunRequestBuilder.get()
                    .provider(getProvider())
                    .category(AliyunRequestBuilder.Category.ECS)
                    .parameter("Action", "DescribeDisks")
                    .parameter("RegionId", regionId)
                    .parameter("PageNumber", pageNumber++)
                    .parameter("PageSize", 50)//max
                    .build();

            new AliyunRequestExecutor<Void>(getProvider(),
                    AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                    request,
                    responseHandler).execute();

            if (processedCount.intValue() >= totalCount.intValue()) {
                break;
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

        Map<String, Object> entity = new HashMap<String, Object>();
        entity.put("RegionId", regionId);
        entity.put("ZoneId", options.getDataCenterId());
        entity.put("DiskName", options.getName());
        entity.put("Description", options.getDescription());
        if (options.getVolumeSize() != null) {
            entity.put("Size", options.getVolumeSize().getQuantity().intValue());
        }
        entity.put("SnapshotId", options.getSnapshotId());

        HttpUriRequest request = AliyunRequestBuilder.post()
                .provider(getProvider())
                .category(AliyunRequestBuilder.Category.ECS)
                .parameter("Action", "CreateDisk")
                .entity(entity)
                .clientToken(true)
                .build();

        ResponseHandler<String> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, String>(
                new StreamToJSONObjectProcessor(),
                new DriverToCoreMapper<JSONObject, String>() {
                    @Override
                    public String mapFrom(JSONObject json) {
                        try {
                            return json.getString("DiskId");
                        } catch (JSONException jsonException) {
                            stdLogger.error("Failed to parse JSON", jsonException);
                            throw new RuntimeException(jsonException);
                        }
                    }
                },
                JSONObject.class);

        return new AliyunRequestExecutor<String>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                responseHandler).execute();
    }

    @Override
    public void remove(@Nonnull String volumeId) throws InternalException, CloudException {
        Map<String, Object> entity = new HashMap<String, Object>();
        entity.put("DiskId", volumeId);

        HttpUriRequest request = AliyunRequestBuilder.post()
                .provider(getProvider())
                .category(AliyunRequestBuilder.Category.ECS)
                .parameter("Action", "DeleteDisk")
                .entity(entity)
                .build();

        new AliyunRequestExecutor<Void>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                new AliyunValidateJsonResponseHandler(getProvider())).execute();
    }

    @Override
    public void attach(@Nonnull String volumeId, @Nonnull String toServer, @Nonnull String deviceId) throws InternalException, CloudException {
        Map<String, Object> entity = new HashMap<String, Object>();
        entity.put("DiskId", volumeId);
        entity.put("InstanceId", toServer);
        entity.put("Device", deviceId);

        HttpUriRequest request = AliyunRequestBuilder.post()
                .provider(getProvider())
                .category(AliyunRequestBuilder.Category.ECS)
                .parameter("Action", "AttachDisk")
                .entity(entity)
                .build();

        new AliyunRequestExecutor<Void>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                new AliyunValidateJsonResponseHandler(getProvider())).execute();
    }

    @Override
    public void detach(@Nonnull String volumeId, boolean force) throws InternalException, CloudException {
        Volume volume = getVolume(volumeId);
        if (volume.getProviderVirtualMachineId() == null || volume.getProviderVirtualMachineId().isEmpty()) {
            throw new CloudException("Volume " + volumeId + " has not been attached to a instance");
        }

        Map<String, Object> entity = new HashMap<String, Object>();
        entity.put("DiskId", volumeId);
        entity.put("InstanceId", volume.getProviderVirtualMachineId());

        HttpUriRequest request = AliyunRequestBuilder.post()
                .provider(getProvider())
                .category(AliyunRequestBuilder.Category.ECS)
                .parameter("Action", "DetachDisk")
                .entity(entity)
                .build();

        new AliyunRequestExecutor<Void>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                new AliyunValidateJsonResponseHandler(getProvider())).execute();
    }

}
