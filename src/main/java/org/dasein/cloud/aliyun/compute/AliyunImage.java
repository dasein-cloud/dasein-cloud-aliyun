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
import org.dasein.cloud.AsynchronousTask;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.aliyun.util.requester.AliyunHttpClientBuilderFactory;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestBuilder;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestExecutor;
import org.dasein.cloud.aliyun.util.requester.AliyunResponseHandlerWithMapper;
import org.dasein.cloud.aliyun.util.requester.AliyunValidateJsonResponseHandler;
import org.dasein.cloud.compute.AbstractImageSupport;
import org.dasein.cloud.compute.Architecture;
import org.dasein.cloud.compute.ComputeServices;
import org.dasein.cloud.compute.ImageCapabilities;
import org.dasein.cloud.compute.ImageClass;
import org.dasein.cloud.compute.ImageCopyOptions;
import org.dasein.cloud.compute.ImageCreateOptions;
import org.dasein.cloud.compute.ImageFilterOptions;
import org.dasein.cloud.compute.MachineImage;
import org.dasein.cloud.compute.MachineImageFormat;
import org.dasein.cloud.compute.MachineImageState;
import org.dasein.cloud.compute.MachineImageSupport;
import org.dasein.cloud.compute.MachineImageVolume;
import org.dasein.cloud.compute.Platform;
import org.dasein.cloud.compute.SnapshotCreateOptions;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.compute.Volume;
import org.dasein.cloud.compute.VolumeFilterOptions;
import org.dasein.cloud.compute.VolumeState;
import org.dasein.cloud.util.requester.DriverToCoreMapper;
import org.dasein.cloud.util.requester.streamprocessors.StreamToJSONObjectProcessor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Jeffrey Yan on 5/8/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.05.1
 */
public class AliyunImage extends AbstractImageSupport<Aliyun> implements MachineImageSupport {

    static private final Logger stdLogger = Aliyun.getStdLogger(AliyunImage.class);

    protected AliyunImage(Aliyun provider) {
        super(provider);
    }

    @Override
    public ImageCapabilities getCapabilities() throws CloudException, InternalException {
        return new AliyunImageCapabilities(getProvider());
    }

    @Nullable
    @Override
    public MachineImage getImage(@Nonnull String providerImageId) throws CloudException, InternalException {
        final String regionId = getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }

        HttpUriRequest request = AliyunRequestBuilder.get()
                .provider(getProvider())
                .category(AliyunRequestBuilder.Category.ECS)
                .parameter("Action", "DescribeImages")
                .parameter("RegionId", regionId)
                .parameter("ImageId", providerImageId)
                .build();

        ResponseHandler<MachineImage> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, MachineImage>(
                new StreamToJSONObjectProcessor(),
                new DriverToCoreMapper<JSONObject, MachineImage>() {
                    @Override
                    public MachineImage mapFrom(JSONObject json) {
                        try {
                            JSONArray imagesJson = json.getJSONObject("Images").getJSONArray("Image");
                            if (imagesJson.length() >= 1) {
                                JSONObject imageJson = imagesJson.getJSONObject(0);
                                return toMachineImage(imageJson, regionId);
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

        return new AliyunRequestExecutor<MachineImage>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                responseHandler).execute();
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        return true;
    }

    private @Nonnull MachineImage toMachineImage(@Nonnull JSONObject imageJson, @Nonnull String regionId)
            throws JSONException, InternalException {
        String ownerAlias = imageJson.getString("ImageOwnerAlias");
        String owner = "--" + getProvider().getCloudName() + "--";
        if (ownerAlias.equals("self")) {
            owner = getProvider().getContext().getAccountNumber();
        } else if (ownerAlias.equals("others")) {
            owner = "--others--";
        } else if (ownerAlias.equals("marketplace")) {
            owner = "--marketplace--";
        }

        String imageId = imageJson.getString("ImageId");
        String name = imageJson.getString("OSName");
        String description = imageJson.getString("Description");
        Platform platform = Platform.guess(name);
        int minimumDiskSizeGb = imageJson.getInt("Size");
        Date created = getProvider().parseIso8601Date(imageJson.getString("CreationTime"));

        Architecture architecture = Architecture.I64;
        if("i386".equals(imageJson.getString("Architecture"))) {
            architecture = Architecture.I32;
        }

        MachineImageState machineImageState = MachineImageState.DELETED;
        String status = imageJson.getString("Status");
        if (status.equals("Available")) {
            machineImageState = MachineImageState.ACTIVE;
        } else if (status.equals("Creating")) {
            machineImageState = MachineImageState.PENDING;
        }

        List<MachineImageVolume> volumes = new ArrayList<MachineImageVolume>();
        JSONArray diskDeviceMappingsJson = imageJson.getJSONObject("DiskDeviceMappings").getJSONArray("DiskDeviceMapping");
        for (int i = 0; i < diskDeviceMappingsJson.length(); i++) {
            JSONObject diskDeviceMappingJson = diskDeviceMappingsJson.getJSONObject(i);
            String device = diskDeviceMappingJson.getString("Device");
            String snapshotId = diskDeviceMappingJson.getString("SnapshotId");
            int size = diskDeviceMappingJson.getInt("Size");
            volumes.add(MachineImageVolume.getInstance(device, snapshotId, size, null, null));
        }

        MachineImage machineImage = MachineImage
                .getInstance(owner, regionId, imageId, ImageClass.MACHINE, machineImageState, name, description,
                        architecture, platform).createdAt(created.getTime()).withStorageFormat(MachineImageFormat.VHD)
                .withVolumes(volumes);
        machineImage.setMinimumDiskSizeGb(minimumDiskSizeGb);
        return machineImage;
    }

    private List<MachineImage> describeImages(@Nullable Architecture architecture, @Nullable ImageClass imageClass,
            @Nullable Platform platform, @Nullable String regex, @Nonnull boolean matchesAny,
            @Nonnull String ownerAlias) throws InternalException, CloudException {
        final ImageFilterOptions imageFilterOptions = ImageFilterOptions.getInstance();
        if (architecture != null) {
            imageFilterOptions.withArchitecture(architecture);
        }
        if (imageClass != null) {
            imageFilterOptions.withImageClass(imageClass);
        }
        if (platform != null) {
            imageFilterOptions.onPlatform(platform);
        }
        if (regex != null) {
            imageFilterOptions.matchingRegex(regex);
        }
        if (matchesAny) {
            imageFilterOptions.matchingAny();
        } else {
            imageFilterOptions.matchingAll();
        }

        final String regionId = getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }

        final List<MachineImage> result = new ArrayList<MachineImage>();
        final AtomicInteger totalCount = new AtomicInteger(0);
        final AtomicInteger processedCount = new AtomicInteger(0);

        ResponseHandler<Void> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, Void>(
                new StreamToJSONObjectProcessor(),
                new DriverToCoreMapper<JSONObject, Void>() {
                    @Override
                    public Void mapFrom(JSONObject json) {
                        try {
                            totalCount.set(json.getInt("TotalCount"));

                            JSONArray imagesJson = json.getJSONObject("Images").getJSONArray("Image");
                            for (int i = 0; i < imagesJson.length(); i++) {
                                JSONObject imageJson = imagesJson.getJSONObject(i);
                                MachineImage machineImage = toMachineImage(imageJson, regionId);
                                if (machineImage != null && imageFilterOptions.matches(machineImage)) {
                                    result.add(machineImage);
                                }
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
                    .parameter("Action", "DescribeImages")
                    .parameter("RegionId", regionId)
                    .parameter("PageNumber", pageNumber++)
                    .parameter("PageSize", 50) //max
                    .parameter("ImageOwnerAlias", ownerAlias)
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

    @Nonnull
    @Override
    public Iterable<MachineImage> listImages(@Nullable ImageFilterOptions options)
            throws CloudException, InternalException {
        if (options == null) {
            List<MachineImage> result = new ArrayList<MachineImage>();
            result.addAll(describeImages(null, null, null, null, true, "self"));
            result.addAll(describeImages(null, null, null, null, true, "others "));
            return result;
        } else if (options.getAccountNumber() != null && !options.getAccountNumber().isEmpty()) {
            if (getContext().getAccountNumber().equals(options.getAccountNumber())) {
                return describeImages(options.getArchitecture(), options.getImageClass(), options.getPlatform(),
                        options.getRegex(), options.isMatchesAny(), "self");
            } else {
                stdLogger.warn("Aliyun doesn't support filter Image by account number, return all shared image");
                return describeImages(options.getArchitecture(), options.getImageClass(), options.getPlatform(),
                        options.getRegex(), options.isMatchesAny(), "others ");
            }
        } else {
            List<MachineImage> result = new ArrayList<MachineImage>();
            result.addAll(describeImages(options.getArchitecture(), options.getImageClass(), options.getPlatform(),
                    options.getRegex(), options.isMatchesAny(), "self"));
            result.addAll(describeImages(options.getArchitecture(), options.getImageClass(), options.getPlatform(),
                    options.getRegex(), options.isMatchesAny(), "others "));
            return result;
        }
    }

    @Override
    public @Nonnull Iterable<MachineImage> searchPublicImages(@Nonnull ImageFilterOptions options) throws CloudException, InternalException {
        if (options.getAccountNumber() != null && !options.getAccountNumber().isEmpty()) {
            stdLogger.warn("Aliyun doesn't support filter Image by account number");
        }

        List<MachineImage> result = new ArrayList<MachineImage>();
        result.addAll(describeImages(options.getArchitecture(), options.getImageClass(), options.getPlatform(),
                options.getRegex(), options.isMatchesAny(), "system"));
        result.addAll(describeImages(options.getArchitecture(), options.getImageClass(), options.getPlatform(),
                options.getRegex(), options.isMatchesAny(), "marketplace "));
        return result;
    }

    @Override
    protected MachineImage capture(@Nonnull ImageCreateOptions options, @Nullable AsynchronousTask<MachineImage> task) throws CloudException, InternalException {
        ComputeServices computeServices = getProvider().getComputeServices();

        if( task != null ) {
            task.setStartTime(System.currentTimeMillis());
        }

        //find root volume
        VolumeFilterOptions volumeFilterOptions = VolumeFilterOptions.getInstance()
                .attachedTo(options.getVirtualMachineId());
        Iterable<Volume> volumes = computeServices.getVolumeSupport().listVolumes(volumeFilterOptions);
        Volume rootVolume = null;
        for (Volume volume : volumes) {
            if (volume.isRootVolume() && VolumeState.AVAILABLE.equals(volume.getCurrentState())) {
                rootVolume = volume;
                break;
            }
        }
        if (rootVolume == null) {
            throw new InternalException(
                    "Virtual Machine " + options.getVirtualMachineId() + " has no Available root volume");
        }

        //stop VM if necessary
        boolean virtualMachineStopped = false;
        if (options.getReboot()) {
            VirtualMachine virtualMachine = computeServices.getVirtualMachineSupport()
                    .getVirtualMachine(options.getVirtualMachineId());

            if (VmState.RUNNING.equals(virtualMachine.getCurrentState())) {
                computeServices.getVirtualMachineSupport().stop(options.getVirtualMachineId());
                virtualMachineStopped = true;
            }
        }

        //create snapshot
        SnapshotCreateOptions snapshotCreateOptions = SnapshotCreateOptions
                .getInstanceForCreate(rootVolume.getProviderVolumeId(), UUID.randomUUID().toString(),
                        "Dasein created temporary snapshot for capture image");
        String snapshotId = computeServices.getSnapshotSupport().createSnapshot(snapshotCreateOptions);
        //TODO: need to wait for create snapshot complete?

        //start VM
        if(virtualMachineStopped) {
            computeServices.getVirtualMachineSupport().start(options.getVirtualMachineId());
        }

        //create image
        String regionId = getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }

        Map<String, Object> entity = new HashMap<String, Object>();
        entity.put("RegionId", regionId);
        entity.put("SnapshotId", snapshotId);
        entity.put("ImageName", options.getName());
        entity.put("Description", options.getDescription());

        HttpUriRequest request = AliyunRequestBuilder.post()
                .provider(getProvider())
                .category(AliyunRequestBuilder.Category.ECS)
                .parameter("Action", "CreateImage")
                .entity(entity)
                .clientToken(true)
                .build();

        ResponseHandler<String> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, String>(
                new StreamToJSONObjectProcessor(),
                new DriverToCoreMapper<JSONObject, String>() {
                    @Override
                    public String mapFrom(JSONObject json) {
                        try {
                            return json.getString("ImageId");
                        } catch (JSONException jsonException) {
                            stdLogger.error("Failed to parse JSON", jsonException);
                            throw new RuntimeException(jsonException.getMessage());
                        }
                    }
                },
                JSONObject.class);

        String imageId = new AliyunRequestExecutor<String>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                responseHandler).execute();
        //TODO: need to wait for create image complete?

        //delete snapshot
        computeServices.getSnapshotSupport().remove(snapshotId);

        MachineImage machineImage = getImage(imageId);
        if (task != null) {
            task.completeWithResult(machineImage);
        }

        return machineImage;
    }

    @Override
    public void remove(@Nonnull String providerImageId, boolean checkState) throws CloudException, InternalException {
        String regionId = getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }

        Map<String, Object> entity = new HashMap<String, Object>();
        entity.put("RegionId", regionId);
        entity.put("ImageId", providerImageId);

        HttpUriRequest request = AliyunRequestBuilder.post()
                .provider(getProvider())
                .category(AliyunRequestBuilder.Category.ECS)
                .parameter("Action", "DeleteImage")
                .entity(entity)
                .build();

        new AliyunRequestExecutor<Void>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                new AliyunValidateJsonResponseHandler(getProvider())).execute();
    }

    @Override
    public @Nonnull String copyImage( @Nonnull ImageCopyOptions options ) throws CloudException, InternalException {
        String regionId = getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }

        Map<String, Object> entity = new HashMap<String, Object>();
        entity.put("RegionId", regionId);
        entity.put("ImageId", options.getProviderImageId());
        entity.put("DestinationRegionId", options.getTargetRegionId());
        entity.put("DestinationImageName", options.getName());
        entity.put("DestinationDescription", options.getDescription());

        HttpUriRequest request = AliyunRequestBuilder.post()
                .provider(getProvider())
                .category(AliyunRequestBuilder.Category.ECS)
                .parameter("Action", "CopyImage")
                .entity(entity)
                .clientToken(true)
                .build();

        ResponseHandler<String> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, String>(
                new StreamToJSONObjectProcessor(),
                new DriverToCoreMapper<JSONObject, String>() {
                    @Override
                    public String mapFrom(JSONObject json) {
                        try {
                            return json.getString("ImageId");
                        } catch (JSONException jsonException) {
                            stdLogger.error("Failed to parse JSON", jsonException);
                            throw new RuntimeException(jsonException.getMessage());
                        }
                    }
                },
                JSONObject.class);

        return  new AliyunRequestExecutor<String>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                responseHandler).execute();
    }

    @Override
    public @Nonnull Iterable<String> listShares(@Nonnull String providerImageId) throws CloudException, InternalException {
        String regionId = getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }

        final List<String> result = new ArrayList<String>();
        final AtomicInteger totalCount = new AtomicInteger(0);

        ResponseHandler<Void> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, Void>(
                new StreamToJSONObjectProcessor(),
                new DriverToCoreMapper<JSONObject, Void>() {
                    @Override
                    public Void mapFrom(JSONObject json) {
                        try {
                            totalCount.set(json.getInt("TotalCount"));

                            JSONArray accountsJson = json.getJSONObject("Accounts").getJSONArray("Account");
                            for (int i = 0; i < accountsJson.length(); i++) {
                                JSONObject accountJson = accountsJson.getJSONObject(i);
                                result.add(accountJson.getString("AliyunId"));
                            }
                            return null;
                        } catch (JSONException jsonException) {
                            stdLogger.error("Failed to parse JSON", jsonException);
                            throw new RuntimeException(jsonException);
                        }
                    }
                },
                JSONObject.class);

        int pageNumber = 1;
        while(true) {
            HttpUriRequest request = AliyunRequestBuilder.get()
                    .provider(getProvider())
                    .category(AliyunRequestBuilder.Category.ECS)
                    .parameter("Action", "DescribeImageSharePermission")
                    .parameter("RegionId", regionId)
                    .parameter("ImageId", providerImageId)
                    .parameter("PageNumber", pageNumber++)
                    .parameter("PageSize", 50)//max
                    .build();

            new AliyunRequestExecutor<Void>(getProvider(),
                    AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                    request,
                    responseHandler).execute();

            if (totalCount.intValue() <= result.size()) {
                break;
            }
        }
        return result;
    }

    private void modifyImageShare(@Nonnull String providerImageId, @Nonnull String operation, @Nonnull String... accountNumbers)
            throws InternalException, CloudException {
        String regionId = getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }
        if (accountNumbers.length > 10) {
            throw new InternalException("Can add/remove 10 accounts one time");
        }

        Map<String, Object> entity = new HashMap<String, Object>();
        entity.put("RegionId", regionId);
        entity.put("ImageId", providerImageId);
        int i = 1;
        for (String accountNumber : accountNumbers) {
            entity.put(operation + "." + i, accountNumber);
            i = i + 1;
        }

        HttpUriRequest request = AliyunRequestBuilder.post()
                .provider(getProvider())
                .category(AliyunRequestBuilder.Category.ECS)
                .parameter("Action", "ModifyImageSharePermission")
                .entity(entity)
                .build();

        new AliyunRequestExecutor<Void>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                new AliyunValidateJsonResponseHandler(getProvider())).execute();
    }

    @Override
    public void addImageShare(@Nonnull String providerImageId, @Nonnull String accountNumber) throws CloudException, InternalException {
        modifyImageShare(providerImageId, "AddAccount", accountNumber);
    }

    @Override
    public void removeAllImageShares(@Nonnull String providerImageId) throws CloudException, InternalException {
        Iterable<String> shares = listShares(providerImageId);
        List<String> parts = new ArrayList<String>();
        for (String accountId : shares) {
            parts.add(accountId);
            if (parts.size() == 10) {
                modifyImageShare(providerImageId, "RemoveAccount", parts.toArray(new String[] {}));
                parts.clear();
            }
        }

        if (!parts.isEmpty()) {
            modifyImageShare(providerImageId, "RemoveAccount", parts.toArray(new String[] {}));
        }
    }

    @Override
    public void removeImageShare(@Nonnull String providerImageId, @Nonnull String accountNumber) throws CloudException, InternalException {
        modifyImageShare(providerImageId, "RemoveAccount", accountNumber);
    }

}
