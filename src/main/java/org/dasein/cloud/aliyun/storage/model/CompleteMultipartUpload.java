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
import java.util.List;

/**
 * Created by Jeffrey Yan on 7/14/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.09.1
 */
@XmlRootElement(name="CompleteMultipartUpload")
@XmlAccessorType(XmlAccessType.FIELD)
public class CompleteMultipartUpload {
    @XmlElement(name="Part")
    private List<Part> parts;

    public List<Part> getParts() {
        return parts;
    }

    public void setParts(List<Part> parts) {
        this.parts = parts;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Part {
        @XmlElement(name="PartNumber")
        private Integer partNumber;

        @XmlElement(name="ETag")
        private String ETag;

        public Integer getPartNumber() {
            return partNumber;
        }

        public void setPartNumber(Integer partNumber) {
            this.partNumber = partNumber;
        }

        public String getETag() {
            return ETag;
        }

        public void setETag(String ETag) {
            this.ETag = ETag;
        }
    }
}
