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

import org.apache.http.client.methods.HttpUriRequest;
import org.apache.log4j.Logger;
import org.dasein.cloud.*;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.aliyun.util.requester.AliyunHttpClientBuilderFactory;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestBuilder;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestExecutor;
import org.dasein.cloud.aliyun.util.requester.AliyunValidateJsonResponseHandler;
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

    /**
     * IdentityGenerator contains vlanId, subnetId, vrouterId, routeTableId.
     * All vlan, subnet/vswitch, vrouter and route table ids will be transformed
     */
    protected class IdentityGenerator {

        private String vlanId;
        private String subnetId;
        private String vrouterId;
        private String routeTableId;

        public IdentityGenerator (String generateId) {
            if (!getProvider().isEmpty(generateId)) {
                String[] segments = generateId.split(":");
                if (segments.length == 4) {
                    if (!getProvider().isEmpty(segments[0])) {
                        this.vlanId = segments[0];
                    }
                    if (!getProvider().isEmpty(segments[1])) {
                        this.subnetId = segments[1];
                    }
                    if (!getProvider().isEmpty(segments[2])) {
                        this.vrouterId = segments[2];
                    }
                    if (!getProvider().isEmpty(segments[3])) {
                        this.routeTableId = segments[3];
                    }
                }
            }
        }

        public IdentityGenerator (String vlanId, String subnetId, String vrouterId, String routeTableId) {
            this.vlanId = vlanId;
            this.subnetId = subnetId;
            this.vrouterId = vrouterId;
            this.routeTableId = routeTableId;
        }

        public String getVlanId() {
            return vlanId;
        }

        public String getSubnetId() {
            return subnetId;
        }

        public String getVrouterId() {
            return vrouterId;
        }

        public String getRouteTableId() {
            return routeTableId;
        }

        public String toString() {
            String id = "";
            if (!getProvider().isEmpty(this.vlanId)) {
                id += this.vlanId;
            }
            id += ":";
            if (!getProvider().isEmpty(this.subnetId)) {
                id += this.subnetId;
            }
            id += ":";
            if (!getProvider().isEmpty(this.vrouterId)) {
                id += this.vrouterId;
            }
            id += ":";
            if (!getProvider().isEmpty(this.routeTableId)) {
                id += this.routeTableId;
            }
            return id;
        }
    }

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
            throw new InternalException("Aliyun supports IPV4 only!");
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RouteTableId", new IdentityGenerator(routingTableId).getRouteTableId());
        params.put("DestinationCidrBlock", destinationCidr);
        if (!getProvider().isEmpty(vmId)) {
            params.put("NextHopType", getProvider().capitalize(
                    AliyunNetworkCommon.RouteEntryNextHopType.instance.name()));
            params.put("NextHopId", vmId);
        } else {
            stdLogger.warn("Add route to Virtual Machine, you must specify the VM instance ID!");
            throw new InternalException("Add route to Virtual Machine, you must specify the VM instance ID!");
        }
        
        HttpUriRequest request = AliyunRequestBuilder.post()
        		.provider(getProvider())
        		.category(AliyunRequestBuilder.Category.ECS)
        		.parameter("Action", "CreateRouteEntry")
        		.entity(params)
        		.clientToken(true)
        		.build();
        
        new AliyunRequestExecutor<Void>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                new AliyunValidateJsonResponseHandler(getProvider())).execute();
        
        return Route.getRouteToVirtualMachine(IPVersion.IPV4, destinationCidr, getContext().getAccountNumber(), vmId);
    }

    @Nonnull
    @Override
    public Subnet createSubnet(@Nonnull SubnetCreateOptions options) throws CloudException, InternalException {
        
    	Map<String, Object> params = new HashMap<String, Object>();
        params.put("ZoneId", options.getProviderDataCenterId());
        params.put("CidrBlock", options.getCidr());
        IdentityGenerator vlanId = new IdentityGenerator(options.getProviderVlanId());
        params.put("VpcId", vlanId.getVlanId());
        if (!getProvider().isEmpty(options.getName())) {
            params.put("VSwitchName", options.getName());
        }
        if (!getProvider().isEmpty(options.getDescription())) {
            params.put("Description", options.getDescription());
        }
        
        HttpUriRequest request = AliyunRequestBuilder.post()
        		.provider(getProvider())
        		.category(AliyunRequestBuilder.Category.ECS)
        		.parameter("Action", "CreateVSwitch")
        		.entity(params)
        		.clientToken(true)
        		.build();
        
        String vSwitchId = (String) new AliyunRequestExecutor<Map<String, Object>>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                AliyunNetworkCommon.getDefaultResponseHandler(getProvider(), "VSwitchId")).execute().get("VSwitchId");
        
        return getSubnet(new IdentityGenerator(vlanId.getVlanId(), vSwitchId, null, null).toString());
        
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
        if (!getProvider().isEmpty(cidr)) {
            params.put("CidrBlock", cidr);
        }
        if (!getProvider().isEmpty(name)) {
            params.put("VpcName", name);
        }
        if (!getProvider().isEmpty(description)) {
            params.put("Description", description);
        }
        
        HttpUriRequest request = AliyunRequestBuilder.post()
        		.provider(getProvider())
        		.category(AliyunRequestBuilder.Category.ECS)
        		.parameter("Action", "CreateVpc")
        		.entity(params)
        		.clientToken(true)
        		.build();
        
        Map<String, Object> result = new AliyunRequestExecutor<Map<String, Object>>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                AliyunNetworkCommon.getDefaultResponseHandler(getProvider(), "VpcId", "VRouterId", "RouteTableId")).execute();
        
        return getVlan(new IdentityGenerator((String)result.get("VpcId"), null, (String)result.get("VRouterId"), (String)result.get("RouteTableId")).toString());
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
        IdentityGenerator identityGenerator = new IdentityGenerator(vlanId);
        if (!getProvider().isEmpty(identityGenerator.getVrouterId())) {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("VRouterId", identityGenerator.getVrouterId());
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeRouteTables", params);
            JSONObject response = method.get().asJson();
            try {
                JSONArray routingTables = response.getJSONObject("RouteTables").getJSONArray("RouteTable");
                if (routingTables != null && routingTables.length() > 0) {
                	return toRoutingTable(routingTables.getJSONObject(0), identityGenerator.getVlanId());
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
        IdentityGenerator identityGenerator = new IdentityGenerator(id);
        // get routing table instance by router id and routing table id. and use vpcId (retrieve above) to set the vpc field.
        if (!getProvider().isEmpty(identityGenerator.getVrouterId())) {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("VRouterId", identityGenerator.getVrouterId());
            params.put("RouteTableId", identityGenerator.getRouteTableId());
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeRouteTables", params);
            JSONObject response = method.get().asJson();
            try {
                JSONArray routingTables = response.getJSONObject("RouteTables").getJSONArray("RouteTable");
                if (routingTables != null && routingTables.length() > 0) {
                	return toRoutingTable(routingTables.getJSONObject(0), identityGenerator.getVlanId());
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
        IdentityGenerator identityGenerator = new IdentityGenerator(subnetId);
        if (!getProvider().isEmpty(identityGenerator.getVlanId())) {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("VpcId", identityGenerator.getVlanId());
            params.put("VSwitchId", identityGenerator.getSubnetId());
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeVSwitches", params);
            JSONObject response = method.get().asJson();
            try {
                JSONArray subnets = response.getJSONObject("VSwitches").getJSONArray("VSwitch");
                if (subnets != null && subnets.length() > 0) {
                	return toSubnet(subnets.getJSONObject(0));
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
        params.put("VpcId", new IdentityGenerator(vlanId).getVlanId());
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeVpcs", params);
        JSONObject response = method.get().asJson();
        try {
            JSONArray vlans = response.getJSONObject("Vpcs").getJSONArray("Vpc");
            if (vlans != null && vlans.length() > 0) {
            	return toVlan(vlans.getJSONObject(0));
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
                IdentityGenerator identityGenerator = new IdentityGenerator(inVlanId);
                NetworkServices service = getProvider().getNetworkServices();
                if (service != null) {
                    //IP Address Support
                    IpAddressSupport ipAddressSupport = service.getIpAddressSupport();
                    if (ipAddressSupport != null) {
                        for (IpAddress ipAddress : ipAddressSupport.listIpPool(IPVersion.IPV4, false)) {
                            if (!getProvider().isEmpty(ipAddress.getProviderVlanId())
                                    && ipAddress.getProviderVlanId().equals(identityGenerator.getVlanId())) {
                                iterator.push(ipAddress);
                            }
                        }
                    }
                    //Security Group/Firewall Support
                    FirewallSupport firewallSupport = service.getFirewallSupport();
                    if (firewallSupport != null) {
                        for (Firewall firewall : firewallSupport.list()) {
                            if (!getProvider().isEmpty(firewall.getProviderVlanId())
                                    && firewall.getProviderVlanId().equals(identityGenerator.getVlanId())) {
                                iterator.push(firewall);
                            }
                        }
                    }
                    //Routing Table
                    for (RoutingTable routingTable : listRoutingTablesForVlan(identityGenerator.getVlanId())) {
                        iterator.push(routingTable);
                    }
                    //Subnet
                    for (Subnet subnet: listSubnets(identityGenerator.getVlanId())) {
                        iterator.push(subnet);
                    }
                    //Load Balancers
                    LoadBalancerSupport loadBalancerSupport = service.getLoadBalancerSupport();
                    for (LoadBalancer loadBalancer : loadBalancerSupport.listLoadBalancers()) {
                        if (!getProvider().isEmpty(loadBalancer.getProviderVlanId())
                                && loadBalancer.getProviderVlanId().equals(identityGenerator.getVlanId())) {
                            iterator.push(loadBalancer);
                        }
                    }
                }
                //VMs
                ComputeServices computeService = getProvider().getComputeServices();
                if (computeService != null) {
                    VirtualMachineSupport virtualMachineSupport = computeService.getVirtualMachineSupport();
                    if (virtualMachineSupport != null) {
                        for (VirtualMachine virtualMachine : virtualMachineSupport.listVirtualMachines()) {
                            if (!getProvider().isEmpty(virtualMachine.getProviderVlanId())
                                    && virtualMachine.getProviderVlanId().equals(identityGenerator.getVlanId())) {
                                iterator.push(virtualMachine);
                            }
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
        routingTables.add(getRoutingTableForVlan(new IdentityGenerator(vlanId).getVlanId()));
        return routingTables;
    }

    @Nonnull
    @Override
    public Iterable<Subnet> listSubnets(@Nullable String vlanId) throws CloudException, InternalException {
        List<Subnet> subnets = new ArrayList<Subnet>();
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("VpcId", new IdentityGenerator(vlanId).getVlanId());
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
     *
     */
    @Override
    public void removeRoute(@Nonnull String routingTableId, @Nonnull String destinationCidr) throws CloudException, InternalException {
        //search routes by routing table and destination cidr
        RoutingTable routingTable = getRoutingTable(routingTableId);
        String nextHopId = null;
        for (Route route : routingTable.getRoutes()) {
            if (route.getDestinationCidr().equals(destinationCidr)) {
                if (!getProvider().isEmpty(route.getGatewayVirtualMachineId())) {
                    nextHopId = route.getGatewayVirtualMachineId();
                } else if (!getProvider().isEmpty(route.getGatewayId())) {
                    nextHopId = route.getGatewayId();
                }
                break;
            }
        }

        //remove route from routing table
        if (!getProvider().isEmpty(nextHopId)) {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("RouteTableId", new IdentityGenerator(routingTableId).getRouteTableId());
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
        params.put("VSwitchId", new IdentityGenerator(providerSubnetId).getSubnetId());
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DeleteVSwitch", params);
        JSONObject response = method.post().asJson();
        getProvider().validateResponse(response);
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        return true;
    }

    private Subnet toSubnet (JSONObject response) throws InternalException {
        try {
            //cidr, currentState, description, name, providerOwnerId, providerRegionId, providerSubnetId, providerVlanId
            SubnetState state = SubnetState.PENDING;
            if (!getProvider().isEmpty(response.getString("Status")) &&
                    response.getString("Status").equals(AliyunNetworkCommon.SubnetStatus.Available)) {
                state = SubnetState.AVAILABLE;
            }
            IdentityGenerator identityGenerator = new IdentityGenerator(response.getString("VpcId"), response.getString("VSwitchId"), null, null);
            Subnet subnet = Subnet.getInstance(getContext().getAccountNumber(), getContext().getRegionId(), identityGenerator.getVlanId(),
                    identityGenerator.toString(), state, response.getString("VSwitchName"), response.getString("Description"),
                    response.getString("CidrBlock"));
            //availableIpAddresses
            if (response.getInt("AvailableIpAddressCount") > 0) {
                subnet = subnet.withAvailableIpAddresses(response.getInt("AvailableIpAddressCount"));
            }
            //providerDataCenterId
            if (!getProvider().isEmpty("ZoneId")) {
                subnet = subnet.constrainedToDataCenter(response.getString("ZoneId"));
            }
            //supportedTraffic
            subnet.supportingTraffic(IPVersion.IPV4);
            return subnet;
        } catch (JSONException e) {
            throw new InternalException(e);
        }
    }

    /**
     * networkType is unused any more
     * @param response
     * @return
     * @throws InternalException
     */
    private VLAN toVlan(JSONObject response) throws InternalException {
        try {
            VLAN vlan = new VLAN();
            vlan.setCidr(response.getString("CidrBlock"));
            if (response.getString("Status").equals(AliyunNetworkCommon.VlanStatus.Available)) {
                vlan.setCurrentState(VLANState.AVAILABLE);
            } else {
                vlan.setCurrentState(VLANState.PENDING);
            }
            vlan.setDescription(response.getString("Description"));
            vlan.setName(response.getString("VpcName"));
            vlan.setProviderOwnerId(getContext().getAccountNumber());
            vlan.setProviderRegionId(response.getString("RegionId"));
            vlan.setProviderVlanId(new IdentityGenerator(response.getString("VpcId"), null, response.getString("VRouterId"), null).toString());
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
            routingTable.setMain(true);
            routingTable.setName("routing table - " + vlanId);
            routingTable.setDescription("routing table for vlan " + vlanId);
            routingTable.setProviderOwnerId(getContext().getAccountNumber());
            routingTable.setProviderRegionId(getContext().getRegionId());
            routingTable.setProviderVlanId(vlanId);
            routingTable.setProviderRoutingTableId(new IdentityGenerator(
                    vlanId, null, response.getString("VRouterId"), response.getString("RouteTableId")).toString());
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

    private Route toRoute (JSONObject response) throws InternalException {
        try {
            //TODO unsure if the Route next hop is INSTANCE or TUNNEL from the API. not support TUNNEL.
            return Route.getRouteToVirtualMachine(IPVersion.IPV4, response.getString("DestinationCidrBlock"),
                    getContext().getAccountNumber(), response.getString("InstanceId"));
        } catch (JSONException e) {
            throw new InternalException(e);
        }
    }
}
