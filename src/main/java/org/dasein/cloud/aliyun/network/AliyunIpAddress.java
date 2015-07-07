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


import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.util.InetAddressUtils;
import org.apache.log4j.Logger;
import org.dasein.cloud.*;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.aliyun.util.requester.AliyunHttpClientBuilderFactory;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestBuilder;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestExecutor;
import org.dasein.cloud.aliyun.util.requester.AliyunResponseHandlerWithMapper;
import org.dasein.cloud.aliyun.util.requester.AliyunValidateJsonResponseHandler;
import org.dasein.cloud.network.*;
import org.dasein.cloud.util.requester.DriverToCoreMapper;
import org.dasein.cloud.util.requester.streamprocessors.StreamToJSONObjectProcessor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Jane Wang on 5/7/2015.
 *
 * @author Jane Wang
 * @since 2015.05.01
 */
public class AliyunIpAddress extends AbstractIpAddressSupport<Aliyun> {

    private static final Logger stdLogger = Aliyun.getStdLogger(AliyunIpAddress.class);

    private transient volatile AliyunIpAddressCapabilities capabilities;

    public AliyunIpAddress(@Nonnull Aliyun provider) {
        super(provider);
    }

    public void assign(@Nonnull String addressId, @Nonnull String serverId) throws InternalException, CloudException {
        
    	Map<String, Object> params = new HashMap<String, Object>();
        params.put("AllocationId", addressId);
        params.put("InstanceId", serverId);

        HttpUriRequest request = AliyunRequestBuilder.post()
        		.provider(getProvider())
        		.category(AliyunRequestBuilder.Category.ECS)
        		.parameter("Action", "AssociateEipAddress")
        		.entity(params)
        		.build();
        
        new AliyunRequestExecutor<Void>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                new AliyunValidateJsonResponseHandler(getProvider())).execute();
        
    }

    public void assignToNetworkInterface(@Nonnull String addressId, @Nonnull String nicId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("Aliyun doesn't support assign IP address to Network Interface!");
    }

    public String forward(@Nonnull String addressId, int publicPort, @Nonnull Protocol protocol, int privatePort, @Nonnull String onServerId)
            throws InternalException, CloudException {
        throw new OperationNotSupportedException("Aliyun doesn't support IP forward!");
    }

    public IPAddressCapabilities getCapabilities() throws CloudException, InternalException {
        if (capabilities == null) {
            capabilities = new AliyunIpAddressCapabilities(getProvider());
        }
        return capabilities;
    }

    public IpAddress getIpAddress(@Nonnull String addressId) throws InternalException, CloudException {
 
        HttpUriRequest request = AliyunRequestBuilder.get()
        		.provider(getProvider())
        		.category(AliyunRequestBuilder.Category.ECS)
        		.parameter("Action", "DescribeEipAddresses")
        		.parameter("RegionId", getContext().getRegionId())
        		.parameter("AllocationId", addressId)
        		.build();
        
        ResponseHandler<IpAddress> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, IpAddress>(
           		new StreamToJSONObjectProcessor(),
           		new DriverToCoreMapper<JSONObject, IpAddress>() {
                       @Override
                       public IpAddress mapFrom(JSONObject json) {
                           try {
                               JSONArray eipAddresses = json.getJSONObject("EipAddresses").getJSONArray("EipAddress");
                               if (eipAddresses != null && eipAddresses.length() > 0) {
                            	   return toIpAddress(eipAddresses.getJSONObject(0));
                               }
                               return null;
                           } catch (InternalException internalException) {
                               stdLogger.error("Failed to validate response", internalException);
                               throw new RuntimeException(internalException.getMessage());
                           } catch (JSONException e) {
                        	   stdLogger.error("Failed to parse EIP address", e);
                        	   throw new RuntimeException(e.getMessage());
                           }
                       }
                   },
                   JSONObject.class);
        
        return new AliyunRequestExecutor<IpAddress>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                responseHandler).execute();
        		
    }

    public boolean isSubscribed() throws CloudException, InternalException {
        return true;
    }

    public Iterable<IpAddress> listIpPool(@Nonnull IPVersion version, boolean unassignedOnly) throws InternalException, CloudException {
        
    	if (!version.equals(IPVersion.IPV4)) {
            return Collections.emptyList();
        }
        
    	List<IpAddress> allIpAddresses = new ArrayList<IpAddress>();
        final AtomicInteger currentPageNumber = new AtomicInteger(1);
        final AtomicInteger maxPageNumber = new AtomicInteger(1);
        
        ResponseHandler<List<IpAddress>> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, List<IpAddress>>(
        		new StreamToJSONObjectProcessor(),
        		new DriverToCoreMapper<JSONObject, List<IpAddress>>() {
                    @Override
                    public List<IpAddress> mapFrom(JSONObject json) {
                        try {
                        	List<IpAddress> ipAddresses = new ArrayList<IpAddress>();
                        	JSONArray eipAddresses = json.getJSONObject("EipAddresses").getJSONArray("EipAddress");
                            for (int i = 0; i < eipAddresses.length(); i++) {
                                JSONObject eipAddress = eipAddresses.getJSONObject(i);
                                ipAddresses.add(toIpAddress(eipAddress));
                            }
                            maxPageNumber.addAndGet(json.getInt("TotalCount") / AliyunNetworkCommon.DefaultPageSize
                                    + (json.getInt("TotalCount") % AliyunNetworkCommon.DefaultPageSize > 0 ? 1 : 0));
                            currentPageNumber.incrementAndGet();
                            return ipAddresses;
                        } catch (InternalException internalException) {
                            stdLogger.error("Failed to validate response", internalException);
                            throw new RuntimeException(internalException.getMessage());
                        } catch (JSONException e) {
                        	stdLogger.error("Parsing firewall failed", e);
                        	throw new RuntimeException(e.getMessage());
						}
                    }
                },
                JSONObject.class);
        
        do {
            
            AliyunRequestBuilder builder = AliyunRequestBuilder.get()
            		.provider(getProvider())
            		.category(AliyunRequestBuilder.Category.ECS)
            		.parameter("Action", "DescribeEipAddresses")
            		.parameter("RegionId", getContext().getRegionId())
            		.parameter("PageSize", AliyunNetworkCommon.DefaultPageSize)
            		.parameter("PageNumber", currentPageNumber);
            if (unassignedOnly) {
                builder.parameter("Status", AliyunNetworkCommon.IpAddressStatus.Available.name());
            }
            HttpUriRequest request = builder.build();
            
            allIpAddresses.addAll(new AliyunRequestExecutor<List<IpAddress>>(getProvider(),
                    AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                    request,
                    responseHandler).execute());
                    
        } while (currentPageNumber.intValue() < maxPageNumber.intValue());
        
        return allIpAddresses;
    }

    /**
     * Return status contains: Associating, Unassociating, InUse and Avaiable
     * @param version the version of the IP protocol for which you are looking for IP addresses
     * @return resourceStatus list
     * @throws InternalException
     * @throws CloudException
     */
    public Iterable<ResourceStatus> listIpPoolStatus(@Nonnull IPVersion version) throws InternalException, CloudException {
        if (!version.equals(IPVersion.IPV4)) {
            return Collections.emptyList();
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("RegionId", getContext().getRegionId());
        params.put("PageSize", AliyunNetworkCommon.DefaultPageSize);
        List<ResourceStatus> allResourceStatuses = new ArrayList<ResourceStatus>();
        final AtomicInteger currentPageNumber = new AtomicInteger(1);
        final AtomicInteger maxPageNumber = new AtomicInteger(1);
        
        ResponseHandler<List<ResourceStatus>> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, List<ResourceStatus>>(
        		new StreamToJSONObjectProcessor(),
        		new DriverToCoreMapper<JSONObject, List<ResourceStatus>>() {
                    @Override
                    public List<ResourceStatus> mapFrom(JSONObject json) {
                        try {
                        	List<ResourceStatus> resourceStatuses = new ArrayList<ResourceStatus>();
                        	JSONArray eipAddresses = json.getJSONObject("EipAddresses").getJSONArray("EipAddress");
                            for (int i = 0; i < eipAddresses.length(); i++) {
                                JSONObject eipAddress = eipAddresses.getJSONObject(i);
                                resourceStatuses.add(new ResourceStatus(eipAddress.getString("AllocationId"), eipAddress.getString("Status")));
                            }
                            maxPageNumber.addAndGet(json.getInt("TotalCount") / AliyunNetworkCommon.DefaultPageSize
                                    + (json.getInt("TotalCount") % AliyunNetworkCommon.DefaultPageSize > 0 ? 1 : 0));
                            currentPageNumber.incrementAndGet();
                            return resourceStatuses;
                        } catch (JSONException e) {
                        	stdLogger.error("Parsing firewall failed", e);
                        	throw new RuntimeException(e.getMessage());
						}
                    }
                },
                JSONObject.class);
        
        do {
            
        	HttpUriRequest request = AliyunRequestBuilder.get()
            		.provider(getProvider())
            		.category(AliyunRequestBuilder.Category.ECS)
            		.parameter("Action", "DescribeEipAddresses")
            		.parameter("RegionId", getContext().getRegionId())
            		.parameter("PageSize", AliyunNetworkCommon.DefaultPageSize)
            		.parameter("PageNumber", currentPageNumber)
            		.build();
        	
        	allResourceStatuses.addAll(new AliyunRequestExecutor<List<ResourceStatus>>(getProvider(),
                    AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                    request,
                    responseHandler).execute());
        	
        } while (currentPageNumber.intValue() < maxPageNumber.intValue());
        
        return allResourceStatuses;
    }

    public void releaseFromPool(@Nonnull String addressId) throws InternalException, CloudException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("AllocationId", addressId);
        
        HttpUriRequest request = AliyunRequestBuilder.post()
        		.provider(getProvider())
        		.category(AliyunRequestBuilder.Category.ECS)
        		.parameter("Action", "ReleaseEipAddress")
        		.entity(params)
        		.build();
        
        new AliyunRequestExecutor<Void>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                new AliyunValidateJsonResponseHandler(getProvider())).execute();
    }

    public void releaseFromServer(@Nonnull String addressId) throws InternalException, CloudException {
        
    	Map<String, Object> params = new HashMap<String, Object>();
        IpAddress ipAddress = getIpAddress(addressId);
        if (ipAddress != null && !getProvider().isEmpty(ipAddress.getServerId())) {
            
        	params.put("AllocationId", addressId);
            params.put("InstanceId", ipAddress.getServerId());
            
            HttpUriRequest request = AliyunRequestBuilder.post()
            		.provider(getProvider())
            		.category(AliyunRequestBuilder.Category.ECS)
            		.parameter("Action", "UnassociateEipAddresss")
            		.entity(params)
            		.build();
            
            new AliyunRequestExecutor<Void>(getProvider(),
                    AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                    request,
                    new AliyunValidateJsonResponseHandler(getProvider())).execute();
        }
    }

    public String request(@Nonnull IPVersion version) throws InternalException, CloudException {
        
    	if (!version.equals(IPVersion.IPV4)) {
            throw new InternalException("Aliyun supports IPV4 ip address only!");
        }
        
        HttpUriRequest request = AliyunRequestBuilder.post()
        		.provider(getProvider())
        		.category(AliyunRequestBuilder.Category.ECS)
        		.parameter("Action", "AllocateEipAddress")
        		.parameter("RegionId", getContext().getRegionId())
        		.parameter("InternetChargeType", AliyunNetworkCommon.InternetChargeType.PayByTraffic.name())
        		.parameter("Bandwidth", AliyunNetworkCommon.DefaultIpAddressBandwidth)
        		.clientToken(true)
        		.build();
        
        return (String) new AliyunRequestExecutor<Map<String, Object>>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                AliyunNetworkCommon.getDefaultResponseHandler(getProvider(), "AllocationId")).execute().get("AllocationId");
        
    }

    public String requestForVLAN(@Nonnull IPVersion version) throws InternalException, CloudException {
        return requestForVLAN(version, null);
    }

    public String requestForVLAN(@Nonnull IPVersion version, @Nonnull String vlanId) throws InternalException, CloudException {
        return request(version);
    }

    /**
     * Ip addresses for vlan use only:
     * A class: 10.0.0.0 - 10.255.255.255 (7/24)
     * B class: 172.16.0.0 - 172.31.255.255 (14/16)
     * C class 192.168.0.0 - 192.168.255.255 (21/8)
     * @param ipAddress IP address
     * @return true - public ip address; false - private ip address
     * @throws InternalException
     */
    public static boolean isPublicIpAddress(String ipAddress) throws InternalException {
        if (!InetAddressUtils.isIPv4Address(ipAddress)) {
            throw new InternalException("Aliyun supports IPV4 address only!");
        }
        if (ipAddress.startsWith("10.") || ipAddress.startsWith("192.168.")) {
            return false;
        }
        if (ipAddress.startsWith("172.")) {
            String[] ipSegments = ipAddress.split(".");
            Integer segment = Integer.valueOf(ipSegments[1]);
            if (segment >= 16 && segment <= 31) {
                return false;
            }
        }
        return true;
    }

    private IpAddress toIpAddress(JSONObject response) throws InternalException {
        try {
            IpAddress ipAddress = new IpAddress();
            ipAddress.setAddress(response.getString("IpAddress"));
            ipAddress.setAddressType(AddressType.PUBLIC);
            ipAddress.setForVlan(true);
            ipAddress.setIpAddressId(response.getString("AllocationId"));
            ipAddress.setRegionId(response.getString("RegionId"));
            if (!getProvider().isEmpty(response.getString("InstanceId"))) {
                ipAddress.setServerId(response.getString("InstanceId"));
            }
            ipAddress.setReserved(false);
            ipAddress.setVersion(IPVersion.IPV4);
            return ipAddress;
        } catch (JSONException e) {
            throw new InternalException(e);
        }
    }
}
