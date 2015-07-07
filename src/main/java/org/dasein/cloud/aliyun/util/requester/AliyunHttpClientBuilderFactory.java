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

import org.apache.http.impl.client.HttpClientBuilder;

/**
 * Created by Jeffrey Yan on 7/7/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.05.1
 */
public class AliyunHttpClientBuilderFactory {
    static public HttpClientBuilder newHttpClientBuilder() {
        HttpClientBuilder builder = HttpClientBuilder.create();
        builder.setUserAgent("Dasein Cloud");
        //HttpProtocolParams.setContentCharset(params, Consts.UTF_8.toString());
        //TODO: overwrite HttpRequestRetryHandler to handle idempotency
        //refer http://docs.aliyun.com/?spm=5176.100054.3.1.Ym5tBh#/ecs/open-api/appendix&idempotency
        return builder;
    }
}
