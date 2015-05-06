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

package org.dasein.cloud.aliyun;

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.dc.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Jeffrey Yan on 5/5/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.05.1
 */
public class AliyunDataCenter extends AbstractDataCenterServices<Aliyun> implements DataCenterServices {

    static private final Logger stdLogger = Aliyun.getStdLogger(AliyunMethod.class);

    private transient volatile AliyunDataCenterCapabilities capabilities;

    public AliyunDataCenter(Aliyun provider) {
        super(provider);
    }

    @Nonnull
    @Override
    public DataCenterCapabilities getCapabilities() throws InternalException, CloudException {
        if( capabilities == null ) {
            capabilities = new AliyunDataCenterCapabilities(getProvider());
        }
        return capabilities;
    }

    @Override
    public @Nonnull Iterable<DataCenter> listDataCenters(@Nonnull String providerRegionId) throws InternalException, CloudException {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("RegionId", providerRegionId);
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeZones", parameters);

        JSONObject json = method.get().asJson();
        List<DataCenter> result = new ArrayList<DataCenter>();
        try {
            JSONArray regions = json.getJSONObject("Zones").getJSONArray("Zone");
            for (int i = 0; i < regions.length(); i++) {
                JSONObject region = regions.getJSONObject(i);
                String zoneId = region.getString("ZoneId");
                result.add(new DataCenter(zoneId, zoneId, providerRegionId, true, true));
            }
            return result;
        } catch (JSONException jsonException) {
            stdLogger.error("Failed to parse JSON due to field not exist", jsonException);
            throw new InternalException(jsonException);
        }
    }

    @Override
    public @Nonnull Iterable<Region> listRegions() throws InternalException, CloudException {
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeRegions");
        JSONObject json = method.get().asJson();
        List<Region> result = new ArrayList<Region>();
        try {
            JSONArray regions = json.getJSONObject("Regions").getJSONArray("Region");
            for (int i = 0; i < regions.length(); i++) {
                JSONObject region = regions.getJSONObject(i);
                String regionId = region.getString("RegionId");
                result.add(new Region(regionId, regionId, true, true));
            }
            return result;
        } catch (JSONException jsonException) {
            stdLogger.error("Failed to parse JSON due to field not exist", jsonException);
            throw new InternalException(jsonException);
        }
    }
}
