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

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.util.requester.DriverToCoreMapper;
import org.dasein.cloud.util.requester.streamprocessors.StreamToJSONObjectProcessor;
import org.json.JSONObject;

/**
 * Created by Jeffrey Yan on 7/7/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.05.1
 */
public class AliyunValidateJsonResponseHandler extends AliyunResponseHandlerWithMapper<JSONObject, Void> {

    static private final Logger stdLogger = Aliyun.getStdLogger(AliyunValidateJsonResponseHandler.class);

    public AliyunValidateJsonResponseHandler(final Aliyun aliyun) {
        super(new StreamToJSONObjectProcessor(),
                new DriverToCoreMapper<JSONObject, Void>() {
                    @Override
                    public Void mapFrom(JSONObject json) {
                        try {
                            aliyun.validateResponse(json);
                        } catch (CloudException cloudException) {
                            stdLogger.error("Failed to validate response", cloudException);
                            throw new RuntimeException(cloudException.getMessage());
                        } catch (InternalException internalException) {
                            stdLogger.error("Failed to validate response", internalException);
                            throw new RuntimeException(internalException.getMessage());
                        }
                        return null;
                    }
                },
                JSONObject.class);
    }
}
