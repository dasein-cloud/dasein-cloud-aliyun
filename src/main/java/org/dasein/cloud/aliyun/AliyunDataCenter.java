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
package org.dasein.cloud.aliyun;

import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.aliyun.util.requester.AliyunHttpClientBuilderFactory;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestBuilder;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestExecutor;
import org.dasein.cloud.aliyun.util.requester.AliyunResponseHandlerWithMapper;
import org.dasein.cloud.dc.AbstractDataCenterServices;
import org.dasein.cloud.dc.DataCenter;
import org.dasein.cloud.dc.DataCenterCapabilities;
import org.dasein.cloud.dc.DataCenterServices;
import org.dasein.cloud.dc.Region;
import org.dasein.cloud.util.APITrace;
import org.dasein.cloud.util.Cache;
import org.dasein.cloud.util.CacheLevel;
import org.dasein.cloud.util.requester.DriverToCoreMapper;
import org.dasein.cloud.util.requester.streamprocessors.StreamToJSONObjectProcessor;
import org.dasein.util.uom.time.TimePeriod;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Created by Jeffrey Yan on 5/5/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.05.01
 */
public class AliyunDataCenter extends AbstractDataCenterServices<Aliyun> implements DataCenterServices {

    static private final Logger stdLogger = Aliyun.getStdLogger(AliyunDataCenter.class);

    private transient volatile AliyunDataCenterCapabilities capabilities;

    public AliyunDataCenter(Aliyun provider) {
        super(provider);
    }

    @Override
    public @Nonnull DataCenterCapabilities getCapabilities() throws InternalException, CloudException {
        if( capabilities == null ) {
            capabilities = new AliyunDataCenterCapabilities(getProvider());
        }
        return capabilities;
    }

    @Override
    public @Nullable DataCenter getDataCenter(@Nonnull String providerDataCenterId) throws InternalException, CloudException {
        ProviderContext context = getProvider().getContext();
        if (context == null) {
            throw new InternalException("No context exists for this request");
        }

        String regionId = context.getRegionId();
        if (regionId == null) {
            throw new InternalException("No region is established for this request");
        }

        for (DataCenter dataCenter : listDataCenters(regionId)) {
            if (dataCenter.getProviderDataCenterId().equals(providerDataCenterId)) {
                return dataCenter;
            }
        }
        return null;
    }

    @Override
    public @Nullable Region getRegion(@Nonnull String providerRegionId) throws InternalException, CloudException {
        for (Region region : listRegions()) {
            if (region.getProviderRegionId().equals(providerRegionId)) {
                return region;
            }
        }
        return null;
    }

    @Override
    public @Nonnull Iterable<DataCenter> listDataCenters(@Nonnull final String providerRegionId) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "DataCenter.listDataCenters");
        try {
            ProviderContext context = getProvider().getContext();
            if (context == null) {
                throw new InternalException("No context was set for this request");
            }

            Cache<DataCenter> cache = null;
            Collection<DataCenter> dataCenters;

            if (providerRegionId.equals(context.getRegionId())) {//only cache context's region
                cache = Cache.getInstance(getProvider(), "dataCenters", DataCenter.class, CacheLevel.REGION_ACCOUNT,
                        TimePeriod.valueOf(1, "day"));
                dataCenters = (Collection<DataCenter>) cache.get(context);
                if (dataCenters != null) {
                    return dataCenters;
                }
            }

            HttpUriRequest request = AliyunRequestBuilder.get()
                    .provider(getProvider())
                    .category(AliyunRequestBuilder.Category.ECS)
                    .parameter("Action", "DescribeZones")
                    .parameter("RegionId", providerRegionId)
                    .build();
            ResponseHandler<Collection<DataCenter>> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, Collection<DataCenter>>(
                    new StreamToJSONObjectProcessor(),
                    new DriverToCoreMapper<JSONObject, Collection<DataCenter>>() {
                        @Override
                        public Collection<DataCenter> mapFrom(JSONObject json) {
                            try {
                                Collection<DataCenter> result = new ArrayList<DataCenter>();

                                JSONArray dataCentersJson = json.getJSONObject("Zones").getJSONArray("Zone");
                                for (int i = 0; i < dataCentersJson.length(); i++) {
                                    JSONObject dataCenterJson = dataCentersJson.getJSONObject(i);
                                    String zoneId = dataCenterJson.getString("ZoneId");
                                    DataCenter dataCenter = new DataCenter(zoneId, zoneId, providerRegionId, true,
                                            true);
                                    JSONArray resourceTypesJson = dataCenterJson
                                            .getJSONObject("AvailableResourceCreation").getJSONArray("ResourceTypes");
                                    List<String> resourceTypes = new ArrayList<String>();
                                    for (int j = 0; j < resourceTypesJson.length(); j++) {
                                        resourceTypes.add(resourceTypesJson.getString(j));
                                    }
                                    dataCenter.setCompute(resourceTypes.contains("Instance"));
                                    dataCenter.setStorage(resourceTypes.contains("Disk"));
                                    result.add(dataCenter);
                                }
                                return result;
                            } catch (JSONException jsonException) {
                                stdLogger.error("Failed to parse JSON", jsonException);
                                throw new RuntimeException(jsonException.getMessage());
                            }
                        }
                    },
                    JSONObject.class);

            dataCenters = new AliyunRequestExecutor<Collection<DataCenter>>(getProvider(),
                    AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                    request,
                    responseHandler).execute();

            if (cache != null) {
                cache.put(context, dataCenters);
            }
            return dataCenters;
        } finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull Iterable<Region> listRegions() throws InternalException, CloudException {
        APITrace.begin(getProvider(), "DataCenter.listRegions");

        try {
            ProviderContext context = getProvider().getContext();
            if (context == null) {
                throw new InternalException("No context was set for this request");
            }

            Cache<Region> cache = Cache.getInstance(getProvider(), "regions", Region.class, CacheLevel.CLOUD_ACCOUNT);
            Collection<Region> regions = (Collection<Region>) cache.get(context);
            if (regions != null) {
                return regions;
            }

            HttpUriRequest request = AliyunRequestBuilder.get()
                    .provider(getProvider())
                    .category(AliyunRequestBuilder.Category.ECS)
                    .parameter("Action", "DescribeRegions")
                    .build();
            ResponseHandler<Collection<Region>> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, Collection<Region>>(
                    new StreamToJSONObjectProcessor(),
                    new DriverToCoreMapper<JSONObject, Collection<Region>>() {
                        @Override
                        public Collection<Region> mapFrom(JSONObject json) {
                            try {
                                Collection<Region> result = new ArrayList<Region>();
                                JSONArray regionsJson = json.getJSONObject("Regions").getJSONArray("Region");
                                for (int i = 0; i < regionsJson.length(); i++) {
                                    JSONObject regionJson = regionsJson.getJSONObject(i);
                                    String regionId = regionJson.getString("RegionId");
                                    Region region = new Region(regionId, regionId, true, true);
                                    if (regionId.equals("cn-hongkong")) {
                                        region.setJurisdiction("HK");
                                    } else {
                                        region.setJurisdiction(
                                                regionId.substring(0, regionId.indexOf('-')).toUpperCase(Locale.ENGLISH));
                                    }
                                    result.add(region);
                                }
                                return result;
                            } catch (JSONException jsonException) {
                                stdLogger.error("Failed to parse JSON", jsonException);
                                throw new RuntimeException(jsonException.getMessage());
                            }
                        }
                    },
                    JSONObject.class);

            regions = new AliyunRequestExecutor<Collection<Region>>(getProvider(),
                    AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                    request,
                    responseHandler).execute();

            cache.put(context, regions);
            return regions;
        } finally {
            APITrace.end();
        }
    }
}
