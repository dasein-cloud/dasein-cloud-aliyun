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
import org.apache.http.NameValuePair;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.aliyun.util.requester.AliyunMethodInternal.Category;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Jeffrey Yan on 7/1/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.05.1
 */
public abstract class AliyunRequestBuilderFactory {
    static private final Logger stdLogger = Aliyun
            .getStdLogger(org.dasein.cloud.aliyun.util.requester.AliyunRequestBuilderFactory.class);
    static private final Logger wireLogger = Aliyun
            .getWireLogger(org.dasein.cloud.aliyun.util.requester.AliyunRequestBuilderFactory.class);

    static protected final String ENCODING = "UTF-8";
    static protected final String SIGNATURE_ALGORITHM = "HmacSHA1";

    protected final Aliyun aliyun;
    protected final Category category;
    protected final String httpMethod;
    protected final String action;
    protected final Map<String, String> parameters;

    public AliyunRequestBuilderFactory(Aliyun aliyun, Category category, String httpMethod, String action,
            Map<String, Object> parameters) {
        this.aliyun = aliyun;
        this.category = category;
        this.httpMethod = httpMethod;
        this.action = action;
        this.parameters = new HashMap<String, String>();
        for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
            Object value = parameter.getValue();
            if (value instanceof Date) {
                this.parameters.put(parameter.getKey(), aliyun.formatIso8601Date((Date) value));
            } else {
                this.parameters.put(parameter.getKey(), value.toString());
            }
        }
    }

    public RequestBuilder build() throws InternalException {
        RequestBuilder requestBuilder;
        if("GET".equals(httpMethod)) {
            requestBuilder = RequestBuilder.get();
        } else if("POST".equals(httpMethod)) {
            requestBuilder = RequestBuilder.post();
        } else {
            stdLogger.error("RequestBuilderFactory.build() failed due to URI invalid: httpMethod can only be GET or POST");
            throw new IllegalArgumentException("httpMethod can only be GET or POST");
        }
        requestBuilder.setVersion(new ProtocolVersion("HTTP", 1, 1));

        URIBuilder uriBuilder = new URIBuilder();
        uriBuilder.setScheme("https").setHost(category.getHost(this.aliyun)).setPath("/");
        try {
            requestBuilder.setUri(uriBuilder.build().toString());
        } catch (URISyntaxException uriSyntaxException) {
            stdLogger.error("RequestBuilderFactory.build() failed due to URI invalid: " + uriSyntaxException.getMessage());
            throw new InternalException(uriSyntaxException);
        }

        applyFrameworkParameters(requestBuilder);
        sign(requestBuilder);

        if("GET".equals(httpMethod)) {
            for (Map.Entry<String, String> parameter : this.parameters.entrySet()) {
                requestBuilder.addParameter(parameter.getKey(), parameter.getValue());
            }
        } else if("POST".equals(httpMethod)){
            List<NameValuePair> params = new ArrayList<NameValuePair>();
            for( Map.Entry<String, String> entry : parameters.entrySet() ) {
                params.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
            }
            try {
                requestBuilder.setEntity(new UrlEncodedFormEntity(params, ENCODING));
            } catch (UnsupportedEncodingException unsupportedEncodingException) {
                stdLogger.error("AliyunMethod.post() failed due to encoding not supported: "
                        + unsupportedEncodingException.getMessage());
                throw new InternalException(unsupportedEncodingException);
            }
        }

        return requestBuilder;
    }

    protected abstract void applyFrameworkParameters(RequestBuilder requestBuilder);
    protected abstract void sign(RequestBuilder requestBuilder) throws InternalException;

    protected String urlEncode(String value) throws InternalException {
        if (value == null) {
            return null;
        }
        try {
            return URLEncoder.encode(value, ENCODING).replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
        } catch (UnsupportedEncodingException unsupportedEncodingException) {
            stdLogger.error("AliyunMethod.urlEncode() failed due to encoding not supported: " + unsupportedEncodingException.getMessage());
            throw new InternalException(unsupportedEncodingException);
        }
    }

    protected String computeSignature(byte[] accessKeySecret, String stringToSign) throws InternalException {
        String signature;
        try {
            Mac mac = Mac.getInstance(SIGNATURE_ALGORITHM);
            mac.init(new SecretKeySpec(accessKeySecret, SIGNATURE_ALGORITHM));
            byte[] signedData = mac.doFinal(stringToSign.getBytes(ENCODING));
            signature = new String(Base64.encodeBase64(signedData));
        } catch (NoSuchAlgorithmException noSuchAlgorithmException) {
            stdLogger.error("AliyunMethod.sign() failed due to algorithm not supported: " + noSuchAlgorithmException.getMessage());
            throw new InternalException(noSuchAlgorithmException);
        } catch (InvalidKeyException invalidKeyException) {
            stdLogger.error("AliyunMethod.sign() failed due to key invalid: " + invalidKeyException.getMessage());
            throw new InternalException(invalidKeyException);
        } catch (UnsupportedEncodingException unsupportedEncodingException) {
            stdLogger.error("AliyunMethod.sign() failed due to encoding not supported: " + unsupportedEncodingException.getMessage());
            throw new InternalException(unsupportedEncodingException);
        }
        return signature;
    }
}
