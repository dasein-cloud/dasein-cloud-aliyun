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

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.aliyun.util.requester.AliyunMethodInternal.Response;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Created by Jeffrey Yan on 7/1/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.05.1
 */
public class AliyunResponseHandler implements ResponseHandler<Response> {

    static private final Logger stdLogger = Aliyun
            .getStdLogger(AliyunResponseHandler.class);
    static private final Logger wireLogger = Aliyun
            .getWireLogger(AliyunResponseHandler.class);

    @Override
    public Response handleResponse(HttpResponse httpResponse) throws ClientProtocolException, IOException {
        int httpCode = httpResponse.getStatusLine().getStatusCode();

        stdLogger.debug("HTTP STATUS: " + httpCode);

        if( httpCode == HttpStatus.SC_NOT_FOUND || httpCode == HttpStatus.SC_GONE ) {
            return null;
        }

        HttpEntity entity = httpResponse.getEntity();
        if (entity == null) {
            throw new IOException();
        }
        Response response = new Response(entity.getContentType().getValue(),
                new ByteArrayInputStream(EntityUtils.toByteArray(entity)));
        if (httpCode < HttpStatus.SC_OK || httpCode >= HttpStatus.SC_MULTIPLE_CHOICES) {
            stdLogger.error("Unexpected OK for GET request, got " + httpCode);
            throw extractException(httpCode, entity.getContentType().getValue(), response);
        } else {
            return response;
        }
    }

    protected ClientProtocolException extractException(int httpCode, String contentType, Response response) {
        String code;
        String message;
        String requestId;
        String hostId;

        if(response.isJson()) {
            try {
                JSONObject json = response.asJson();
                if( wireLogger.isDebugEnabled() ) {
                    wireLogger.debug(json);
                    wireLogger.debug("");
                }
                code = json.getString("Code");
                message = json.getString("Message");
                requestId = json.getString("RequestId");
                hostId = json.getString("HostId");
            } catch (JSONException jsonException) {
                stdLogger.error("Failed to parse JSON", jsonException);
                return new ClientProtocolException(jsonException);
            } catch (CloudException cloudException) {
                stdLogger.error("Failed to read JSON", cloudException);
                return new ClientProtocolException(cloudException);
            } catch (InternalException internalException) {
                stdLogger.error("Failed to read JSON", internalException);
                return new ClientProtocolException(internalException);
            }
        } else if(response.isXml()) {
            try {
                Document document = response.asXml();
                code = document.getElementsByTagName("Code").item(0).getTextContent();
                message = document.getElementsByTagName("Message").item(0).getTextContent();
                requestId = document.getElementsByTagName("RequestId").item(0).getTextContent();
                hostId = document.getElementsByTagName("HostId").item(0).getTextContent();
            } catch (CloudException cloudException) {
                stdLogger.error("Failed to read XML", cloudException);
                return new ClientProtocolException(cloudException);
            } catch (InternalException internalException) {
                stdLogger.error("Failed to read XML", internalException);
                return new ClientProtocolException(internalException);
            }
        } else {
            return new ClientProtocolException("Response is not JSON nor XML, but is " + contentType);
        }

        stdLogger.error(
                " [" + "code:" + code + "; message:" + message + "; requestId:" + requestId + "; hostId:"
                        + hostId + "] ");
        return new AliyunResponseException(httpCode, code, message, requestId, hostId);

    }
}
