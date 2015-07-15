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
import org.dasein.cloud.CloudException;
import org.dasein.cloud.ContextRequirements;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.aliyun.compute.AliyunCompute;
import org.dasein.cloud.aliyun.storage.AliyunStorage;
import org.dasein.cloud.compute.ComputeServices;
import org.dasein.cloud.dc.DataCenterServices;
import org.dasein.cloud.dc.Region;
import org.dasein.cloud.storage.StorageServices;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.SimpleTimeZone;

/**
 * Created by Jeffrey Yan on 5/5/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.05.01
 */
public class Aliyun extends AbstractCloud {

    static private Logger stdLogger = Aliyun.getStdLogger(Aliyun.class);

    static private final String ISO8601_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    static private final String RFC822_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss z";
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

    @Override
    public @Nullable ComputeServices getComputeServices() {
        return new AliyunCompute(this);
    }

    @Override
    public @Nonnull StorageServices getStorageServices() {
        return new AliyunStorage(this);
    }

    public String formatIso8601Date(Date date) {
        SimpleDateFormat df = new SimpleDateFormat(ISO8601_DATE_FORMAT);
        df.setTimeZone(new SimpleTimeZone(0, "GMT"));
        return df.format(date);
    }

    public Date parseIso8601Date(@Nonnull String date) throws InternalException {
        if (date == null || date.isEmpty()) {
            return new Date(0);
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat(ISO8601_DATE_FORMAT);

        try {
            return dateFormat.parse(date);
        } catch (ParseException parseException) {
            throw new InternalException("Could not parse date: " + date);
        }
    }

    public String formatRfc822Date(Date date) {
        SimpleDateFormat rfc822DateFormat = new SimpleDateFormat(RFC822_DATE_FORMAT, Locale.US);
        rfc822DateFormat.setTimeZone(new SimpleTimeZone(0, "GMT"));
        return rfc822DateFormat.format(date);
    }

    public boolean isEmpty(CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    public boolean isEmpty(Collection coll) {
        return (coll == null || coll.isEmpty());
    }

    public String capitalize(String str) {
        int strLen;
        if (str == null || (strLen = str.length()) == 0) {
            return str;
        }
        return new StringBuilder(strLen).append(Character.toTitleCase(str.charAt(0))).append(str.substring(1))
                .toString();
    }

    public void addValueIfNotEmpty(@Nonnull Map<String, Object> parameters, @Nonnull String key, String value) {
        if (isEmpty(value)) {
            return;
        }
        parameters.put(key, value);
    }

    public void validateResponse(JSONObject json) throws CloudException, InternalException {
        try {
            String requestId = json.getString("RequestId");
            if (requestId != null && !requestId.isEmpty()) {
                return;
            } else {
                throw new CloudException("Response is not valid: no RequestId field");
            }
        } catch (JSONException jsonException) {
            stdLogger.error("Failed to parse JSON", jsonException);
            throw new InternalException(jsonException);
        }
    }
}
