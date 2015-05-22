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
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.GzipDecompressingEntity;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SimpleTimeZone;
import java.util.UUID;

/**
 * Created by Jeffrey Yan on 5/5/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.05.01
 */
public class AliyunMethod {
    static private final Logger stdLogger = Aliyun.getStdLogger(AliyunMethod.class);
    static private final Logger wireLogger = Aliyun.getWireLogger(AliyunMethod.class);

    static private final String ENCODING = "UTF-8";
    static private final String SIGNATURE_ALGORITHM = "HmacSHA1";

    private Aliyun aliyun;
    private Category category;
    private String action;
    private Map<String, Object> parameters;
    private boolean generateClientToken;

    public AliyunMethod(Aliyun aliyun, Category category, String action) {
        this.aliyun = aliyun;
        this.category = category;
        this.action = action;
        this.parameters = new HashMap<String, Object>();
    }

    public AliyunMethod(Aliyun aliyun, Category category, String action, boolean generateClientToken) {
        this(aliyun, category, action);
        this.generateClientToken = generateClientToken;
    }

    public AliyunMethod(Aliyun aliyun, Category category, String action, Map<String, Object> parameters) {
        this(aliyun, category, action);
        this.parameters = parameters;
    }

    public AliyunMethod(Aliyun aliyun, Category category, String action, Map<String, Object> parameters,
                        boolean generateClientToken) {
        this(aliyun, category, action, parameters);
        this.generateClientToken = generateClientToken;
    }

    private String urlEncode(String value) throws InternalException {
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

    private String sign(String httpMethod, Map<String, String> requestParameters) throws InternalException {
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
        stringToSign.append(httpMethod).append("&").append(urlEncode("/")).append("&");
        stringToSign.append(urlEncode(canonicalString));

        byte[][] accessKey = ( byte[][] ) aliyun.getContext().getConfigurationValue(Aliyun.DSN_ACCESS_KEY);
        byte[] accessKeySecret = accessKey[1];
        try {
            Mac mac = Mac.getInstance(SIGNATURE_ALGORITHM);
            byte[] secretKey = Arrays.copyOf(accessKeySecret, accessKeySecret.length + 1);
            secretKey[accessKeySecret.length] = '&';
            mac.init(new SecretKeySpec(secretKey, SIGNATURE_ALGORITHM));
            byte[] signedData = mac.doFinal(stringToSign.toString().getBytes(ENCODING));
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

    private  Map<String, String> transformParameters(String httpMethod) throws InternalException {
        byte[][] accessKey = ( byte[][] ) aliyun.getContext().getConfigurationValue(Aliyun.DSN_ACCESS_KEY);

        Map<String, String> requestParameters = new HashMap<String, String>();
        requestParameters.put("Action", this.action);
        requestParameters.put("Version", "2014-05-26");
        requestParameters.put("AccessKeyId", new String(accessKey[0]));
        requestParameters.put("TimeStamp", aliyun.formatIso8601Date(new Date()));
        requestParameters.put("SignatureMethod", "HMAC-SHA1");
        requestParameters.put("SignatureVersion", "1");
        requestParameters.put("SignatureNonce", UUID.randomUUID().toString());
        requestParameters.put("Format", "JSON");
        for (Map.Entry<String, Object> parameter : this.parameters.entrySet()) {
            Object value = parameter.getValue();
            if (value instanceof Date) {
                requestParameters.put(parameter.getKey(), aliyun.formatIso8601Date((Date) value));
            } else {
                requestParameters.put(parameter.getKey(), value.toString());
            }
        }

        String signature = sign(httpMethod, requestParameters);
        requestParameters.put("Signature", signature);

        return requestParameters;
    }

    private Response invoke(HttpRequestBase httpRequest) throws InternalException, CloudException {
        if( wireLogger.isDebugEnabled() ) {
            wireLogger.debug(httpRequest.getRequestLine().toString());
            for( Header header : httpRequest.getAllHeaders() ) {
                wireLogger.debug(header.getName() + ": " + header.getValue());
            }
            wireLogger.debug("");
        }

        HttpResponse httpResponse;
        try {
            HttpClient client = getClient();
            httpResponse = client.execute(httpRequest);
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
        return transformResponse(httpResponse);
    }

    private Response transformResponse(HttpResponse httpResponse) throws CloudException, InternalException {
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
                stdLogger.error("Failed to parse JSON", jsonException);
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
    }

    public Response get() throws InternalException, CloudException {
        if( stdLogger.isTraceEnabled() ) {
            stdLogger.trace("ENTER - " + AliyunMethod.class.getName() + ".get(" + category.getHost(this.aliyun)  + ")");
        }
        if( wireLogger.isDebugEnabled() ) {
            wireLogger.debug("");
            wireLogger.debug(">>> [GET (" + (new Date()) + ")] -> " + category.getHost(this.aliyun)
                    + " >--------------------------------------------------------------------------------------");
        }
        try {
            Map<String, String> requestParameters = transformParameters("GET");

            HttpGet httpGet = new HttpGet();
            URIBuilder uriBuilder = new URIBuilder();
            uriBuilder.setScheme("https").setHost(category.getHost(this.aliyun)).setPath("/");
            for(Map.Entry<String, String> requestParameter: requestParameters.entrySet()) {
                uriBuilder.setParameter(requestParameter.getKey(), requestParameter.getValue());
            }
            try {
                httpGet.setURI(uriBuilder.build());
            } catch (URISyntaxException uriSyntaxException) {
                stdLogger.error("AliyunMethod.get() failed due to URI invalid: " + uriSyntaxException.getMessage());
                throw new InternalException(uriSyntaxException);
            }
            return invoke(httpGet);
        } finally {
            if( stdLogger.isTraceEnabled() ) {
                stdLogger.trace("EXIT - " + AliyunMethod.class.getName() + ".get(" + category.getHost(this.aliyun) + ")");
            }
            if( wireLogger.isDebugEnabled() ) {
                wireLogger.debug(">>> [GET (" + (new Date()) + ")] -> " + category.getHost(this.aliyun)
                        + " >--------------------------------------------------------------------------------------");
                wireLogger.debug("");
            }
        }
    }

    public Response post() throws InternalException, CloudException {
        if( stdLogger.isTraceEnabled() ) {
            stdLogger.trace("ENTER - " + AliyunMethod.class.getName() + ".post(" + category.getHost(this.aliyun)  + ")");
        }
        if( wireLogger.isDebugEnabled() ) {
            wireLogger.debug("");
            wireLogger.debug(">>> [POST (" + (new Date()) + ")] -> " + category.getHost(this.aliyun)
                    + " >--------------------------------------------------------------------------------------");
        }
        try {
            Map<String, String> requestParameters = transformParameters("POST");

            HttpPost httpPost = new HttpPost();
            URIBuilder uriBuilder = new URIBuilder();
            uriBuilder.setScheme("https").setHost(category.getHost(this.aliyun)).setPath("/");

            List<NameValuePair> params = new ArrayList<NameValuePair>();

            for( Map.Entry<String, String> entry : requestParameters.entrySet() ) {
                params.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
            }

            try {
                httpPost.setURI(uriBuilder.build());
                httpPost.setEntity(new UrlEncodedFormEntity(params, ENCODING));
            } catch (URISyntaxException uriSyntaxException) {
                stdLogger.error("AliyunMethod.post() failed due to URI invalid: " + uriSyntaxException.getMessage());
                throw new InternalException(uriSyntaxException);
            } catch (UnsupportedEncodingException unsupportedEncodingException) {
                stdLogger.error("AliyunMethod.post() failed due to encoding not supported: " + unsupportedEncodingException.getMessage());
                throw new InternalException(unsupportedEncodingException);
            }
            return invoke(httpPost);
        } finally {
            if( stdLogger.isTraceEnabled() ) {
                stdLogger.trace("EXIT - " + AliyunMethod.class.getName() + ".post(" + category.getHost(this.aliyun) + ")");
            }
            if( wireLogger.isDebugEnabled() ) {
                wireLogger.debug(">>> [POST (" + (new Date()) + ")] -> " + category.getHost(this.aliyun)
                        + " >--------------------------------------------------------------------------------------");
                wireLogger.debug("");
            }
        }
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
                        new HttpHost(proxyHost, proxyPort));
            }
        }
        DefaultHttpClient client = new DefaultHttpClient(params);
        //TODO: overwrite HttpRequestRetryHandler to handle idempotency
        //refer http://docs.aliyun.com/?spm=5176.100054.3.1.Ym5tBh#/ecs/open-api/appendix&idempotency
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

        public String getHost(Aliyun aliyun) {
            String endpoint = ".aliyuncs.com";
            //ignore config one, use hardcode URL
            /*
            ProviderContext context = aliyun.getContext();
            if (context != null) {
                Cloud cloud = context.getCloud();
                if (cloud != null) {
                    String configEndpoint = cloud.getEndpoint();
                    if(configEndpoint != null && !"".equals(configEndpoint)) {
                        endpoint = configEndpoint;
                    }
                }
            }
            */
            return host + endpoint;
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
