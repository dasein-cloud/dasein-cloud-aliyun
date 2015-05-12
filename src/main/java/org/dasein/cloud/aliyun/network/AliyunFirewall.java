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
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.aliyun.AliyunMethod;
import org.dasein.cloud.network.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Created by Jane Wang on 5/7/2015.
 *
 * @author Jane Wang
 * @since 2015.05.01
 */
public class AliyunFirewall extends AbstractFirewallSupport<Aliyun> {

    private static final Logger stdLogger = Aliyun.getStdLogger(AliyunFirewall.class);

    private transient volatile AliyunFirewallCapabilities capabilities;

    public AliyunFirewall(@Nonnull Aliyun provider) {
        super(provider);
    }

    public void delete(@Nonnull String firewallId) throws InternalException, CloudException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getProvider().getContext().getRegionId());
        params.put("SecurityGroupId", firewallId);
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DeleteSecurityGroup", params);
        method.post();
    }

    @Nonnull
    public FirewallCapabilities getCapabilities() throws CloudException, InternalException {
        if (capabilities == null) {
            return new AliyunFirewallCapabilities(getProvider());
        }
        return capabilities;
    }

    public boolean isSubscribed() throws CloudException, InternalException {
        return true;
    }

    @Nonnull
    public Iterable<Firewall> list() throws InternalException, CloudException {
        Map<String, Object> params = new HashMap<String, Object>();
        List<Firewall> firewalls = new ArrayList<Firewall>();
        params.put("RegionId", getContext().getRegionId());
        params.put("PageSize", AliyunNetworkCommon.DefaultPageSize);
        int maxPageNumber = 1;
        int currentPageNumber = 1;
        do {
            params.put("PageNumber", currentPageNumber);
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeSecurityGroups", params);
            JSONObject response = method.get().asJson();
            try {
                JSONArray securityGroups = response.getJSONObject("SecurityGroups").getJSONArray("SecurityGroup");
                for (int i = 0; i < securityGroups.length(); i++) {
                    JSONObject securityGroup = securityGroups.getJSONObject(i);
                    if (!AliyunNetworkCommon.isEmpty(securityGroup.getString("SecurityGroupId"))){
                        firewalls.add(getFirewall(securityGroup.getString("SecurityGroupId")));
                    }
                }
                maxPageNumber = response.getInt("TotalCount") / AliyunNetworkCommon.DefaultPageSize +
                        response.getInt("TotalCount") % AliyunNetworkCommon.DefaultPageSize > 0 ? 1 : 0;
                currentPageNumber++;
            } catch (JSONException e) {
                stdLogger.error("An exception occurs during list all firewalls!", e);
                throw new InternalException(e);
            }
        } while (currentPageNumber < maxPageNumber);
        return firewalls;
    }

    @Nonnull
    @Override
    public String authorize(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull Permission permission, @Nonnull RuleTarget sourceEndpoint, @Nonnull Protocol protocol, @Nonnull RuleTarget destinationEndpoint, int beginPort, int endPort, @Nonnegative int precedence) throws CloudException, InternalException {
        return authorize(firewallId, FirewallRuleCreateOptions.getInstance(direction, permission, sourceEndpoint, protocol, destinationEndpoint, beginPort, endPort));
    }

    @Nonnull
    @Override
    public String authorize(@Nonnull String firewallId, @Nonnull FirewallRuleCreateOptions options) throws CloudException, InternalException {
        if (options.getDirection() != null && options.getDirection().equals(Direction.EGRESS)) {
            throw new OperationNotSupportedException("Aliyun doesn't support EGRESS rule!");
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        params.put("SecurityGroupId", firewallId);
        if (!options.getSourceEndpoint().getRuleTargetType().equals(RuleTargetType.CIDR) || !options.getSourceEndpoint().getRuleTargetType().equals(RuleTargetType.GLOBAL)) {
            throw new OperationNotSupportedException("Aliyun only supports firewall with source target type equals to CIDR or Global(security group for the same account)!");
        } else {
            if (options.getSourceEndpoint().getRuleTargetType().equals(RuleTargetType.CIDR) && !AliyunNetworkCommon.isEmpty(options.getSourceEndpoint().getCidr())) {
                String ipAddress = options.getSourceEndpoint().getCidr().split("/")[0];
                if (AliyunNetworkCommon.isEmpty(ipAddress) || !InetAddressUtils.isIPv4Address(ipAddress)) {
                    throw new OperationNotSupportedException("Aliyun supports IPV4 address only!");
                } else {
                    params.put("SourceCidrIp", ipAddress); //TODO: check auth by source cidr
                    if (!AliyunIpAddress.isPublicIpAddress(ipAddress)) {
                        params.put("NicType", AliyunNetworkCommon.AliyunFirewallNicType.INTRANET.name().toLowerCase());
                    }
                }
            } else if (options.getSourceEndpoint().getRuleTargetType().equals(RuleTargetType.GLOBAL) && !AliyunNetworkCommon.isEmpty(options.getSourceEndpoint().getProviderFirewallId())) {
                String targetFirewallId = options.getSourceEndpoint().getProviderFirewallId();
                params.put("SourceGroupId", targetFirewallId); //TODO: check auth by another security group within the same account
                params.put("NicType", AliyunNetworkCommon.AliyunFirewallNicType.INTRANET.name().toLowerCase());
            }
        }
        if (options.getDestinationEndpoint() == null || options.getDestinationEndpoint().getRuleTargetType().equals(RuleTargetType.VLAN)
                || options.getDestinationEndpoint().getRuleTargetType().equals(RuleTargetType.VM)) {
            if (options.getProtocol() != null) {
                if (options.getProtocol().equals(Protocol.ANY)) {
                    params.put("IpProtocol", AliyunNetworkCommon.IpProtocolAll);
                } else { //no validation for IPSEC, will throw 400 error when invoke
                    params.put("IpProtocol", options.getProtocol().name().toLowerCase());
                }
            } else {
                throw new InternalException("Protocol cannot be empty during authorize rule!");
            }
            params.put("PortRange", toPortRange(options.getProtocol(), options.getPortRangeStart(), options.getPortRangeEnd()));
            if (options.getPermission() != null) {
                if (options.getPermission().equals(Permission.ALLOW)) {
                    params.put("Policy", AliyunNetworkCommon.AliyunFirewallPermission.ACCEPT.name().toLowerCase());
                } else if (options.getPermission().equals(Permission.DENY)) {
                    params.put("Policy", AliyunNetworkCommon.AliyunFirewallPermission.DROP.name().toLowerCase());
                }
            }
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "AuthorizeSecurityGroup", params);
            method.post();
            return FirewallRule.getRuleId(firewallId, options.getSourceEndpoint().getCidr(), options.getDirection(), options.getProtocol(),
                    options.getPermission(), RuleTarget.getGlobal(firewallId), options.getPortRangeStart(), options.getPortRangeEnd());
        } else {
            throw new OperationNotSupportedException("Aliyun support only VLAN and VM type destination target!");
        }
    }

    @Nonnull
    @Override
    public String create(@Nonnull FirewallCreateOptions options) throws InternalException, CloudException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        if (!AliyunNetworkCommon.isEmpty(options.getDescription())) {
            params.put("Description", options.getDescription());
        }
        if (!AliyunNetworkCommon.isEmpty(options.getName())) {
            params.put("SecurityGroupName", options.getName());
        }
        if (!AliyunNetworkCommon.isEmpty(options.getProviderVlanId())) {
            params.put("VpcId", options.getProviderVlanId());
        }
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "AllocateEipAddress", params);
        try {
            JSONObject response = method.post().asJson();
            return response.getString("SecurityGroupId");
        } catch (JSONException e) {
            stdLogger.error("An exception occurs during create security group!", e);
            throw new InternalException(e);
        }
    }

    @Nullable
    @Override
    public Firewall getFirewall(@Nonnull String firewallId) throws InternalException, CloudException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        params.put("SecurityGroupId", firewallId);
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeSecurityGroupAttribute", params);
        JSONObject response = method.get().asJson();
        return toFirewall(firewallId, response);
    }

    @Nonnull
    @Override
    public Iterable<ResourceStatus> listFirewallStatus() throws InternalException, CloudException {
        Map<String, Object> params = new HashMap<String, Object>();
        List<ResourceStatus> resourceStatus = new ArrayList<ResourceStatus>();
        params.put("RegionId", getContext().getRegionId());
        params.put("PageSize", AliyunNetworkCommon.DefaultPageSize);
        int totalPageCount = 1;
        int currentPageNumber = 1;
        AliyunMethod method = null;
        do {
            params.put("PageNumber", currentPageNumber);
            method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeSecurityGroups", params);
            JSONObject response = method.get().asJson();
            try {
                int totalCount = response.getInt("TotalCount");
                totalPageCount = totalCount / AliyunNetworkCommon.DefaultPageSize + totalCount % AliyunNetworkCommon.DefaultPageSize > 0 ? 1 : 0;
                JSONArray securityGroups = response.getJSONObject("SecurityGroups").getJSONArray("SecurityGroup");
                for (int i = 0; i < securityGroups.length(); i++) {
                    JSONObject securityGroup = securityGroups.getJSONObject(i);
                    String securityGroupId = securityGroup.getString("SecurityGroupId");
                    resourceStatus.add(new ResourceStatus(securityGroupId, true));
                }
                currentPageNumber++;
            } catch (JSONException e) {
                throw new InternalException("An exception occurs during parsing response from describe security groups!");
            }
        } while (currentPageNumber < totalPageCount);
        return resourceStatus;
    }

    @Override
    public void revoke(@Nonnull String providerFirewallRuleId) throws InternalException, CloudException {
        FirewallRule firewallRule = FirewallRule.parseId(providerFirewallRuleId);
        String source = null;
        if (firewallRule.getSourceEndpoint().getRuleTargetType().equals(RuleTargetType.CIDR)) {
            source = firewallRule.getSourceEndpoint().getCidr();
        } else if (firewallRule.getSourceEndpoint().getRuleTargetType().equals(RuleTargetType.GLOBAL)) {
            source = firewallRule.getSourceEndpoint().getProviderFirewallId();
        } else {
            throw new OperationNotSupportedException("Aliyun only support CIDR and GLOBAL source target type!");
        }
        if (firewallRule.getDirection() == null) {
            revoke(firewallRule.getFirewallId(), source, firewallRule.getProtocol(), firewallRule.getStartPort(), firewallRule.getEndPort());
        } else if (firewallRule.getPermission() == null) {
            revoke(firewallRule.getFirewallId(), firewallRule.getDirection(), source, firewallRule.getProtocol(), firewallRule.getStartPort(), firewallRule.getEndPort());
        } else if (firewallRule.getDestinationEndpoint() == null) {
            revoke(firewallRule.getFirewallId(), firewallRule.getDirection(), firewallRule.getPermission(), source, firewallRule.getProtocol(), firewallRule.getStartPort(), firewallRule.getEndPort());
        } else {
            revoke(firewallRule.getFirewallId(), firewallRule.getDirection(), firewallRule.getPermission(), source, firewallRule.getProtocol(), firewallRule.getDestinationEndpoint(), firewallRule.getStartPort(), firewallRule.getEndPort());
        }
    }

    @Override
    public void revoke(@Nonnull String firewallId, @Nonnull String source, @Nonnull Protocol protocol, int beginPort, int endPort) throws CloudException, InternalException {
        revoke(firewallId, Direction.INGRESS, null, source, protocol, beginPort, endPort);
    }

    @Override
    public void revoke(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull String source, @Nonnull Protocol protocol, int beginPort, int endPort) throws CloudException, InternalException {
        revoke(firewallId, source, protocol, beginPort, endPort);
    }

    @Override
    public void revoke(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull Permission permission, @Nonnull String source, @Nonnull Protocol protocol, int beginPort, int endPort) throws CloudException, InternalException {
        revoke(firewallId, direction, permission, source, protocol, null, beginPort, endPort);
    }

    @Override
    public void revoke(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull Permission permission, @Nonnull String source, @Nonnull Protocol protocol, @Nonnull RuleTarget target, int beginPort, int endPort) throws CloudException, InternalException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        params.put("SecurityGroupId", firewallId);
        //protocol
        if (protocol == null) {
            throw new InternalException("Revoke error: protocol can not be null!");
        } else {
            if (protocol.equals(Protocol.ANY)) {
                params.put("IpProtocol", AliyunNetworkCommon.IpProtocolAll);
            } else {
                params.put("IpProtocol", protocol.name().toLowerCase());
            }
        }
        //portRange
        if (beginPort == 0 || endPort < beginPort) { //default value
            if (protocol.equals(Protocol.ICMP) || protocol.equals(Protocol.ANY)) {
                params.put("PortRange", "-1/-1");
            } else if (protocol.equals(Protocol.TCP) || protocol.equals(Protocol.UDP)) {
                params.put("PortRange", "1/65535");
            } else {
                throw new OperationNotSupportedException("Aliyun doesn't support " + protocol.name().toLowerCase() + " protocol!");
            }
        } else { //customer provided value
            params.put("PortRange", beginPort + "/" + endPort);
        }

        if (isCidrBlock(source)) {
            params.put("DestCidrIp", source); //revoke rule auth by source cidr block
            if (!AliyunIpAddress.isPublicIpAddress(source)) {
                params.put("NicType", AliyunNetworkCommon.AliyunFirewallNicType.INTRANET.name().toLowerCase());
            }
        } else {
            params.put("DestGroupId", source); //revoke rule auth by source security group
            params.put("NicType", AliyunNetworkCommon.AliyunFirewallNicType.INTRANET.name().toLowerCase());
        }
//        if (!AliyunNetworkCommon.isEmpty(source)) {
//            params.put("DestCidrIp", source);
//        }

        if (permission != null) {
            if (permission.equals(Permission.ALLOW)) {
                params.put("Policy", AliyunNetworkCommon.AliyunFirewallPermission.ACCEPT.name().toLowerCase());
            } else {
                params.put("Policy", AliyunNetworkCommon.AliyunFirewallPermission.DROP.name().toLowerCase());
            }
        }
        if (direction.equals(Direction.EGRESS)) {
            throw new OperationNotSupportedException("Aliyun doesn't support Egress firewall rule!");
        }
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "RevokeSecurityGroup", params);
        method.post();
    }

    private Firewall toFirewall (String firewallId, JSONObject jsonObject) throws InternalException {
        Firewall firewall = new Firewall();
        ArrayList<FirewallRule> firewallRuleList = new ArrayList<FirewallRule>();
        firewall.setRegionId(getContext().getRegionId());
        firewall.setActive(true);
        firewall.setAvailable(true);
        try {
            firewall.setProviderFirewallId(jsonObject.getString("SecurityGroupId"));
            if (!AliyunNetworkCommon.isEmpty(jsonObject.getString("Description"))) {
                firewall.setDescription(jsonObject.getString("Description"));
            }
            JSONArray firewallRules = jsonObject.getJSONObject("Permissions").getJSONArray("Permission");
            for (int i = 0; i < firewallRules.length(); i++) {
                JSONObject firewallRule = firewallRules.getJSONObject(i);
                RuleTarget sourceEndpoint = null;
                if (!AliyunNetworkCommon.isEmpty(firewallRule.getString("SourceCidrIp"))) { //retrieve auth by cidr
                    sourceEndpoint = RuleTarget.getCIDR(firewallRule.getString("SourceCidrIp"));
                } else if (!AliyunNetworkCommon.isEmpty(firewallRule.getString("SourceGroupId"))) { //retrieve auth by security group within the same account
                    if (!AliyunNetworkCommon.isEmpty(firewallRule.getString("SourceGroupOwnerAccount"))) {
                        throw new OperationNotSupportedException("Aliyun doesn't support cross account firewall rule authorization!");
                    }
                    sourceEndpoint = RuleTarget.getGlobal(firewallRule.getString("SourceGroupId"));
                } else {
                    throw new InternalException("Either sourceCidrIp or sourceGroupId should be assigned!");
                }
                Direction direction = Direction.INGRESS;
                Protocol protocol = null;
                if (!AliyunNetworkCommon.isEmpty(firewallRule.getString("IpProtocol"))) {
                    if (firewallRule.getString("IpProtocol").toLowerCase().equals(AliyunNetworkCommon.IpProtocolAll.toLowerCase())) {
                        protocol = Protocol.ANY;
                    } else {
                        protocol = Protocol.valueOf(firewallRule.getString("IpProtocol").toUpperCase());
                    }
                }
                Permission permission = null;
                if (!AliyunNetworkCommon.isEmpty(firewallRule.getString("Policy"))) {
                    if (firewallRule.getString("Policy").equals(AliyunNetworkCommon.AliyunFirewallPermission.ACCEPT.name().toLowerCase())) {
                        permission = Permission.ALLOW;
                    } else {
                        permission = Permission.DENY;
                    }
                }
                //all instance that protected by this firewall
                RuleTarget destinationEndpoint = RuleTarget.getGlobal(firewallId);
                int startPort = -1, endPort = -1;
                if (!AliyunNetworkCommon.isEmpty(firewallRule.getString("PortRange"))) {
                    startPort = Integer.valueOf(firewallRule.getString("PortRange").split("/")[0]);
                    endPort = Integer.valueOf(firewallRule.getString("PortRange").split("/")[1]);
                }
                FirewallRule rule = FirewallRule.getInstance(null, firewallId, sourceEndpoint, direction, protocol, permission, destinationEndpoint, startPort, endPort);
                firewallRuleList.add(rule);
            }
            firewall.setRules(firewallRuleList);
            return firewall;
        } catch (JSONException e) {
            throw new InternalException("An exception occurs during parsing json to the firewall instance!");
        }
    }

    /**
     * if startPort and endPort are smaller than 0, then the default value of each protocol will be return.
     * else it is startPort + "/" + endPort
     * @param protocol
     * @param startPort
     * @param endPort
     * @return
     * @throws InternalException
     */
    private String toPortRange (Protocol protocol, int startPort, int endPort) throws InternalException {
        //TODO, finish when add GRE to protocol
        if (startPort > 0 && endPort >= startPort) { //set to customized range
            return startPort + "/" + endPort;
        } else { //set to default range
            if (protocol != null && (protocol.equals(Protocol.ICMP) || protocol.equals(Protocol.ANY))) {
                return "-1/-1";
            } else if (protocol != null && (protocol.equals(Protocol.TCP) || protocol.equals(Protocol.UDP))) {
                return "1/65535";
            } else {
                throw new InternalException("Invalid PortRange, from " + startPort + " to " + endPort);
            }
        }
    }

    /**
     * The verify of CIDR block should be (IPV4 Address) + "/" + (Integer)
     * @param source
     * @return true - CIDR block, false - normal string, deems as a security group id
     * @throws CloudException
     * @throws InternalException
     */
    private boolean isCidrBlock (String source) throws CloudException, InternalException {
        if (AliyunNetworkCommon.isEmpty(source)) {
            if (source.contains("/")) {
                String ipAddress = source.split("/")[0];
                if (InetAddressUtils.isIPv4Address(ipAddress)) {
                    try{
                        Integer.valueOf(source.split("/")[1]);
                        return true;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                } else {
                    throw new OperationNotSupportedException("Aliyun doesn't support IPV6 address!");
                }
            } else {
                return false;
            }
        } else {
            throw new InternalException("source to be check is empty!");
        }
    }

}
