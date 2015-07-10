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

import org.apache.http.Consts;
import org.apache.http.NameValuePair;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.message.HeaderGroup;
import org.apache.log4j.Logger;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.util.requester.streamprocessors.JsonStreamToObjectProcessor;
import org.dasein.cloud.util.requester.streamprocessors.StreamProcessor;
import org.dasein.cloud.util.requester.streamprocessors.StreamToDocumentProcessor;
import org.dasein.cloud.util.requester.streamprocessors.StreamToJSONObjectProcessor;
import org.dasein.cloud.util.requester.streamprocessors.XmlStreamToObjectProcessor;

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

    protected RequestBuilder requestBuilder;

    protected Aliyun aliyun;
    protected String subdomain;
    protected String path;
    protected Category category;

    protected HeaderGroup headergroup;
    protected Map<String, String> formEntity;
    protected String stringEntity;
    protected ContentType contentType;

    private boolean clientToken; //TODO, handle resend case by use client token


    private AliyunRequestBuilder(RequestBuilder requestBuilder) {
        this.requestBuilder = requestBuilder;
        this.headergroup = new HeaderGroup();
        this.path = "/";
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

    public AliyunRequestBuilder subdomain(String subdomain) {
        this.subdomain = subdomain;
        return this;
    }

    public AliyunRequestBuilder path(String path) {
        this.path = path;
        return this;
    }

    public AliyunRequestBuilder header(final String name, final String value) {
        requestBuilder.addHeader(name, value);
        headergroup.addHeader(new BasicHeader(name, value));
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

    public <T> AliyunRequestBuilder entity(T object, StreamProcessor<T> streamProcessor) {
        stringEntity = streamProcessor.write(object);
        if (streamProcessor instanceof StreamToDocumentProcessor
                || streamProcessor instanceof XmlStreamToObjectProcessor) {
            contentType = ContentType.create("application/xml", Consts.UTF_8);
        } else if(streamProcessor instanceof JsonStreamToObjectProcessor
                || streamProcessor instanceof StreamToJSONObjectProcessor) {
            contentType = ContentType.APPLICATION_JSON;
        }

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
        String host = category.getHost(this.aliyun);
        host = aliyun.isEmpty(subdomain) ? host : subdomain + "." + host;
        uriBuilder.setScheme("https").setHost(host).setPath(path);
        try {
            requestBuilder.setUri(uriBuilder.build().toString());
        } catch (URISyntaxException uriSyntaxException) {
            logger.error("RequestBuilderFactory.build() failed due to URI invalid: " + uriSyntaxException.getMessage());
            throw new InternalException(uriSyntaxException);
        }
        if (formEntity != null) {
            List<NameValuePair> params = new ArrayList<NameValuePair>();
            for( Map.Entry<String, String> entry : formEntity.entrySet() ) {
                params.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
            }
            try {
                requestBuilder.setEntity(new UrlEncodedFormEntity(params, ENCODING));
                contentType = ContentType.create(URLEncodedUtils.CONTENT_TYPE, ENCODING);
            } catch (UnsupportedEncodingException unsupportedEncodingException) {
                logger.error("AliyunRequestBuilder.build() failed due to encoding not supported: "
                        + unsupportedEncodingException.getMessage());
                throw new InternalException(unsupportedEncodingException);
            }
        }

        if (stringEntity != null) {
            requestBuilder.setEntity(new StringEntity(stringEntity, contentType));
        }

        AliyunRequestBuilderStrategy requestBuilderStrategy = category.getRequestBuilderStrategy(aliyun);
        requestBuilderStrategy.applyFrameworkParameters(this);
        requestBuilderStrategy.sign(this);

        return requestBuilder.build();
    }

    public enum Category {
        ECS("ecs", "Elastic Compute Service", AliyunEcsRequestBuilderStrategy.class),
        SLB("slb", "Server Load Balancer", AliyunSlbRequestBuilderStrategy.class),
        RDS("rds", "Relational Database Service", AliyunRdsRequestBuilderStrategy.class),
        OSS("oss", "Open Storage Service", AliyunOssRequestBuilderStrategy.class),
        MQS("mqs", "Message Queue Service", AliyunOssRequestBuilderStrategy.class);

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
            if (this == OSS || this == MQS) {
                String regionId = aliyun.getContext().getRegionId();
                if (regionId == null) {
                    throw new RuntimeException("No region was set for this request");
                }
                return host + "-" + regionId + endpoint;
            } else {
                return host + endpoint;
            }

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
