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

import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;

/**
 * Created by Jeffrey Yan on 5/6/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.05.01
 */
public class AliyunException extends CloudException {
    static public AliyunException newInstance(int httpCode, String code, String message, String requestId,
            String hostId) {
        //http://docs.aliyun.com/?spm=5176.100054.3.1.Ym5tBh#/ecs/open-api/requestmethod&commonresponse
        //http://docs.aliyun.com/?spm=5176.100055.3.4.7xKK1V#/oss/api-reference/error-response
        CloudErrorType errorType;
        if (httpCode == 403 || code.equals("InvalidAccessKeyId.NotFound") || code.equals("IncompleteSignature") || code
                .equals("IllegalTimestamp") || code.equals("SignatureNonceUsed")) {
            errorType = CloudErrorType.AUTHENTICATION;
        } else if (code.equals("Throttling")) {
            errorType = CloudErrorType.THROTTLING;
        } else if (code.equals("InsufficientBalance")) {
            errorType = CloudErrorType.QUOTA;
        } else if (httpCode >= 400 && httpCode < 500) {
            errorType = CloudErrorType.COMMUNICATION;
        } else { //5xx
            errorType = CloudErrorType.GENERAL;
        }

        return new AliyunException(errorType, httpCode, code, message, requestId, hostId);
    }

    private String requestId;
    private String hostId;

    private AliyunException(CloudErrorType errorType, int httpCode, String code, String message, String requestId,
            String hostId) {
        super(errorType, httpCode, code, message);
        this.requestId = requestId;
        this.hostId = hostId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getHostId() {
        return hostId;
    }
}
