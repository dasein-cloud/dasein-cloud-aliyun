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

    //TODO check VPN support
    @Override
    public Route addRouteToGateway(@Nonnull String routingTableId, @Nonnull IPVersion version, @Nullable String destinationCidr, @Nonnull String gatewayId) throws CloudException, InternalException {
        if (!version.equals(IPVersion.IPV4)) {
            throw new InternalException("Aliyun supports IPV4 only!");
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RouteTableId", routingTableId);
        params.put("DestinationCidrBlock", destinationCidr);
        if (!AliyunNetworkCommon.isEmpty(gatewayId)) {
            params.put("NextHopType", AliyunNetworkCommon.toUpperCaseFirstLetter(
                    AliyunNetworkCommon.AliyunRouteEntryNextHopType.INSTANCE.name().toLowerCase()));
            params.put("NextHopId", gatewayId);
        } else {
            stdLogger.warn("Add route to gateway, you must specify the gateway ID!");
            throw new InternalException("Add route to gateway, you must specify the gateway ID!");
        }
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "CreateRouteEntry", params, true);
        JSONObject response = method.post().asJson();
        getProvider().validateResponse(response);
        return Route.getRouteToGateway(IPVersion.IPV4, destinationCidr, gatewayId);
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
            throw new InternalException("Aliyun supports IPV4 only!");
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RouteTableId", routingTableId);
        params.put("DestinationCidrBlock", destinationCidr);
        if (!AliyunNetworkCommon.isEmpty(vmId)) {
            params.put("NextHopType", AliyunNetworkCommon.toUpperCaseFirstLetter(
                    AliyunNetworkCommon.AliyunRouteEntryNextHopType.INSTANCE.name().toLowerCase()));
            params.put("NextHopId", vmId);
        } else {
            stdLogger.warn("Add route to Virtual Machine, you must specify the VM instance ID!");
            throw new InternalException("Add route to Virtual Machine, you must specify the VM instance ID!");
        }
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "CreateRouteEntry", params, true);
        JSONObject response = method.post().asJson();
        getProvider().validateResponse(response);
        return Route.getRouteToVirtualMachine(IPVersion.IPV4, destinationCidr, getContext().getAccountNumber(), vmId);
    }

    @Nonnull
    @Override
    public Subnet createSubnet(@Nonnull SubnetCreateOptions options) throws CloudException, InternalException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ZoneId", options.getProviderDataCenterId());
        params.put("CidrBlock", options.getCidr());
        params.put("VpcId", options.getProviderVlanId());
        if (!AliyunNetworkCommon.isEmpty(options.getName())) {
            params.put("VSwitchName", options.getName());
        }
        if (!AliyunNetworkCommon.isEmpty(options.getDescription())) {
            params.put("Description", options.getDescription());
        }
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "CreateVSwitch", params, true);
        JSONObject response = method.post().asJson();
        try {
            return getSubnet(response.getString("VSwitchId"));
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
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "CreateVpc", params, true);
        JSONObject response = method.post().asJson();
        try {
            return getVlan(response.getString("VpcId"));
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during createVlan!", e);
            throw new InternalException(e);
        }
    }

    @Nonnull
    @Override
    public VLAN createVlan(@Nonnull VlanCreateOptions vco) throws CloudException, InternalException {
        return createVlan(vco.getCidr(), vco.getName(), vco.getDescription(), vco.getDomain(), vco.getDnsServers(), vco.getNtpServers());
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
     * In Aliyun, a vlan has only one vroute and one routing table, and they are created automatically during the creation of vlan.
     * @param vlanId VPC ID
     * @return the earlier/main routing table for VPC
     * @throws CloudException
     * @throws InternalException
     */
    @Override
    public RoutingTable getRoutingTableForVlan(@Nonnull String vlanId) throws CloudException, InternalException {
        String vrouterId = getAssociatedVRouterIdByVlan(vlanId);
        if (!AliyunNetworkCommon.isEmpty(vrouterId)) {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("VRouterId", vrouterId);
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeRouteTables", params);
            JSONObject response = method.get().asJson();
            try {
                JSONArray routingTables = response.getJSONObject("RouteTables").getJSONArray("RouteTable");
                for (int i = 0; i < routingTables.length(); i++) {
                    return toRoutingTable(routingTables.getJSONObject(i), vlanId);
                }
                return null;
            } catch (JSONException e) {
                stdLogger.error("An exception occurs during retrieve route tables for vlan with id " + vlanId, e);
                throw new InternalException(e);
            }
        } else {
            throw new InternalException("Failed to find associated vrouter for vlan with id " + vlanId);
        }
    }

    /**
     * Since Aliyun only support search routing table by both router id and routing table id,
     * so first search routing table id associated router id.
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
                JSONArray routingTables = response.getJSONObject("RouteTables").getJSONArray("RouteTable");
                for (int i = 0; i < routingTables.length(); i++) {
                    return toRoutingTable(routingTables.getJSONObject(i), vpcId);
                }
                return null;
            } catch (JSONException e) {
                stdLogger.error("An exception occurs during get routing table with id " + id, e);
                throw new InternalException(e);
            }
        } else {
            throw new InternalException("Not find the matching router for routing table with id " + id);
        }
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
                JSONArray subnets = response.getJSONObject("VSwitches").getJSONArray("VSwitch");
                for (int i = 0; i < subnets.length(); i++) {
                    return toSubnet(subnets.getJSONObject(i));
                }
                return null;
            } catch (JSONException e) {
                stdLogger.error("An exception occurs during get subnet with id " + subnetId, e);
                throw new InternalException(e);
            }
        } else {
            throw new InternalException("Cannot find associated vlan id for subnet with id " + subnetId);
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
            JSONArray vlans = response.getJSONObject("Vpcs").getJSONArray("Vpc");
            for (int i = 0; i < vlans.length(); i++) {
                return toVlan(vlans.getJSONObject(i));
            }
            return null;
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during get vlan with id !" + vlanId, e);
            throw new InternalException(e);
        }
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
     * In Aliyun, one vlan has only one router and one routing table.
     * @param vlanId VPC ID
     * @return routing table list
     * @throws CloudException
     * @throws InternalException
     */
    @Nonnull
    @Override
    public Iterable<RoutingTable> listRoutingTablesForVlan(@Nonnull String vlanId) throws CloudException, InternalException {
        List<RoutingTable> routingTables = new ArrayList<RoutingTable>();
        routingTables.add(getRoutingTableForVlan(vlanId));
        return routingTables;
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
                JSONArray subnetsResponse = response.getJSONObject("VSwitches").getJSONArray("VSwitch");
                for (int i = 0; i < subnetsResponse.length(); i++) {
                    subnets.add(toSubnet(subnetsResponse.getJSONObject(i)));
                }
                totalPageNumber = response.getInt("TotalCount") / AliyunNetworkCommon.DefaultPageSize +
                        response.getInt("TotalCount") % AliyunNetworkCommon.DefaultPageSize > 0 ? 1 : 0;
                currentPageNumber++;
            } catch (JSONException e) {
                stdLogger.error("An exception occurs during list subnets for vlan with id " + vlanId, e);
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
                JSONArray vlansResponse = response.getJSONObject("Vpcs").getJSONArray("Vpc");
                for (int i = 0; i < vlansResponse.length(); i++) {
                    vlans.add(toVlan(vlansResponse.getJSONObject(i)));
                }
                totalPageNumber = response.getInt("TotalCount") / AliyunNetworkCommon.DefaultPageSize +
                        response.getInt("TotalCount") % AliyunNetworkCommon.DefaultPageSize > 0 ? 1 : 0;
                currentPageNumber++;
            } catch (JSONException e) {
                stdLogger.error("An exception occurs during list vlans!", e);
                throw new InternalException(e);
            }
        } while (currentPageNumber < totalPageNumber);
        return vlans;
    }

    /**
     * Aliyun provider remove route by routingTableId, destinationCidr and nextHopId, however dasein provider only two of them.
     * In fact, the destinationCidr could be unique for different route. So here, search for all route match routing table id and destination cidr.
     * If the result set contains more than one route, throw out internal exception for warning.
     * @param routingTableId
     * @param destinationCidr
     * @throws CloudException
     * @throws InternalException
     */
    @Override
    public void removeRoute(@Nonnull String routingTableId, @Nonnull String destinationCidr) throws CloudException, InternalException {
        //search routes by routing table and destination cidr
        RoutingTable routingTable = getRoutingTable(routingTableId);
        String nextHopId = null;
        for (Route route : routingTable.getRoutes()) {
            if (route.getDestinationCidr().equals(destinationCidr)) { //TODO unique cidr
                if (!AliyunNetworkCommon.isEmpty(route.getGatewayVirtualMachineId())) {
                    nextHopId = route.getGatewayVirtualMachineId();
                } else if (!AliyunNetworkCommon.isEmpty(route.getGatewayId())) {
                    nextHopId = route.getGatewayId();
                }
                break;
            }
        }

        //remove route from routing table
        if (!AliyunNetworkCommon.isEmpty(nextHopId)) {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("RouteTableId", routingTableId);
            params.put("DestinationCidrBlock", destinationCidr);
            params.put("NextHopId", nextHopId);
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DeleteRouteEntry", params);
            getProvider().validateResponse(method.post().asJson());
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
                throw new InternalException(e);
            }
        } while (currentPageNumber < totalPageNumber);
        return null;
    }

    private String getAssociatedVRouterIdByVlan (String vlanId) throws InternalException, CloudException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        params.put("VpcId", vlanId);
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeVpcs", params);
        JSONObject response = method.get().asJson();
        try {
            JSONArray vpcsResponse = response.getJSONObject("Vpcs").getJSONArray("Vpc");
            for (int i = 0; i < vpcsResponse.length(); i++) {
                JSONObject vlan = vpcsResponse.getJSONObject(i);
                return vlan.getString("VRouterId");
            }
            return null;
        } catch (JSONException e) {
            throw new InternalException(e);
        }
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
                JSONArray vlansResponse = response.getJSONObject("Vpcs").getJSONArray("Vpc");
                for (int i = 0; i < vlansResponse.length(); i++) {
                    JSONArray subnetsResponse = vlansResponse.getJSONObject(i).getJSONObject("VSwitchIds").getJSONArray("VSwitchId");
                    for (int j = 0; j < subnetsResponse.length(); j++) {
                        if (subnetsResponse.getString(j).equals(subnetId)) {
                            return vlansResponse.getJSONObject(i).getString("VpcId");
                        }
                    }
                }
                totalPageNumber = response.getInt("TotalCount") / AliyunNetworkCommon.DefaultPageSize +
                        response.getInt("TotalCount") % AliyunNetworkCommon.DefaultPageSize > 0 ? 1 : 0;
                currentPageNumber++;
            } catch (JSONException e) {
                throw new InternalException(e);
            }
        } while (currentPageNumber < totalPageNumber);
        return null;
    }

    private Subnet toSubnet (JSONObject response) throws InternalException {
        try {
            //cidr, currentState, description, name, providerOwnerId, providerRegionId, providerSubnetId, providerVlanId
            Subnet subnet = Subnet.getInstance(getContext().getAccountNumber(), getContext().getRegionId(), response.getString("VpcId"),
                    response.getString("VSwitchId"), SubnetState.valueOf(response.getString("Status").toUpperCase()),
                    response.getString("VSwitchName"), response.getString("Description"), response.getString("CidrBlock"));
            //availableIpAddresses
            if (response.getInt("AvailableIpAddressCount") > 0) {
                subnet = subnet.withAvailableIpAddresses(response.getInt("AvailableIpAddressCount"));
            }
            //providerDataCenterId
            if (!AliyunNetworkCommon.isEmpty("ZoneId")) {
                subnet = subnet.constrainedToDataCenter(response.getString("ZoneId"));
            }
            //supportedTraffic
            subnet.supportingTraffic(IPVersion.IPV4);
            return subnet;
        } catch (JSONException e) {
            throw new InternalException(e);
        }
    }

    private VLAN toVlan(JSONObject response) throws InternalException {
        try {
            VLAN vlan = new VLAN();
            vlan.setCidr(response.getString("CidrBlock"));
            vlan.setCurrentState(VLANState.valueOf(response.getString("Status").toUpperCase()));
            vlan.setDescription(response.getString("Description"));
            vlan.setName(response.getString("VpcName"));
            vlan.setProviderOwnerId(getContext().getAccountNumber());
            vlan.setProviderRegionId(response.getString("RegionId"));
            vlan.setProviderVlanId(response.getString("VpcId"));
            vlan.setSupportedTraffic(IPVersion.IPV4);
            vlan.setVisibleScope(VisibleScope.ACCOUNT_REGION);
            return vlan;
        } catch (JSONException e) {
            throw new InternalException(e);
        }
    }

    /**
     * toRoutingTable can generate a basic routing table instance, however additional fields should be set by the caller,
     * One vlan can have one vrouter and one routing table.
     * such as provider vlan id.
     * @param response JSONObject which represents a routing table object
     * @return basic routing table instance
     * @throws InternalException
     */
    private RoutingTable toRoutingTable (JSONObject response, String vlanId) throws InternalException {
        RoutingTable routingTable = new RoutingTable();
        try {
            //TODO: check if description and name is a must
            routingTable.setMain(true);
            routingTable.setProviderOwnerId(getContext().getAccountNumber());
            routingTable.setProviderRegionId(getContext().getRegionId());
            routingTable.setProviderRoutingTableId(response.getString("RouteTableId"));
            routingTable.setProviderVlanId(vlanId);
            List<Route> routes = new ArrayList<Route>();
            JSONArray routesResponse = response.getJSONObject("RouteEntrys").getJSONArray("RouteEntry");
            for (int i = 0; i < routesResponse.length(); i++) {
                routes.add(toRoute(routesResponse.getJSONObject(i)));
            }
            routingTable.setRoutes(routes.toArray(new Route[routes.size()]));
            return routingTable;
        } catch (JSONException e) {
            throw new InternalException(e);
        }
    }

//    private String    destinationCidr;
//    private String    gatewayAddress;
//    private String    gatewayId;
//    private String    gatewayOwnerId;
//    private String    gatewayNetworkInterfaceId;
//    private String    gatewayVirtualMachineId;
//    private IPVersion version
    private Route toRoute (JSONObject response) throws InternalException {
        try {
            //Response contains RouteTableId, DestinationCidrBlock, Type(System/Custom), NextHopId, Status(Pending/Available/Modifying)
            //TODO unsure if the Route next hop is INSTANCE or TUNNEL.
            Route.getRouteToGateway(IPVersion.IPV4, response.getString("DestinationCidrBlock"),
                    response.getString("InstanceId"));
            Route.getRouteToVirtualMachine(IPVersion.IPV4, response.getString("DestinationCidrBlock"),
                    getContext().getAccountNumber(), response.getString("InstanceId"));

            return null;
        } catch (JSONException e) {
            throw new InternalException(e);
        }
    }
}
