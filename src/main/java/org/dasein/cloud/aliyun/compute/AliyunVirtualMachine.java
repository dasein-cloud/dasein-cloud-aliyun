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
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.VisibleScope;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.aliyun.AliyunMethod;
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
import org.dasein.cloud.util.Cache;
import org.dasein.cloud.util.CacheLevel;
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
        String regionId = getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("RegionId", regionId);
        if (withLaunchOptions.getDataCenterId() != null) {
            parameters.put("ZoneId", withLaunchOptions.getDataCenterId());
        }
        parameters.put("ImageId", withLaunchOptions.getMachineImageId());
        parameters.put("InstanceType", withLaunchOptions.getStandardProductId());
        parameters.put("SecurityGroupId", withLaunchOptions.getFirewallIds()[0]); //support only one when create
        if (withLaunchOptions.getFriendlyName() != null) {
            parameters.put("InstanceName", withLaunchOptions.getFriendlyName());
        }
        if (withLaunchOptions.getDescription() != null) {
            parameters.put("Description", withLaunchOptions.getDescription());
        }
        parameters.put("InternetChargeType", "PayByTraffic");
        parameters.put("InternetMaxBandwidthIn", "200");
        parameters.put("InternetMaxBandwidthOut", "100");
        if (withLaunchOptions.getHostName() != null) {
            parameters.put("HostName", withLaunchOptions.getHostName());
        }
        if (withLaunchOptions.getBootstrapPassword() != null) {
            parameters.put("Password", withLaunchOptions.getBootstrapPassword());
        }
        parameters.put("SystemDisk.Category", "cloud");//hard code to cloud, because others are not persistent
        int volumeCount = 1;
        for (VolumeAttachment volumeAttachment : withLaunchOptions.getVolumes()) {
            if (volumeAttachment.getVolumeToCreate() != null) {
                VolumeCreateOptions volumeCreateOptions = volumeAttachment.getVolumeToCreate();
                if (volumeAttachment.isRootVolume()) {
                    parameters.put("SystemDisk.DiskName", volumeCreateOptions.getName());
                    parameters.put("SystemDisk.Description", volumeCreateOptions.getDescription());
                } else {
                    parameters.put("DataDisk." + volumeCount + ".Size", volumeCreateOptions.getVolumeSize());
                    parameters.put("DataDisk." + volumeCount + ".Category", "cloud");
                    parameters.put("DataDisk." + volumeCount + ".SnapshotId", volumeCreateOptions.getSnapshotId());
                    parameters.put("DataDisk." + volumeCount + ".DiskName", volumeCreateOptions.getName());
                    parameters.put("DataDisk." + volumeCount + ".Description", volumeCreateOptions.getDescription());
                    parameters.put("DataDisk." + volumeCount + ".Device", volumeCreateOptions.getDeviceId());
                    parameters.put("DataDisk." + volumeCount + ".DeleteWithInstance", "false");
                }
            }
        }

        //only Vpc type has subnet id, should aliyun has two types of network product? Classic | Vpc
        //refer http://docs.aliyun.com/?spm=5176.100054.3.1.Ym5tBh#/ecs/open-api/datatype&instanceattributestype
        if (withLaunchOptions.getSubnetId() != null) {
            parameters.put("VSwitchId", withLaunchOptions.getSubnetId());
            parameters.put("PrivateIpAddress", withLaunchOptions.getPrivateIp());
        }

        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "CreateInstance", parameters, true);
        JSONObject json = method.post().asJson();
        try {
            String instanceId = json.getString("InstanceId");
            return getVirtualMachine(instanceId);
        } catch (JSONException jsonException) {
            stdLogger.error("Failed to parse JSON", jsonException);
            throw new InternalException(jsonException);
        }
    }

    @Override
    public void reboot( @Nonnull String vmId ) throws CloudException, InternalException {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("InstanceId", vmId);
        parameters.put("ForceStop", "false");
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "RebootInstance", parameters);
        JSONObject json = method.post().asJson();
        getProvider().validateResponse(json);
    }

    @Override
    public void start( @Nonnull String vmId ) throws InternalException, CloudException {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("InstanceId", vmId);
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "StartInstance", parameters);
        JSONObject json = method.post().asJson();
        getProvider().validateResponse(json);
    }

    @Override
    public void stop( @Nonnull String vmId, boolean force ) throws InternalException, CloudException {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("InstanceId", vmId);
        parameters.put("ForceStop", Boolean.valueOf(force));
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "StopInstance", parameters);
        JSONObject json = method.post().asJson();
        getProvider().validateResponse(json);
    }

    @Override
    public void terminate(@Nonnull String vmId, @Nullable String explanation)
            throws InternalException, CloudException {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("InstanceId", vmId);
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DeleteInstance", parameters);
        JSONObject json = method.post().asJson();
        getProvider().validateResponse(json);
    }

    @Override
    @Deprecated
    public VirtualMachine modifyInstance( @Nonnull String vmId, @Nonnull String[] firewalls ) throws InternalException, CloudException {
        return alterVirtualMachineFirewalls(vmId, firewalls);
    }

    @Override
    public @Nonnull VirtualMachine alterVirtualMachineFirewalls(@Nonnull String virtualMachineId, @Nonnull String[] firewalls) throws InternalException, CloudException{
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
            Map<String, Object> parameters = new HashMap<String, Object>();
            parameters.put("InstanceId", virtualMachineId);
            parameters.put("SecurityGroupId", firewallId);
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "LeaveSecurityGroup",
                    parameters);
            JSONObject json = method.post().asJson();
            getProvider().validateResponse(json);
        }
        for (String firewallId : toAdd) {
            Map<String, Object> parameters = new HashMap<String, Object>();
            parameters.put("InstanceId", virtualMachineId);
            parameters.put("SecurityGroupId", firewallId);
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "JoinSecurityGroup",
                    parameters);
            JSONObject json = method.post().asJson();
            getProvider().validateResponse(json);
        }

        virtualMachine.setProviderFirewallIds(firewalls);
        return virtualMachine;
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
        Date creationTime = getProvider().parseIso8601Date(json.getString("CreationTime"));
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
        String regionId = getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("RegionId", regionId);
        parameters.put("InstanceIds", "[\"" + vmId + "\"]");
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeInstances");
        JSONObject json = method.get().asJson();
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
            throw new InternalException(jsonException);
        }
    }

    @Override
    public @Nonnull Iterable<VirtualMachine> listVirtualMachines() throws InternalException, CloudException {
        String regionId = getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }

        List<VirtualMachine> result = new ArrayList<VirtualMachine>();
        int pageNumber = 1;
        int processedCount = 0;
        while(true) {
            Map<String, Object> parameters = new HashMap<String, Object>();
            parameters.put("RegionId", regionId);
            parameters.put("PageNumber", pageNumber++);
            parameters.put("PageSize", 50);//max
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeInstances", parameters);
            JSONObject json = method.get().asJson();
            try {
                int totalCount = json.getInt("TotalCount");
                JSONArray virtualMachinesJson = json.getJSONObject("Instances").getJSONArray("Instance");
                for (int i = 0; i < virtualMachinesJson.length(); i++) {
                    JSONObject virtualMachineJson = virtualMachinesJson.getJSONObject(i);
                    result.add(toVirtualMachine(virtualMachineJson));
                    processedCount++;
                }
                if (processedCount >= totalCount) {
                    break;
                }
            } catch (JSONException jsonException) {
                stdLogger.error("Failed to parse JSON", jsonException);
                throw new InternalException(jsonException);
            }
        }
        return result;
    }

    @Override
    public @Nonnull Iterable<String> listFirewalls( @Nonnull String vmId ) throws InternalException, CloudException {
        return Arrays.asList(getVirtualMachine(vmId).getProviderFirewallIds());
    }

    @Override
    public @Nonnull Iterable<ResourceStatus> listVirtualMachineStatus() throws InternalException, CloudException {
        String regionId = getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }

        List<ResourceStatus> result = new ArrayList<ResourceStatus>();
        int pageNumber = 1;
        int processedCount = 0;
        while(true) {
            Map<String, Object> parameters = new HashMap<String, Object>();
            parameters.put("RegionId", regionId);
            parameters.put("PageNumber", pageNumber++);
            parameters.put("PageSize", 50);//max
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeInstanceStatus", parameters);
            JSONObject json = method.get().asJson();
            try {
                int totalCount = json.getInt("TotalCount");
                JSONArray virtualMachinesJson = json.getJSONObject("InstanceStatuses").getJSONArray("InstanceStatus");
                for (int i = 0; i < virtualMachinesJson.length(); i++) {
                    JSONObject virtualMachineJson = virtualMachinesJson.getJSONObject(i);

                    String virtualMachineId = virtualMachineJson.getString("InstanceId");
                    VmState virtualMachineState = toVmState(virtualMachineJson.getString("Status"));
                    result.add(new ResourceStatus(virtualMachineId, virtualMachineState));
                    processedCount++;
                }
                if (processedCount >= totalCount) {
                    break;
                }
            } catch (JSONException jsonException) {
                stdLogger.error("Failed to parse JSON", jsonException);
                throw new InternalException(jsonException);
            }
        }
        return result;
    }

    @Override
    public @Nonnull Iterable<VirtualMachineProduct> listProducts( @Nonnull VirtualMachineProductFilterOptions options, @Nullable Architecture architecture ) throws InternalException, CloudException {
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
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeInstanceTypes");
            JSONObject json = method.get().asJson();
            if (json == null) {
                return Collections.emptyList();
            }
            try {
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
                    products.add(product);
                }
                cache.put(getContext(), products);
            } catch (JSONException jsonException) {
                stdLogger.error("Failed to parse JSON", jsonException);
                throw new InternalException(jsonException);
            }
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
    }

}
