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

import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.network.*;
import javax.annotation.Nullable;

/**
 * Created by Jane Wang on 5/12/2015.
 *
 * @author Jane Wang
 * @since 2015.5.1
 */
public class ECSNetworkServices extends AbstractNetworkServices<Aliyun> {

    public ECSNetworkServices (Aliyun cloud) { super(cloud); }

    @Nullable
    @Override
    public DNSSupport getDnsSupport() {
        //TODO
        return super.getDnsSupport();
    }

    @Nullable
    @Override
    public FirewallSupport getFirewallSupport() {
        return new AliyunFirewall(getProvider());
    }

    @Nullable
    @Override
    public IpAddressSupport getIpAddressSupport() {
        return new AliyunIPAddress(getProvider());
    }

    @Nullable
    @Override
    public LoadBalancerSupport getLoadBalancerSupport() {
        //TODO
        return super.getLoadBalancerSupport();
    }

    @Nullable
    @Override
    public NetworkFirewallSupport getNetworkFirewallSupport() {
        //TODO check not support?
        return null;
    }

    @Nullable
    @Override
    public VLANSupport getVlanSupport() {
        //TODO
        return super.getVlanSupport();
    }

    @Nullable
    @Override
    public VPNSupport getVpnSupport() {
        //TODO not support?
        return super.getVpnSupport();
    }


}
