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

import org.dasein.cloud.InternalException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.TimeZone;

/**
 * Created by Jane Wang on 5/7/2015.
 *
 * @author Jane Wang
 * @since 2015.05.01
 *
 */
public class AliyunNetworkCommon {

   public static final int DefaultPageSize = 10; //from 10 to 50, default is 10

   //ip address
   public static enum IpAddressStatus {Associating, Unassociating, InUse, Available};
   public static enum InternetChargeType {PayByBandwidth, PayByTraffic};
   public static final String DefaultIpAddressBandwidth = "5Mbps";

   //Firewall
   public static enum FirewallPermission {accept, drop};
   public static enum FirewallNicType {internet, intranet};
   public static enum FirewallIpProtocol {tcp, udp, icmp, gre, all};

   //Network
   public static enum RouteEntryNextHopType {instance, tunnel};
   public static enum VlanStatus {Pending, Available};
   public static enum SubnetStatus {Pending, Available};

   //Load Balancer
   public static enum AliyunLbScheduleAlgorithm {wrr, wlc};
   public static enum AliyunLbSwitcher {on, off};
   public static enum AliyunLbPersistenceType {insert, server};
   public static enum LoadBalancerAddressType {internet, intranet};
   public static enum AliyunLbState {inactive, active, locked};
   public static enum AliyunLbEndpointState {normal, abnormal, unavailable};
   public static final int DefaultServerWeight = 50;
   public static final int DefaultPersistenceTimeout = 5 * 60;
   public static final int DefaultLoadBalancerBandwidth = -1;

}
