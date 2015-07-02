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

import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.util.requester.DriverToCoreMapper;
import org.dasein.cloud.util.requester.streamprocessors.JsonStreamToObjectProcessor;
import org.dasein.cloud.util.requester.streamprocessors.StreamToDocumentProcessor;
import org.dasein.cloud.util.requester.streamprocessors.StreamToJSONObjectProcessor;
import org.dasein.cloud.util.requester.streamprocessors.XmlStreamToObjectProcessor;
import org.json.JSONObject;
import org.w3c.dom.Document;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Jeffrey Yan on 5/25/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.05.1
 */
public class AliyunMethodInternal {
    static private final Logger stdLogger = Aliyun.getStdLogger(
            AliyunMethodInternal.class);
    static private final Logger wireLogger = Aliyun.getWireLogger(AliyunMethodInternal.class);

    private Aliyun aliyun;
    private Category category;
    private String action;
    private Map<String, Object> parameters;
    private boolean generateClientToken;

    public AliyunMethodInternal(Aliyun aliyun, Category category, String action) {
        this.aliyun = aliyun;
        this.category = category;
        this.action = action;
        this.parameters = new HashMap<String, Object>();
    }

    public AliyunMethodInternal(Aliyun aliyun, Category category, String action, boolean generateClientToken) {
        this(aliyun, category, action);
        this.generateClientToken = generateClientToken;
    }

    public AliyunMethodInternal(Aliyun aliyun, Category category, String action, Map<String, Object> parameters) {
        this(aliyun, category, action);
        this.parameters = parameters;
    }

    public AliyunMethodInternal(Aliyun aliyun, Category category, String action, Map<String, Object> parameters,
            boolean generateClientToken) {
        this(aliyun, category, action, parameters);
        this.generateClientToken = generateClientToken;
    }

    private HttpClientBuilder getHttpClientBuilder(){
        HttpClientBuilder builder = HttpClientBuilder.create();
        builder.setUserAgent("Dasein Cloud");
        //HttpProtocolParams.setContentCharset(params, Consts.UTF_8.toString());
        //TODO: overwrite HttpRequestRetryHandler to handle idempotency
        //refer http://docs.aliyun.com/?spm=5176.100054.3.1.Ym5tBh#/ecs/open-api/appendix&idempotency
        return builder;
    }

    public Response get() throws InternalException, CloudException {
        if( stdLogger.isTraceEnabled() ) {
            stdLogger.trace("ENTER - " + org.dasein.cloud.aliyun.AliyunMethod.class.getName() + ".get(" + category.getHost(this.aliyun)  + ")");
        }
        if( wireLogger.isDebugEnabled() ) {
            wireLogger.debug("");
            wireLogger.debug(">>> [GET (" + (new Date()) + ")] -> " + category.getHost(this.aliyun)
                    + " >--------------------------------------------------------------------------------------");
        }

        try {
            RequestBuilder requestBuilder = category.getRequestBuilder(aliyun, "GET", action, parameters);
            return new AliyunRequestExecutor(aliyun, getHttpClientBuilder(), requestBuilder.build(),
                    new AliyunResponseHandler()).execute();
        } finally {
            if( stdLogger.isTraceEnabled() ) {
                stdLogger.trace("EXIT - " + org.dasein.cloud.aliyun.AliyunMethod.class.getName() + ".get(" + category.getHost(this.aliyun) + ")");
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
            stdLogger.trace("ENTER - " + org.dasein.cloud.aliyun.AliyunMethod.class.getName() + ".post(" + category.getHost(this.aliyun)  + ")");
        }
        if( wireLogger.isDebugEnabled() ) {
            wireLogger.debug("");
            wireLogger.debug(">>> [POST (" + (new Date()) + ")] -> " + category.getHost(this.aliyun)
                    + " >--------------------------------------------------------------------------------------");
        }
        try {
            RequestBuilder requestBuilder = category.getRequestBuilder(aliyun, "POST", action, parameters);
            return new AliyunRequestExecutor(aliyun, getHttpClientBuilder(), requestBuilder.build(),
                    new AliyunResponseHandler()).execute();
        } finally {
            if( stdLogger.isTraceEnabled() ) {
                stdLogger.trace("EXIT - " + org.dasein.cloud.aliyun.AliyunMethod.class.getName() + ".post(" + category.getHost(this.aliyun) + ")");
            }
            if( wireLogger.isDebugEnabled() ) {
                wireLogger.debug(">>> [POST (" + (new Date()) + ")] -> " + category.getHost(this.aliyun)
                        + " >--------------------------------------------------------------------------------------");
                wireLogger.debug("");
            }
        }
    }

    public enum Category {
        ECS("ecs", "Elastic Compute Service", AliyunEcsRequestBuilderFactory.class),
        SLB("slb", "Server Load Balancer", AliyunSlbRequestBuilderFactory.class),
        RDS("rds", "Relational Database Service", AliyunRdsRequestBuilderFactory.class),
        OSS("oss", "Open Storage Service", AliyunEcsRequestBuilderFactory.class),
        SLS("sls", "Simple Log Service", AliyunEcsRequestBuilderFactory.class);

        private String host;
        private String name;
        private Class<? extends AliyunRequestBuilderFactory> requestBuilderFactoryClass;

        Category(String host, String name, Class<? extends AliyunRequestBuilderFactory> requestBuilderFactoryClass) {
            this.host = host;
            this.name = name;
            this.requestBuilderFactoryClass = requestBuilderFactoryClass;
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

        public RequestBuilder getRequestBuilder(Aliyun aliyun, String httpMethod, String action,
                Map<String, Object> parameters) throws InternalException {
            try {
                Constructor<? extends AliyunRequestBuilderFactory> constructor = requestBuilderFactoryClass
                        .getConstructor(Aliyun.class, Category.class, String.class, String.class, Map.class);
                AliyunRequestBuilderFactory requestBuilderFactory = constructor
                        .newInstance(aliyun, this, httpMethod, action, parameters);
                return requestBuilderFactory.build();
            } catch (Exception exception) {
                throw new InternalException(exception);
            }
        }

        @Deprecated
        public String getName() {
            return name;
        }
    }

    public static class Response {

        private String contentType;
        private InputStream inputStream;

        protected Response(String contentType, InputStream inputStream) {
            this.contentType = contentType;
            this.inputStream = inputStream;
        }

        public boolean isJson() {
            return contentType.contains("application/json") || contentType.contains("text/json");
        }

        public boolean isXml() {
            return contentType.contains("application/xml") || contentType.contains("text/xml");
        }

        public <V> V asPojo(Class<V> clz) throws CloudException, InternalException {
            if (isJson()) {
                return new JsonStreamToObjectProcessor<V>().read(inputStream, clz);
            } else if (isXml()) {
                return new XmlStreamToObjectProcessor<V>().read(inputStream, clz);
            } else {
                throw new InternalException("Response.asPojo(): Failed due to response is not JSON nor XML, but is " + contentType);
            }
        }

        public <T, V> V asPojo(DriverToCoreMapper<T, V> mapper) throws CloudException, InternalException {
            if (isJson()) {
                return mapper.mapFrom((T) asJson());
            } else if (isXml()) {
                return mapper.mapFrom((T) asXml());
            } else {
                throw new InternalException("Response.asPojo(): Failed due to response is not JSON nor XML, but is " + contentType);
            }
        }

        public JSONObject asJson() throws CloudException, InternalException {
            if (isJson()) {
                try {
                    return new StreamToJSONObjectProcessor().read(inputStream, JSONObject.class);
                } catch (IOException ioException) {
                    stdLogger.error(
                            "Response.asJson(): Failed to read response due to a cloud I/O error: " + ioException
                                    .getMessage());
                    throw new CloudException(ioException);
                }
            }
            throw new InternalException("Response.asJson(): Failed due to response is not JSON, but is " + contentType);
        }

        public Document asXml() throws CloudException, InternalException {
            if (isXml()) {
                try {
                    new StreamToDocumentProcessor().read(inputStream, Document.class);
                } catch (IOException ioException) {
                    stdLogger.error(
                            "Response.asDocument(): Failed to read response due to a cloud I/O error: " + ioException
                                    .getMessage());
                    throw new CloudException(ioException);
                }
            }
            throw new InternalException("Response.asDocument(): Failed due to response is not XML, but is " + contentType);
        }

    }
}
