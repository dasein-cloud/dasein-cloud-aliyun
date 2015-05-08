/*
 * *
 *  * Copyright (C) 2009-2015 Dell, Inc.
 *  * See annotations for authorship information
 *  *
 *  * ====================================================================
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  * ====================================================================
 *
 */
package org.dasein.cloud.aliyun.network;

import com.fasterxml.jackson.core.JsonToken;
import com.sun.corba.se.spi.orb.Operation;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.util.InetAddressUtils;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.aliyun.AliyunMethod;
import org.dasein.cloud.network.*;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Created by Jane Wang on 5/7/2015.
 *
 * @author Jane Wang
 * @since 2015.5.1
 *
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
        return null;
    }

    @Nonnull
    @Override
    public String authorize(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull Permission permission, @Nonnull RuleTarget sourceEndpoint, @Nonnull Protocol protocol, @Nonnull RuleTarget destinationEndpoint, int beginPort, int endPort, @Nonnegative int precedence) throws CloudException, InternalException {
        //valid direction
        if (direction.equals(Direction.EGRESS)) {
            throw new OperationNotSupportedException("Aliyun doesn't support EGRESS rule!");
        }
        //valid ip address type
        if (!StringUtils.isEmpty(sourceEndpoint.getCidr())) {
            String ipAddress = sourceEndpoint.getCidr().split("/")[0];
            if (StringUtils.isEmpty(ipAddress) || !InetAddressUtils.isIPv4Address(ipAddress)) {
                throw new OperationNotSupportedException("Aliyun support IPV4 address only!");
            }
        }
        return authorize(firewallId, FirewallRuleCreateOptions.getInstance(direction, permission, sourceEndpoint, protocol, destinationEndpoint, beginPort, endPort));
    }

    @Nonnull
    @Override
    public String authorize(@Nonnull String firewallId, @Nonnull FirewallRuleCreateOptions options) throws CloudException, InternalException {
        Map<String, Object> params = new HashMap<String, Object>();
        AliyunMethod method = null;
        if (options.getDestinationEndpoint() == null || options.getDestinationEndpoint().getRuleTargetType().equals(RuleTargetType.VLAN)
                || options.getDestinationEndpoint().getRuleTargetType().equals(RuleTargetType.VM)) {
            //authorize
            params.put("RegionId", getProvider().getContext().getRegionId());
            params.put("SecurityGroupId", firewallId);
            if (options.getProtocol() != null) {
                if (options.getProtocol().equals(Protocol.ANY)) {
                    params.put("IpProtocol", AliyunNetworkCommon.IpProtocolAll);
                } else { //no validation for IPSEC, will throw 400 error when invoke
                    params.put("IpProtocol", options.getProtocol().name().toLowerCase());
                }
            }
            if (options.getPortRangeStart() > 0 && options.getPortRangeEnd() >= options.getPortRangeStart()) {
                params.put("PortRange", options.getPortRangeStart() + "/" + options.getPortRangeEnd());
            } else { //default value
                if (options.getProtocol().equals(Protocol.ICMP) || options.getProtocol().equals(Protocol.ANY)) {
                    params.put("PortRange", "-1/-1");
                } else if (options.getProtocol().equals(Protocol.TCP) || options.getProtocol().equals(Protocol.UDP)) {
                    params.put("PortRange", "1/65535");
                } else {
                    throw new InternalException("Invalid PortRange, from " + options.getPortRangeStart() + " to " + options.getPortRangeEnd());
                }
            }
            if (options.getSourceEndpoint().getRuleTargetType().equals(RuleTargetType.CIDR)
                    && !StringUtils.isEmpty(options.getSourceEndpoint().getCidr())) {
                params.put("SourceCidrIp", options.getSourceEndpoint().getCidr());
            }
            if (options.getPermission().equals(Permission.ALLOW)) {
                params.put("Policy", AliyunNetworkCommon.AliyunFirewallPermission.ACCEPT.name().toLowerCase());
            } else if (options.getPermission().equals(Permission.DENY)) {
                params.put("Policy", AliyunNetworkCommon.AliyunFirewallPermission.DROP.name().toLowerCase());
            }
            method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "AuthorizeSecurityGroup", params);
            method.post();
        }
        return null;
    }

    @Nonnull
    @Override
    public String create(@Nonnull FirewallCreateOptions options) throws InternalException, CloudException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("SecurityGroupName", options.getName());
        params.put("VpcId", options.getProviderVlanId());
        params.put("RegionId", getProvider().getContext().getRegionId());
        params.put("Description", options.getDescription());
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "AllocateEipAddress", params);
        try {
            JSONObject response = method.get().asJson();
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
        params.put("RegionId", getProvider().getContext().getRegionId());
        params.put("SecurityGroupId", firewallId);
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeSecurityGroupAttribute", params);
        JSONObject response = method.get().asJson();
        //TODO check VLAN or VM and generate Rules for firewall
        return super.getFirewall(firewallId);

    }

    @Nonnull
    @Override
    public Iterable<ResourceStatus> listFirewallStatus() throws InternalException, CloudException {
        return super.listFirewallStatus();
    }

    @Override
    public void revoke(@Nonnull String providerFirewallRuleId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("Aliyun doesn't support revoke by firewallRuleId!");
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
        params.put("RegionId", getProvider().getContext().getRegionId());
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
        if (!StringUtils.isEmpty(source)) {
            params.put("SourceCidrIp", source);
        }
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
}
