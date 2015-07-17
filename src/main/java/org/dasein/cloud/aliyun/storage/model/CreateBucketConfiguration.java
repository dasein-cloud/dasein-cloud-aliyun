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

package org.dasein.cloud.aliyun.storage.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Created by Jeffrey Yan on 7/9/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.09.1
 */

@XmlRootElement(name="CreateBucketConfiguration ")
@XmlAccessorType(XmlAccessType.FIELD)
public class CreateBucketConfiguration {

    @XmlElement(name="LocationConstraint")
    private String locationConstraint;

    public String getLocationConstraint() {
        return locationConstraint;
    }

    public void setLocationConstraint(String locationConstraint) {
        this.locationConstraint = locationConstraint;
    }
}
