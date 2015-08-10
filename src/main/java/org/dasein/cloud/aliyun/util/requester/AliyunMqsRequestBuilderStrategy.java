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

import org.apache.http.NameValuePair;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.util.requester.streamprocessors.XmlStreamToObjectProcessor;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by Jeffrey Yan on 7/16/2015.
 *
 * @author Jeffrey Yan
 * @since 2015.09.1
 */
public class AliyunMqsRequestBuilderStrategy extends AliyunOssRequestBuilderStrategy {

    static private final Logger logger = Aliyun.getStdLogger(AliyunMqsRequestBuilderStrategy.class);

    private static final String MQS_HEADER_PREFIX = "x-mqs-";

    public AliyunMqsRequestBuilderStrategy(Aliyun aliyun) {
        super(aliyun);
    }

    private boolean isQueryAccountRequest(AliyunRequestBuilder aliyunRequestBuilder) {
        for (NameValuePair nameValuePair : aliyunRequestBuilder.requestBuilder.getParameters()) {
            if ("account".equals(nameValuePair.getName())) {
                return true;
            }
        }
        return false;
    }

    public void applyUri(AliyunRequestBuilder aliyunRequestBuilder) throws InternalException {
        URIBuilder uriBuilder = new URIBuilder();
        String host = aliyunRequestBuilder.category.getHost(this.aliyun);
        String regionId = aliyun.getContext().getRegionId();
        if (regionId == null) {
            throw new InternalException("No region was set for this request");
        }
        if(isQueryAccountRequest(aliyunRequestBuilder)) {
            int firstDot = host.indexOf('.');
            host = host.substring(0, firstDot) + "-" + regionId + host.substring(firstDot);
        } else {
            String accountId = AccountIdHolder.get(aliyun);
            int firstDot = host.indexOf('.');
            host = accountId + "." + host.substring(0, firstDot) + "-" + regionId +
                    host.substring(firstDot);
        }

        uriBuilder.setScheme("http").setHost(host).setPath(aliyunRequestBuilder.path);//support HTTP only
        try {
            aliyunRequestBuilder.requestBuilder.setUri(uriBuilder.build().toString());
        } catch (URISyntaxException uriSyntaxException) {
            logger.error("RequestBuilderFactory.build() failed due to URI invalid: " + uriSyntaxException.getMessage());
            throw new InternalException(uriSyntaxException);
        }
    }

    @Override
    public void applyFrameworkParameters(AliyunRequestBuilder aliyunRequestBuilder) {
        aliyunRequestBuilder.requestBuilder.addHeader(DATE, aliyun.formatRfc822Date(new Date()));
        aliyunRequestBuilder.requestBuilder.addHeader(CONTENT_MD5, "");
        //MQS response MD5 is not correct, is there bug in MQS?
        /*
        HttpEntity httpEntity = aliyunRequestBuilder.requestBuilder.getEntity();
        if(httpEntity != null) {
            if (httpEntity.isRepeatable()) {
                try {
                    byte[] md5 = computeMD5Hash(httpEntity.getContent());
                    String md5Base64 = new String(Base64.encodeBase64(md5));
                    aliyunRequestBuilder.requestBuilder.addHeader(CONTENT_MD5, md5Base64);
                } catch (Exception exception) {
                    aliyunRequestBuilder.requestBuilder.addHeader(CONTENT_MD5, "");
                }
            } else {
                aliyunRequestBuilder.requestBuilder.addHeader(CONTENT_MD5, "");
            }
        }
        */
        aliyunRequestBuilder.header("x-mqs-version", "2014-07-08");
    }

    @Override
    public void sign(AliyunRequestBuilder aliyunRequestBuilder) throws InternalException {
        String stringToSign = buildCanonicalizedString(aliyunRequestBuilder);

        byte[][] accessKey = (byte[][]) aliyun.getContext().getConfigurationValue(Aliyun.DSN_ACCESS_KEY);
        byte[] accessKeySecret = accessKey[1];
        String signature = computeSignature(accessKeySecret, stringToSign);
        aliyunRequestBuilder.requestBuilder.addHeader(AUTHORIZATION,
                "MQS " + new String(accessKey[0]) + ":" + signature);
    }

    @Override
    protected boolean isCanonicalizedHeader(String name) {
        return name.startsWith(MQS_HEADER_PREFIX);
    }

    @Override
    protected boolean isCanonicalizedResourceSignedParamter(String name) {
        return true;
    }

    @Override
    protected String buildCanonicalizedResource(AliyunRequestBuilder aliyunRequestBuilder) throws InternalException {
        StringBuilder canonicalStringBuilder = new StringBuilder();
        canonicalStringBuilder.append(aliyunRequestBuilder.path);
        canonicalStringBuilder.append(buildCanonicalizedParameters(aliyunRequestBuilder));
        return canonicalStringBuilder.toString();
    }

    private static class AccountIdHolder {
        private static final long CACHE_CLEAR_FREQUENCY = 1000;
        private static final long CACHE_ALIVE_MILLIS = 60 * 60 * 1000; //1 hour

        private static final ConcurrentMap<String, Future<Account>> cache = new ConcurrentHashMap<String, Future<Account>>();
        private static AtomicLong count = new AtomicLong();

        public static String get(final Aliyun aliyun) throws InternalException {
            byte[][] accessKey = (byte[][]) aliyun.getContext().getConfigurationValue(Aliyun.DSN_ACCESS_KEY);
            String accessKeyId = new String(accessKey[0]);
            Future<Account> future = cache.get(accessKeyId);
            if (future == null) {
                Callable<Account> callable = new Callable<Account>() {
                    @Override
                    public Account call() throws Exception {
                        HttpUriRequest request = AliyunRequestBuilder.get()
                                .provider(aliyun)
                                .category(AliyunRequestBuilder.Category.MQS)
                                .parameter("account", null)
                                .build();

                        ResponseHandler<Account> responseHandler = new AliyunResponseHandler<Account>(
                                new XmlStreamToObjectProcessor<Account>(),
                                Account.class);

                        try {
                            Account account = new AliyunRequestExecutor<Account>(aliyun,
                                    AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                                    request,
                                    responseHandler).execute();
                            return account;
                        } catch (CloudException cloudException) {
                            throw new RuntimeException(cloudException);
                        }
                    }
                };
                FutureTask<Account> futureTask = new FutureTask<Account>(callable);
                future = cache.putIfAbsent(accessKeyId, futureTask);
                if(future == null) {
                    future = futureTask;
                    futureTask.run();
                }
            }
            try {
                Account account = future.get();
                boolean success;
                do {
                    Future<Account> currentFuture = cache.get(accessKeyId);
                    Account currentAccount = currentFuture.get();
                    success = cache.replace(accessKeyId, currentFuture, currentAccount.newFuture());
                } while (!success);

                if (count.incrementAndGet() >= CACHE_CLEAR_FREQUENCY) {
                    clear();
                }
                return account.getAccountId();
            } catch (InterruptedException interruptedException) {
                cache.remove(accessKeyId);
                Thread.currentThread().interrupt();
                throw new InternalException(interruptedException);
            } catch (ExecutionException executionException) {
                cache.remove(accessKeyId);
                throw new InternalException(executionException);
            }
        }

        private static void clear() throws ExecutionException, InterruptedException {
            long current;
            while ((current = count.get()) >= CACHE_CLEAR_FREQUENCY) {
                if (count.compareAndSet(current, current - CACHE_CLEAR_FREQUENCY)) {
                    long now = System.currentTimeMillis();

                    for(Map.Entry<String, Future<Account>> entry : cache.entrySet()) {
                        String key = entry.getKey();
                        Future<Account> value = entry.getValue();
                        if(value.isCancelled()) {
                            cache.remove(key);
                        }
                        if (value.isDone() && now - value.get().getLastRetrieveTime() >= CACHE_ALIVE_MILLIS) {
                            cache.remove(key, value);//ignore if failed, as has been updated by another thread
                        }
                    }
                }
            }
        }


        @XmlRootElement(name = "Account", namespace = "http://mqs.aliyuncs.com/doc/v1")
        @XmlAccessorType(XmlAccessType.FIELD)
        public static class Account {
            @XmlElement(name="AccountId", namespace = "http://mqs.aliyuncs.com/doc/v1")
            private String accountId;

            private long lastRetrieveTime = System.currentTimeMillis();

            public Account() {
            }

            public Account(String accountId) {
                this.accountId = accountId;
            }

            public String getAccountId() {
                return accountId;
            }

            public long getLastRetrieveTime() {
                return lastRetrieveTime;
            }

            public Future<Account> newFuture() {
                return new AccountFuture(accountId);
            }

            private class AccountFuture implements Future<Account> {
                private Account account;

                protected AccountFuture(String accountId) {
                    account = new Account(accountId);
                }

                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    return false;
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }

                @Override
                public boolean isDone() {
                    return true;
                }

                @Override
                public Account get() throws InterruptedException, ExecutionException {
                    return account;
                }

                @Override
                public Account get(long timeout, TimeUnit unit)
                        throws InterruptedException, ExecutionException, TimeoutException {
                    return account;
                }
            }
        }
    }
}
