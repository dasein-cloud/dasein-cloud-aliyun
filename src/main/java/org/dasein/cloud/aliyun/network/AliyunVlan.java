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

import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.Tag;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.network.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Created by jwang7 on 5/13/2015.
 */
public class AliyunVlan extends AbstractVLANSupport<Aliyun> {

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
    public Route addRouteToNetworkInterface(@Nonnull String routingTableId, @Nonnull IPVersion version, @Nullable String destinationCidr, @Nonnull String nicId) throws CloudException, InternalException {
        return super.addRouteToNetworkInterface(routingTableId, version, destinationCidr, nicId);
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
    public void disassociateRoutingTableFromSubnet(@Nonnull String subnetId, @Nonnull String routingTableId) throws CloudException, InternalException {
        super.disassociateRoutingTableFromSubnet(subnetId, routingTableId);
    }

    @Override
    public void assignRoutingTableToVlan(@Nonnull String vlanId, @Nonnull String routingTableId) throws CloudException, InternalException {
        super.assignRoutingTableToVlan(vlanId, routingTableId);
    }

    @Override
    public void attachNetworkInterface(@Nonnull String nicId, @Nonnull String vmId, int index) throws CloudException, InternalException {
        super.attachNetworkInterface(nicId, vmId, index);
    }

    @Override
    public String createInternetGateway(@Nonnull String vlanId) throws CloudException, InternalException {
        return super.createInternetGateway(vlanId);
    }

    @Nonnull
    @Override
    public String createRoutingTable(@Nonnull String vlanId, @Nonnull String name, @Nonnull String description) throws CloudException, InternalException {
        return super.createRoutingTable(vlanId, name, description);
    }

    @Nonnull
    @Override
    public NetworkInterface createNetworkInterface(@Nonnull NICCreateOptions options) throws CloudException, InternalException {
        return super.createNetworkInterface(options);
    }

    @Nonnull
    @Override
    public Subnet createSubnet(@Nonnull SubnetCreateOptions options) throws CloudException, InternalException {
        return super.createSubnet(options);
    }

    @Nonnull
    @Override
    public VLAN createVlan(@Nonnull String cidr, @Nonnull String name, @Nonnull String description, @Nonnull String domainName, @Nonnull String[] dnsServers, @Nonnull String[] ntpServers) throws CloudException, InternalException {
        return super.createVlan(cidr, name, description, domainName, dnsServers, ntpServers);
    }

    @Nonnull
    @Override
    public VLAN createVlan(@Nonnull VlanCreateOptions vco) throws CloudException, InternalException {
        return super.createVlan(vco);
    }

    @Override
    public void detachNetworkInterface(@Nonnull String nicId) throws CloudException, InternalException {
        super.detachNetworkInterface(nicId);
    }

    @Override
    public VLANCapabilities getCapabilities() throws CloudException, InternalException {
        return null;
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
        return null;
    }

    @Nonnull
    @Override
    @Deprecated
    public String getProviderTermForVlan(@Nonnull Locale locale) {
        return null;
    }

    @Override
    public NetworkInterface getNetworkInterface(@Nonnull String nicId) throws CloudException, InternalException {
        return super.getNetworkInterface(nicId);
    }

    @Override
    public RoutingTable getRoutingTableForSubnet(@Nonnull String subnetId) throws CloudException, InternalException {
        return super.getRoutingTableForSubnet(subnetId);
    }

    @Override
    public RoutingTable getRoutingTableForVlan(@Nonnull String vlanId) throws CloudException, InternalException {
        return super.getRoutingTableForVlan(vlanId);
    }

    @Override
    public RoutingTable getRoutingTable(@Nonnull String id) throws CloudException, InternalException {
        return super.getRoutingTable(id);
    }

    @Override
    public Subnet getSubnet(@Nonnull String subnetId) throws CloudException, InternalException {
        return super.getSubnet(subnetId);
    }

    @Override
    public VLAN getVlan(@Nonnull String vlanId) throws CloudException, InternalException {
        return super.getVlan(vlanId);
    }

    @Override
    public boolean isConnectedViaInternetGateway(@Nonnull String vlanId) throws CloudException, InternalException {
        return super.isConnectedViaInternetGateway(vlanId);
    }

    @Nonnull
    @Override
    public Iterable<String> listFirewallIdsForNIC(@Nonnull String nicId) throws CloudException, InternalException {
        return super.listFirewallIdsForNIC(nicId);
    }

    @Nonnull
    @Override
    public Iterable<ResourceStatus> listNetworkInterfaceStatus() throws CloudException, InternalException {
        return super.listNetworkInterfaceStatus();
    }

    @Nonnull
    @Override
    public Iterable<NetworkInterface> listNetworkInterfaces() throws CloudException, InternalException {
        return super.listNetworkInterfaces();
    }

    @Nonnull
    @Override
    public Iterable<NetworkInterface> listNetworkInterfacesForVM(@Nonnull String forVmId) throws CloudException, InternalException {
        return super.listNetworkInterfacesForVM(forVmId);
    }

    @Nonnull
    @Override
    public Iterable<NetworkInterface> listNetworkInterfacesInSubnet(@Nonnull String subnetId) throws CloudException, InternalException {
        return super.listNetworkInterfacesInSubnet(subnetId);
    }

    @Nonnull
    @Override
    public Iterable<NetworkInterface> listNetworkInterfacesInVLAN(@Nonnull String vlanId) throws CloudException, InternalException {
        return super.listNetworkInterfacesInVLAN(vlanId);
    }

    @Nonnull
    @Override
    public Iterable<Networkable> listResources(@Nonnull String inVlanId) throws CloudException, InternalException {
        return super.listResources(inVlanId);
    }

    @Nonnull
    @Override
    public Iterable<RoutingTable> listRoutingTablesForSubnet(@Nonnull String subnetId) throws CloudException, InternalException {
        return super.listRoutingTablesForSubnet(subnetId);
    }

    @Nonnull
    @Override
    public Iterable<RoutingTable> listRoutingTablesForVlan(@Nonnull String vlanId) throws CloudException, InternalException {
        return super.listRoutingTablesForVlan(vlanId);
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

    @Nonnull
    @Override
    public String[] mapServiceAction(@Nonnull ServiceAction action) {
        return super.mapServiceAction(action);
    }

    @Override
    public void removeInternetGateway(@Nonnull String vlanId) throws CloudException, InternalException {
        super.removeInternetGateway(vlanId);
    }

    @Override
    public void removeNetworkInterface(@Nonnull String nicId) throws CloudException, InternalException {
        super.removeNetworkInterface(nicId);
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
    public void removeVlan(String vlanId) throws CloudException, InternalException {
        super.removeVlan(vlanId);
    }

    @Override
    public void removeSubnetTags(@Nonnull String subnetId, @Nonnull Tag... tags) throws CloudException, InternalException {
        super.removeSubnetTags(subnetId, tags);
    }

    @Override
    public void removeSubnetTags(@Nonnull String[] subnetIds, @Nonnull Tag... tags) throws CloudException, InternalException {
        super.removeSubnetTags(subnetIds, tags);
    }

    @Override
    public void removeVLANTags(@Nonnull String vlanId, @Nonnull Tag... tags) throws CloudException, InternalException {
        super.removeVLANTags(vlanId, tags);
    }

    @Override
    public void removeVLANTags(@Nonnull String[] vlanIds, @Nonnull Tag... tags) throws CloudException, InternalException {
        super.removeVLANTags(vlanIds, tags);
    }

    @Override
    public void updateRoutingTableTags(@Nonnull String routingTableId, @Nonnull Tag... tags) throws CloudException, InternalException {
        super.updateRoutingTableTags(routingTableId, tags);
    }

    @Override
    public void updateSubnetTags(@Nonnull String subnetId, @Nonnull Tag... tags) throws CloudException, InternalException {
        super.updateSubnetTags(subnetId, tags);
    }

    @Override
    public void updateSubnetTags(@Nonnull String[] subnetIds, @Nonnull Tag... tags) throws CloudException, InternalException {
        super.updateSubnetTags(subnetIds, tags);
    }

    @Override
    public void updateVLANTags(@Nonnull String vlanId, @Nonnull Tag... tags) throws CloudException, InternalException {
        super.updateVLANTags(vlanId, tags);
    }

    @Override
    public void updateVLANTags(@Nonnull String[] vlanIds, @Nonnull Tag... tags) throws CloudException, InternalException {
        super.updateVLANTags(vlanIds, tags);
    }

    @Override
    public void updateInternetGatewayTags(@Nonnull String internetGatewayId, @Nonnull Tag... tags) throws CloudException, InternalException {
        super.updateInternetGatewayTags(internetGatewayId, tags);
    }

    @Override
    public void updateInternetGatewayTags(@Nonnull String[] internetGatewayIds, @Nonnull Tag... tags) throws CloudException, InternalException {
        super.updateInternetGatewayTags(internetGatewayIds, tags);
    }

    @Override
    public void updateRoutingTableTags(@Nonnull String[] routingTableIds, @Nonnull Tag... tags) throws CloudException, InternalException {
        super.updateRoutingTableTags(routingTableIds, tags);
    }

    @Override
    public void removeRoutingTableTags(@Nonnull String[] routingTableIds, @Nonnull Tag... tags) throws CloudException, InternalException {
        super.removeRoutingTableTags(routingTableIds, tags);
    }

    @Override
    public void removeInternetGatewayTags(@Nonnull String internetGatewayId, @Nonnull Tag... tags) throws CloudException, InternalException {
        super.removeInternetGatewayTags(internetGatewayId, tags);
    }

    @Override
    public void removeInternetGatewayTags(@Nonnull String[] internetGatewayIds, @Nonnull Tag... tags) throws CloudException, InternalException {
        super.removeInternetGatewayTags(internetGatewayIds, tags);
    }

    @Override
    public void setSubnetTags(@Nonnull String[] subnetIds, @Nonnull Tag... tags) throws CloudException, InternalException {
        super.setSubnetTags(subnetIds, tags);
    }

    @Override
    public void setRoutingTableTags(@Nonnull String[] routingTableIds, @Nonnull Tag... tags) throws CloudException, InternalException {
        super.setRoutingTableTags(routingTableIds, tags);
    }

    @Override
    public void setInternetGatewayTags(@Nonnull String[] internetGatewayIds, @Nonnull Tag... tags) throws CloudException, InternalException {
        super.setInternetGatewayTags(internetGatewayIds, tags);
    }

    @Override
    public void setVLANTags(@Nonnull String[] vlanIds, @Nonnull Tag... tags) throws CloudException, InternalException {
        super.setVLANTags(vlanIds, tags);
    }

    @Override
    public void setVLANTags(@Nonnull String vlanId, @Nonnull Tag... tags) throws CloudException, InternalException {
        super.setVLANTags(vlanId, tags);
    }

    @Override
    public void setSubnetTags(@Nonnull String subnetId, @Nonnull Tag... tags) throws CloudException, InternalException {
        super.setSubnetTags(subnetId, tags);
    }

    @Override
    public void setRoutingTableTags(@Nonnull String routingTableId, @Nonnull Tag... tags) throws CloudException, InternalException {
        super.setRoutingTableTags(routingTableId, tags);
    }

    @Override
    public void setInternetGatewayTags(@Nonnull String internetGatewayId, @Nonnull Tag... tags) throws CloudException, InternalException {
        super.setInternetGatewayTags(internetGatewayId, tags);
    }

    @Override
    public void removeRoutingTableTags(@Nonnull String routingTableId, @Nonnull Tag... tags) throws CloudException, InternalException {
        super.removeRoutingTableTags(routingTableId, tags);
    }

    @Override
    public void removeInternetGatewayById(@Nonnull String id) throws CloudException, InternalException {
        super.removeInternetGatewayById(id);
    }

    @Nonnull
    @Override
    public Iterable<InternetGateway> listInternetGateways(@Nullable String vlanId) throws CloudException, InternalException {
        return super.listInternetGateways(vlanId);
    }

    @Nullable
    @Override
    public InternetGateway getInternetGatewayById(@Nonnull String gatewayId) throws CloudException, InternalException {
        return super.getInternetGatewayById(gatewayId);
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        return false;
    }

    @Nullable
    @Override
    public String getAttachedInternetGatewayId(@Nonnull String vlanId) throws CloudException, InternalException {
        return super.getAttachedInternetGatewayId(vlanId);
    }
}
