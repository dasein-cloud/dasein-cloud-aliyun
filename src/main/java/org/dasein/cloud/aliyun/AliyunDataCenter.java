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

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.dc.AbstractDataCenterServices;
import org.dasein.cloud.dc.DataCenter;
import org.dasein.cloud.dc.DataCenterCapabilities;
import org.dasein.cloud.dc.DataCenterServices;
import org.dasein.cloud.dc.Region;
import org.dasein.cloud.util.APITrace;
import org.dasein.cloud.util.Cache;
import org.dasein.cloud.util.CacheLevel;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    public @Nonnull Iterable<DataCenter> listDataCenters(@Nonnull String providerRegionId) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "listDataCenters");
        try {
            ProviderContext context = getProvider().getContext();
            if (context == null) {
                throw new InternalException("No context was set for this request");
            }

            Cache<DataCenter> cache = null;
            Collection<DataCenter> dataCenters;

            if (providerRegionId.equals(context.getRegionId())) {//only cache context's region
                cache = Cache.getInstance(getProvider(), "dataCenters", DataCenter.class, CacheLevel.REGION_ACCOUNT);
                dataCenters = (Collection<DataCenter>) cache.get(context);
                if (dataCenters != null) {
                    return dataCenters;
                }
            }
            dataCenters = new ArrayList<DataCenter>();

            Map<String, Object> parameters = new HashMap<String, Object>();
            parameters.put("RegionId", providerRegionId);
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeZones",
                    parameters);

            JSONObject json = method.get().asJson();

            JSONArray dataCentersJson = json.getJSONObject("Zones").getJSONArray("Zone");
            for (int i = 0; i < dataCentersJson.length(); i++) {
                JSONObject dataCenterJson = dataCentersJson.getJSONObject(i);
                String zoneId = dataCenterJson.getString("ZoneId");
                DataCenter dataCenter = new DataCenter(zoneId, zoneId, providerRegionId, true, true);
                JSONArray resourceTypesJson = dataCenterJson.getJSONObject("AvailableResourceCreation").getJSONArray("ResourceTypes");
                List<String> resourceTypes = new ArrayList<String>();
                for (int j = 0; j < resourceTypesJson.length(); j++) {
                    resourceTypes.add(resourceTypesJson.getString(j));
                }
                dataCenter.setCompute(resourceTypes.contains("Instance"));
                dataCenter.setStorage(resourceTypes.contains("Disk"));
                dataCenters.add(dataCenter);
            }

            if (cache != null) {
                cache.put(context, dataCenters);
            }
            return dataCenters;
        } catch (JSONException jsonException) {
            stdLogger.error("Failed to parse JSON due to field not exist", jsonException);
            throw new InternalException(jsonException);
        } finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull Iterable<Region> listRegions() throws InternalException, CloudException {
        APITrace.begin(getProvider(), "listRegions");

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
            regions = new ArrayList<Region>();

            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeRegions");
            JSONObject json = method.get().asJson();

            JSONArray regionsJson = json.getJSONObject("Regions").getJSONArray("Region");
            for (int i = 0; i < regionsJson.length(); i++) {
                JSONObject regionJson = regionsJson.getJSONObject(i);
                String regionId = regionJson.getString("RegionId");
                Region region = new Region(regionId, regionId, true, true);
                if (regionId.equals("cn-hongkong")) {
                    region.setJurisdiction("HK");
                } else {
                    region.setJurisdiction(regionId.substring(0, regionId.indexOf('-')).toUpperCase(Locale.ENGLISH));
                }
                regions.add(region);
            }

            cache.put(context, regions);
            return regions;
        }  catch (JSONException jsonException) {
            stdLogger.error("Failed to parse JSON due to field not exist", jsonException);
            throw new InternalException(jsonException);
        } finally {
            APITrace.end();
        }
    }
}
