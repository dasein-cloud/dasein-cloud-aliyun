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

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.aliyun.Aliyun;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Created by Jeffrey Yan on 7/1/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.05.1
 */
public abstract class AliyunRequestBuilderStrategy {
    static private final Logger logger = Aliyun
            .getStdLogger(AliyunRequestBuilderStrategy.class);

    static public final String ENCODING = "UTF-8";
    static public final String SIGNATURE_ALGORITHM = "HmacSHA1";

    protected Aliyun aliyun;

    public AliyunRequestBuilderStrategy(Aliyun aliyun) {
        this.aliyun = aliyun;
    }

    public abstract void applyUri(AliyunRequestBuilder aliyunRequestBuilder) throws InternalException;
    public abstract void applyFrameworkParameters(AliyunRequestBuilder aliyunRequestBuilder);
    public abstract void sign(AliyunRequestBuilder aliyunRequestBuilder) throws InternalException;

    protected String urlEncode(String value) throws InternalException {
        if (value == null) {
            return null;
        }
        try {
            return URLEncoder.encode(value, ENCODING).replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
        } catch (UnsupportedEncodingException unsupportedEncodingException) {
            logger.error(
                    "AliyunRequestBuilderStrategy.urlEncode() failed due to encoding not supported: " + unsupportedEncodingException
                            .getMessage());
            throw new InternalException(unsupportedEncodingException);
        }
    }

    protected String computeSignature(byte[] accessKeySecret, String stringToSign) throws InternalException {
        String signature;
        try {
            Mac mac = Mac.getInstance(SIGNATURE_ALGORITHM);
            mac.init(new SecretKeySpec(accessKeySecret, SIGNATURE_ALGORITHM));
            byte[] signedData = mac.doFinal(stringToSign.getBytes(ENCODING));
            signature = new String(Base64.encodeBase64(signedData));
        } catch (NoSuchAlgorithmException noSuchAlgorithmException) {
            logger.error("AliyunRequestBuilderStrategy.sign() failed due to algorithm not supported: " + noSuchAlgorithmException
                    .getMessage());
            throw new InternalException(noSuchAlgorithmException);
        } catch (InvalidKeyException invalidKeyException) {
            logger.error("AliyunRequestBuilderStrategy.sign() failed due to key invalid: " + invalidKeyException.getMessage());
            throw new InternalException(invalidKeyException);
        } catch (UnsupportedEncodingException unsupportedEncodingException) {
            logger.error("AliyunMethod.sign() failed due to encoding not supported: " + unsupportedEncodingException
                    .getMessage());
            throw new InternalException(unsupportedEncodingException);
        }
        return signature;
    }
}
