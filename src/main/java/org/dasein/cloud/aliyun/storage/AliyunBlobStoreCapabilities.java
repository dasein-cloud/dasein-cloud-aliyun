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

import org.dasein.cloud.AbstractCapabilities;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.storage.BlobStoreCapabilities;
import org.dasein.cloud.util.NamingConstraints;
import org.dasein.util.uom.storage.*;
import org.dasein.util.uom.storage.Byte;

import javax.annotation.Nonnull;
import java.util.Locale;

/**
 * Created by Jeffrey Yan on 7/9/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.09.1
 */
public class AliyunBlobStoreCapabilities extends AbstractCapabilities<Aliyun> implements BlobStoreCapabilities {
    public AliyunBlobStoreCapabilities(@Nonnull Aliyun provider) {
        super(provider);
    }

    @Override
    public boolean allowsNestedBuckets() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean allowsRootObjects() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean allowsPublicSharing() throws CloudException, InternalException {
        return true;
    }

    @Override
    public int getMaxBuckets() throws CloudException, InternalException {
        return 10;
    }

    @Nonnull
    @Override
    public Storage<Byte> getMaxObjectSize() throws InternalException, CloudException {
        return new Storage<org.dasein.util.uom.storage.Byte>(5000000000L, Storage.BYTE);
    }

    @Override
    public int getMaxObjectsPerBucket() throws CloudException, InternalException {
        return -1;
    }

    @Nonnull
    @Override
    public NamingConstraints getBucketNamingConstraints() throws CloudException, InternalException {
        return NamingConstraints.getAlphaNumeric(3, 255).lowerCaseOnly().limitedToLatin1().constrainedBy(new char[]{'-'});
    }

    @Nonnull
    @Override
    public NamingConstraints getObjectNamingConstraints() throws CloudException, InternalException {
        return NamingConstraints.getAlphaNumeric(1, 1023).withRegularExpression("^[^/\\].*");
    }

    @Nonnull
    @Override
    public String getProviderTermForBucket(@Nonnull Locale locale) {
        return "bucket";
    }

    @Nonnull
    @Override
    public String getProviderTermForObject(@Nonnull Locale locale) {
        return "object";
    }
}
