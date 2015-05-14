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
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.network.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by Jane Wang on 5/13/2015.
 *
 * @author Jane Wang
 * @since
 */
public class AliyunVlan extends AbstractVLANSupport<Aliyun> {

    private static final Logger stdLogger = Aliyun.getStdLogger(AliyunVlan.class);

    private transient volatile AliyunVlanCapabilities capabilities;

    protected AliyunVlan(Aliyun provider) {
        super(provider);
    }

    @Override
    public Route addRouteToAddress(@Nonnull String routingTableId, @Nonnull IPVersion version, @Nullable String destinationCidr, @Nonnull String address) throws CloudException, InternalException {
        return super.addRouteToAddress(routingTableId, version, destinationCidr, address);
    }

    @Override
    public Route addRouteToGateway(@Nonnull String routingTableId, @Nonnull IPVersion version, @Nullable String destinationCidr, @Nonnull String gatewayId) throws CloudException, InternalException {
        return super.addRouteToGateway(routingTableId, version, destinationCidr, gatewayId);
    }

    @Override
    public Route addRouteToVirtualMachine(@Nonnull String routingTableId, @Nonnull IPVersion version, @Nullable String destinationCidr, @Nonnull String vmId) throws CloudException, InternalException {
        return super.addRouteToVirtualMachine(routingTableId, version, destinationCidr, vmId);
    }

    @Override
    public void assignRoutingTableToSubnet(@Nonnull String subnetId, @Nonnull String routingTableId) throws CloudException, InternalException {
        super.assignRoutingTableToSubnet(subnetId, routingTableId);
    }

    @Override
    public void assignRoutingTableToVlan(@Nonnull String vlanId, @Nonnull String routingTableId) throws CloudException, InternalException {
        super.assignRoutingTableToVlan(vlanId, routingTableId);
    }

    @Override
    public String createInternetGateway(@Nonnull String vlanId) throws CloudException, InternalException {
        return super.createInternetGateway(vlanId);
    }

    @Nonnull
    @Override
    public Subnet createSubnet(@Nonnull SubnetCreateOptions options) throws CloudException, InternalException {
        return super.createSubnet(options);
    }

    @Nonnull
    @Override
    public VLAN createVlan(@Nonnull String cidr, @Nonnull String name, @Nonnull String description, @Nonnull String domainName,
                           @Nonnull String[] dnsServers, @Nonnull String[] ntpServers) throws CloudException, InternalException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        if (!AliyunNetworkCommon.isEmpty(cidr)) {
            params.put("CidrBlock", cidr);
        }
        if (!AliyunNetworkCommon.isEmpty(name)) {
            params.put("VpcName", name);
        }
        if (!AliyunNetworkCommon.isEmpty(description)) {
            params.put("Description", description);
        }
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "CreateVpc", params);
        JSONObject response = method.post().asJson();
        try {
            return toVlan(cidr, description, VLANState.PENDING.name(), response.getString("VpcId"), name);
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during createVlan!", e);
            throw new InternalException(e);
        }
    }

    @Nonnull
    @Override
    public VLAN createVlan(@Nonnull VlanCreateOptions vco) throws CloudException, InternalException {
        return createVlan(vco.getCidr(), vco.getName(), vco.getDescription(), null, null, null);
    }

    @Override
    public VLANCapabilities getCapabilities() throws CloudException, InternalException {
        if (capabilities == null) {
            capabilities = new AliyunVlanCapabilities(getProvider());
        }
        return capabilities;
    }

    @Nonnull
    @Override
    @Deprecated
    public String getProviderTermForNetworkInterface(@Nonnull Locale locale) {
        return null;
    }

    @Nonnull
    @Override
    @Deprecated
    public String getProviderTermForSubnet(@Nonnull Locale locale) {
        return "VSwitch";
    }

    @Nonnull
    @Override
    @Deprecated
    public String getProviderTermForVlan(@Nonnull Locale locale) {
        return "VPC";
    }

    /**
     * Since Aliyun doesn't provider retrieving the default automatically created main routing table,
     * however the automatically created main table's create time should be the earliest
     * @param vlanId VPC ID
     * @return the earlier/main routing table for VPC
     * @throws CloudException
     * @throws InternalException
     */
    @Override
    public RoutingTable getRoutingTableForVlan(@Nonnull String vlanId) throws CloudException, InternalException {

        //retrieve routing table id
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        params.put("VpcId", vlanId);
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeVpcs", params);
        JSONObject response = method.get().asJson();
        String vrouterId = null;
        try {
            for (int i = 0; i < response.getJSONObject("Vpcs").getJSONArray("Vpc").length(); i++) {
                JSONObject vpc = response.getJSONObject("Vpcs").getJSONArray("Vpc").getJSONObject(i);
                vrouterId = vpc.getString("VRouterId");
                break;
            }
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during get routing tables for vlan!", e);
            throw new InternalException(e);
        }

        //retrieve routing table and routes
        params.remove("VpcId");
        params.put("VRouterId", vrouterId);
        params.put("PageSize", AliyunNetworkCommon.DefaultPageSize);
        int totalPageNumber = 1;
        int currentPageNumber = 1;
        Date currentMinCreateTime = null;
        RoutingTable routingTable = null;
        do {
            params.put("PageNumber", currentPageNumber);
            method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeRouteTables", params);
            response = method.get().asJson();
            try {
                for (int i = 0; i < response.getJSONObject("RouteTables").getJSONArray("RouteTable").length(); i++) {
                    JSONObject routingTableResponse = response.getJSONObject("RouteTables").getJSONArray("RouteTable").getJSONObject(i);
                    if (currentMinCreateTime == null ||
                            currentMinCreateTime.after(AliyunNetworkCommon.parseFromUTCString(routingTableResponse.getString("CreationTime")))) {
                        currentMinCreateTime = AliyunNetworkCommon.parseFromUTCString(routingTableResponse.getString("CreationTime"));
                        routingTable = toRoutingTable(routingTableResponse);
                        routingTable.setMain(true);
                        routingTable.setProviderVlanId(vlanId);
                    }
                }
                totalPageNumber = response.getInt("TotalCount") / AliyunNetworkCommon.DefaultPageSize +
                        response.getInt("TotalCount") % AliyunNetworkCommon.DefaultPageSize > 0 ? 1 : 0;
                currentPageNumber++;
            } catch (JSONException e) {
                stdLogger.error("An exception occurs during retrieve route tables!", e);
                throw new InternalException(e);
            }
        } while (currentPageNumber < totalPageNumber);
        return routingTable;
    }

    /**
     * Since Aliyun only support search routing table by both router id and routing table id,
     * so first search routing table id associated router id.
     * Note: left main field to false.
     * @param id routing table id
     * @return routing table instance
     * @throws CloudException
     * @throws InternalException
     */
    @Override
    public RoutingTable getRoutingTable(@Nonnull String id) throws CloudException, InternalException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        params.put("PageSize", AliyunNetworkCommon.DefaultPageSize);

        String routerId = null;
        String vpcId = null;
        int totalPageNumber = 1;
        int currentPageNumber = 1;
        do {
            params.put("PageNumber", currentPageNumber);
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeVRouters", params);
            JSONObject response = method.get().asJson();
            try {
                for (int i = 0; i < response.getJSONObject("VRouters").getJSONArray("VRouter").length(); i++) {
                    JSONObject router = response.getJSONObject("VRouters").getJSONArray("VRouter").getJSONObject(i);
                    for (int j = 0; j < router.getJSONObject("RouteTableIds").getJSONArray("RouteTableId").length(); j++) {
                        String routingTableId = router.getJSONObject("RouteTableIds").getJSONArray("RouteTableId").getString(j);
                        if (routingTableId.equals(id)) {
                            //find matched routing table
                            routerId = router.getString("VRouterId");
                            vpcId = router.getString("VpcId");
                            break;
                        }
                    }
                }
                totalPageNumber = response.getInt("TotalCount") / AliyunNetworkCommon.DefaultPageSize +
                        response.getInt("TotalCount") % AliyunNetworkCommon.DefaultPageSize > 0 ? 1 : 0;
                currentPageNumber++;
            } catch (JSONException e) {
                stdLogger.error("An exception occurs during get routing table!", e);
                throw new InternalException(e);
            }
        } while (currentPageNumber < totalPageNumber);

        if (!AliyunNetworkCommon.isEmpty(routerId)) {
            params = new HashMap<String, Object>();
            params.put("RegionId", getContext().getRegionId());
            params.put("VRouterId", routerId);
            params.put("RouteTableId", id);
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeRouteTables", params);
            JSONObject response = method.get().asJson();
            try {
                for (int i = 0; i < response.getJSONObject("RouteTables").getJSONArray("RouteTable").length(); i++) {
                    JSONObject routingTableResponse = response.getJSONObject("RouteTables").getJSONArray("RouteTable").getJSONObject(i);
                    RoutingTable routingTable = toRoutingTable(routingTableResponse);
                    routingTable.setProviderVlanId(vpcId);
                    return routingTable;
                }
            } catch (JSONException e) {
                stdLogger.error("An exception occurs during get routing table!", e);
                throw new InternalException(e);
            }
        } else {
            throw new InternalException("Not find the matching router for routing table " + id);
        }
        return null;
    }

    @Override
    public Subnet getSubnet(@Nonnull String subnetId) throws CloudException, InternalException {
        String vlanId = getAssociatedVlanId(subnetId);
        if (!AliyunNetworkCommon.isEmpty(vlanId)) {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("VpcId", vlanId);
            params.put("VSwitchId", subnetId);
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeVSwitches", params);
            JSONObject response = method.get().asJson();
            try {
                for (int i = 0; i < response.getJSONObject("VSwitches").getJSONArray("VSwitch").length(); i++) {
                    JSONObject subnetResponse = response.getJSONObject("VSwitches").getJSONArray("VSwitch").getJSONObject(i);
                    return toSubnet(subnetResponse);
                }
            } catch (JSONException e) {
                stdLogger.error("An exception occurs during get subnet!", e);
                throw new InternalException(e);
            }
        } else {
            throw new InternalException("Cannot find associated vlan id for subnet " + subnetId);
        }
        return null;
    }

    @Override
    public VLAN getVlan(@Nonnull String vlanId) throws CloudException, InternalException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        params.put("VpcId", vlanId);
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeVpcs", params);
        JSONObject response = method.get().asJson();
        try {
            for (int i = 0; i < response.getJSONObject("Vpcs").getJSONArray("Vpc").length(); i++) {
                JSONObject vlanResponse = response.getJSONObject("Vpcs").getJSONArray("Vpc").getJSONObject(i);
                return toVlan(vlanResponse);
            }
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during parse response to vpc instance!", e);
            throw new InternalException(e);
        }
        return super.getVlan(vlanId);
    }

    /**
     * VLAN resource contains: RoutingTable, VSwitch, Vpc
     * @param inVlanId Vpc ID
     * @return resource list contains resource id and status
     * @throws CloudException
     * @throws InternalException
     */
    @Nonnull
    @Override
    public Iterable<Networkable> listResources(@Nonnull String inVlanId) throws CloudException, InternalException {
        //TODO
        return super.listResources(inVlanId);
    }

    /**
     * Note: not filled the main field
     * @param vlanId VPC ID
     * @return routing table list
     * @throws CloudException
     * @throws InternalException
     */
    @Nonnull
    @Override
    public Iterable<RoutingTable> listRoutingTablesForVlan(@Nonnull String vlanId) throws CloudException, InternalException {
        //find router and associated routing tables
        String associatedVRouterId = getAssociatedVRouterId(vlanId);
        List<RoutingTable> routingTables = new ArrayList<RoutingTable>();
        if (!AliyunNetworkCommon.isEmpty(associatedVRouterId)) {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("VRouterId", associatedVRouterId);
            params.put("PageSize", AliyunNetworkCommon.DefaultPageSize);
            int totalPageNumber = 1;
            int currentPageNumber = 1;
            do {
                params.put("PageNumber", currentPageNumber);
                AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeRouteTables", params);
                try {
                    JSONObject response = method.get().asJson();
                    for (int i = 0; i < response.getJSONObject("RouteTables").getJSONArray("RouteTable").length(); i++) {
                        JSONObject routingTableResponse = response.getJSONObject("RouteTables").getJSONArray("RouteTable").getJSONObject(i);
                        RoutingTable routingTable = toRoutingTable(routingTableResponse);
                        routingTable.setProviderVlanId(vlanId);
                        routingTables.add(routingTable);
                    }
                    totalPageNumber = response.getInt("TotalCount") / AliyunNetworkCommon.DefaultPageSize +
                            response.getInt("TotalCount") % AliyunNetworkCommon.DefaultPageSize > 0 ? 1 : 0;
                    currentPageNumber++;
                } catch (JSONException e) {
                    stdLogger.error("An exception occurs during listing routing tables for vlan " + vlanId, e);
                    throw new InternalException(e);
                }
            } while (currentPageNumber < totalPageNumber);
            return routingTables;
        } else {
            throw new InternalException("Cannot find associated vrouter id for vlan " + vlanId);
        }
    }

    @Nonnull
    @Override
    public Iterable<Subnet> listSubnets(@Nullable String vlanId) throws CloudException, InternalException {
        return super.listSubnets(vlanId);
    }

    @Nonnull
    @Override
    public Iterable<ResourceStatus> listVlanStatus() throws CloudException, InternalException {
        return super.listVlanStatus();
    }

    @Nonnull
    @Override
    public Iterable<VLAN> listVlans() throws CloudException, InternalException {
        return super.listVlans();
    }

    @Override
    public void removeRoute(@Nonnull String routingTableId, @Nonnull String destinationCidr) throws CloudException, InternalException {
        super.removeRoute(routingTableId, destinationCidr);
    }

    @Override
    public void removeRoutingTable(@Nonnull String routingTableId) throws CloudException, InternalException {
        super.removeRoutingTable(routingTableId);
    }

    @Override
    public void removeSubnet(String providerSubnetId) throws CloudException, InternalException {
        super.removeSubnet(providerSubnetId);
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        return true;
    }



    private String getAssociatedVRouterId (String vlanId) throws InternalException, CloudException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        params.put("VpcId", vlanId);
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeVpcs", params);
        try {
            JSONObject response  = method.get().asJson();
            for (int i = 0; i < response.getJSONObject("Vpcs").getJSONArray("Vpc").length(); i++) {
                JSONObject vlan = response.getJSONObject("Vpcs").getJSONArray("Vpc").getJSONObject(i);
                return vlan.getString("VRouterId");
            }
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during list routing tables for vlan!", e);
            throw new InternalException(e);
        }
        return null;
    }

    private String getAssociatedVlanId (String subnetId) throws InternalException, CloudException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        int totalPageNumber = 1;
        int currentPageNumber = 1;
        do {
            params.put("PageNumber", currentPageNumber);
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeVpcs", params);
            JSONObject response = method.get().asJson();
            try {
                for (int i = 0; i < response.getJSONObject("Vpcs").getJSONArray("Vpc").length(); i++) {
                    JSONObject vlanResponse = response.getJSONObject("Vpcs").getJSONArray("Vpc").getJSONObject(i);
                    for (int j = 0; j < vlanResponse.getJSONObject("VSwitchIds").getJSONArray("VSwitchId").length(); j++) {
                        String subnetIdResponse = vlanResponse.getJSONObject("VSwitchIds").getJSONArray("VSwitchId").getString(j);
                        if (subnetIdResponse.equals(subnetId)) {
                            return vlanResponse.getString("VpcId");
                        }
                    }
                }
            } catch (JSONException e) {
                stdLogger.error("An exception occurs during get associated vlan id by subnet id " + subnetId, e);
                throw new InternalException(e);
            }
        } while (currentPageNumber < totalPageNumber);
        return null;
    }

    private VLAN toVlan(String cidr, String description, String status, String vlanId, String vpcName) throws InternalException {
        VLAN vlan = new VLAN();
        vlan.setProviderOwnerId(getContext().getAccountNumber());
        if (!AliyunNetworkCommon.isEmpty(description)) {
            vlan.setDescription(description);
        }
        if (!AliyunNetworkCommon.isEmpty(cidr)) {
            vlan.setCidr(cidr);
        }
        if (status.toLowerCase().equals(VLANState.AVAILABLE.name().toLowerCase())) {
            vlan.setCurrentState(VLANState.AVAILABLE);
        } else if (status.toLowerCase().equals(VLANState.PENDING.name().toLowerCase())) {
            vlan.setCurrentState(VLANState.PENDING);
        } else {
            throw new OperationNotSupportedException("Aliyun supports vlan status AVAILABLE and PENDING only!");
        }
        if (!AliyunNetworkCommon.isEmpty(vpcName)) {
            vlan.setName(vpcName);
        }
        if (!AliyunNetworkCommon.isEmpty(vlanId)) {
            vlan.setProviderVlanId(vlanId);
        }
        vlan.setSupportedTraffic(IPVersion.IPV4);
        vlan.setVisibleScope(null); //TODO unknown
        return vlan;
    }

    private Subnet toSubnet (JSONObject response) throws InternalException {
        try {
            return Subnet.getInstance(getContext().getAccountNumber(), getContext().getRegionId(), response.getString("VpcId"),
                    response.getString("VSwitchId"), SubnetState.valueOf(response.getString("Status").toUpperCase()),
                    response.getString("VSwitchName"), response.getString("Description"), response.getString("CidrBlock"));
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during parsing response to subnet instance!", e);
            throw new InternalException(e);
        }
    }

    private VLAN toVlan(JSONObject response) throws InternalException {
        try {
            return toVlan(response.getString("CidrBlock"), response.getString("Description"), response.getString("Status"), response.getString("VpcId"), response.getString("VpcName"));
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during parsing response to vlan instance!", e);
            throw new InternalException(e);
        }
    }

    private RoutingTable toRoutingTable (JSONObject response) throws InternalException {
        RoutingTable routingTable = new RoutingTable();
        try {
            routingTable.setProviderOwnerId(getContext().getAccountNumber());
            routingTable.setProviderRegionId(getContext().getRegionId());
            routingTable.setProviderRoutingTableId(response.getString("RouteTableId"));
            List<Route> routes = new ArrayList<Route>();
            for (int i = 0; i < response.getJSONObject("RouteEntrys").getJSONArray("RouteEntry").length(); i++) {
                routes.add(toRoute(response.getJSONObject("RouteEntrys").getJSONArray("RouteEntry").getJSONObject(i)));
            }
            routingTable.setRoutes((Route[]) routes.toArray());
            return routingTable;
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during parsing response to routing table!", e);
            throw new InternalException(e);
        }
    }

    private Route toRoute (JSONObject response) throws InternalException {
        try {
            return Route.getRouteToVirtualMachine(IPVersion.IPV4, response.getString("DestinationCidrBlock"),
                    getContext().getAccountNumber(), response.getString("InstanceId"));
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during parsing response to route instance!", e);
            throw new InternalException(e);
        }
    }
}
