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

import org.dasein.cloud.*;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.network.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

/**
 * Created by jwang7 on 5/7/2015.
 *
 * @author Jane Wang
 * @since 2015.05.01
 *
 */
public class AliyunFirewallCapabilities extends AbstractCapabilities<Aliyun> implements FirewallCapabilities {

    public AliyunFirewallCapabilities(@Nonnull Aliyun provider) {
        super(provider);
    }

    @Nonnull
    public FirewallConstraints getFirewallConstraintsForCloud() throws InternalException, CloudException {
        return FirewallConstraints.getInstance();
    }

    @Nonnull
    public String getProviderTermForFirewall(@Nonnull Locale locale) {
        return "Security Group";
    }

    @Nullable
    public VisibleScope getFirewallVisibleScope() {
        return null; //TODO
    }

    @Nonnull
    public Requirement identifyPrecedenceRequirement(boolean inVlan) throws InternalException, CloudException {
        return Requirement.NONE;
    }

    public boolean isZeroPrecedenceHighest() throws InternalException, CloudException {
        return true;
    }

    @Nonnull
    public Iterable<RuleTargetType> listSupportedDestinationTypes(boolean inVlan) throws InternalException, CloudException {
        //If not specify the direction, then a maximum set of rule target types will be returned.
        return listSupportedDestinationTypes(inVlan, Direction.INGRESS);
    }

    @Nonnull
    public Iterable<RuleTargetType> listSupportedDestinationTypes(boolean inVlan, Direction direction) throws InternalException, CloudException {
        if (direction.equals(Direction.INGRESS)) {
            return Collections.unmodifiableList(Arrays.asList(RuleTargetType.GLOBAL, RuleTargetType.VM));
        } else {
            return Collections.emptyList();
        }
    }

    @Nonnull
    public Iterable<Direction> listSupportedDirections(boolean inVlan) throws InternalException, CloudException {
        return Collections.singletonList(Direction.INGRESS);
    }

    @Nonnull
    public Iterable<Permission> listSupportedPermissions(boolean inVlan) throws InternalException, CloudException {
        return Collections.unmodifiableList(Arrays.asList(Permission.ALLOW, Permission.DENY));
    }

    @Nonnull
    public Iterable<Protocol> listSupportedProtocols(boolean inVlan) throws InternalException, CloudException {
        return Collections.unmodifiableList(Arrays.asList(Protocol.TCP, Protocol.UDP, Protocol.ICMP, Protocol.ANY));
    }

    @Nonnull
    public Iterable<RuleTargetType> listSupportedSourceTypes(boolean inVlan) throws InternalException, CloudException {
        return listSupportedSourceTypes(inVlan, Direction.INGRESS);
    }

    @Nonnull
    public Iterable<RuleTargetType> listSupportedSourceTypes(boolean inVlan, Direction direction) throws InternalException, CloudException {
        return Collections.unmodifiableList(Arrays.asList(RuleTargetType.CIDR, RuleTargetType.GLOBAL));
    }

    public boolean requiresRulesOnCreation() throws CloudException, InternalException {
        return false;
    }

    @Nonnull
    public Requirement requiresVLAN() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    public boolean supportsRules(@Nonnull Direction direction, @Nonnull Permission permission, boolean inVlan) throws CloudException, InternalException {
        return direction.equals(Direction.INGRESS);
    }

    public boolean supportsFirewallCreation(boolean inVlan) throws CloudException, InternalException {
        return true;
    }

    public boolean supportsFirewallDeletion() throws CloudException, InternalException {
        return true;
    }

 }
