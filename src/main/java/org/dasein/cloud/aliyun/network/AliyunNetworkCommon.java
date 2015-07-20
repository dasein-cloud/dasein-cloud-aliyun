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

import java.util.HashMap;
import java.util.Map;

import org.apache.http.client.ResponseHandler;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.aliyun.util.requester.AliyunHttpClientBuilderFactory;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestBuilder;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestExecutor;
import org.dasein.cloud.aliyun.util.requester.AliyunResponseHandlerWithMapper;
import org.dasein.cloud.util.requester.DriverToCoreMapper;
import org.dasein.cloud.util.requester.streamprocessors.StreamToJSONObjectProcessor;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by Jane Wang on 5/7/2015.
 *
 * @author Jane Wang
 * @since 2015.05.01
 *
 */
public class AliyunNetworkCommon {

	private static final Logger stdLogger = Aliyun
			.getStdLogger(AliyunNetworkCommon.class);

	public static final int DefaultPageSize = 10; // from 10 to 50, default is
													// 10

	// ip address
	public static enum IpAddressStatus {
		Associating, Unassociating, InUse, Available
	};

	public static enum InternetChargeType {
		PayByBandwidth, PayByTraffic
	};

	public static final String DefaultIpAddressBandwidth = "5Mbps";

	// Firewall
	public static enum FirewallPermission {
		accept, drop
	};

	public static enum FirewallNicType {
		internet, intranet
	};

	public static enum FirewallIpProtocol {
		tcp, udp, icmp, gre, all
	};

	// Network
	public static enum RouteEntryNextHopType {
		instance, tunnel
	};

	public static enum VlanStatus {
		Pending, Available
	};

	public static enum SubnetStatus {
		Pending, Available
	};

	// Load Balancer
	public static enum AliyunLbScheduleAlgorithm {
		wrr, wlc
	};

	public static enum AliyunLbSwitcher {
		on, off
	};

	public static enum AliyunLbPersistenceType {
		insert, server
	};

	public static enum LoadBalancerAddressType {
		internet, intranet
	};

	public static enum AliyunLbState {
		inactive, active, locked
	};

	public static enum AliyunLbEndpointState {
		normal, abnormal, unavailable
	};

	public static final int DefaultServerWeight = 50;
	public static final int DefaultPersistenceTimeout = 5 * 60;
	public static final int DefaultLoadBalancerBandwidth = -1;
	
	public static enum RequestMethod { GET, POST, DELETE, PUT }
	
	public static <V> V executeDefaultRequest(Aliyun provider, Map<String, Object> params, 
			AliyunRequestBuilder.Category category, String action, RequestMethod requestMethod, 
			boolean clientToken, ResponseHandler<V> handler) throws InternalException, CloudException {

		AliyunRequestBuilder builder = null;
		if (requestMethod.equals(RequestMethod.GET)) { 
			builder = AliyunRequestBuilder.get();
			for (String key : params.keySet()) {
				builder = builder.parameter(key, params.get(key));
			}
		} else if (requestMethod.equals(RequestMethod.POST)) {
			builder = AliyunRequestBuilder.post();
			builder = builder.entity(params);
		} else {
			throw new InternalException("Not supported request method " + requestMethod.name());
		}
		builder = builder.provider(provider).category(category).parameter("Action", action);
		if (clientToken) {
			builder = builder.clientToken(true);
		}

		return new AliyunRequestExecutor<V>(
				provider,
				AliyunHttpClientBuilderFactory.newHttpClientBuilder(), 
				builder.build(),
				handler).execute();

	}

}
