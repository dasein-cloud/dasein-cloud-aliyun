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
 * Created by Jeffrey Yan on 7/13/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.09.1
 */
@XmlRootElement(name="AccessControlPolicy")
@XmlAccessorType(XmlAccessType.FIELD)
public class AccessControlPolicy {

    @XmlElement(name="Owner")
    private Owner owner;

    @XmlElement(name="AccessControlList")
    private AccessControlList accessControlList;

    public Owner getOwner() {
        return owner;
    }

    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    public AccessControlList getAccessControlList() {
        return accessControlList;
    }

    public void setAccessControlList(AccessControlList accessControlList) {
        this.accessControlList = accessControlList;
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

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class AccessControlList {
        @XmlElement(name="Grant")
        private String grant;

        public String getGrant() {
            return grant;
        }

        public void setGrant(String grant) {
            this.grant = grant;
        }
    }
}
