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

import org.dasein.cloud.AbstractCapabilities;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.VisibleScope;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.compute.SnapshotCapabilities;
import org.dasein.cloud.util.NamingConstraints;

import javax.annotation.Nonnull;
import java.util.Locale;

/**
 * Created by Jeffrey Yan on 5/15/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.05.1
 */
public class AliyunSnapshotCapabilities extends AbstractCapabilities<Aliyun> implements SnapshotCapabilities {

    public AliyunSnapshotCapabilities(@Nonnull Aliyun provider) {
        super(provider);
    }

    @Override
    public @Nonnull String getProviderTermForSnapshot(@Nonnull Locale locale) {
        return "snapshot";
    }

    @Override
    public @Nonnull VisibleScope getSnapshotVisibleScope() {
        return VisibleScope.ACCOUNT_REGION;
    }

    @Override
    public @Nonnull Requirement identifyAttachmentRequirement() throws InternalException, CloudException {
        return Requirement.OPTIONAL;
    }

    @Override
    public boolean supportsSnapshotCopying() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean supportsSnapshotCreation() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean supportsSnapshotSharing() throws InternalException, CloudException {
        return false;
    }

    @Override
    public boolean supportsSnapshotSharingWithPublic() throws InternalException, CloudException {
        return false;
    }

    @Nonnull
    @Override
    public NamingConstraints getSnapshotNamingConstraints() throws CloudException, InternalException {
        return NamingConstraints.getAlphaNumeric(2, 128).withRegularExpression(
                "^((?!http)(?!auto)[a-zA-Z])[a-zA-Z0-9_\\-]{1,127}").withNoSpaces();
    }
}
