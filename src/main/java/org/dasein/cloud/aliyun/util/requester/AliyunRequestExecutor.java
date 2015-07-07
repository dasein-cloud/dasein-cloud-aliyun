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

import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClientBuilder;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.CloudProvider;
import org.dasein.cloud.aliyun.AliyunException;
import org.dasein.cloud.util.requester.DaseinRequestExecutor;

/**
 * Created by Jeffrey Yan on 7/1/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.05.1
 */
public class AliyunRequestExecutor<T> extends DaseinRequestExecutor<T> {

    public AliyunRequestExecutor(CloudProvider provider, HttpClientBuilder httpClientBuilder,
            HttpUriRequest httpUriRequest, ResponseHandler<T> responseHandler) {
        super(provider, httpClientBuilder, httpUriRequest, responseHandler);
    }

    protected CloudException translateException(Exception exception) {
        if (exception instanceof AliyunResponseException) {
            AliyunResponseException aliyunResponseException = (AliyunResponseException) exception;
            return AliyunException
                    .newInstance(aliyunResponseException.getHttpCode(), aliyunResponseException.getProviderCode(),
                            aliyunResponseException.getMessage(), aliyunResponseException.getRequestId(),
                            aliyunResponseException.getHostId());
        } else {
            return super.translateException(exception);
        }
    }
}