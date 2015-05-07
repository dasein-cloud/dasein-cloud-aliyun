/*
 * *
 *  * Copyright (C) 2009-2015 Dell, Inc.
 *  * See annotations for authorship information
 *  *
 *  * ====================================================================
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  * ====================================================================
 *
 */

package org.dasein.cloud.aliyun;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.GzipDecompressingEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.json.JSONException;
import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by Jeffrey Yan on 5/5/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.05.1
 */
public class AliyunMethod {
    static private final Logger stdLogger = Aliyun.getStdLogger(AliyunMethod.class);
    static private final Logger wireLogger = Aliyun.getWireLogger(AliyunMethod.class);

    static private final String ISO8601_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    private Aliyun aliyun;
    private Category category;
    private String action;
    private Map<String, Object> parameters;

    public AliyunMethod(Aliyun aliyun, Category category, String action) {
        this.aliyun = aliyun;
        this.category = category;
        this.action = action;
        this.parameters = new HashMap<String, Object>();
    }

    public AliyunMethod(Aliyun aliyun, Category category, String action, Map<String, Object> parameters) {
        this.aliyun = aliyun;
        this.category = category;
        this.action = action;
        this.parameters = parameters;
    }

    private String formatIso8601Date(Date date) {
        SimpleDateFormat df = new SimpleDateFormat(ISO8601_DATE_FORMAT);
        df.setTimeZone(new SimpleTimeZone(0, "GMT"));
        return df.format(date);
    }

    private static String urlEncode(String value) throws InternalException {
        if (value == null) {
            return null;
        }
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
        } catch (UnsupportedEncodingException unsupportedEncodingException) {
            throw new InternalException(unsupportedEncodingException);
        }
    }

    private String sign(byte[] accessKeySecret, Map<String, String> requestParameters) throws InternalException {
        String signature;
        String[] sortedKeys = requestParameters.keySet().toArray(new String[]{});
        Arrays.sort(sortedKeys);
        StringBuilder canonicalStringBuilder = new StringBuilder();
        for(String key : sortedKeys) {
            canonicalStringBuilder.append("&").append(urlEncode(key)).append("=")
                    .append(urlEncode(requestParameters.get(key)));
        }
        String canonicalString = canonicalStringBuilder.toString().substring(1);

        StringBuilder stringToSign = new StringBuilder();
        stringToSign.append("GET").append("&").append(urlEncode("/")).append("&");
        stringToSign.append(urlEncode(canonicalString));

        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            byte[] secretKey = Arrays.copyOf(accessKeySecret, accessKeySecret.length + 1);
            secretKey[accessKeySecret.length] = '&';
            mac.init(new SecretKeySpec(secretKey, "HmacSHA1"));
            byte[] signedData = mac.doFinal(stringToSign.toString().getBytes("UTF-8"));
            signature = new String(Base64.encodeBase64(signedData));
        } catch (NoSuchAlgorithmException noSuchAlgorithmException) {
            throw new InternalException(noSuchAlgorithmException);
        } catch (InvalidKeyException invalidKeyException) {
            throw new InternalException(invalidKeyException);
        } catch (UnsupportedEncodingException unsupportedEncodingException) {
            throw new InternalException(unsupportedEncodingException);
        }
        return signature;
    }

    public Response get() throws InternalException, CloudException {
        if( stdLogger.isTraceEnabled() ) {
            stdLogger.trace("ENTER - " + AliyunMethod.class.getName() + ".get(" + category.getHost()  + ")");
        }
        if( wireLogger.isDebugEnabled() ) {
            wireLogger.debug("");
            wireLogger.debug(">>> [GET (" + (new Date()) + ")] -> " + category.getHost()
                    + " >--------------------------------------------------------------------------------------");
        }

        try {

            byte[][] accessKey = ( byte[][] ) aliyun.getContext().getConfigurationValue(Aliyun.DSN_ACCESS_KEY);

            Map<String, String> requestParameters = new HashMap<String, String>();
            requestParameters.put("Action", this.action);
            requestParameters.put("Version", "2014-05-26");
            requestParameters.put("AccessKeyId", new String(accessKey[0]));
            requestParameters.put("TimeStamp", formatIso8601Date(new Date()));
            requestParameters.put("SignatureMethod", "HMAC-SHA1");
            requestParameters.put("SignatureVersion", "1");
            requestParameters.put("SignatureNonce", UUID.randomUUID().toString());
            requestParameters.put("Format", "JSON");
            for (Map.Entry<String, Object> parameter : this.parameters.entrySet()) {
                Object value = parameter.getValue();
                if (value instanceof Date) {
                    requestParameters.put(parameter.getKey(), formatIso8601Date((Date) value));
                } else {
                    requestParameters.put(parameter.getKey(), value.toString());
                }
            }

            String signature = sign(accessKey[1], requestParameters);
            requestParameters.put("Signature", signature);

            HttpGet httpGet;
            URIBuilder uriBuilder = new URIBuilder();
            uriBuilder.setScheme("https").setHost(category.getHost()).setPath("/");
            for(Map.Entry<String, String> requestParameter: requestParameters.entrySet()) {
                uriBuilder.setParameter(requestParameter.getKey(), requestParameter.getValue());
            }
            try {
                httpGet = new HttpGet(uriBuilder.build());
            } catch (URISyntaxException uriSyntaxException) {
                throw new InternalException(uriSyntaxException);
            }

            if( wireLogger.isDebugEnabled() ) {
                wireLogger.debug(httpGet.getRequestLine().toString());
                for( Header header : httpGet.getAllHeaders() ) {
                    wireLogger.debug(header.getName() + ": " + header.getValue());
                }
                wireLogger.debug("");
            }

            HttpResponse httpResponse;

            try {
                HttpClient client = getClient();
                httpResponse = client.execute(httpGet);
                if( wireLogger.isDebugEnabled() ) {
                    wireLogger.debug(httpResponse.getStatusLine().toString());
                    for( Header header : httpResponse.getAllHeaders() ) {
                        wireLogger.debug(header.getName() + ": " + header.getValue());
                    }
                    wireLogger.debug("");
                }
            } catch( IOException ioException ) {
                stdLogger.error("I/O error from server communications: " + ioException.getMessage());
                throw new InternalException(ioException);
            }
            int httpCode = httpResponse.getStatusLine().getStatusCode();

            stdLogger.debug("HTTP STATUS: " + httpCode);

            if( httpCode == HttpStatus.SC_NOT_FOUND || httpCode == HttpStatus.SC_GONE ) {
                return null;
            }
            if (httpCode < HttpStatus.SC_OK || httpCode >= HttpStatus.SC_MULTIPLE_CHOICES) {
                stdLogger.error("Unexpected OK for GET request, got " + httpCode);

                HttpEntity entity = httpResponse.getEntity();
                if (entity == null) {
                    throw new CloudException();
                }
                Response response = new Response(entity);
                JSONObject json = response.asJson();
                if( wireLogger.isDebugEnabled() ) {
                    wireLogger.debug(json);
                    wireLogger.debug("");
                }
                String code;
                String message;
                String requestId;
                String hostId;
                try {
                    code = json.getString("Code");
                    message = json.getString("Message");
                    requestId = json.getString("RequestId");
                    hostId = json.getString("HostId");
                } catch (JSONException jsonException) {
                    stdLogger.error("Failed to parse JSON due to field not exist", jsonException);
                    throw new InternalException(jsonException);
                }
                stdLogger.error(
                        " [" + "code:" + code + "; message:" + message + "; requestId:" + requestId + "; hostId:"
                                + hostId + "] ");
                throw AliyunException.newInstance(httpCode, code, message, requestId, hostId);
            } else {
                HttpEntity entity = httpResponse.getEntity();
                if (entity == null) {
                    throw new CloudException();
                }
                return new Response(entity);
            }
        } finally {
            if( stdLogger.isTraceEnabled() ) {
                stdLogger.trace("EXIT - " + AliyunMethod.class.getName() + ".get(" + category.getHost() + ")");
            }
            if( wireLogger.isDebugEnabled() ) {
                wireLogger.debug(">>> [GET (" + (new Date()) + ")] -> " + category.getHost()
                        + " >--------------------------------------------------------------------------------------");
                wireLogger.debug("");
            }
        }
    }

    public Response post() {
        return null;
    }

    private HttpClient getClient() throws InternalException {
        ProviderContext ctx = aliyun.getContext();
        if( ctx == null ) {
            throw new InternalException("No context was specified for this request");
        }

        final HttpParams params = new BasicHttpParams();

        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(params, Consts.UTF_8.toString());
        HttpProtocolParams.setUserAgent(params, "Dasein Cloud");

        Properties customProperties = ctx.getCustomProperties();
        if( customProperties != null ) {
            String proxyHost = customProperties.getProperty("proxyHost");
            String proxyPortStr = customProperties.getProperty("proxyPort");
            int proxyPort = 0;
            if( proxyPortStr != null ) {
                proxyPort = Integer.parseInt(proxyPortStr);
            }
            if( proxyHost != null && proxyHost.length() > 0 && proxyPort > 0 ) {
                params.setParameter(ConnRoutePNames.DEFAULT_PROXY,
                        new HttpHost(proxyHost, proxyPort)
                );
            }
        }
        DefaultHttpClient client = new DefaultHttpClient(params);
        client.addRequestInterceptor(new HttpRequestInterceptor() {
            public void process(
                    final HttpRequest request,
                    final HttpContext context) throws HttpException, IOException {
                if( !request.containsHeader("Accept-Encoding") ) {
                    request.addHeader("Accept-Encoding", "gzip");
                }
            }
        });
        client.addResponseInterceptor(new HttpResponseInterceptor() {
            public void process(
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                HttpEntity entity = response.getEntity();
                if( entity != null ) {
                    Header header = entity.getContentEncoding();
                    if( header != null ) {
                        for( HeaderElement codec : header.getElements() ) {
                            if( codec.getName().equalsIgnoreCase("gzip") ) {
                                response.setEntity(
                                        new GzipDecompressingEntity(response.getEntity()));
                                break;
                            }
                        }
                    }
                }
            }
        });
        return client;
    }

    public enum Category {
        ECS("ecs", "Elastic Compute Service"),
        SLB("slb", "Server Load Balancer"),
        RDS("rds", "Relational Database Service"),
        OSS("oss", "Open Storage Service"),
        SLS("sls", "Simple Log Service");

        private String host;
        private String name;

        private Category(String host, String name) {
            this.host = host;
            this.name = name;
        }

        public String getHost() {
            return host + ".aliyuncs.com";
        }

        @Deprecated
        public String getName() {
            return name;
        }


    }

    public class Response {
        private HttpEntity entity;

        private Response(HttpEntity entity) {
            this.entity = entity;
        }

        public JSONObject asJson() throws CloudException {
            try {
                String json = EntityUtils.toString(entity);
                return new JSONObject(json);
            } catch (IOException ioException) {
                stdLogger.error("Response.asJSON(): Failed to read response due to a cloud I/O error: " + ioException
                        .getMessage());
                throw new CloudException(ioException);
            } catch (JSONException jsonException) {
                stdLogger.error("Response.asJSON(): Failed to parse response due to a JSON error: " + jsonException
                        .getMessage());
                throw new CloudException(jsonException);
            }
        }

        public InputStream asStream() throws CloudException {
            try {
                if (entity == null) {
                    return null;
                }
                return entity.getContent();
            } catch (IOException ioException) {
                stdLogger.error("Response.asStream(): Failed to read response due to a cloud I/O error: " + ioException
                        .getMessage());
                throw new CloudException(ioException);
            }
        }
    }
}
