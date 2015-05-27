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

import org.dasein.cloud.InternalException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.TimeZone;

/**
 * Created by Jane Wang on 5/7/2015.
 *
 * @author Jane Wang
 * @since 2015.05.01
 *
 */
public class AliyunNetworkCommon {

   public static final int DefaultPageSize = 10; //from 10 to 50

   //Firewall
   public static enum AliyunFirewallPermission {ACCEPT, DROP};
   public static enum AliyunFirewallNicType {INTERNET, INTRANET};
   public static final String IpProtocolAll = "ALL";

   //Network
   public static enum AliyunEipStatus {ASSOCIATING, UNASSOCIATING, INUSE, AVAILABLE};
   public static enum AliyunRouteEntryNextHopType {INSTANCE, TUNNEL};
   public static enum AliyunRouteType {SYSTEM ,CUSTOM};
   public static enum AliyunLbNetworkType {INTERNET, INTRANET};

   //Load Balancer
   public static enum AliyunLbScheduleAlgorithm {WRR, WLC};
   public static enum AliyunLbSwitcher {ON, OFF};
   public static enum AliyunLbPersistenceType {INSERT, SERVER};
   public static enum AliyunLbState {INACTIVE, ACTIVE, LOCKED};
   public static enum AliyunLbEndpointState {NORMAL, ABNORMAL, UNAVAILABLE};
   public static enum AliyunLbListenerState {STARTING, RUNNING, CONFIGURING, STOPPING, STOPPED};
   public static final int DefaultWeight = 100;
   public static final int DefaultPersistenceTimeout = 5 * 60;
   public static final int DefaultBandwidth = -1;

   //TODO check in Aliyun document says "YYYY-MM-DD'T'hh:mm'Z'", however example shows "YYYY-MM-DD'T'hh:mm:ss'Z'"
   private  static final String TimeFormat = "YYYY-MM-DD'T'hh:mm:ss'Z'";

   public static boolean isEmpty (Object obj) {
      if (obj instanceof String) {
         if (obj == null || ((String) obj).trim().length() == 0) {
            return true;
         } else {
            return false;
         }
      } else if (obj instanceof Collection) {
         if (obj == null || ((Collection) obj).size() <= 0) {
            return true;
         } else {
            return false;
         }
      }
      return false;
   }

   public static Date parseFromUTCString(String source) throws InternalException {
      final SimpleDateFormat format = new SimpleDateFormat(TimeFormat);
      TimeZone timeZone = TimeZone.getTimeZone("UTC");
      format.setTimeZone(timeZone);
      try {
         return format.parse(source);
      } catch (ParseException e) {
         throw new InternalException(e);
      }
   }

   public static String toUpperCaseFirstLetter(String s)
   {
      if(Character.isUpperCase(s.charAt(0)))
         return s;
      else
         return (new StringBuilder()).append(Character.toUpperCase(s.charAt(0))).append(s.substring(1)).toString();
   }

}
