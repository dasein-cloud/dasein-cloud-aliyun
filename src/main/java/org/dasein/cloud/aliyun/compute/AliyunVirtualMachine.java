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
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.VisibleScope;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.aliyun.util.requester.AliyunHttpClientBuilderFactory;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestBuilder;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestExecutor;
import org.dasein.cloud.aliyun.util.requester.AliyunResponseHandlerWithMapper;
import org.dasein.cloud.aliyun.util.requester.AliyunValidateJsonResponseHandler;
import org.dasein.cloud.compute.AbstractVMSupport;
import org.dasein.cloud.compute.Architecture;
import org.dasein.cloud.compute.VMLaunchOptions;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.compute.VirtualMachineCapabilities;
import org.dasein.cloud.compute.VirtualMachineLifecycle;
import org.dasein.cloud.compute.VirtualMachineProduct;
import org.dasein.cloud.compute.VirtualMachineProductFilterOptions;
import org.dasein.cloud.compute.VirtualMachineSupport;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.compute.VolumeAttachment;
import org.dasein.cloud.compute.VolumeCreateOptions;
import org.dasein.cloud.network.IPVersion;
import org.dasein.cloud.network.RawAddress;
import org.dasein.cloud.util.APITrace;
import org.dasein.cloud.util.Cache;
import org.dasein.cloud.util.CacheLevel;
import org.dasein.cloud.util.requester.DriverToCoreMapper;
import org.dasein.cloud.util.requester.streamprocessors.StreamToJSONObjectProcessor;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Storage;
import org.dasein.util.uom.time.TimePeriod;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Jeffrey Yan on 5/12/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.05.1
 */
public class AliyunVirtualMachine extends AbstractVMSupport<Aliyun> implements VirtualMachineSupport {

    static private final Logger stdLogger = Aliyun.getStdLogger(AliyunVirtualMachine.class);

    protected AliyunVirtualMachine(Aliyun provider) {
        super(provider);
    }

    @Override
    public @Nonnull VirtualMachineCapabilities getCapabilities() throws InternalException, CloudException {
        return new AliyunVirtualMachineCapabilities(getProvider());
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        return true;
    }

    @Override
    public @Nonnull VirtualMachine launch(@Nonnull VMLaunchOptions withLaunchOptions)
            throws CloudException, InternalException {
        APITrace.begin(getProvider(), "VirtualMachine.launch");
        try {
            String regionId = getContext().getRegionId();
            if (regionId == null) {
                throw new InternalException("No region was set for this request");
            }

            Map<String, Object> entity = new HashMap<String, Object>();
            entity.put("RegionId", regionId);
            if (withLaunchOptions.getDataCenterId() != null) {
                entity.put("ZoneId", withLaunchOptions.getDataCenterId());
            }
            entity.put("ImageId", withLaunchOptions.getMachineImageId());
            entity.put("InstanceType", withLaunchOptions.getStandardProductId());
            entity.put("SecurityGroupId", withLaunchOptions.getFirewallIds()[0]); //support only one when create
            if (withLaunchOptions.getFriendlyName() != null) {
                entity.put("InstanceName", withLaunchOptions.getFriendlyName());
            }
            if (withLaunchOptions.getDescription() != null) {
                entity.put("Description", withLaunchOptions.getDescription());
            }
            entity.put("InternetChargeType", "PayByTraffic");
            entity.put("InternetMaxBandwidthIn", "200");
            entity.put("InternetMaxBandwidthOut", "100");
            if (withLaunchOptions.getHostName() != null) {
                entity.put("HostName", withLaunchOptions.getHostName());
            }
            if (withLaunchOptions.getBootstrapPassword() != null) {
                entity.put("Password", withLaunchOptions.getBootstrapPassword());
            }
            entity.put("SystemDisk.Category", "cloud");//hard code to cloud, because others are not persistent
            int volumeCount = 1;
            for (VolumeAttachment volumeAttachment : withLaunchOptions.getVolumes()) {
                if (volumeAttachment.getVolumeToCreate() != null) {
                    VolumeCreateOptions volumeCreateOptions = volumeAttachment.getVolumeToCreate();
                    if (volumeAttachment.isRootVolume()) {
                        entity.put("SystemDisk.DiskName", volumeCreateOptions.getName());
                        entity.put("SystemDisk.Description", volumeCreateOptions.getDescription());
                    } else {
                        entity.put("DataDisk." + volumeCount + ".Size", volumeCreateOptions.getVolumeSize());
                        entity.put("DataDisk." + volumeCount + ".Category", "cloud");
                        entity.put("DataDisk." + volumeCount + ".SnapshotId", volumeCreateOptions.getSnapshotId());
                        entity.put("DataDisk." + volumeCount + ".DiskName", volumeCreateOptions.getName());
                        entity.put("DataDisk." + volumeCount + ".Description", volumeCreateOptions.getDescription());
                        entity.put("DataDisk." + volumeCount + ".Device", volumeCreateOptions.getDeviceId());
                        entity.put("DataDisk." + volumeCount + ".DeleteWithInstance", "false");
                    }
                }
            }

            //only Vpc type has subnet id, should aliyun has two types of network product? Classic | Vpc
            //refer http://docs.aliyun.com/?spm=5176.100054.3.1.Ym5tBh#/ecs/open-api/datatype&instanceattributestype
            if (withLaunchOptions.getSubnetId() != null) {
                entity.put("VSwitchId", withLaunchOptions.getSubnetId());
                entity.put("PrivateIpAddress", withLaunchOptions.getPrivateIp());
            }

            HttpUriRequest request = AliyunRequestBuilder.post()
                    .provider(getProvider())
                    .category(AliyunRequestBuilder.Category.ECS)
                    .parameter("Action", "CreateInstance")
                    .entity(entity)
                    .clientToken(true)
                    .build();

            ResponseHandler<String> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, String>(
                    new StreamToJSONObjectProcessor(),
                    new DriverToCoreMapper<JSONObject, String>() {
                        @Override
                        public String mapFrom(JSONObject json) {
                            try {
                                return json.getString("InstanceId");
                            } catch (JSONException jsonException) {
                                stdLogger.error("Failed to parse JSON", jsonException);
                                throw new RuntimeException(jsonException.getMessage());
                            }
                        }
                    },
                    JSONObject.class);

            String instanceId = new AliyunRequestExecutor<String>(getProvider(),
                    AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                    request,
                    responseHandler).execute();

            return getVirtualMachine(instanceId);
        } finally {
            APITrace.end();
        }
    }

    @Override
    public void reboot( @Nonnull String vmId ) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "VirtualMachine.reboot");
        try {
            Map<String, Object> entity = new HashMap<String, Object>();
            entity.put("InstanceId", vmId);
            entity.put("ForceStop", "false");

            HttpUriRequest request = AliyunRequestBuilder.post().provider(getProvider())
                    .category(AliyunRequestBuilder.Category.ECS).parameter("Action", "RebootInstance")
                    .entity(entity)
                    .build();

            new AliyunRequestExecutor<Void>(getProvider(),
                    AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                    request,
                    new AliyunValidateJsonResponseHandler(getProvider())).execute();
        } finally {
            APITrace.end();
        }
    }

    @Override
    public void start( @Nonnull String vmId ) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "VirtualMachine.start");
        try {
            Map<String, Object> entity = new HashMap<String, Object>();
            entity.put("InstanceId", vmId);

            HttpUriRequest request = AliyunRequestBuilder.post()
                    .provider(getProvider())
                    .category(AliyunRequestBuilder.Category.ECS)
                    .parameter("Action", "StartInstance")
                    .entity(entity)
                    .build();

            new AliyunRequestExecutor<Void>(getProvider(),
                    AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                    request,
                    new AliyunValidateJsonResponseHandler(getProvider())).execute();
        } finally {
            APITrace.end();
        }
    }

    @Override
    public void stop( @Nonnull String vmId, boolean force ) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "VirtualMachine.stop");
        try {
            Map<String, Object> entity = new HashMap<String, Object>();
            entity.put("InstanceId", vmId);
            entity.put("ForceStop", Boolean.valueOf(force));

            HttpUriRequest request = AliyunRequestBuilder.post().provider(getProvider())
                    .category(AliyunRequestBuilder.Category.ECS).parameter("Action", "StopInstance")
                    .entity(entity)
                    .build();

            new AliyunRequestExecutor<Void>(getProvider(),
                    AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                    request,
                    new AliyunValidateJsonResponseHandler(getProvider())).execute();
        } finally {
            APITrace.end();
        }
    }

    @Override
    public void terminate(@Nonnull String vmId, @Nullable String explanation)
            throws InternalException, CloudException {
        APITrace.begin(getProvider(), "VirtualMachine.terminate");
        try {
            Map<String, Object> entity = new HashMap<String, Object>();
            entity.put("InstanceId", vmId);

            HttpUriRequest request = AliyunRequestBuilder.post().provider(getProvider())
                    .category(AliyunRequestBuilder.Category.ECS).parameter("Action", "DeleteInstance")
                    .entity(entity)
                    .build();

            new AliyunRequestExecutor<Void>(getProvider(),
                    AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                    request,
                    new AliyunValidateJsonResponseHandler(getProvider())).execute();
        } finally {
            APITrace.end();
        }
    }

    @Override
    @Deprecated
    public VirtualMachine modifyInstance( @Nonnull String vmId, @Nonnull String[] firewalls ) throws InternalException, CloudException {
        return alterVirtualMachineFirewalls(vmId, firewalls);
    }

    @Override
    public @Nonnull VirtualMachine alterVirtualMachineFirewalls(@Nonnull String virtualMachineId, @Nonnull String[] firewalls) throws InternalException, CloudException{
        APITrace.begin(getProvider(), "VirtualMachine.alterVirtualMachineFirewalls");
        try {
            List<String> targetFirewalls = Arrays.asList(firewalls);
            VirtualMachine virtualMachine = getVirtualMachine(virtualMachineId);
            List<String> currentFirewalls = Arrays.asList(virtualMachine.getProviderFirewallIds());

            List<String> toRemove = new ArrayList<String>();
            List<String> toAdd = new ArrayList<String>();
            for (String firewall : currentFirewalls) {
                if (!targetFirewalls.contains(firewall)) {
                    toRemove.add(firewall);
                }
            }
            for (String firewall : targetFirewalls) {
                if (!currentFirewalls.contains(firewall)) {
                    toAdd.add(firewall);
                }
            }

            for (String firewallId : toRemove) {
                Map<String, Object> entity = new HashMap<String, Object>();
                entity.put("InstanceId", virtualMachineId);
                entity.put("SecurityGroupId", firewallId);

                HttpUriRequest request = AliyunRequestBuilder.post()
                        .provider(getProvider())
                        .category(AliyunRequestBuilder.Category.ECS)
                        .parameter("Action", "LeaveSecurityGroup")
                        .entity(entity)
                        .build();

                new AliyunRequestExecutor<Void>(getProvider(),
                        AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                        request,
                        new AliyunValidateJsonResponseHandler(getProvider())).execute();
            }
            for (String firewallId : toAdd) {
                Map<String, Object> entity = new HashMap<String, Object>();
                entity.put("InstanceId", virtualMachineId);
                entity.put("SecurityGroupId", firewallId);

                HttpUriRequest request = AliyunRequestBuilder.post()
                        .provider(getProvider())
                        .category(AliyunRequestBuilder.Category.ECS)
                        .parameter("Action", "JoinSecurityGroup")
                        .entity(entity)
                        .build();

                new AliyunRequestExecutor<Void>(getProvider(),
                        AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                        request,
                        new AliyunValidateJsonResponseHandler(getProvider())).execute();
            }

            virtualMachine.setProviderFirewallIds(firewalls);
            return virtualMachine;
        } finally {
            APITrace.end();
        }
    }

    private @Nonnull VmState toVmState(@Nonnull String status) {
        if (status.equals("Stopped")) {
            return VmState.STOPPED;
        } else if (status.equals("Starting")) {
            return VmState.PENDING;
        } else if (status.equals("Running")) {
            return VmState.RUNNING;
        } else if (status.equals("Stopping")) {
            return VmState.STOPPING;
        } else if (status.equals("Deleted")) {
            return VmState.TERMINATED;
        }
        return VmState.ERROR;
    }

    private @Nonnull VirtualMachine toVirtualMachine(@Nonnull JSONObject json) throws JSONException, InternalException, CloudException {
        VirtualMachine virtualMachine = new VirtualMachine();
        virtualMachine.setProviderVirtualMachineId(json.getString("InstanceId"));
        virtualMachine.setName(json.getString("InstanceName"));
        virtualMachine.setDescription(json.getString("Description"));
        virtualMachine.setProviderMachineImageId(json.getString("ImageId"));
        virtualMachine.setProviderRegionId(json.getString("RegionId"));
        virtualMachine.setProviderDataCenterId(json.getString("ZoneId"));
        virtualMachine.setProductId(json.getString("InstanceType"));
        //json.getString("HostName")
        virtualMachine.setCurrentState(toVmState(json.getString("Status")));

        List<String> securityGroupIds = new ArrayList<String>();
        JSONArray securityGroupIdsJsonArray = json.getJSONObject("SecurityGroupIds").getJSONArray("SecurityGroupId");
        for (int i = 0; i < securityGroupIdsJsonArray.length(); i++) {
            securityGroupIds.add(securityGroupIdsJsonArray.getString(i));
        }
        virtualMachine.setProviderFirewallIds(securityGroupIds.toArray(new String[] {}));

        //ignore InternetMaxBandwidthIn, InternetMaxBandwidthOut, InternetChargeType
        Date creationTime = getProvider().parseIso8601DateWithoutSecond(json.getString("CreationTime"));
        virtualMachine.setCreationTimestamp(creationTime.getTime());

        String networkType = json.getString("InstanceNetworkType");
        if(networkType.equals("Classic")) {
            List<RawAddress> privateIpAddresses = new ArrayList<RawAddress>();
            JSONArray innerIpAddressesJsonArray = json.getJSONObject("InnerIpAddress").getJSONArray("IpAddress");
            for (int i = 0; i < innerIpAddressesJsonArray.length(); i++) {
                privateIpAddresses.add(new RawAddress(innerIpAddressesJsonArray.getString(i), IPVersion.IPV4));
            }
            virtualMachine.setPrivateAddresses(privateIpAddresses.toArray(new RawAddress[] {}));

            List<RawAddress> publicIpAddresses = new ArrayList<RawAddress>();
            JSONArray publicIpAddressesJsonArray = json.getJSONObject("PublicIpAddress").getJSONArray("IpAddress");
            for (int i = 0; i < publicIpAddressesJsonArray.length(); i++) {
                publicIpAddresses.add(new RawAddress(publicIpAddressesJsonArray.getString(i), IPVersion.IPV4));
            }
            virtualMachine.setPublicAddresses(publicIpAddresses.toArray(new RawAddress[] {}));
        } else if(networkType.equals("Vpc")) {
            JSONObject vpcAttributesJsonObject = json.getJSONObject("VpcAttributes");
            virtualMachine.setProviderVlanId(vpcAttributesJsonObject.getString("VpcId"));
            virtualMachine.setProviderSubnetId(vpcAttributesJsonObject.getString("VSwitchId"));

            List<RawAddress> privateIpAddresses = new ArrayList<RawAddress>();
            JSONArray privateIpAddressesJsonArray = vpcAttributesJsonObject.getJSONObject("PrivateIpAddress")
                    .getJSONArray("IpAddress");
            for (int i = 0; i < privateIpAddressesJsonArray.length(); i++) {
                privateIpAddresses.add(new RawAddress(privateIpAddressesJsonArray.getString(i), IPVersion.IPV4));
            }
            virtualMachine.setPrivateAddresses(privateIpAddresses.toArray(new RawAddress[] {}));
            //ignore VpcAttributes.NatIpAddress

            JSONObject eipAddressJsonObject = json.getJSONObject("EipAddress");
            String eipAddress = eipAddressJsonObject.getString("IpAddress");
            virtualMachine.setPublicAddresses(new RawAddress(eipAddress, IPVersion.IPV4));
            //ignore EipAddress.AllocationId, EipAddress.InternetChargeType
        }
        //ignore OperationLocks

        virtualMachine.setArchitecture(Architecture.I64);
        virtualMachine.setIoOptimized(false);//no IO optimized instance
        virtualMachine.setClonable(false);
        virtualMachine.setPausable(false);
        virtualMachine.setImagable(getProvider().getComputeServices().getImageSupport().getCapabilities()
                .canImage(virtualMachine.getCurrentState()));
        virtualMachine.setRebootable(getCapabilities().canReboot(virtualMachine.getCurrentState()));
        virtualMachine.setPersistent(true);
        virtualMachine.setProviderOwnerId(getContext().getAccountNumber());
        virtualMachine.setIpForwardingAllowed(false);//no document mentions instance can be a NAT server
        virtualMachine.setVisibleScope(VisibleScope.ACCOUNT_DATACENTER);
        virtualMachine.setLifecycle(VirtualMachineLifecycle.NORMAL);
        return virtualMachine;
    }

    @Override
    public @Nullable VirtualMachine getVirtualMachine(@Nonnull String vmId) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "VirtualMachine.getVirtualMachine");
        try {
            String regionId = getContext().getRegionId();
            if (regionId == null) {
                throw new InternalException("No region was set for this request");
            }

            HttpUriRequest request = AliyunRequestBuilder.get().provider(getProvider())
                    .category(AliyunRequestBuilder.Category.ECS)
                    .parameter("Action", "DescribeInstances").parameter("RegionId", regionId)
                    .parameter("InstanceIds", "[\"" + vmId + "\"]")
                    .build();

            ResponseHandler<VirtualMachine> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, VirtualMachine>(
                    new StreamToJSONObjectProcessor(),
                    new DriverToCoreMapper<JSONObject, VirtualMachine>() {
                        @Override
                        public VirtualMachine mapFrom(JSONObject json) {
                            try {
                                JSONArray virtualMachinesJson = json.getJSONObject("Instances").getJSONArray("Instance");
                                if (virtualMachinesJson.length() >= 1) {
                                    JSONObject virtualMachineJson = virtualMachinesJson.getJSONObject(0);
                                    return toVirtualMachine(virtualMachineJson);
                                } else {
                                    return null;
                                }
                            } catch (JSONException jsonException) {
                                stdLogger.error("Failed to parse JSON", jsonException);
                                throw new RuntimeException(jsonException);
                            } catch (CloudException cloudException) {
                                stdLogger.error("Failed to parse JSON", cloudException);
                                throw new RuntimeException(cloudException.getMessage());
                            } catch (InternalException internalException) {
                                stdLogger.error("Failed to parse JSON", internalException);
                                throw new RuntimeException(internalException.getMessage());
                            }
                        }
                    },
                    JSONObject.class);

            return new AliyunRequestExecutor<VirtualMachine>(getProvider(),
                    AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                    request,
                    responseHandler).execute();
        } finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull Iterable<VirtualMachine> listVirtualMachines() throws InternalException, CloudException {
        APITrace.begin(getProvider(), "VirtualMachine.listVirtualMachines");
        try {
            String regionId = getContext().getRegionId();
            if (regionId == null) {
                throw new InternalException("No region was set for this request");
            }

            final List<VirtualMachine> result = new ArrayList<VirtualMachine>();
            final AtomicInteger totalCount = new AtomicInteger(0);
            final AtomicInteger processedCount = new AtomicInteger(0);

            ResponseHandler<Void> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, Void>(
                    new StreamToJSONObjectProcessor(),
                    new DriverToCoreMapper<JSONObject, Void>() {
                        @Override
                        public Void mapFrom(JSONObject json) {
                            try {
                                totalCount.set(json.getInt("TotalCount"));

                                JSONArray virtualMachinesJson = json.getJSONObject("Instances").getJSONArray("Instance");
                                for (int i = 0; i < virtualMachinesJson.length(); i++) {
                                    JSONObject virtualMachineJson = virtualMachinesJson.getJSONObject(i);
                                    result.add(toVirtualMachine(virtualMachineJson));
                                    processedCount.incrementAndGet();
                                }
                                return null;
                            } catch (JSONException jsonException) {
                                stdLogger.error("Failed to parse JSON", jsonException);
                                throw new RuntimeException(jsonException);
                            } catch (CloudException cloudException) {
                                stdLogger.error("Failed to parse JSON", cloudException);
                                throw new RuntimeException(cloudException);
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
                        .parameter("Action", "DescribeInstances")
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
        } finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull Iterable<String> listFirewalls( @Nonnull String vmId ) throws InternalException, CloudException {
        return Arrays.asList(getVirtualMachine(vmId).getProviderFirewallIds());
    }

    @Override
    public @Nonnull Iterable<ResourceStatus> listVirtualMachineStatus() throws InternalException, CloudException {
        APITrace.begin(getProvider(), "VirtualMachine.listVirtualMachineStatus");
        try {
            String regionId = getContext().getRegionId();
            if (regionId == null) {
                throw new InternalException("No region was set for this request");
            }

            final List<ResourceStatus> result = new ArrayList<ResourceStatus>();
            final AtomicInteger totalCount = new AtomicInteger(0);
            final AtomicInteger processedCount = new AtomicInteger(0);

            ResponseHandler<Void> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, Void>(
                    new StreamToJSONObjectProcessor(),
                    new DriverToCoreMapper<JSONObject, Void>() {
                        @Override
                        public Void mapFrom(JSONObject json) {
                            try {
                                totalCount.set(json.getInt("TotalCount"));

                                JSONArray virtualMachinesJson = json.getJSONObject("InstanceStatuses").getJSONArray("InstanceStatus");
                                for (int i = 0; i < virtualMachinesJson.length(); i++) {
                                    JSONObject virtualMachineJson = virtualMachinesJson.getJSONObject(i);
                                    String virtualMachineId = virtualMachineJson.getString("InstanceId");
                                    VmState virtualMachineState = toVmState(virtualMachineJson.getString("Status"));
                                    result.add(new ResourceStatus(virtualMachineId, virtualMachineState));
                                    processedCount.incrementAndGet();
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
                        .parameter("Action", "DescribeInstanceStatus")
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
        } finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull Iterable<VirtualMachineProduct> listProducts( @Nonnull VirtualMachineProductFilterOptions options, @Nullable Architecture architecture ) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "VirtualMachine.listProducts");
        try {
            List<VirtualMachineProduct> products = new ArrayList<VirtualMachineProduct>();

            Cache<VirtualMachineProduct> cache = Cache
                    .getInstance(getProvider(), "vmProducts", VirtualMachineProduct.class, CacheLevel.CLOUD,
                            TimePeriod.valueOf(1, "day"));
            Iterable<VirtualMachineProduct> cachedProducts = cache.get(getContext());
            if (cachedProducts != null && cachedProducts.iterator().hasNext()) {
                Iterator<VirtualMachineProduct> iterator = cachedProducts.iterator();
                while (iterator.hasNext()) {
                    products.add(iterator.next());
                }
            } else {
                HttpUriRequest request = AliyunRequestBuilder.get()
                        .provider(getProvider())
                        .category(AliyunRequestBuilder.Category.ECS)
                        .parameter("Action", "DescribeInstanceTypes")
                        .build();

                ResponseHandler<List<VirtualMachineProduct>> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, List<VirtualMachineProduct>>(
                        new StreamToJSONObjectProcessor(),
                        new DriverToCoreMapper<JSONObject, List<VirtualMachineProduct>>() {
                            @Override
                            public List<VirtualMachineProduct> mapFrom(JSONObject json) {
                                if (json == null) {
                                    return null;
                                }
                                try {
                                    List<VirtualMachineProduct> result = new ArrayList<VirtualMachineProduct>();
                                    JSONArray productsJson = json.getJSONObject("InstanceTypes").getJSONArray("InstanceType");
                                    for (int i = 0; i < productsJson.length(); i++) {
                                        JSONObject productJson = productsJson.getJSONObject(i);
                                        VirtualMachineProduct product = new VirtualMachineProduct();

                                        product.setRamSize(new Storage<Gigabyte>(productJson.getInt("MemorySize"), Storage.GIGABYTE));
                                        //Aliyun root volume, Linux has 20G while Windows has 40G
                                        product.setRootVolumeSize(new Storage<Gigabyte>(20, Storage.GIGABYTE));
                                        product.setCpuCount(productJson.getInt("CpuCoreCount"));
                                        String instanceTypeId = productJson.getString("InstanceTypeId");
                                        product.setProviderProductId(instanceTypeId);
                                        product.setDescription(instanceTypeId);
                                        product.setName(instanceTypeId);
                                        result.add(product);
                                    }
                                    return result;
                                } catch (JSONException jsonException) {
                                    stdLogger.error("Failed to parse JSON", jsonException);
                                    throw new RuntimeException(jsonException);
                                }
                            }
                        },
                        JSONObject.class);

                List<VirtualMachineProduct> result = new AliyunRequestExecutor<List<VirtualMachineProduct>>(getProvider(),
                        AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                        request,
                        responseHandler).execute();

                if (result == null) {
                    return Collections.emptyList();
                }
                cache.put(getContext(), products);
            }

            List<VirtualMachineProduct> result = new ArrayList<VirtualMachineProduct>();
            for (VirtualMachineProduct product : products) {
                if (options != null) {
                    if (options.matches(product)) {
                        result.add(product);
                    }
                } else {
                    result.add(product);
                }
            }
            return result;
        } finally {
            APITrace.end();
        }
    }

}
