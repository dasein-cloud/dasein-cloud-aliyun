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

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.dasein.cloud.util.requester.DriverToCoreMapper;
import org.dasein.cloud.util.requester.streamprocessors.StreamProcessor;

import java.io.IOException;

/**
 * Created by Jeffrey Yan on 7/6/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.05.1
 */
public class AliyunResponseHandlerWithMapper<T, V> implements ResponseHandler<V> {

    protected Class<T> classType;
    protected StreamProcessor<T> processor;
    private DriverToCoreMapper<T,V> mapper;

    public AliyunResponseHandlerWithMapper(StreamProcessor<T> processor, DriverToCoreMapper<T, V> mapper,
            Class<T> classType) {
        this.processor = processor;
        this.classType = classType;
        this.mapper = mapper;
    }

    @Override
    public V handleResponse(HttpResponse httpResponse) throws ClientProtocolException, IOException {
        ResponseHandler<T> responseHandler = new AliyunResponseHandler<T>(processor, classType);
        T responseObject = responseHandler.handleResponse(httpResponse);
        if (responseObject == null) {
            return null;
        }

        return mapper.mapFrom(responseObject);
    }
}
