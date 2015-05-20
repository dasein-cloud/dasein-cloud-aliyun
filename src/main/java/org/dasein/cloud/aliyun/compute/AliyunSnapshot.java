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

package org.dasein.cloud.aliyun.compute;

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.aliyun.AliyunMethod;
import org.dasein.cloud.compute.AbstractSnapshotSupport;
import org.dasein.cloud.compute.Snapshot;
import org.dasein.cloud.compute.SnapshotCapabilities;
import org.dasein.cloud.compute.SnapshotCreateOptions;
import org.dasein.cloud.compute.SnapshotState;
import org.dasein.cloud.compute.SnapshotSupport;
import org.dasein.cloud.compute.Volume;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Jeffrey Yan on 5/15/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.05.1
 */
public class AliyunSnapshot extends AbstractSnapshotSupport<Aliyun> implements SnapshotSupport {

    static private final Logger stdLogger = Aliyun.getStdLogger(AliyunSnapshot.class);

    protected AliyunSnapshot(Aliyun provider) {
        super(provider);
    }

    @Override
    public @Nonnull SnapshotCapabilities getCapabilities() throws CloudException, InternalException {
        return new AliyunSnapshotCapabilities(getProvider());
    }

    @Override
    public boolean isSubscribed() throws InternalException, CloudException {
        return true;
    }

    private @Nonnull Snapshot toSnapshot(@Nonnull JSONObject snapshotJson, @Nonnull String regionId)
            throws JSONException, InternalException, CloudException {
        Snapshot snapshot = new Snapshot();
        snapshot.setProviderSnapshotId(snapshotJson.getString("SnapshotId"));
        snapshot.setName(snapshotJson.getString("SnapshotName"));
        snapshot.setDescription(snapshotJson.getString("Description"));

        String progress = snapshotJson.getString("Progress");
        int progressPercent = progress.endsWith("%") ?
                Integer.parseInt(progress.substring(0, progress.length() - 1)) :
                Integer.parseInt(progress);
        if (progressPercent < 100) {
            snapshot.setCurrentState(SnapshotState.PENDING);
        } else {
            snapshot.setCurrentState(SnapshotState.AVAILABLE);
        }
        snapshot.setProgress(progress);

        snapshot.setOwner(getContext().getAccountNumber());
        snapshot.setRegionId(regionId);
        snapshot.setSizeInGb(snapshotJson.getInt("SourceDiskSize"));
        snapshot.setVolumeId(snapshotJson.getString("SourceDiskId"));
        snapshot.setVisibleScope(getCapabilities().getSnapshotVisibleScope());
        snapshot.setSnapshotTimestamp(getProvider().parseIso8601Date(snapshotJson.getString("CreationTime")).getTime());
        return snapshot;
    }

    @Override
    public @Nullable Snapshot getSnapshot(@Nonnull String snapshotId) throws InternalException, CloudException {
        String regionId = getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("RegionId", regionId);
        parameters.put("SnapshotIds", "[\"" + snapshotId + "\"]");
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeSnapshots", parameters);
        JSONObject json = method.get().asJson();

        try {
            JSONArray snapshotsJson = json.getJSONObject("Snapshots").getJSONArray("Snapshot");
            if (snapshotsJson.length() >= 1) {
                JSONObject snapshotJson = snapshotsJson.getJSONObject(0);
                return toSnapshot(snapshotJson, regionId);
            } else {
                return null;
            }
        } catch (JSONException jsonException) {
            stdLogger.error("Failed to parse JSON", jsonException);
            throw new InternalException(jsonException);
        }
    }

    @Override
    public @Nonnull Iterable<Snapshot> listSnapshots() throws InternalException, CloudException {
        String regionId = getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }

        List<Snapshot> result = new ArrayList<Snapshot>();
        int pageNumber = 1;
        int processedCount = 0;
        while(true) {
            Map<String, Object> parameters = new HashMap<String, Object>();
            parameters.put("RegionId", regionId);
            parameters.put("PageNumber", pageNumber++);
            parameters.put("PageSize", 50);//max
            AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DescribeSnapshots", parameters);
            JSONObject json = method.get().asJson();

            try {
                int totalCount = json.getInt("TotalCount");
                JSONArray snapshotsJson = json.getJSONObject("Snapshots").getJSONArray("Snapshot");
                for (int i = 0; i < snapshotsJson.length(); i++) {
                    JSONObject snapshotJson = snapshotsJson.getJSONObject(i);
                    Snapshot snapshot = toSnapshot(snapshotJson, regionId);
                    result.add(snapshot);
                    processedCount++;
                }
                if (processedCount >= totalCount) {
                    break;
                }
            } catch (JSONException jsonException) {
                stdLogger.error("Failed to parse JSONt", jsonException);
                throw new InternalException(jsonException);
            }
        }
        return result;
    }

    @Override
    public @Nullable String createSnapshot(@Nonnull SnapshotCreateOptions options) throws CloudException, InternalException {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("DiskId", options.getVolumeId());
        parameters.put("SnapshotName", options.getName());
        parameters.put("Description", options.getDescription());
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "CreateSnapshot", parameters);
        JSONObject json = method.post().asJson();
        try {
            String snapshotId = json.getString("SnapshotId");
            return snapshotId;
        } catch (JSONException jsonException) {
            stdLogger.error("Failed to parse JSON", jsonException);
            throw new InternalException(jsonException);
        }
    }


    @Override
    public void remove(@Nonnull String snapshotId) throws InternalException, CloudException {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("SnapshotId", snapshotId);
        AliyunMethod method = new AliyunMethod(getProvider(), AliyunMethod.Category.ECS, "DeleteSnapshot", parameters);
        JSONObject json = method.post().asJson();
        getProvider().validateResponse(json);
    }

}
