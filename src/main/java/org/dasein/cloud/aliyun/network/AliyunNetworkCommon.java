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
package org.dasein.cloud.aliyun.network;

/**
 * Created by Jane Wang on 5/7/2015.
 *
 * @author Jane Wang
 * @since 2015.05.01
 *
 */
public class AliyunNetworkCommon {

   public static final int DefaultPageSize = 10; //from 10 to 50

   public static enum AliyunFirewallPermission {ACCEPT, DROP};

   public static enum AliyunFirewallNicType {INTERNET, INTRANET};

   public static final String IpProtocolAll = "ALL";

   public static enum AliyunEipStatus {ASSOCIATING, UNASSOCIATING, INUSE, AVAILABLE};

   public static boolean isEmpty (Object obj) {
      if (obj instanceof String) {
         if (obj == null || ((String) obj).length() == 0) {
            return true;
         } else {
            return false;
         }
      }
      return false;
   }
}
