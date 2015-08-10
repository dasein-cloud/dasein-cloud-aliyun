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

import org.dasein.cloud.aliyun.Aliyun;

import java.util.Map;

/**
 * Created by Jeffrey Yan on 7/24/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.09.1
 */
public class AliyunCdnRequestBuilderStrategy extends AliyunEcsRequestBuilderStrategy {

    public AliyunCdnRequestBuilderStrategy(Aliyun aliyun) {
        super(aliyun);
    }

    @Override
    protected Map<String, String> getFrameworkParameters() {
        Map<String, String> frameworkParameters = super.getFrameworkParameters();
        frameworkParameters.put("Version", "2014-11-11");
        //Does OwnerId or OwnerAccount need to be set?
        //http://docs.aliyun.com/?spm=5176.100054.3.3.z9LwSY#/pub/cdn/api-reference/call-method
        return frameworkParameters;
    }
}
