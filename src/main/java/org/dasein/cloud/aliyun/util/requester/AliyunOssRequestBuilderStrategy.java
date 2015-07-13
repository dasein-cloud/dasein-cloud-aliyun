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
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.aliyun.Aliyun;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Jeffrey Yan on 7/9/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.09.1
 */
public class AliyunOssRequestBuilderStrategy extends AliyunRequestBuilderStrategy {
    static private final Logger logger = Aliyun.getStdLogger(AliyunOssRequestBuilderStrategy.class);

    private static final String OSS_PREFIX = "x-oss-";

    protected static final String NEW_LINE = "\n";
    protected static final String AUTHORIZATION = "Authorization";
    protected static final String CONTENT_MD5 = "Content-MD5";
    protected static final String CONTENT_TYPE = "Content-Type";
    protected static final String DATE = "Date";

    private static final String SUBRESOURCE_ACL = "acl";
    private static final String SUBRESOURCE_REFERER = "referer";
    private static final String SUBRESOURCE_LOCATION = "location";
    private static final String SUBRESOURCE_LOGGING = "logging";
    private static final String SUBRESOURCE_WEBSITE = "website";
    private static final String SUBRESOURCE_LIFECYCLE = "lifecycle";
    private static final String SUBRESOURCE_UPLOADS = "uploads";
    private static final String SUBRESOURCE_DELETE = "delete";
    private static final String SUBRESOURCE_CORS = "cors";
    private static final String UPLOAD_ID = "uploadId";
    private static final String PART_NUMBER = "partNumber";
    private static final String SECURITY_TOKEN = "security-token";
    private static final String RESPONSE_HEADER_CONTENT_TYPE = "response-content-type";
    private static final String RESPONSE_HEADER_CONTENT_LANGUAGE = "response-content-language";
    private static final String RESPONSE_HEADER_EXPIRES = "response-expires";
    private static final String RESPONSE_HEADER_CACHE_CONTROL = "response-cache-control";
    private static final String RESPONSE_HEADER_CONTENT_DISPOSITION = "response-content-disposition";
    private static final String RESPONSE_HEADER_CONTENT_ENCODING = "response-content-encoding";

    private static final List<String> SIGNED_PARAMTERS = Arrays.asList(new String[] {
            SUBRESOURCE_ACL, SUBRESOURCE_UPLOADS, SUBRESOURCE_LOCATION,
            SUBRESOURCE_CORS, SUBRESOURCE_LOGGING, SUBRESOURCE_WEBSITE,
            SUBRESOURCE_REFERER, SUBRESOURCE_LIFECYCLE, SUBRESOURCE_DELETE,
            UPLOAD_ID, PART_NUMBER, SECURITY_TOKEN,
            RESPONSE_HEADER_CACHE_CONTROL, RESPONSE_HEADER_CONTENT_DISPOSITION,
            RESPONSE_HEADER_CONTENT_ENCODING, RESPONSE_HEADER_CONTENT_LANGUAGE,
            RESPONSE_HEADER_CONTENT_TYPE, RESPONSE_HEADER_EXPIRES
    });


    public AliyunOssRequestBuilderStrategy(Aliyun aliyun) {
        super(aliyun);
    }

    public void applyUri(AliyunRequestBuilder aliyunRequestBuilder) throws InternalException {
        URIBuilder uriBuilder = new URIBuilder();
        String host = aliyunRequestBuilder.category.getHost(this.aliyun);
        if(!aliyun.isEmpty(aliyunRequestBuilder.subdomain)) {
            String regionId = aliyun.getContext().getRegionId();
            if (regionId == null) {
                throw new InternalException("No region was set for this request");
            }

            int firstDot = host.indexOf('.');
            host = aliyunRequestBuilder.subdomain + "." + host.substring(0, firstDot) + "-" + regionId +
                    host.substring(firstDot);
        }
        uriBuilder.setScheme("https").setHost(host).setPath(aliyunRequestBuilder.path);
        try {
            aliyunRequestBuilder.requestBuilder.setUri(uriBuilder.build().toString());
        } catch (URISyntaxException uriSyntaxException) {
            logger.error("RequestBuilderFactory.build() failed due to URI invalid: " + uriSyntaxException.getMessage());
            throw new InternalException(uriSyntaxException);
        }
    }

    private byte[] computeMD5Hash(InputStream is) throws NoSuchAlgorithmException, IOException {
        BufferedInputStream bis = new BufferedInputStream(is);

        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[16384];
            int bytesRead;

            while ((bytesRead = bis.read(buffer, 0, buffer.length)) != -1) {
                messageDigest.update(buffer, 0, bytesRead);
            }
            return messageDigest.digest();
        } finally {
            try {
                bis.close();
            } catch (Exception exception) {
                logger.warn("Unable to close input stream of hash candidate: " + exception);
            }
        }
    }

    @Override
    public void applyFrameworkParameters(AliyunRequestBuilder aliyunRequestBuilder) {
        aliyunRequestBuilder.requestBuilder.addHeader(DATE, aliyun.formatRfc822Date(new Date()));
        HttpEntity httpEntity = aliyunRequestBuilder.requestBuilder.getEntity();
        if(httpEntity != null) {
            if (httpEntity.isRepeatable()) {
                try {
                    byte[] md5 = computeMD5Hash(httpEntity.getContent());
                    String md5Base64 = new String(Base64.encodeBase64(md5));
                    aliyunRequestBuilder.requestBuilder.addHeader(CONTENT_MD5, md5Base64);
                } catch (Exception exception) {
                    aliyunRequestBuilder.requestBuilder.addHeader(CONTENT_MD5, "");
                }
            } else {
                aliyunRequestBuilder.requestBuilder.addHeader(CONTENT_MD5, "");
            }
        }
    }

    @Override
    public void sign(AliyunRequestBuilder aliyunRequestBuilder) throws InternalException {
        RequestBuilder requestBuilder = aliyunRequestBuilder.requestBuilder;
        StringBuilder stringToSign = new StringBuilder();
        stringToSign.append(requestBuilder.getMethod()).append(NEW_LINE);
        if (requestBuilder.getFirstHeader(CONTENT_MD5) != null) {
            stringToSign.append(requestBuilder.getFirstHeader(CONTENT_MD5).getValue());
        }
        stringToSign.append(NEW_LINE);
        if(aliyunRequestBuilder.contentType != null) {
            stringToSign.append(aliyunRequestBuilder.contentType.toString());
        }
        stringToSign.append(NEW_LINE);
        stringToSign.append(requestBuilder.getFirstHeader(DATE).getValue()).append(NEW_LINE);
        stringToSign.append(buildCanonicalizedHeaders(aliyunRequestBuilder));
        stringToSign.append(buildCanonicalizedResource(aliyunRequestBuilder));

        byte[][] accessKey = (byte[][]) aliyun.getContext().getConfigurationValue(Aliyun.DSN_ACCESS_KEY);

        byte[] accessKeySecret = accessKey[1];
        String signature = computeSignature(accessKeySecret, stringToSign.toString());
        aliyunRequestBuilder.requestBuilder.addHeader(AUTHORIZATION, "OSS " + new String(accessKey[0]) + ":" + signature);
    }

    protected String buildCanonicalizedHeaders(AliyunRequestBuilder aliyunRequestBuilder) {
        Map<String, Header[]> ossHeaders = new HashMap<String, Header[]>();
        for (Header header : aliyunRequestBuilder.headergroup.getAllHeaders()) {
            String name = header.getName();
            String nameLowerCase = name.toLowerCase();
            if (nameLowerCase.startsWith(OSS_PREFIX) && !ossHeaders.containsKey(name)) {
                ossHeaders.put(nameLowerCase, aliyunRequestBuilder.headergroup.getHeaders(name));
            }
        }

        String[] sortedKeys = ossHeaders.keySet().toArray(new String[]{});
        Arrays.sort(sortedKeys);

        StringBuilder canonicalStringBuilder = new StringBuilder();
        for(String key : sortedKeys) {
            canonicalStringBuilder.append(key).append(':');
            Header[] headers = ossHeaders.get(key);
            for(Header header : headers) {
                canonicalStringBuilder.append(header.getValue()).append(',');
            }
            if(headers.length!=0) {
                canonicalStringBuilder.deleteCharAt(canonicalStringBuilder.length() - 1);
            }
            canonicalStringBuilder.append(NEW_LINE);
        }
        return canonicalStringBuilder.toString();
    }

    protected String buildCanonicalizedResource(AliyunRequestBuilder aliyunRequestBuilder) {
        Map<String, String> requestParameters = new HashMap<String, String>();
        for (NameValuePair nameValuePair : aliyunRequestBuilder.requestBuilder.getParameters()) {
            requestParameters.put(nameValuePair.getName(), nameValuePair.getValue());
        }

        String[] sortedKeys = requestParameters.keySet().toArray(new String[]{});
        Arrays.sort(sortedKeys);

        StringBuilder canonicalStringBuilder = new StringBuilder();
        canonicalStringBuilder.append('/');
        if (!aliyun.isEmpty(aliyunRequestBuilder.subdomain)) {
            canonicalStringBuilder.append(aliyunRequestBuilder.subdomain).append(aliyunRequestBuilder.path);
        }

        char separator = '?';
        for(String key : sortedKeys) {
            if (!SIGNED_PARAMTERS.contains(key)) {
                continue;
            }

            canonicalStringBuilder.append(separator);
            String value = requestParameters.get(key);
            if(aliyun.isEmpty(value)) {
                canonicalStringBuilder.append(key);
            } else {
                canonicalStringBuilder.append(key).append("=").append(value);
            }
            separator = '&';
        }
        return canonicalStringBuilder.toString();
    }
}
