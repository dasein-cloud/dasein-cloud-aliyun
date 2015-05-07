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

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.network.*;

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
public class AliyunFirewall extends AbstractFirewallSupport<Aliyun> implements FirewallSupport {

    private static final Logger stdLogger = Aliyun.getStdLogger(AliyunFirewall.class);

    private transient volatile AliyunFirewallCapabilities capabilities;

    public AliyunFirewall(@Nonnull Aliyun provider) {
        super(provider);
    }

    public void delete(@Nonnull String firewallId) throws InternalException, CloudException {

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
        return super.authorize(firewallId, direction, permission, sourceEndpoint, protocol, destinationEndpoint, beginPort, endPort, precedence);
    }

    @Nonnull
    @Override
    public String authorize(@Nonnull String firewallId, @Nonnull FirewallRuleCreateOptions options) throws CloudException, InternalException {
        return super.authorize(firewallId, options);
    }

    @Nonnull
    @Override
    public String create(@Nonnull FirewallCreateOptions options) throws InternalException, CloudException {
        Map<String, Object> params = new HashMap<String, Object>();
        return super.create(options);
    }

    @Nullable
    @Override
    public Map<FirewallConstraints.Constraint, Object> getActiveConstraintsForFirewall(@Nonnull String firewallId) throws CloudException, InternalException {
        return super.getActiveConstraintsForFirewall(firewallId);
    }

    @Nullable
    @Override
    public Firewall getFirewall(@Nonnull String firewallId) throws InternalException, CloudException {
        return super.getFirewall(firewallId);
    }

    @Nonnull
    @Override
    public Iterable<ResourceStatus> listFirewallStatus() throws InternalException, CloudException {
        return super.listFirewallStatus();
    }

    @Override
    public void revoke(@Nonnull String providerFirewallRuleId) throws InternalException, CloudException {
        super.revoke(providerFirewallRuleId);
    }

    @Override
    public void revoke(@Nonnull String firewallId, @Nonnull String source, @Nonnull Protocol protocol, int beginPort, int endPort) throws CloudException, InternalException {
        super.revoke(firewallId, source, protocol, beginPort, endPort);
    }

    @Override
    public void revoke(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull String source, @Nonnull Protocol protocol, int beginPort, int endPort) throws CloudException, InternalException {
        super.revoke(firewallId, direction, source, protocol, beginPort, endPort);
    }

    @Override
    public void revoke(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull Permission permission, @Nonnull String source, @Nonnull Protocol protocol, int beginPort, int endPort) throws CloudException, InternalException {
        super.revoke(firewallId, direction, permission, source, protocol, beginPort, endPort);
    }

    @Override
    public void revoke(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull Permission permission, @Nonnull String source, @Nonnull Protocol protocol, @Nonnull RuleTarget target, int beginPort, int endPort) throws CloudException, InternalException {
        super.revoke(firewallId, direction, permission, source, protocol, target, beginPort, endPort);
    }
}
