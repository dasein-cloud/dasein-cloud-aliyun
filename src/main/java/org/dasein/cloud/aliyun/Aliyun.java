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
import org.dasein.cloud.AbstractCloud;
import org.dasein.cloud.ContextRequirements;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.dc.DataCenterServices;
import org.dasein.cloud.dc.Region;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Created by Jeffrey Yan on 5/5/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.05.01
 */
public class Aliyun extends AbstractCloud {

    static private Logger stdLogger = Aliyun.getStdLogger(Aliyun.class);


    static public final String DSN_ACCESS_KEY = "accessKey";

    static private @Nonnull String getLastItem(@Nonnull String name) {
        int idx = name.lastIndexOf('.');

        if( idx < 0 ) {
            return name;
        }
        else if( idx == (name.length()-1) ) {
            return "";
        }
        return name.substring(idx+1);
    }

    static private @Nonnull Logger getLogger(@Nonnull Class<?> cls, @Nonnull String type) {
        String pkg = getLastItem(cls.getPackage().getName());

        if( pkg.equals("aliyun") ) {
            pkg = "";
        }
        else {
            pkg = pkg + ".";
        }
        return Logger.getLogger("dasein.cloud.aliyun." + type + "." + pkg + getLastItem(cls.getName()));
    }

    static public @Nonnull Logger getStdLogger(Class<?> cls) {
        return getLogger(cls, "std");
    }

    static public @Nonnull Logger getWireLogger(Class<?> cls) {
        return getLogger(cls, "wire");
    }

    @Override
    public @Nonnull String getCloudName() {
        ProviderContext ctx = getContext();
        String name = ( ctx == null ? null : ctx.getCloud().getCloudName() );

        return ( ( name == null ) ? "Aliyun" : name );
    }

    @Override
    public @Nonnull String getProviderName() {
        ProviderContext ctx = getContext();
        String name = ( ctx == null ? null : ctx.getCloud().getProviderName() );

        return ( ( name == null ) ? "Alibaba" : name );
    }

    @Override
    public @Nonnull ContextRequirements getContextRequirements() {
        return new ContextRequirements(
                new ContextRequirements.Field(DSN_ACCESS_KEY, "Aliyun API access keys", ContextRequirements.FieldType.KEYPAIR, ContextRequirements.Field.ACCESS_KEYS, true),
                new ContextRequirements.Field("proxyHost", "Proxy host", ContextRequirements.FieldType.TEXT, false),
                new ContextRequirements.Field("proxyPort", "Proxy port", ContextRequirements.FieldType.TEXT, false));
    }


    @Override
    public @Nullable String testContext() {
        if (stdLogger.isTraceEnabled()) {
            stdLogger.trace("Enter - " + Aliyun.class.getName() + ".textContext()");
        }
        try {
            ProviderContext context = getContext();

            if (context == null) {
                return null;
            }

            DataCenterServices dataCenterServices = getDataCenterServices();
            Iterable<Region> regions = dataCenterServices.listRegions();
            if (regions.iterator().hasNext()) {
                return context.getAccountNumber();
            } else {
                return null;
            }
        } catch (Throwable throwable) {
            stdLogger.warn("Failed to test Aliyun connection context: " + throwable.getMessage(), throwable);
            return null;
        } finally {
            if (stdLogger.isTraceEnabled()) {
                stdLogger.trace("Exit - " + Aliyun.class.getName() + ".testContext()");
            }
        }
    }

    @Override
    public @Nonnull DataCenterServices getDataCenterServices() {
        return new AliyunDataCenter(this);
    }
}
