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
package org.dasein.cloud.aliyun.network;

import org.apache.log4j.Logger;
import org.dasein.cloud.*;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.aliyun.AliyunMethod;
import org.dasein.cloud.network.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.*;

/**
 * Created by Jane Wang on 5/7/2015.
 *
 * @author Jane Wang
 * @since 2015.05.01
 */
public class AliyunIPAddress extends AbstractIpAddressSupport<Aliyun> {

    static private final Logger stdLogger = Aliyun.getStdLogger(AliyunIPAddress.class);
    static private final ExecutorService threadPool = Executors.newFixedThreadPool(10);

    private transient volatile AliyunIpAddressCapabilities capabilities;

    public AliyunIPAddress(@Nonnull Aliyun provider) {
        super(provider);
    }

    public void assign(@Nonnull String addressId, @Nonnull String serverId) throws InternalException, CloudException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("AllocationId", addressId);
        params.put("InstanceId", serverId);
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "AssociateEipAddress", params);
        method.post();
    }

    public void assignToNetworkInterface(@Nonnull String addressId, @Nonnull String nicId) throws InternalException, CloudException {
        //NO-OP
    }

    public String forward(@Nonnull String addressId, int publicPort, @Nonnull Protocol protocol, int privatePort, @Nonnull String onServerId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("Aliyun doesn't support IP forward!");
    }

    public IPAddressCapabilities getCapabilities() throws CloudException, InternalException {
        if (capabilities == null) {
            capabilities = new AliyunIpAddressCapabilities(getProvider());
        }
        return capabilities;
    }

    public IpAddress getIpAddress(@Nonnull String addressId) throws InternalException, CloudException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        params.put("AllocationId", addressId);
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeEipAddresses", params);
        JSONObject response = method.get().asJson();
        try {
            JSONArray eipAddresses = response.getJSONObject("EipAddresses").getJSONArray("EipAddress");
            for (int i = 0; i < eipAddresses.length(); i++) {
                JSONObject eipAddress = eipAddresses.getJSONObject(i);
                return toIpAddress(eipAddress);
            }
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during Describe EIP Address!", e);
            throw new InternalException(e);
        }
        return null;
    }

    public boolean isSubscribed() throws CloudException, InternalException {
        return true;
    }

    public Future<Iterable<IpAddress>> listIpPoolConcurrently(@Nonnull IPVersion version, boolean unassignedOnly) throws InternalException, CloudException {
        ListIpPoolCallable callable = new ListIpPoolCallable(version, unassignedOnly);
        return threadPool.submit(callable);
    }

    public Iterable<IpAddress> listIpPool(@Nonnull IPVersion version, boolean unassignedOnly) throws InternalException, CloudException {
        try {
            Future<Iterable<IpAddress>> task = listIpPoolConcurrently(version, unassignedOnly);
            return task.get();
        } catch (ExecutionException e) {
            stdLogger.error("An exception occurs during list ip pool!", e);
            throw new InternalException(e);
        } catch (InterruptedException e) {
            stdLogger.error("An exception occurs during list ip pool!", e);
            throw new InternalException(e);
        }
    }

    public Iterable<ResourceStatus> listIpPoolStatus(@Nonnull IPVersion version) throws InternalException, CloudException {
        if (!version.equals(IPVersion.IPV4)) {
            return Collections.emptyList();
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        params.put("PageSize", AliyunNetworkCommon.DefaultPageSize);
        List<ResourceStatus> resourceStatuses = new ArrayList<ResourceStatus>();
        int currentPageNumber = 1;
        int maxPageNumber = 1;
        do {
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeEipAddresses", params);
            JSONObject response = method.get().asJson();
            try {
                JSONArray eipAddresses = response.getJSONObject("EipAddresses").getJSONArray("EipAddress");
                for (int i = 0; i < eipAddresses.length(); i++) {
                    JSONObject eipAddress = eipAddresses.getJSONObject(i);
                    maxPageNumber = eipAddress.getInt("TotalCount") / AliyunNetworkCommon.DefaultPageSize
                            + (eipAddress.getInt("TotalCount") % AliyunNetworkCommon.DefaultPageSize > 0 ? 1 : 0);
                    if (eipAddress.getString("Status") != null && eipAddress.getString("Status").toLowerCase().equals(AliyunNetworkCommon.AliyunEipStatus.AVAILABLE.name().toLowerCase())) {
                        resourceStatuses.add(new ResourceStatus(eipAddress.getString("AllocationId"), true));
                    }
                }
                currentPageNumber++;
            } catch (JSONException e) {
                stdLogger.error("An exception occurs during List Ip Pool Status!", e);
                throw new InternalException(e);
            }
        } while (currentPageNumber < maxPageNumber);
        return resourceStatuses;
    }

    public void releaseFromPool(@Nonnull String addressId) throws InternalException, CloudException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("AllocationId", addressId);
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "ReleaseEipAddress", params);
        method.post();
    }

    public void releaseFromServer(@Nonnull String addressId) throws InternalException, CloudException {
        Map<String, Object> params = new HashMap<String, Object>();
        IpAddress ipAddress = getIpAddress(addressId);
        if (ipAddress != null && !AliyunNetworkCommon.isEmpty(ipAddress.getServerId())) {
            String instanceId = ipAddress.getServerId();
            params.put("AllocationId", addressId);
            params.put("InstanceId", instanceId);
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "UnassociateEipAddresss", params);
            method.post();
        }
    }

    public String request(@Nonnull IPVersion version) throws InternalException, CloudException {
        if (!version.equals(IPVersion.IPV4)) {
            throw new OperationNotSupportedException("Aliyun supports IPV4 ip address only!");
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "AllocateEipAddress", params);
        JSONObject response = method.get().asJson();
        try{
            return response.getString("AllocationId");
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during Allocate EIP Address!", e);
            throw new InternalException(e);
        }
    }

    public String requestForVLAN(@Nonnull IPVersion version) throws InternalException, CloudException {
        throw new OperationNotSupportedException("Aliyun doesn't support request for vlan!");
    }

    public String requestForVLAN(@Nonnull IPVersion version, @Nonnull String vlanId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("Aliyun doesn't support request for vlan!");
    }

    public static boolean isPublicIpAddress(String ipAddress) throws InternalException {
        if (AliyunNetworkCommon.isEmpty(ipAddress)) {
            throw new InternalException("Invalid ip address: ip address is empty!");
        }
        if (ipAddress.startsWith("10.") || ipAddress.startsWith("192.168.")) {
            return false;
        }
        if (ipAddress.startsWith("172.")) {
            String[] ipSegments = ipAddress.split(".");
            Integer segment = Integer.valueOf(ipSegments[1]);
            if (segment >= 16 && segment <= 31) {
                return false;
            }
        }
        return true;
    }

    private IpAddress toIpAddress(JSONObject jsonObject) throws InternalException {
        try {
            IpAddress ipAddress = new IpAddress();
            ipAddress.setAddress(jsonObject.getString("IpAddress"));
            if (!AliyunNetworkCommon.isEmpty(jsonObject.getString("InstanceId"))) {
                ipAddress.setServerId(jsonObject.getString("InstanceId"));
            }
            ipAddress.setRegionId(jsonObject.getString("RegionId"));
            ipAddress.setVersion(IPVersion.IPV4);
            //TODO check forVlan (Aliyun support assign ip to vm) and AddressType, AWS assigned while Google not.
            ipAddress.setForVlan(false);
            if (!isPublicIpAddress(ipAddress.getRawAddress().getIpAddress())) {
                ipAddress.setAddressType(AddressType.PRIVATE);
            } else {
                ipAddress.setAddressType(AddressType.PUBLIC);
            }
            return ipAddress;
        } catch (JSONException e) {
            throw new InternalException(e);
        }
    }

    protected class ListIpPoolCallable implements Callable<Iterable<IpAddress>> {
        IPVersion version;
        boolean unassignedOnly;

        public ListIpPoolCallable( IPVersion version, boolean unassignedOnly ) {
            this.version = version;
            this.unassignedOnly = unassignedOnly;
        }

        public Iterable<IpAddress> call() throws CloudException, InternalException {
            if (!version.equals(IPVersion.IPV4)) {
                return Collections.emptyList();
            }
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("RegionId", getContext().getRegionId());
            params.put("PageSize", AliyunNetworkCommon.DefaultPageSize);
            if (unassignedOnly) {
                params.put("Status", "Available");
            }
            List<IpAddress> ipAddresses = new ArrayList<IpAddress>();
            int currentPageNumber = 1;
            int maxPageNumber = 1;
            do {
                params.put("PageNumber", currentPageNumber);
                AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeEipAddresses", params);
                JSONObject response = method.get().asJson();
                try {
                    JSONArray eipAddresses = response.getJSONObject("EipAddresses").getJSONArray("EipAddress");
                    for (int i = 0; i < eipAddresses.length(); i++) {
                        JSONObject eipAddress = eipAddresses.getJSONObject(i);
                        maxPageNumber = eipAddress.getInt("TotalCount") / AliyunNetworkCommon.DefaultPageSize
                                + (eipAddress.getInt("TotalCount") % AliyunNetworkCommon.DefaultPageSize > 0 ? 1 : 0);
                        ipAddresses.add(toIpAddress(eipAddress));
                    }
                    currentPageNumber++;
                } catch (JSONException e) {
                    stdLogger.error("An exception occurs during Describe Eip Addresses!", e);
                    throw new InternalException(e);
                }
            } while (currentPageNumber < maxPageNumber);
            return ipAddresses;
        }
    }
}
