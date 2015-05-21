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
import org.dasein.cloud.compute.ComputeServices;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.compute.VirtualMachineSupport;
import org.dasein.cloud.network.*;
import org.dasein.util.Jiterator;
import org.dasein.util.JiteratorPopulator;
import org.dasein.util.PopulatorThread;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Created by Jane Wang on 5/13/2015.
 *
 * @author Jane Wang
 * @since 2015.05.01
 */
public class AliyunVlan extends AbstractVLANSupport<Aliyun> {

    private static final Logger stdLogger = Aliyun.getStdLogger(AliyunVlan.class);

    private transient volatile AliyunVlanCapabilities capabilities;

    protected AliyunVlan(Aliyun provider) {
        super(provider);
    }

    /**
     * Cidr block 100.64.0.0/10 in aliyun is reserved for use.
     * Note: in Aliyun the nextHopIp can be empty, however, this can only be created by the System.
     * @param routingTableId routing table id
     * @param version ip traffic version
     * @param destinationCidr destination cidr block
     * @param vmId virtual machine id
     * @return route instance (to virtual machine)
     * @throws CloudException not support traffic type except IPV4
     * @throws InternalException
     */
    @Override
    public Route addRouteToVirtualMachine(@Nonnull String routingTableId, @Nonnull IPVersion version, @Nullable String destinationCidr, @Nonnull String vmId) throws CloudException, InternalException {
        if (!version.equals(IPVersion.IPV4)) {
            throw new InternalException("Aliyun only support IPV4!");
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RouteTableId", routingTableId);
        params.put("DestinationCidrBlock", destinationCidr);
        if (!AliyunNetworkCommon.isEmpty(vmId)) {
            params.put("NextHopType", AliyunNetworkCommon.toUpperCaseFirstLetter(
                    AliyunNetworkCommon.AliyunRouteEntryNextHopType.INSTANCE.name().toLowerCase()));
            params.put("NextHopId", vmId);
        }
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "CreateRouteEntry", params);
        JSONObject response = method.post().asJson();
        getProvider().validateResponse(response);
        return Route.getRouteToVirtualMachine(IPVersion.IPV4, destinationCidr, getContext().getAccountNumber(), vmId);
    }

    @Nonnull
    @Override
    public Subnet createSubnet(@Nonnull SubnetCreateOptions options) throws CloudException, InternalException {
        Map<String, Object> params = new HashMap<String, Object>();
        if (!AliyunNetworkCommon.isEmpty(options.getProviderDataCenterId())) {
            params.put("ZoneId", options.getProviderDataCenterId());
        }
        if (!AliyunNetworkCommon.isEmpty(options.getCidr())) {
            params.put("CidrBlock", options.getCidr());
        }
        if (!AliyunNetworkCommon.isEmpty(options.getProviderVlanId())) {
            params.put("VpcId", options.getProviderVlanId());
        }
        if (!AliyunNetworkCommon.isEmpty(options.getName())) {
            params.put("VSwitchName", options.getName());
        }
        if (!AliyunNetworkCommon.isEmpty(options.getDescription())) {
            params.put("Description", options.getDescription());
        }
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "CreateVSwitch", params);
        JSONObject response = method.post().asJson();
        try {
            return Subnet.getInstance(getContext().getAccountNumber(), getContext().getRegionId(), options.getProviderVlanId(),
                    response.getString("VSwitchId"), SubnetState.PENDING, options.getName(), options.getDescription(), options.getCidr());
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during create subnet!", e);
            throw new InternalException(e);
        }
    }

    /**
     * Aliyun doesn't support domain name, dns servers, ntp servers for vlan.
     * @param cidr vlan cidr block
     * @param name name
     * @param description description
     * @param domainName unsupported
     * @param dnsServers unsupported
     * @param ntpServers unsupported
     * @return vlan instance
     * @throws CloudException invoke exception
     * @throws InternalException response parsing exception
     */
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
        return capabilities.getProviderTermForNetworkInterface(locale);
    }

    @Nonnull
    @Override
    @Deprecated
    public String getProviderTermForSubnet(@Nonnull Locale locale) {
        return capabilities.getProviderTermForSubnet(locale);
    }

    @Nonnull
    @Override
    @Deprecated
    public String getProviderTermForVlan(@Nonnull Locale locale) {
        return capabilities.getProviderTermForVlan(locale);
    }

    /**
     * Aliyun doesn't provider retrieving the default automatically created main routing table,
     * however the automatically created main table's create time should be the earliest.
     * @param vlanId VPC ID
     * @return the earlier/main routing table for VPC
     * @throws CloudException
     * @throws InternalException
     */
    @Override
    public RoutingTable getRoutingTableForVlan(@Nonnull String vlanId) throws CloudException, InternalException {
        String vrouterId = getAssociatedVRouterIdByVlan(vlanId);
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("VRouterId", vrouterId);
        params.put("PageSize", AliyunNetworkCommon.DefaultPageSize);
        int totalPageNumber = 1;
        int currentPageNumber = 1;
        Date currentMinCreateTime = null;
        RoutingTable mainRoutingTable = null;
        do {
            params.put("PageNumber", currentPageNumber);
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeRouteTables", params);
            JSONObject response = method.get().asJson();
            try {
                for (int i = 0; i < response.getJSONObject("RouteTables").getJSONArray("RouteTable").length(); i++) {
                    JSONObject routingTableResponse = response.getJSONObject("RouteTables").getJSONArray("RouteTable").getJSONObject(i);
                    if (currentMinCreateTime == null ||
                            currentMinCreateTime.after(AliyunNetworkCommon.parseFromUTCString(routingTableResponse.getString("CreationTime")))) {
                        currentMinCreateTime = AliyunNetworkCommon.parseFromUTCString(routingTableResponse.getString("CreationTime"));
                        mainRoutingTable = toRoutingTable(routingTableResponse, vlanId, true);
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
        return mainRoutingTable;
    }

    /**
     * Since Aliyun only support search routing table by both router id and routing table id,
     * so first search routing table id associated router id.
     * Note: since Aliyun doesn't support create routing table API, so only one routing table
     *       for vlan will exist created during the vlan creation.
     * @param id routing table id
     * @return routing table instance
     * @throws CloudException
     * @throws InternalException
     */
    @Override
    public RoutingTable getRoutingTable(@Nonnull String id) throws CloudException, InternalException {
        // retrieve router id and vpc id by routing table id through invoking DescribeVRouters
        Map<String, String> idsMap = getAssociatedIdsByRoutingTable(id);
        String routerId = idsMap.get("VRouterId");
        String vpcId = idsMap.get("VpcId");

        // get routing table instance by router id and routing table id. and use vpcId (retrieve above) to set the vpc field.
        if (!AliyunNetworkCommon.isEmpty(routerId)) {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("VRouterId", routerId);
            params.put("RouteTableId", id);
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeRouteTables", params);
            JSONObject response = method.get().asJson();
            try {
                for (int i = 0; i < response.getJSONObject("RouteTables").getJSONArray("RouteTable").length(); i++) {
                    JSONObject routingTableResponse = response.getJSONObject("RouteTables").getJSONArray("RouteTable").getJSONObject(i);
                    return toRoutingTable(routingTableResponse, vpcId, true);
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
        String vlanId = getAssociatedVlanIdBySubnet(subnetId);
        if (!AliyunNetworkCommon.isEmpty(vlanId)) {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("VpcId", vlanId);
            params.put("VSwitchId", subnetId);
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeVSwitches", params);
            JSONObject response = method.get().asJson();
            try {
                for (int i = 0; i < response.getJSONObject("VSwitches").getJSONArray("VSwitch").length(); i++) {
                    return toSubnet(response.getJSONObject("VSwitches").getJSONArray("VSwitch").getJSONObject(i));
                }
            } catch (JSONException e) {
                stdLogger.error("An exception occurs during get subnet!", e);
                throw new InternalException(e);
            }
            return null;
        } else {
            throw new InternalException("Cannot find associated vlan id for subnet " + subnetId);
        }
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
     * VLAN resource contains: Subnet/VSwitch, RoutingTable, SecurityGroup/Firewall, Load Balancers, VMs
     * @param inVlanId Vpc ID
     * @return resource list contains resource id and status
     * @throws CloudException
     * @throws InternalException
     */
    @Nonnull
    @Override
    public Iterable<Networkable> listResources(@Nonnull final String inVlanId) throws CloudException, InternalException {
        getProvider().hold();
        PopulatorThread<Networkable> populator = new PopulatorThread<Networkable>(new JiteratorPopulator<Networkable>() {
            @Override
            public void populate(@Nonnull Jiterator<Networkable> iterator) throws Exception {
                NetworkServices service = getProvider().getNetworkServices();
                if (service != null) {
                    //IP Address Support
                    IpAddressSupport ipAddressSupport = service.getIpAddressSupport();
                    if (ipAddressSupport != null) {
                        for (IpAddress ipAddress : ipAddressSupport.listIpPool(IPVersion.IPV4, false)) {
                            iterator.push(ipAddress);
                        }
                    }
                    //Security Group/Firewall Support
                    FirewallSupport firewallSupport = service.getFirewallSupport();
                    if (firewallSupport != null) {
                        for (Firewall firewall : firewallSupport.list()) {
                            iterator.push(firewall);
                        }
                    }
                    //Routing Table
                    for (RoutingTable routingTable : listRoutingTablesForVlan(inVlanId)) {
                        iterator.push(routingTable);
                    }
                    //Subnet
                    for (Subnet subnet: listSubnets(inVlanId)) {
                        iterator.push(subnet);
                    }
                    //Load Balancers
                    LoadBalancerSupport loadBalancerSupport = service.getLoadBalancerSupport();
                    for (LoadBalancer loadBalancer : loadBalancerSupport.listLoadBalancers()) {
                        iterator.push(loadBalancer);
                    }
                }
                //VMs
                ComputeServices computeService = getProvider().getComputeServices();
                if (computeService != null) {
                    VirtualMachineSupport virtualMachineSupport = computeService.getVirtualMachineSupport();
                    if (virtualMachineSupport != null) {
                        for (VirtualMachine virtualMachine : virtualMachineSupport.listVirtualMachines()) {
                            iterator.push(virtualMachine);
                        }
                    }
                }
            }
        });
        populator.populate();
        return populator.getResult();
    }

    /**
     * Retrieve vlan associated routing tables.
     * Note: set main field to false
     * @param vlanId VPC ID
     * @return routing table list
     * @throws CloudException
     * @throws InternalException
     */
    @Nonnull
    @Override
    public Iterable<RoutingTable> listRoutingTablesForVlan(@Nonnull String vlanId) throws CloudException, InternalException {
        //find router and associated routing tables
        String associatedVRouterId = getAssociatedVRouterIdByVlan(vlanId);
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
                        routingTables.add(toRoutingTable(routingTableResponse, vlanId, false));
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
        List<Subnet> subnets = new ArrayList<Subnet>();
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("VpcId", vlanId);
        params.put("PageSize", AliyunNetworkCommon.DefaultPageSize);
        int totalPageNumber = 1;
        int currentPageNumber = 1;
        do {
            params.put("PageNumber", currentPageNumber);
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeVSwitches", params);
            JSONObject response = method.get().asJson();
            try {
                for (int i = 0; i < response.getJSONObject("VSwitches").getJSONArray("VSwitch").length(); i++) {
                    subnets.add(toSubnet(response.getJSONObject("VSwitches").getJSONArray("VSwitch").getJSONObject(i)));
                }
                totalPageNumber = response.getInt("TotalCount") / AliyunNetworkCommon.DefaultPageSize +
                        response.getInt("TotalCount") % AliyunNetworkCommon.DefaultPageSize > 0 ? 1 : 0;
                currentPageNumber++;
            } catch (JSONException e) {
                stdLogger.error("An exception occurs during list subnets!", e);
                throw new InternalException(e);
            }
        } while (currentPageNumber < totalPageNumber);
        return subnets;
    }

    @Nonnull
    @Override
    public Iterable<ResourceStatus> listVlanStatus() throws CloudException, InternalException {
        List<ResourceStatus> resourceStatus = new ArrayList<ResourceStatus>();
        Iterator<VLAN> vlanIter = listVlans().iterator();
        while (vlanIter.hasNext()) {
            VLAN vlan = vlanIter.next();
            resourceStatus.add(new ResourceStatus(vlan.getProviderVlanId(), vlan.getCurrentState()));
        }
        return resourceStatus;
    }

    @Nonnull
    @Override
    public Iterable<VLAN> listVlans() throws CloudException, InternalException {
        List<VLAN> vlans = new ArrayList<VLAN>();
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        params.put("PageSize", AliyunNetworkCommon.DefaultPageSize);
        int totalPageNumber = 1;
        int currentPageNumber = 1;
        do {
            params.put("PageNumber", currentPageNumber);
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeVpcs", params);
            JSONObject response = method.get().asJson();
            try {
                for (int i = 0; i < response.getJSONObject("Vpcs").getJSONArray("Vpc").length(); i++) {
                    vlans.add(toVlan(response.getJSONObject("Vpcs").getJSONArray("Vpc").getJSONObject(i)));
                }
                totalPageNumber = response.getInt("TotalCount") / AliyunNetworkCommon.DefaultPageSize +
                        response.getInt("TotalCount") % AliyunNetworkCommon.DefaultPageSize > 0 ? 1 : 0;
                currentPageNumber++;
            } catch (JSONException e) {
                stdLogger.error("An exception occurs during list vlans!", e);
                throw new InternalException(e);
            };
        } while (currentPageNumber < totalPageNumber);
        return vlans;
    }

    /**
     * Aliyun provider remove route by routingTableId, destinationCidr and nextHopId, however dasein provider only two of them.
     * In fact, the destinationCidr could be unique for different route. So here, search for all route match routing table id and destination cidr.
     * If the result set contains more than one route, throw out internal exception for warning.
     * Note: TODO check with System created route cannot be removed, will only remove Custom route.
     * @param routingTableId
     * @param destinationCidr
     * @throws CloudException
     * @throws InternalException
     */
    @Override
    public void removeRoute(@Nonnull String routingTableId, @Nonnull String destinationCidr) throws CloudException, InternalException {
        //search routes by routing table and destination cidr
        String vrouterId = getAssociatedVRouteIdByRoutingTable(routingTableId);
        String nextHopId = null;
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("VRouterId", vrouterId);
        params.put("RouteTableId", routingTableId);
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeRouteTables", params);
        JSONObject response = method.get().asJson();
        try {
            JSONArray routeEntrys = response.getJSONObject("RouteEntrys").getJSONArray("RouteEntry");
            for (int i = 0; i < routeEntrys.length(); i++) {
                JSONObject routeEntry = routeEntrys.getJSONObject(i);
                if (routeEntry.getString("DestinationCidrBlock").trim().equals(destinationCidr.trim())
                        && routeEntry.getString("Type").toUpperCase().equals(AliyunNetworkCommon.AliyunRouteType.CUSTOM.name().toUpperCase())) {
                    nextHopId = routeEntry.getString("InstanceId");
                    break;
                }
            }
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during remove route!", e);
            throw new InternalException(e);
        }

        if (!AliyunNetworkCommon.isEmpty(nextHopId)) {
            params = new HashMap<String, Object>();
            params.put("RouteTableId", routingTableId);
            params.put("DestinationCidrBlock", destinationCidr);
            params.put("NextHopId", nextHopId);
            method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DeleteRouteEntry", params);
            response = method.post().asJson();
            getProvider().validateResponse(response);
        }
    }

    @Override
    public void removeRoutingTable(@Nonnull String routingTableId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Aliyun doesn't support delete routing table, and it will be deleted automatically when the associated VPC is deleted!");
    }

    @Override
    public void removeSubnet(String providerSubnetId) throws CloudException, InternalException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("VSwitchId", providerSubnetId);
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DeleteVSwitch", params);
        JSONObject response = method.post().asJson();
        getProvider().validateResponse(response);
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        return true;
    }

    /**
     * retrieve routing table associated resource ids
     * @param routingTableId routing table id
     * @return map contains(key:value): "VlanId": vlan Id; "VRouterId": vrouter id
     * @throws InternalException
     * @throws CloudException
     */
    private Map<String, String> getAssociatedIdsByRoutingTable (String routingTableId) throws InternalException, CloudException {
        Map<String, String> idsMap = new HashMap<String, String>();
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        int totalPageNumber = 1;
        int currentPageNumber = 1;
        do {
            params.put("PageNumber", currentPageNumber);
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeVRouters", params);
            JSONObject response = method.get().asJson();
            try {
                for (int i = 0; i < response.getJSONObject("VRouters").getJSONArray("VRouter").length(); i++) {
                    JSONObject vrouter = response.getJSONObject("VRouters").getJSONArray("VRouter").getJSONObject(i);
                    for (int j = 0; j < vrouter.getJSONObject("RouteTableIds").getJSONArray("RouteTableId").length(); j++) {
                        String rtId = vrouter.getJSONObject("RouteTableIds").getJSONArray("RouteTableId").getString(j);
                        if (rtId.equals(routingTableId)) {
                            if (!AliyunNetworkCommon.isEmpty(vrouter.getString("VpcId"))) {
                                idsMap.put("VlanId", vrouter.getString("VpcId"));
                            }
                            if (!AliyunNetworkCommon.isEmpty(vrouter.getString("VRouterId"))) {
                                idsMap.put("VRouterId", vrouter.getString("VRouterId"));
                            }
                            return idsMap;
                        }
                    }
                }
                totalPageNumber = response.getInt("TotalCount") / AliyunNetworkCommon.DefaultPageSize +
                        response.getInt("TotalCount") % AliyunNetworkCommon.DefaultPageSize > 0 ? 1 : 0;
                currentPageNumber++;
            } catch (JSONException e) {
                stdLogger.error("An exception occurs during list routing tables for vlan!", e);
                throw new InternalException(e);
            }
        } while (currentPageNumber < totalPageNumber);
        return null;
    }

    private String getAssociatedVRouteIdByRoutingTable(String routingTableId) throws InternalException, CloudException {
        return getAssociatedIdsByRoutingTable(routingTableId).get("VRouterId");
    }

    private String getAssociatedVRouterIdByVlan (String vlanId) throws InternalException, CloudException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        params.put("VpcId", vlanId);
        int totalPageNumber = 1;
        int currentPageNumber = 1;
        do {
            params.put("PageNumber", currentPageNumber);
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeVpcs", params);
            JSONObject response = method.get().asJson();
            try {
                for (int i = 0; i < response.getJSONObject("Vpcs").getJSONArray("Vpc").length(); i++) {
                    JSONObject vlan = response.getJSONObject("Vpcs").getJSONArray("Vpc").getJSONObject(i);
                    String vpcId = vlan.getString("VpcId");
                    if (vpcId.equals(vlanId)) {
                        return vlan.getString("VRouterId");
                    }
                }
                totalPageNumber = response.getInt("TotalCount") / AliyunNetworkCommon.DefaultPageSize +
                        response.getInt("TotalCount") % AliyunNetworkCommon.DefaultPageSize > 0 ? 1 : 0;
                currentPageNumber++;
            } catch (JSONException e) {
                stdLogger.error("An exception occurs during list routing tables for vlan!", e);
                throw new InternalException(e);
            }
        } while (currentPageNumber < totalPageNumber);
        return null;
    }

    private String getAssociatedVlanIdBySubnet (String subnetId) throws InternalException, CloudException {
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
                        String vswitchId = vlanResponse.getJSONObject("VSwitchIds").getJSONArray("VSwitchId").getString(j);
                        if (vswitchId.equals(subnetId)) {
                            return vlanResponse.getString("VpcId");
                        }
                    }
                }
                totalPageNumber = response.getInt("TotalCount") / AliyunNetworkCommon.DefaultPageSize +
                        response.getInt("TotalCount") % AliyunNetworkCommon.DefaultPageSize > 0 ? 1 : 0;
                currentPageNumber++;
            } catch (JSONException e) {
                stdLogger.error("An exception occurs during get associated vlan id by subnet id " + subnetId, e);
                throw new InternalException(e);
            }
        } while (currentPageNumber < totalPageNumber);
        return null;
    }

    private Subnet toSubnet (JSONObject response) throws InternalException {
        try {
            Subnet subnet = Subnet.getInstance(getContext().getAccountNumber(), getContext().getRegionId(), response.getString("VpcId"),
                    response.getString("VSwitchId"), SubnetState.valueOf(response.getString("Status").toUpperCase()),
                    response.getString("VSwitchName"), response.getString("Description"), response.getString("CidrBlock"));
            if (response.getInt("AvailableIpAddressCount") > 0) {
                subnet = subnet.withAvailableIpAddresses(response.getInt("AvailableIpAddressCount"));
            }
            if (!AliyunNetworkCommon.isEmpty("ZoneId")) {
                subnet = subnet.constrainedToDataCenter(response.getString("ZoneId"));
            }
            return subnet;
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during parsing response to subnet instance!", e);
            throw new InternalException(e);
        }
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
            stdLogger.warn("Unknown vlan status " + status);
            vlan.setCurrentState(VLANState.PENDING);
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

    private VLAN toVlan(JSONObject response) throws InternalException {
        try {
            return toVlan(response.getString("CidrBlock"), response.getString("Description"),
                    response.getString("Status"), response.getString("VpcId"), response.getString("VpcName"));
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during parsing response to vlan instance!", e);
            throw new InternalException(e);
        }
    }

    /**
     * toRoutingTable can generate a basic routing table instance, however additional fields should be set by the caller,
     * such as provider vlan id, main (true/false).
     * @param response JSONObject which represents a routing table object
     * @return basic routing table instance
     * @throws InternalException
     */
    private RoutingTable toRoutingTable (JSONObject response, String vlanId, Boolean isMain) throws InternalException {
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
            if (!AliyunNetworkCommon.isEmpty(vlanId)) {
                routingTable.setProviderVlanId(vlanId);
            }
            if (isMain) {
                routingTable.setMain(isMain);
            }
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
