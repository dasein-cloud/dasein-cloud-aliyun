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
import java.util.Date;
import java.util.List;

/**
 * Created by Jeffrey Yan on 7/13/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.09.1
 */
@XmlRootElement(name="ListBucketResult")
@XmlAccessorType(XmlAccessType.FIELD)
public class ListBucketResult {
    @XmlElement(name="Name")
    private String name;

    @XmlElement(name="Prefix")
    private String prefix;

    @XmlElement(name="CommonPrefixes")
    private String commonPrefixes;

    @XmlElement(name="Marker")
    private String marker;

    @XmlElement(name="MaxKeys")
    private Integer maxKeys;

    @XmlElement(name="Delimiter")
    private String delimiter;

    @XmlElement(name="IsTruncated")
    private Boolean isTruncated;

    @XmlElement(name="Contents")
    private List<Contents> contentses;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getCommonPrefixes() {
        return commonPrefixes;
    }

    public void setCommonPrefixes(String commonPrefixes) {
        this.commonPrefixes = commonPrefixes;
    }

    public String getMarker() {
        return marker;
    }

    public void setMarker(String marker) {
        this.marker = marker;
    }

    public Integer getMaxKeys() {
        return maxKeys;
    }

    public void setMaxKeys(Integer maxKeys) {
        this.maxKeys = maxKeys;
    }

    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public Boolean isTruncated() {
        return isTruncated;
    }

    public void setIsTruncated(Boolean isTruncated) {
        this.isTruncated = isTruncated;
    }

    public List<Contents> getContentses() {
        return contentses;
    }

    public void setContentses(List<Contents> contentses) {
        this.contentses = contentses;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Contents {
        @XmlElement(name="Key")
        private String key;

        @XmlElement(name="LastModified")
        private Date lastModified;

        @XmlElement(name="ETag")
        private String eTag;

        @XmlElement(name="Type")
        private String type;

        @XmlElement(name="StorageClass")
        private String storageClass;

        @XmlElement(name="Owner")
        private Owner owner;

        @XmlElement(name="Size")
        private Long size;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public Date getLastModified() {
            return lastModified;
        }

        public void setLastModified(Date lastModified) {
            this.lastModified = lastModified;
        }

        public String geteTag() {
            return eTag;
        }

        public void seteTag(String eTag) {
            this.eTag = eTag;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getStorageClass() {
            return storageClass;
        }

        public void setStorageClass(String storageClass) {
            this.storageClass = storageClass;
        }

        public Owner getOwner() {
            return owner;
        }

        public void setOwner(Owner owner) {
            this.owner = owner;
        }

        public Long getSize() {
            return size;
        }

        public void setSize(Long size) {
            this.size = size;
        }

        @XmlAccessorType(XmlAccessType.FIELD)
        public static class Owner {
            @XmlElement(name="ID")
            private String id;

            @XmlElement(name="DisplayName")
            private String displayName;

            public String getId() {
                return id;
            }

            public void setId(String id) {
                this.id = id;
            }

            public String getDisplayName() {
                return displayName;
            }

            public void setDisplayName(String displayName) {
                this.displayName = displayName;
            }
        }
    }
}
