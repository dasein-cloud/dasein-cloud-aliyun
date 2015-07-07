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

import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.aliyun.util.requester.AliyunHttpClientBuilderFactory;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestBuilder;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestExecutor;
import org.dasein.cloud.aliyun.util.requester.AliyunResponseHandlerWithMapper;
import org.dasein.cloud.aliyun.util.requester.AliyunValidateJsonResponseHandler;
import org.dasein.cloud.compute.AbstractSnapshotSupport;
import org.dasein.cloud.compute.Snapshot;
import org.dasein.cloud.compute.SnapshotCapabilities;
import org.dasein.cloud.compute.SnapshotCreateOptions;
import org.dasein.cloud.compute.SnapshotState;
import org.dasein.cloud.compute.SnapshotSupport;
import org.dasein.cloud.util.requester.DriverToCoreMapper;
import org.dasein.cloud.util.requester.streamprocessors.StreamToJSONObjectProcessor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
        final String regionId = getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }

        HttpUriRequest request = AliyunRequestBuilder.get()
                .provider(getProvider())
                .category(AliyunRequestBuilder.Category.ECS)
                .parameter("Action", "DescribeSnapshots")
                .parameter("RegionId", regionId)
                .parameter("SnapshotIds", "[\"" + snapshotId + "\"]")
                .build();

        ResponseHandler<Snapshot> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, Snapshot>(
                new StreamToJSONObjectProcessor(),
                new DriverToCoreMapper<JSONObject, Snapshot>() {
                    @Override
                    public Snapshot mapFrom(JSONObject json) {
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
                            throw new RuntimeException(jsonException.getMessage());
                        } catch (CloudException cloudException) {
                            stdLogger.error("Failed to parse JSON", cloudException);
                            throw new RuntimeException(cloudException);
                        } catch (InternalException internalException) {
                            stdLogger.error("Failed to parse JSON", internalException);
                            throw new RuntimeException(internalException.getMessage());
                        }
                    }
                },
                JSONObject.class);

        return new AliyunRequestExecutor<Snapshot>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                responseHandler).execute();
    }

    @Override
    public @Nonnull Iterable<Snapshot> listSnapshots() throws InternalException, CloudException {
        final String regionId = getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }

        final List<Snapshot> result = new ArrayList<Snapshot>();
        final AtomicInteger totalCount = new AtomicInteger(0);
        final AtomicInteger processedCount = new AtomicInteger(0);

        ResponseHandler<Void> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, Void>(
                new StreamToJSONObjectProcessor(),
                new DriverToCoreMapper<JSONObject, Void>() {
                    @Override
                    public Void mapFrom(JSONObject json) {
                        try {
                            totalCount.set(json.getInt("TotalCount"));

                            JSONArray snapshotsJson = json.getJSONObject("Snapshots").getJSONArray("Snapshot");
                            for (int i = 0; i < snapshotsJson.length(); i++) {
                                JSONObject snapshotJson = snapshotsJson.getJSONObject(i);
                                Snapshot snapshot = toSnapshot(snapshotJson, regionId);
                                result.add(snapshot);
                                processedCount.incrementAndGet();
                            }
                            return null;
                        } catch (JSONException jsonException) {
                            stdLogger.error("Failed to parse JSON", jsonException);
                            throw new RuntimeException(jsonException);
                        } catch (CloudException cloudException) {
                            stdLogger.error("Failed to parse JSON", cloudException);
                            throw new RuntimeException(cloudException);
                        } catch (InternalException internalException) {
                            stdLogger.error("Failed to parse JSON", internalException);
                            throw new RuntimeException(internalException.getMessage());
                        }
                    }
                },
                JSONObject.class);

        int pageNumber = 1;
        while(true) {
                HttpUriRequest request = AliyunRequestBuilder.get()
                        .provider(getProvider())
                        .category(AliyunRequestBuilder.Category.ECS)
                        .parameter("Action", "DescribeSnapshots")
                        .parameter("RegionId", regionId)
                        .parameter("PageNumber", pageNumber++)
                        .parameter("PageSize", 50)//max
                        .build();

                new AliyunRequestExecutor<Void>(getProvider(),
                        AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                        request,
                        responseHandler).execute();

                if (processedCount.intValue() >= totalCount.intValue()) {
                    break;
                }
        }
        return result;
    }

    @Override
    public @Nullable String createSnapshot(@Nonnull SnapshotCreateOptions options) throws CloudException, InternalException {
        Map<String, Object> entity = new HashMap<String, Object>();
        entity.put("DiskId", options.getVolumeId());
        entity.put("SnapshotName", options.getName());
        entity.put("Description", options.getDescription());

        HttpUriRequest request = AliyunRequestBuilder.post()
                .provider(getProvider())
                .category(AliyunRequestBuilder.Category.ECS)
                .parameter("Action", "CreateSnapshot")
                .entity(entity)
                .clientToken(true)
                .build();
        ResponseHandler<String> responseHandler = new AliyunResponseHandlerWithMapper<JSONObject, String>(
                new StreamToJSONObjectProcessor(),
                new DriverToCoreMapper<JSONObject, String>() {
                    @Override
                    public String mapFrom(JSONObject json) {
                        try {
                            String snapshotId = json.getString("SnapshotId");
                            return snapshotId;
                        } catch (JSONException jsonException) {
                            stdLogger.error("Failed to parse JSON", jsonException);
                            throw new RuntimeException(jsonException.getMessage());
                        }
                    }
                },
                JSONObject.class);

        return new AliyunRequestExecutor<String>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                responseHandler).execute();
    }


    @Override
    public void remove(@Nonnull String snapshotId) throws InternalException, CloudException {
        Map<String, Object> entity = new HashMap<String, Object>();
        entity.put("SnapshotId", snapshotId);

        HttpUriRequest request = AliyunRequestBuilder.post()
                .provider(getProvider())
                .category(AliyunRequestBuilder.Category.ECS)
                .parameter("Action", "DeleteSnapshot")
                .entity(entity)
                .build();

        new AliyunRequestExecutor<Void>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                new AliyunValidateJsonResponseHandler(getProvider())).execute();
    }

}
