/*
 *  *
 *  Copyright (C) 2009-2015 Dell, Inc.
 *  See annotations for authorship information
 *
 *  ====================================================================
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  ====================================================================
 *
 */

package org.dasein.cloud.aliyun.storage;

import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.aliyun.storage.model.CreateBucketConfiguration;
import org.dasein.cloud.aliyun.util.requester.AliyunHttpClientBuilderFactory;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestBuilder;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestExecutor;
import org.dasein.cloud.aliyun.util.requester.AliyunResponseHandler;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.storage.AbstractBlobStoreSupport;
import org.dasein.cloud.storage.Blob;
import org.dasein.cloud.storage.BlobStoreCapabilities;
import org.dasein.cloud.storage.BlobStoreSupport;
import org.dasein.cloud.storage.FileTransfer;
import org.dasein.cloud.util.requester.streamprocessors.StreamToStringProcessor;
import org.dasein.cloud.util.requester.streamprocessors.XmlStreamToObjectProcessor;
import org.dasein.util.uom.storage.Byte;
import org.dasein.util.uom.storage.Storage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;

/**
 * Created by Jeffrey Yan on 7/7/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.09.1
 */
public class AliyunBlobStore extends AbstractBlobStoreSupport<Aliyun> implements BlobStoreSupport{

    static private final Logger stdLogger = Aliyun.getStdLogger(AliyunBlobStore.class);

    protected AliyunBlobStore(Aliyun provider) {
        super(provider);
    }

    @Nonnull
    @Override
    public BlobStoreCapabilities getCapabilities() throws CloudException, InternalException {
        return new AliyunBlobStoreCapabilities(getProvider());
    }

    @Override
    protected void get(@Nullable String bucket, @Nonnull String object, @Nonnull File toFile,
            @Nullable FileTransfer transfer) throws InternalException, CloudException {

    }

    @Override
    protected void put(@Nullable String bucket, @Nonnull String objectName, @Nonnull File file)
            throws InternalException, CloudException {

    }

    @Override
    protected void put(@Nullable String bucketName, @Nonnull String objectName, @Nonnull String content)
            throws InternalException, CloudException {

    }

    @Nonnull
    @Override
    public Blob createBucket(@Nonnull String bucket, boolean findFreeName) throws InternalException, CloudException {
        String regionId = getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }

        CreateBucketConfiguration createBucketConfiguration = new CreateBucketConfiguration();
        createBucketConfiguration.setLocationConstraint("oss-" + regionId);

        HttpUriRequest request = AliyunRequestBuilder.put()
                .provider(getProvider())
                .category(AliyunRequestBuilder.Category.OSS)
                .subdomain(bucket)
                .header("x-oss-acl", "private")
                .entity(createBucketConfiguration, new XmlStreamToObjectProcessor<CreateBucketConfiguration>())
                .build();

        ResponseHandler<String> responseHandler = new AliyunResponseHandler<String>(
                new StreamToStringProcessor(),
                String.class);

       new AliyunRequestExecutor<String>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                responseHandler).execute();

        return Blob.getInstance(regionId, "http://" + bucket + ".oss-" + regionId + ".aliyuncs.com", bucket,
                System.currentTimeMillis());
    }

    @Override
    public boolean exists(@Nonnull String bucket) throws InternalException, CloudException {
        return false;
    }

    @Override
    public Blob getBucket(@Nonnull String bucketName) throws InternalException, CloudException {
        return null;
    }

    @Override
    public Blob getObject(@Nullable String bucketName, @Nonnull String objectName)
            throws InternalException, CloudException {
        return null;
    }

    @Nullable
    @Override
    public String getSignedObjectUrl(@Nonnull String bucket, @Nonnull String object,
            @Nonnull String expiresEpochInSeconds) throws InternalException, CloudException {
        return null;
    }

    @Nullable
    @Override
    public Storage<Byte> getObjectSize(@Nullable String bucketName, @Nullable String objectName)
            throws InternalException, CloudException {
        return null;
    }

    @Override
    public boolean isPublic(@Nullable String bucket, @Nullable String object) throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        return false;
    }

    @Nonnull
    @Override
    public Iterable<Blob> list(@Nullable String bucket) throws CloudException, InternalException {
        return null;
    }

    @Override
    public void makePublic(@Nonnull String bucket) throws InternalException, CloudException {

    }

    @Override
    public void makePublic(@Nullable String bucket, @Nonnull String object) throws InternalException, CloudException {

    }

    @Override
    public void move(@Nullable String fromBucket, @Nullable String objectName, @Nullable String toBucket)
            throws InternalException, CloudException {

    }

    @Override
    public void removeBucket(@Nonnull String bucket) throws CloudException, InternalException {

    }

    @Override
    public void removeObject(@Nullable String bucket, @Nonnull String object) throws CloudException, InternalException {

    }

    @Nonnull
    @Override
    public String renameBucket(@Nonnull String oldName, @Nonnull String newName, boolean findFreeName)
            throws CloudException, InternalException {
        return null;
    }

    @Override
    public void renameObject(@Nullable String bucket, @Nonnull String oldName, @Nonnull String newName)
            throws CloudException, InternalException {

    }

    @Nonnull
    @Override
    public Blob upload(@Nonnull File sourceFile, @Nullable String bucket, @Nonnull String objectName)
            throws CloudException, InternalException {
        return null;
    }

    @Nonnull
    @Override
    public String[] mapServiceAction(@Nonnull ServiceAction action) {
        return new String[0];
    }
}
