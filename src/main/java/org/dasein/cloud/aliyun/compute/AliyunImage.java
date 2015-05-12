/*
 * *
 *  * Copyright (C) 2009-2015 Dell, Inc.
 *  * See annotations for authorship information
 *  *
 *  * ====================================================================
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  * ====================================================================
 *
 */

package org.dasein.cloud.aliyun.compute;

import org.apache.log4j.Logger;
import org.dasein.cloud.AsynchronousTask;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.aliyun.AliyunMethod;
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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        return new AliyunMachineImageCapabilities(getProvider());
    }

    @Nullable
    @Override
    public MachineImage getImage(@Nonnull String providerImageId) throws CloudException, InternalException {
        String regionId = getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("RegionId", regionId);
        parameters.put("ImageId", providerImageId);
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeImages", parameters);
        JSONObject json = method.get().asJson();

        try {
            JSONArray imagesJson = json.getJSONObject("Images").getJSONArray("Image");
            if (imagesJson.length() >= 1) {
                JSONObject imageJson = imagesJson.getJSONObject(0);
                return toMachineImage(imageJson, regionId);
            } else {
                return null;
            }
        } catch (JSONException jsonException) {
            stdLogger.error("Failed to parse JSON due to field not exist", jsonException);
            throw new InternalException(jsonException);
        }
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        return true;
    }

    private @Nullable MachineImage toMachineImage( @Nullable JSONObject imageJson, String regionId)
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

    private List<MachineImage> describeImages(Architecture architecture, ImageClass imageClass, Platform platform,
            String regex, boolean matchesAny, String ownerAlias) throws InternalException, CloudException {
        ImageFilterOptions imageFilterOptions = ImageFilterOptions.getInstance();
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

        String regionId = getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }

        List<MachineImage> result = new ArrayList<MachineImage>();
        int pageNumber = 1;
        int processedCount = 0;
        while(true) {
            Map<String, Object> parameters = new HashMap<String, Object>();
            parameters.put("RegionId", regionId);
            parameters.put("PageNumber", pageNumber++);
            parameters.put("PageSize", 50);//max
            parameters.put("ImageOwnerAlias", ownerAlias);
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeImages", parameters);
            JSONObject json = method.get().asJson();

            try {
                int totalCount = json.getInt("TotalCount");
                JSONArray imagesJson = json.getJSONObject("Images").getJSONArray("Image");
                for (int i = 0; i < imagesJson.length(); i++) {
                    JSONObject imageJson = imagesJson.getJSONObject(i);
                    MachineImage machineImage = toMachineImage(imageJson, regionId);
                    if (machineImage != null && imageFilterOptions.matches(machineImage)) {
                        result.add(machineImage);
                    }
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

    @Nonnull
    @Override
    public Iterable<MachineImage> listImages(@Nullable ImageFilterOptions options)
            throws CloudException, InternalException {
        if (options.getAccountNumber() != null && !options.getAccountNumber().isEmpty()) {
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
                computeServices.getVirtualMachineSupport().stop(options.getVirtualMachineId(), true);
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

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("RegionId", regionId);
        parameters.put("SnapshotId", snapshotId);
        parameters.put("ImageName", options.getName());
        parameters.put("Description", options.getDescription());
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "CreateImage", parameters);
        JSONObject json = method.get().asJson();
        String imageId;
        try {
            imageId = json.getString("ImageId");
        } catch (JSONException jsonException) {
            stdLogger.error("Failed to parse JSON due to field not exist", jsonException);
            throw new InternalException(jsonException);
        }
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

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("RegionId", regionId);
        parameters.put("ImageId", providerImageId);
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "CopyImage", parameters);
        JSONObject json = method.get().asJson();
        try {
            String requestId = json.getString("RequestId");
            if (requestId != null && !requestId.isEmpty()) {
                return;
            } else {
                throw new CloudException("Response is not valid: no RequestId field");
            }
        } catch (JSONException jsonException) {
            stdLogger.error("Failed to parse JSON due to field not exist", jsonException);
            throw new InternalException(jsonException);
        }
    }

    @Override
    public @Nonnull String copyImage( @Nonnull ImageCopyOptions options ) throws CloudException, InternalException {
        String regionId = getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("RegionId", regionId);
        parameters.put("ImageId", options.getProviderImageId());
        parameters.put("DestinationRegionId", options.getTargetRegionId());
        parameters.put("DestinationImageName", options.getName());
        parameters.put("DestinationDescription", options.getDescription());
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "CopyImage", parameters);
        JSONObject json = method.get().asJson();
        try {
            return json.getString("ImageId");
        } catch (JSONException jsonException) {
            stdLogger.error("Failed to parse JSON due to field not exist", jsonException);
            throw new InternalException(jsonException);
        }
    }

    @Override
    public @Nonnull Iterable<String> listShares(@Nonnull String providerImageId) throws CloudException, InternalException {
        String regionId = getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }

        List<String> result = new ArrayList<String>();
        int pageNumber = 1;
        while(true) {
            Map<String, Object> parameters = new HashMap<String, Object>();
            parameters.put("RegionId", regionId);
            parameters.put("ImageId", providerImageId);
            parameters.put("PageNumber", pageNumber++);
            parameters.put("PageSize", 50);//max
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeImageSharePermission", parameters);
            JSONObject json = method.get().asJson();

            try {
                JSONArray accountsJson = json.getJSONObject("Accounts").getJSONArray("Account");
                for (int i = 0; i < accountsJson.length(); i++) {
                    JSONObject accountJson = accountsJson.getJSONObject(i);
                    result.add(accountJson.getString("AliyunId"));
                }
                int totalCount = json.getInt("TotalCount");
                if (totalCount <= result.size()) {
                    break;
                }
            } catch (JSONException jsonException) {
                stdLogger.error("Failed to parse JSON due to field not exist", jsonException);
                throw new InternalException(jsonException);
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

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("RegionId", regionId);
        parameters.put("ImageId", providerImageId);
        int i = 1;
        for (String accountNumber : accountNumbers) {
            parameters.put(operation + "." + i, accountNumber);
            i = i + 1;
        }
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "ModifyImageSharePermission", parameters);
        JSONObject json = method.get().asJson();
        try {
            String requestId = json.getString("RequestId");
            if (requestId != null && !requestId.isEmpty()) {
                return;
            } else {
                throw new CloudException("Response is not valid: no RequestId field");
            }
        } catch (JSONException jsonException) {
            stdLogger.error("Failed to parse JSON due to field not exist", jsonException);
            throw new InternalException(jsonException);
        }
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
