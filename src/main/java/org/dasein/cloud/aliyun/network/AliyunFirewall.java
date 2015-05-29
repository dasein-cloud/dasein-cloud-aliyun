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

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        JSONObject response = method.post().asJson();
        getProvider().validateResponse(response);
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
                    if (!getProvider().isEmpty(securityGroup.getString("SecurityGroupId"))){
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

        String methodName = "";

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        params.put("SecurityGroupId", firewallId);

        //set params for sourceCidrIp or sourceGroupId, and destinationCidrIp or destinationGroupId
        if (direction.equals(Direction.INGRESS)) {
            methodName = "AuthorizeSecurityGroup";
            destinationEndpoint = RuleTarget.getGlobal(firewallId);
            if (sourceEndpoint.getRuleTargetType().equals(RuleTargetType.CIDR)) {
                String ipAddress = sourceEndpoint.getCidr().split("/")[0];
                if (!InetAddressUtils.isIPv4Address(ipAddress)) {
                    throw new InternalException("Aliyun supports IPV4 address only!");
                }
                params.put("SourceCidrIp", sourceEndpoint.getCidr());
                if (!AliyunIpAddress.isPublicIpAddress(ipAddress)) {
                    params.put("NicType", AliyunNetworkCommon.FirewallNicType.intranet.name());
                }
            } else if (sourceEndpoint.getRuleTargetType().equals(RuleTargetType.GLOBAL)) {
                if (sourceEndpoint.getProviderFirewallId() == null) {
                    throw new InternalException("Aliyun support source rule type GLOBAL, but you must specify the source firewall ID!");
                }
                params.put("SourceGroupId", sourceEndpoint.getProviderFirewallId());
                params.put("NicType", AliyunNetworkCommon.FirewallNicType.intranet.name());
            } else {
                throw new InternalException("Aliyun supports source CIDR, source Security Group auth!");
            }
        } else {
            methodName = "AuthorizeSecurityGroupEgress";
            sourceEndpoint = RuleTarget.getGlobal(firewallId);
            if (destinationEndpoint.getRuleTargetType().equals(RuleTargetType.CIDR)) {
                String ipAddress = destinationEndpoint.getCidr().split("/")[0];
                if (!InetAddressUtils.isIPv4Address(ipAddress)) {
                    throw new InternalException("Aliyun supports IPV4 address only!");
                }
                params.put("DestCidrIp", destinationEndpoint.getCidr());
                if (!AliyunIpAddress.isPublicIpAddress(ipAddress)) {
                    params.put("NicType", AliyunNetworkCommon.FirewallNicType.intranet.name());
                }
            } else if (destinationEndpoint.getRuleTargetType().equals(RuleTargetType.GLOBAL)) {
                if (destinationEndpoint.getProviderFirewallId() == null) {
                    throw new InternalException("Aliyun support destination rule type GLOBAL, but you must specify the destination firewall ID!");
                }
                params.put("DestGroupId", destinationEndpoint.getProviderFirewallId());
                params.put("NicType", AliyunNetworkCommon.FirewallNicType.intranet.name());
            } else {
                throw new InternalException("Aliyun supports destination CIDR, destination Security Group auth!");
            }
        }
        //protocol
        if (protocol != null) {
            if (protocol.equals(Protocol.ANY)) {
                params.put("IpProtocol", AliyunNetworkCommon.FirewallIpProtocol.all.name());
            } else {
                params.put("IpProtocol", protocol.name().toLowerCase());
            }
        } else {
            throw new InternalException("Aliyun doesn't support empty IP protocol authorization!");
        }
        //port range
        params.put("PortRange", toPortRange(protocol, beginPort, endPort));
        //policy
        if(permission != null) {
            if (permission.equals(Permission.ALLOW)) {
                params.put("Policy", AliyunNetworkCommon.FirewallPermission.accept.name());
            } else {
                params.put("Policy", AliyunNetworkCommon.FirewallPermission.drop.name());
            }
        }
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, methodName, params);
        JSONObject response = method.post().asJson();
        getProvider().validateResponse(response);
        return FirewallRule.getInstance(null, firewallId, sourceEndpoint, direction, protocol, permission, destinationEndpoint,
                beginPort, endPort).getProviderRuleId();
    }

    @Nonnull
    @Override
    public String create(@Nonnull FirewallCreateOptions options) throws InternalException, CloudException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        if (!getProvider().isEmpty(options.getDescription())) {
            params.put("Description", options.getDescription());
        }
        if (!getProvider().isEmpty(options.getName())) {
            params.put("SecurityGroupName", options.getName());
        }
        if (!getProvider().isEmpty(options.getProviderVlanId())) {
            params.put("VpcId", options.getProviderVlanId());
        }
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "AllocateEipAddress", params, true);
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
        return toFirewall(response);
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
        do {
            params.put("PageNumber", currentPageNumber);
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeSecurityGroups", params);
            JSONObject response = method.get().asJson();
            try {
                JSONArray securityGroups = response.getJSONObject("SecurityGroups").getJSONArray("SecurityGroup");
                for (int i = 0; i < securityGroups.length(); i++) {
                    JSONObject securityGroup = securityGroups.getJSONObject(i);
                    String securityGroupId = securityGroup.getString("SecurityGroupId");
                    resourceStatus.add(new ResourceStatus(securityGroupId, true));
                }
                totalPageCount = response.getInt("TotalCount") / AliyunNetworkCommon.DefaultPageSize
                        + response.getInt("TotalCount") % AliyunNetworkCommon.DefaultPageSize > 0 ? 1 : 0;
                currentPageNumber++;
            } catch (JSONException e) {
                stdLogger.error("An exception occurs during list firewall status!", e);
                throw new InternalException(e);
            }
        } while (currentPageNumber < totalPageCount);
        return resourceStatus;
    }

    @Override
    public void revoke(@Nonnull String providerFirewallRuleId) throws InternalException, CloudException {
        FirewallRule firewallRule = FirewallRule.parseId(providerFirewallRuleId);
        String source = null;
        if (firewallRule.getDirection().equals(Direction.INGRESS)) {
            if (firewallRule.getSourceEndpoint() != null
                    && firewallRule.getSourceEndpoint().getRuleTargetType().equals(RuleTargetType.CIDR)) {
                source = firewallRule.getSourceEndpoint().getCidr();
            } else if (firewallRule.getSourceEndpoint() != null
                    && firewallRule.getSourceEndpoint().getRuleTargetType().equals(RuleTargetType.GLOBAL)) {
                source = firewallRule.getSourceEndpoint().getProviderFirewallId();
            }
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
    public void revoke(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull Permission permission, @Nonnull String source, @Nonnull Protocol protocol, @Nonnull RuleTarget target, int beginPort, int endPort) throws CloudException, InternalException {
        String methodName = null;
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        params.put("SecurityGroupId", firewallId);
        //protocol
        if (protocol == null) {
            throw new InternalException("Revoke error: protocol can not be null!");
        } else {
            if (protocol.equals(Protocol.ANY)) {
                params.put("IpProtocol", AliyunNetworkCommon.FirewallIpProtocol.all.name());
            } else {
                params.put("IpProtocol", protocol.name().toLowerCase());
            }
        }
        //portRange
        params.put("PortRange", toPortRange(protocol, beginPort, endPort));
        //source or destination
        if (direction != null && direction.equals(Direction.INGRESS)) {
            methodName = "RevokeSecurityGroup";
            if (isCidrBlock(source)) {
                params.put("SourceCidrIp", source);
                String ipAddress = source.split("/")[0];
                if (AliyunIpAddress.isPublicIpAddress(ipAddress)) {
                    params.put("NicType", AliyunNetworkCommon.FirewallNicType.internet.name());
                } else {
                    params.put("NicType", AliyunNetworkCommon.FirewallNicType.intranet.name());
                }
            } else {
                params.put("SourceGroupId", source);
                params.put("NicType", AliyunNetworkCommon.FirewallNicType.intranet.name());
            }
        } else if (direction != null && direction.equals(Direction.EGRESS)) {
            methodName = "AuthorizeSecurityGroupEgress";
            if (target.getRuleTargetType().equals(RuleTargetType.CIDR)) {
                params.put("DestCidrIp", target.getCidr());
                String ipAddress = target.getCidr().split("/")[0];
                if (AliyunIpAddress.isPublicIpAddress(ipAddress)) {
                    params.put("NicType", AliyunNetworkCommon.FirewallNicType.internet.name());
                } else {
                    params.put("NicType", AliyunNetworkCommon.FirewallNicType.intranet.name());
                }
            } else {
                params.put("DestGroupId", target.getProviderFirewallId());
                params.put("NicType", AliyunNetworkCommon.FirewallNicType.intranet.name());
            }
        } else {
            stdLogger.warn("revoke firewall rule must assign Direction(INGRESS or EGRESS)!");
            throw new InternalException("revoke firewall rule must assign Direction(INGRESS or EGRESS)!");
        }
        //permission
        if (permission != null) {
            if (permission.equals(Permission.ALLOW)) {
                params.put("Policy", AliyunNetworkCommon.FirewallPermission.accept.name());
            } else {
                params.put("Policy", AliyunNetworkCommon.FirewallPermission.drop.name());
            }
        }
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, methodName, params);
        JSONObject response = method.post().asJson();
        getProvider().validateResponse(response);
    }

    private Firewall toFirewall (JSONObject response) throws CloudException, InternalException {
        Firewall firewall = new Firewall();
        try {
            firewall.setRegionId(response.getString("RegionId"));
            firewall.setProviderFirewallId(response.getString("SecurityGroupId"));
            if (!getProvider().isEmpty(response.getString("SecurityGroupName"))) {
                firewall.setName(response.getString("SecurityGroupName"));
            }
            if (!getProvider().isEmpty(response.getString("Description"))) {
                firewall.setDescription(response.getString("Description"));
            }
            if (!getProvider().isEmpty(response.getString("VpcId"))) {
                firewall.setProviderVlanId(response.getString("VpcId"));
            }
            firewall.setActive(true);
            firewall.setAvailable(true);
            firewall.setVisibleScope(VisibleScope.ACCOUNT_REGION);
            List<FirewallRule> firewallRules = new ArrayList<FirewallRule>();
            for (int i = 0; i < response.getJSONObject("Permissions").getJSONArray("Permission").length(); i++) {
                firewallRules.add(toFirewalLRule(
                        response.getJSONObject("Permissions").getJSONArray("Permission").getJSONObject(i),
                        firewall.getProviderFirewallId()));
            }
            firewall.setRules(firewallRules);
        } catch (JSONException e) {
            throw new InternalException(e);
        }
        return firewall;
    }

    private FirewallRule toFirewalLRule (JSONObject response, String firewallId) throws CloudException, InternalException {
        try {
            //protocol
            Protocol protocol = null;
            if (!getProvider().isEmpty(response.getString("IpProtocol"))) {
                if (response.getString("IpProtocol").equals(AliyunNetworkCommon.FirewallIpProtocol.all.name())) {
                    protocol = Protocol.ANY;
                } else if (response.getString("IpProtocol").equals(AliyunNetworkCommon.FirewallIpProtocol.tcp.name())){
                    protocol = Protocol.TCP;
                } else if (response.getString("IpProtocol").equals(AliyunNetworkCommon.FirewallIpProtocol.udp.name())) {
                    protocol = Protocol.UDP;
                } else if (response.getString("IpProtocol").equals(AliyunNetworkCommon.FirewallIpProtocol.icmp.name())) {
                    protocol = Protocol.ICMP;
                } else {
                    throw new InternalException("Parse response exception, dasien doesn't support " + response.getString("IpProtocol") + "!");
                }
            }
            //permission
            Permission permission = Permission.ALLOW;
            if (!getProvider().isEmpty(response.getString("Policy"))) {
                if (!response.getString("Policy").trim().equals(
                        getProvider().capitalize(AliyunNetworkCommon.FirewallPermission.drop.name()))) {
                    permission = Permission.DENY;
                }
            }
            //source endpoint
            RuleTarget sourceEndpoint = null;
            if (!getProvider().isEmpty(response.getString("SourceCidrIp"))) { //retrieve auth by cidr
                sourceEndpoint = RuleTarget.getCIDR(response.getString("SourceCidrIp"));
            } else if (!getProvider().isEmpty(response.getString("SourceGroupId"))) { //retrieve auth by security group within the same account
                if (!getProvider().isEmpty(response.getString("SourceGroupOwnerAccount"))) {
                    throw new InternalException("Dasein doesn't support cross account firewall rule authorization!");
                }
                sourceEndpoint = RuleTarget.getGlobal(response.getString("SourceGroupId"));
            }
            RuleTarget destinationEndpoint = null;
            //destination endpoint
            if (!getProvider().isEmpty(response.getString("DestCidrIp"))) {
                destinationEndpoint = RuleTarget.getCIDR(response.getString("DestCidrIp"));
            } else if (!getProvider().isEmpty(response.getString("DestGroupId"))) {
                if (!getProvider().isEmpty(response.getString("DestGroupOwnerAccount"))) {
                    throw new InternalException("Dasein doesn't support cross account firewall rule authorization!");
                }
                destinationEndpoint = RuleTarget.getGlobal(response.getString("DestGroupId"));
            }
            Direction direction = null;
            if (sourceEndpoint == null || destinationEndpoint == null) {
                if (sourceEndpoint != null) { //INGRESS rule
                    direction = Direction.INGRESS;
                    destinationEndpoint = RuleTarget.getGlobal(firewallId);
                } else { //EGRESS rule
                    direction = Direction.EGRESS;
                    sourceEndpoint = RuleTarget.getGlobal(firewallId);
                }
            } else {
                throw new InternalException("At least one of source endpoint and destination endpoint cannot be empty!");
            }
            //start port and end port
            int startPort = -1, endPort = -1;
            if (!getProvider().isEmpty(response.getString("PortRange"))) {
                startPort = Integer.valueOf(response.getString("PortRange").split("/")[0]);
                endPort = Integer.valueOf(response.getString("PortRange").split("/")[1]);
            }
            FirewallRule firewallRule = FirewallRule.getInstance(
                    null, firewallId, sourceEndpoint, direction, protocol, permission, destinationEndpoint, startPort, endPort);
            if (response.getInt("Priority") >= 0) {
                firewallRule.withPrecedence(response.getInt("Priority"));
            }
            return firewallRule;
        } catch (JSONException e) {
            throw new InternalException(e);
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
        if (startPort > 0 && endPort >= startPort) { //set to customized range
            return startPort + "/" + endPort;
        } else { //set to default range
            if (protocol != null && (protocol.equals(Protocol.ICMP) || protocol.equals(Protocol.ANY))) {
                return "-1/-1";
            } else if (protocol != null && (protocol.equals(Protocol.TCP) || protocol.equals(Protocol.UDP))) {
                return "1/65535";
            } else {
                //Not Support GRE protocol.
                throw new InternalException("Invalid PortRange, from " + startPort + " to " + endPort);
            }
        }
    }

    /**
     * verify the CIDR block format source.
     * @param source CIDR string
     * @return true if source is CIDR block; false if not.
     */
    private boolean isCidrBlock (String source) {
        if (!getProvider().isEmpty(source)) {
            Pattern pattern = Pattern.compile("^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])(\\/(\\d|[1-2]\\d|3[0-2]))?$");
            Matcher matcher = pattern.matcher(source);
            return matcher.find();
        }
        return false;
    }

}
