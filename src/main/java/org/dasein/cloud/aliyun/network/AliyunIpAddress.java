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


import org.apache.http.conn.util.InetAddressUtils;
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
public class AliyunIpAddress extends AbstractIpAddressSupport<Aliyun> {

    private static final Logger stdLogger = Aliyun.getStdLogger(AliyunIpAddress.class);

    private transient volatile AliyunIpAddressCapabilities capabilities;

    public AliyunIpAddress(@Nonnull Aliyun provider) {
        super(provider);
    }

    public void assign(@Nonnull String addressId, @Nonnull String serverId) throws InternalException, CloudException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("AllocationId", addressId);
        params.put("InstanceId", serverId);
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "AssociateEipAddress", params);
        JSONObject response = method.post().asJson();
        getProvider().validateResponse(response);
    }

    public void assignToNetworkInterface(@Nonnull String addressId, @Nonnull String nicId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("Aliyun doesn't support assign IP address to Network Interface!");
    }

    public String forward(@Nonnull String addressId, int publicPort, @Nonnull Protocol protocol, int privatePort, @Nonnull String onServerId)
            throws InternalException, CloudException {
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
                return toIpAddress(eipAddresses.getJSONObject(i));
            }
            return null;
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during Describe EIP Address!", e);
            throw new InternalException(e);
        }
    }

    public boolean isSubscribed() throws CloudException, InternalException {
        return true;
    }

    public Iterable<IpAddress> listIpPool(@Nonnull IPVersion version, boolean unassignedOnly) throws InternalException, CloudException {
        if (!version.equals(IPVersion.IPV4)) {
            return Collections.emptyList();
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        params.put("PageSize", AliyunNetworkCommon.DefaultPageSize);
        if (unassignedOnly) {
            params.put("Status", AliyunNetworkCommon.IpAddressStatus.Available.name());
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
                    ipAddresses.add(toIpAddress(eipAddress));
                }
                maxPageNumber = response.getInt("TotalCount") / AliyunNetworkCommon.DefaultPageSize
                        + (response.getInt("TotalCount") % AliyunNetworkCommon.DefaultPageSize > 0 ? 1 : 0);
                currentPageNumber++;
            } catch (JSONException e) {
                stdLogger.error("An exception occurs during Describe Eip Addresses!", e);
                throw new InternalException(e);
            }
        } while (currentPageNumber < maxPageNumber);
        return ipAddresses;
    }

    /**
     * Return status contains: Associating, Unassociating, InUse and Avaiable
     * @param version the version of the IP protocol for which you are looking for IP addresses
     * @return resourceStatus list
     * @throws InternalException
     * @throws CloudException
     */
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
            params.put("PageNumber", currentPageNumber);
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeEipAddresses", params);
            JSONObject response = method.get().asJson();
            try {
                JSONArray eipAddresses = response.getJSONObject("EipAddresses").getJSONArray("EipAddress");
                for (int i = 0; i < eipAddresses.length(); i++) {
                    JSONObject eipAddress = eipAddresses.getJSONObject(i);
                    resourceStatuses.add(new ResourceStatus(eipAddress.getString("AllocationId"), eipAddress.getString("Status")));
                }
                maxPageNumber = response.getInt("TotalCount") / AliyunNetworkCommon.DefaultPageSize
                        + (response.getInt("TotalCount") % AliyunNetworkCommon.DefaultPageSize > 0 ? 1 : 0);
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
        JSONObject response = method.post().asJson();
        getProvider().validateResponse(response);
    }

    public void releaseFromServer(@Nonnull String addressId) throws InternalException, CloudException {
        Map<String, Object> params = new HashMap<String, Object>();
        IpAddress ipAddress = getIpAddress(addressId);
        if (ipAddress != null && !getProvider().isEmpty(ipAddress.getServerId())) {
            params.put("AllocationId", addressId);
            params.put("InstanceId", ipAddress.getServerId());
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "UnassociateEipAddresss", params);
            JSONObject response = method.post().asJson();
            getProvider().validateResponse(response);
        }
    }

    public String request(@Nonnull IPVersion version) throws InternalException, CloudException {
        if (!version.equals(IPVersion.IPV4)) {
            throw new InternalException("Aliyun supports IPV4 ip address only!");
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        params.put("InternetChargeType", AliyunNetworkCommon.InternetChargeType.PayByTraffic.name());
        params.put("Bandwidth", AliyunNetworkCommon.DefaultIpAddressBandwidth);
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "AllocateEipAddress", params, true);
        JSONObject response = method.post().asJson();
        try{
            return response.getString("AllocationId");
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during Allocate EIP Address!", e);
            throw new InternalException(e);
        }
    }

    public String requestForVLAN(@Nonnull IPVersion version) throws InternalException, CloudException {
        return requestForVLAN(version, null);
    }

    public String requestForVLAN(@Nonnull IPVersion version, @Nonnull String vlanId) throws InternalException, CloudException {
        return request(version);
    }

    /**
     * Ip addresses for vlan use only:
     * A class: 10.0.0.0 - 10.255.255.255 (7/24)
     * B class: 172.16.0.0 - 172.31.255.255 (14/16)
     * C class 192.168.0.0 - 192.168.255.255 (21/8)
     * @param ipAddress IP address
     * @return true - public ip address; false - private ip address
     * @throws InternalException
     */
    public static boolean isPublicIpAddress(String ipAddress) throws InternalException {
        if (!InetAddressUtils.isIPv4Address(ipAddress)) {
            throw new InternalException("Aliyun supports IPV4 address only!");
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

    private IpAddress toIpAddress(JSONObject response) throws InternalException {
        try {
            IpAddress ipAddress = new IpAddress();
            ipAddress.setAddress(response.getString("IpAddress"));
            ipAddress.setAddressType(AddressType.PUBLIC);
            ipAddress.setForVlan(true);
            ipAddress.setIpAddressId(response.getString("AllocationId"));
            ipAddress.setRegionId(response.getString("RegionId"));
            if (!getProvider().isEmpty(response.getString("InstanceId"))) {
                ipAddress.setServerId(response.getString("InstanceId"));
            }
            ipAddress.setReserved(false);
            ipAddress.setVersion(IPVersion.IPV4);
            return ipAddress;
        } catch (JSONException e) {
            throw new InternalException(e);
        }
    }
}
