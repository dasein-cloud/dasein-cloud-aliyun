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

import org.apache.http.NameValuePair;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.util.requester.streamprocessors.StreamProcessor;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Jeffrey Yan on 7/3/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.05.1
 */
public class AliyunRequestBuilder {
    static private final Logger logger = Aliyun
            .getStdLogger(org.dasein.cloud.aliyun.util.requester.AliyunRequestBuilder.class);

    static protected final String ENCODING = "UTF-8";

    private Aliyun aliyun;
    protected RequestBuilder requestBuilder;

    private Category category;

    protected Map<String, String> formEntity;

    private String stringEntity;
    private ContentType stringContentType;

    private boolean clientToken;

    private AliyunRequestBuilder(RequestBuilder requestBuilder) {
        this.requestBuilder = requestBuilder;
    }

    public static AliyunRequestBuilder get() {
        return new AliyunRequestBuilder(RequestBuilder.get());
    }

    public static AliyunRequestBuilder post() {
        return new AliyunRequestBuilder(RequestBuilder.post());
    }

    public static AliyunRequestBuilder put() {
        return new AliyunRequestBuilder(RequestBuilder.put());
    }

    public static AliyunRequestBuilder delete() {
        return new AliyunRequestBuilder(RequestBuilder.delete());
    }

    public AliyunRequestBuilder provider(Aliyun aliyun) {
        this.aliyun = aliyun;
        return this;
    }

    public AliyunRequestBuilder category(Category category) {
        this.category = category;
        return this;
    }

    public AliyunRequestBuilder header(final String name, final String value) {
        requestBuilder.addHeader(name, value);
        return this;
    }

    private String asString(Object value) {
        if (value instanceof Date) {
            return aliyun.formatIso8601Date((Date) value);
        } else {
            return value.toString();
        }
    }

    public AliyunRequestBuilder parameter(final String name, final Object value) {
        requestBuilder.addParameter(name, asString(value));
        return this;
    }

    public AliyunRequestBuilder entity(Map<String, Object> formEntity) {
        this.formEntity = new HashMap<String, String>();
        for (Map.Entry<String, Object> parameter : formEntity.entrySet()) {
            this.formEntity.put(parameter.getKey(), asString(parameter.getValue()));
        }

        stringEntity = null;
        return this;
    }

    public <T> AliyunRequestBuilder entity(T object, StreamProcessor<T> streamProcessor, ContentType contentType) {
        stringEntity = streamProcessor.write(object);
        stringContentType = contentType;

        formEntity = null;
        return this;
    }

    public AliyunRequestBuilder clientToken(boolean clientToken) {
        this.clientToken = clientToken;
        return this;
    }

    public HttpUriRequest build() throws InternalException {
        requestBuilder.setVersion(new ProtocolVersion("HTTP", 1, 1));

        URIBuilder uriBuilder = new URIBuilder();
        uriBuilder.setScheme("https").setHost(category.getHost(this.aliyun)).setPath("/");
        try {
            requestBuilder.setUri(uriBuilder.build().toString());
        } catch (URISyntaxException uriSyntaxException) {
            logger.error("RequestBuilderFactory.build() failed due to URI invalid: " + uriSyntaxException.getMessage());
            throw new InternalException(uriSyntaxException);
        }
        AliyunRequestBuilderStrategy requestBuilderStrategy = category.getRequestBuilderStrategy(aliyun);
        requestBuilderStrategy.applyFrameworkParameters(this);
        requestBuilderStrategy.sign(this);

        if (formEntity != null) {
            List<NameValuePair> params = new ArrayList<NameValuePair>();
            for( Map.Entry<String, String> entry : formEntity.entrySet() ) {
                params.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
            }
            try {
                requestBuilder.setEntity(new UrlEncodedFormEntity(params, ENCODING));
            } catch (UnsupportedEncodingException unsupportedEncodingException) {
                logger.error("AliyunRequestBuilder.build() failed due to encoding not supported: "
                        + unsupportedEncodingException.getMessage());
                throw new InternalException(unsupportedEncodingException);
            }
        }

        if (stringEntity != null) {
            requestBuilder.setEntity(new StringEntity(stringEntity, stringContentType));
        }

        return requestBuilder.build();
    }

    public enum Category {
        ECS("ecs", "Elastic Compute Service", AliyunEcsRequestBuilderStrategy.class),
        SLB("slb", "Server Load Balancer", AliyunSlbRequestBuilderStrategy.class),
        RDS("rds", "Relational Database Service", AliyunRdsRequestBuilderStrategy.class),
        OSS("oss", "Open Storage Service", AliyunEcsRequestBuilderStrategy.class),
        SLS("sls", "Simple Log Service", AliyunEcsRequestBuilderStrategy.class);

        private String host;
        private String name;
        private Class<? extends AliyunRequestBuilderStrategy> requestBuilderFactoryClass;

        Category(String host, String name, Class<? extends AliyunRequestBuilderStrategy> requestBuilderFactoryClass) {
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

        public AliyunRequestBuilderStrategy getRequestBuilderStrategy(Aliyun aliyun) throws InternalException {
            try {
                Constructor<? extends AliyunRequestBuilderStrategy> constructor = requestBuilderFactoryClass
                        .getConstructor(Aliyun.class);
                AliyunRequestBuilderStrategy requestBuilderFactory = constructor.newInstance(aliyun);
                return requestBuilderFactory;
            } catch (Exception exception) {
                throw new InternalException(exception);
            }
        }

        @Deprecated
        public String getName() {
            return name;
        }
    }

}
