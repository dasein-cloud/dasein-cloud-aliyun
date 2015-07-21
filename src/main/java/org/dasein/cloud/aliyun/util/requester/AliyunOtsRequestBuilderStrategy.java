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

package org.dasein.cloud.aliyun.util.requester;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.aliyun.Aliyun;

import java.net.URISyntaxException;
import java.util.Date;

/**
 * Created by Jeffrey Yan on 7/21/2015.
 *
 * In dasein, there is no service like OTS exist. But we keep the sample code here.
         <dependency>
             <groupId>com.google.protobuf</groupId>
             <artifactId>protobuf-java</artifactId>
             <version>2.4.1</version>
             <scope>compile</scope>
             <optional>false</optional>
         </dependency>

         DescribeTableRequest  utr = DescribeTableRequest .newBuilder().setTableName("this is the testing table").build();
         byte[] data = utr.toByteArray();
         DescribeTableRequest  utr2 = DescribeTableRequest.parseFrom(new ByteArrayInputStream(data));
 * @author Jeffrey Yan
 * @since 2015.09.1
 */
public class AliyunOtsRequestBuilderStrategy extends AliyunOssRequestBuilderStrategy {

    static private final Logger logger = Aliyun.getStdLogger(AliyunOtsRequestBuilderStrategy.class);

    private static final String OTS_HEADER_PREFIX = "x-ots-";

    private static final String OTS_INSTANCE_DEFAULT = "otsInstance";

    public AliyunOtsRequestBuilderStrategy(Aliyun aliyun) {
        super(aliyun);
    }

    @Override
    public void applyUri(AliyunRequestBuilder aliyunRequestBuilder) throws InternalException {
        String instance = (String) aliyun.getContext().getConfigurationValue(OTS_INSTANCE_DEFAULT);
        if (aliyun.isEmpty(instance)) {
            throw new InternalException("No OTS_INSTANCE_DEFAULT was set for this request");
        }

        String regionId = aliyun.getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }

        String host = instance + "." + regionId + "." + aliyunRequestBuilder.category.getHost(this.aliyun);

        URIBuilder uriBuilder = new URIBuilder();
        uriBuilder.setScheme("http").setHost(host).setPath(aliyunRequestBuilder.path);//support HTTP only
        try {
            aliyunRequestBuilder.requestBuilder.setUri(uriBuilder.build().toString());
        } catch (URISyntaxException uriSyntaxException) {
            logger.error("RequestBuilderFactory.build() failed due to URI invalid: " + uriSyntaxException.getMessage());
            throw new InternalException(uriSyntaxException);
        }
    }

    @Override
    public void applyFrameworkParameters(AliyunRequestBuilder aliyunRequestBuilder) {
        aliyunRequestBuilder.requestBuilder.addHeader("x-ots-date", aliyun.formatRfc822Date(new Date()));
        aliyunRequestBuilder.requestBuilder.addHeader("x-ots-apiversion", "2014-08-08");

        byte[][] accessKey = (byte[][]) aliyun.getContext().getConfigurationValue(Aliyun.DSN_ACCESS_KEY);
        aliyunRequestBuilder.requestBuilder.addHeader("x-ots-accesskeyid", new String(accessKey[0]));

        String instance = (String) aliyun.getContext().getConfigurationValue(OTS_INSTANCE_DEFAULT);
        aliyunRequestBuilder.requestBuilder.addHeader("x-ots-instancename", instance);

        HttpEntity httpEntity = aliyunRequestBuilder.requestBuilder.getEntity();
        if(httpEntity != null) {
            if (httpEntity.isRepeatable()) {
                try {
                    byte[] md5 = computeMD5Hash(httpEntity.getContent());
                    String md5Base64 = new String(Base64.encodeBase64(md5));
                    aliyunRequestBuilder.requestBuilder.addHeader("x-ots-contentmd5", md5Base64);
                } catch (Exception exception) {
                    aliyunRequestBuilder.requestBuilder.addHeader("x-ots-contentmd5", "");
                }
            } else {
                aliyunRequestBuilder.requestBuilder.addHeader("x-ots-contentmd5", "");
            }
        }
    }

    @Override
    public void sign(AliyunRequestBuilder aliyunRequestBuilder) throws InternalException {
        String stringToSign = buildCanonicalizedString(aliyunRequestBuilder);

        byte[][] accessKey = (byte[][]) aliyun.getContext().getConfigurationValue(Aliyun.DSN_ACCESS_KEY);
        byte[] accessKeySecret = accessKey[1];
        String signature = computeSignature(accessKeySecret, stringToSign);
        aliyunRequestBuilder.requestBuilder.addHeader("x-ots-signature", signature);
    }

    @Override
    protected String buildCanonicalizedString(AliyunRequestBuilder aliyunRequestBuilder) throws InternalException {
        RequestBuilder requestBuilder = aliyunRequestBuilder.requestBuilder;
        StringBuilder stringToSign = new StringBuilder();
        stringToSign.append(aliyunRequestBuilder.path).append(NEW_LINE);
        stringToSign.append(requestBuilder.getMethod()).append(NEW_LINE).append(NEW_LINE);
        stringToSign.append(buildCanonicalizedHeaders(aliyunRequestBuilder));
        return stringToSign.toString();
    }

    @Override
    protected boolean isCanonicalizedHeader(String name) {
        return name.startsWith(OTS_HEADER_PREFIX);
    }
}
