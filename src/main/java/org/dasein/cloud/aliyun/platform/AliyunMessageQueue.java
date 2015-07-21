package org.dasein.cloud.aliyun.platform;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.aliyun.platform.model.mqs.Message;
import org.dasein.cloud.aliyun.platform.model.mqs.MqsError;
import org.dasein.cloud.aliyun.platform.model.mqs.Queue;
import org.dasein.cloud.aliyun.platform.model.mqs.Queues;
import org.dasein.cloud.aliyun.util.requester.AliyunHttpClientBuilderFactory;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestBuilder;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestExecutor;
import org.dasein.cloud.aliyun.util.requester.AliyunResponseException;
import org.dasein.cloud.aliyun.util.requester.AliyunResponseHandler;
import org.dasein.cloud.aliyun.util.requester.AliyunResponseHandlerWithMapper;
import org.dasein.cloud.aliyun.util.requester.AliyunValidateEmptyResponseHandler;
import org.dasein.cloud.platform.AbstractMQSupport;
import org.dasein.cloud.platform.MQCreateOptions;
import org.dasein.cloud.platform.MQMessageIdentifier;
import org.dasein.cloud.platform.MQMessageReceipt;
import org.dasein.cloud.platform.MQState;
import org.dasein.cloud.platform.MQSupport;
import org.dasein.cloud.platform.MessageQueue;
import org.dasein.cloud.util.requester.DriverToCoreMapper;
import org.dasein.cloud.util.requester.streamprocessors.XmlStreamToObjectProcessor;
import org.dasein.util.uom.storage.Storage;
import org.dasein.util.uom.time.Second;
import org.dasein.util.uom.time.TimePeriod;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

public class AliyunMessageQueue extends AbstractMQSupport<Aliyun> implements MQSupport {
	
	private static final Integer RequestPageSize = 1000;
	
	private static final Logger stdLogger = Aliyun.getStdLogger(AliyunMessageQueue.class);
	
	public AliyunMessageQueue(Aliyun provider) {
		super(provider);
	}

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}

	@Override
	public String getProviderTermForMessageQueue(Locale locale) {
		return "queue";
	}

	@Override
	public String createMessageQueue(final MQCreateOptions options)
			throws CloudException, InternalException {
		Queue queue = new Queue();
		if (!getProvider().isEmpty(options.getMetaData().get("DelaySeconds"))) {
			queue.setDelaySeconds(Integer.valueOf(options.getMetaData().get("DelaySeconds")));
		}
		if (!getProvider().isEmpty(options.getMetaData().get("MaximumMessageSize"))) {
			queue.setMaximumMessageSize(Integer.valueOf(options.getMetaData().get("MaximumMessageSize")));
		}
		if (!getProvider().isEmpty(options.getMetaData().get("MessageRetentionPeriod"))) {
			queue.setMessageRetentionPeriod(Integer.valueOf(options.getMetaData().get("MessageRetentionPeriod")));
		}
		if (!getProvider().isEmpty(options.getMetaData().get("PollingWaitSeconds"))) {
			queue.setPollingWaitSeconds(Integer.valueOf(options.getMetaData().get("PollingWaitSeconds")));
		}
		if (options.getVisibilityTimeout() != null) {
			queue.setVisibilityTimeout(options.getVisibilityTimeout().intValue());
		}
		
		HttpUriRequest request = AliyunRequestBuilder.put()
                .provider(getProvider())
                .category(AliyunRequestBuilder.Category.MQS)
                .path("/" + options.getName())      
                .entity(queue, new XmlStreamToObjectProcessor<Queue>())
                .build();
		
		final String regionId = getContext().getRegionId();
		final String accountNumber = getContext().getAccountNumber();
		ResponseHandler<String> responseHandler = new ResponseHandler<String>(){
            @Override
            public String handleResponse(HttpResponse response) throws IOException {
                int httpCode = response.getStatusLine().getStatusCode();
                if (httpCode == HttpStatus.SC_CREATED || httpCode == HttpStatus.SC_NO_CONTENT ) {
                    return parseQueueNameFromLocation(response.getFirstHeader("Location").getValue());
                } else if (httpCode == HttpStatus.SC_CONFLICT) {
                	stdLogger.error("Create queue failed, got " + httpCode);
                	throw new AliyunResponseException(httpCode, null, 
                			"Create queue failed, got " + httpCode,
                			response.getFirstHeader("x-mns-request-id").getValue(),
                			generateHost(accountNumber, regionId, options.getName()));
                } else {
                	stdLogger.error("Other exceptions found, got " + httpCode);
                	throw new AliyunResponseException(httpCode, null, 
                			"Other exceptions found, got " + httpCode,
                			response.getFirstHeader("x-mns-request-id").getValue(),
                			generateHost(accountNumber, regionId, options.getName()));
                }
            }
        };
		
		return new AliyunRequestExecutor<String>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                responseHandler).execute();
	}

	@Override
	public MessageQueue getMessageQueue(String mqId) throws CloudException,
			InternalException {
		
		HttpUriRequest request = AliyunRequestBuilder.get()
                .provider(getProvider())
                .category(AliyunRequestBuilder.Category.MQS)
                .path("/" + mqId)
                .build();
		
		final String accountNumber = getContext().getAccountNumber();
		final String regionId = getContext().getRegionId();
		
		ResponseHandler<MessageQueue> responseHandler = new AliyunResponseHandlerWithMapper<Queue, MessageQueue>(
                new XmlStreamToObjectProcessor<Queue>(),
                new DriverToCoreMapper<Queue, MessageQueue>() {
                    @Override
                    public MessageQueue mapFrom(Queue queue) {
                    	TimePeriod delaySeconds = null;
                		if (queue.getDelaySeconds() != null) {
                			delaySeconds = TimePeriod.valueOf(queue.getDelaySeconds(), "second");
                		}
                		TimePeriod messageRetentionPeriod = null;
                		if (queue.getMessageRetentionPeriod() != null) {
                			messageRetentionPeriod = TimePeriod.valueOf(queue.getMessageRetentionPeriod(), "second");
                		}
                		TimePeriod visibilityTimeout = null;
                		if (queue.getVisibilityTimeout() != null) {
                			visibilityTimeout = TimePeriod.valueOf(queue.getVisibilityTimeout(), "second");
                		}
                		Storage maximumMessageSize = null;
                		if (queue.getMaximumMessageSize() != null) {
                			maximumMessageSize = Storage.valueOf(queue.getMaximumMessageSize(), "byte");
                		}
                		
                		return MessageQueue.getInstance(accountNumber, 
                				regionId,
                				queue.getQueueName(), 
                				queue.getQueueName(), 
                				MQState.ACTIVE, 
                				"message queue " + queue.getQueueName(),
                				"http://" + generateHost(accountNumber, regionId, queue.getQueueName()),
                				delaySeconds, 
                				messageRetentionPeriod, 
                				visibilityTimeout, 
                				maximumMessageSize);
                    }
                },
                Queue.class);
		
		return new AliyunRequestExecutor<MessageQueue>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                responseHandler).execute();
	}

	@Override
	public void removeMessageQueue(String mqId, String reason)
			throws CloudException, InternalException {
		
		HttpUriRequest request = AliyunRequestBuilder.delete()
                .provider(getProvider())
                .category(AliyunRequestBuilder.Category.MQS)
                .path("/" + mqId)
                .build();
		
		new AliyunRequestExecutor<String>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                new AliyunValidateEmptyResponseHandler()).execute();
	}

	@Override
	public Iterable<MessageQueue> listMessageQueues() throws CloudException,
			InternalException {
		
		List<MessageQueue> messageQueues = new ArrayList<MessageQueue>();
		
		String nextMarker = null;
		do {

			AliyunRequestBuilder builder = AliyunRequestBuilder.get()
					.provider(getProvider())
					.category(AliyunRequestBuilder.Category.MQS)
					.path("/")
					.header("x-mqs-ret-number", RequestPageSize.toString());
			if (!getProvider().isEmpty(nextMarker)) {
				builder.header("x-mqs-marker", nextMarker);
			}
			
			HttpUriRequest request = builder.build();
		
			ResponseHandler<Queues> responseHandler = new AliyunResponseHandler<Queues>(
	                new XmlStreamToObjectProcessor<Queues>(),
	                Queues.class);
		
			Queues queues = new AliyunRequestExecutor<Queues>(getProvider(),
					AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
					request,
					responseHandler).execute();
		
			if (queues != null) {
				if (queues.getQueue() != null) {
					for (org.dasein.cloud.aliyun.platform.model.mqs.Queues.Queue queueWithUrl: queues.getQueue()) {
						messageQueues.add(getMessageQueue(parseQueueNameFromLocation(queueWithUrl.getQueueURL())));
					}
				}
				nextMarker = queues.getNextMarker();
			}
			
		} while (!getProvider().isEmpty(nextMarker));
		
		return messageQueues;
	}
	
	@Override
	public Iterable<MQMessageReceipt> receiveMessages(String mqId,
			TimePeriod<Second> waitTime, int count,
			TimePeriod<Second> visibilityTimeout) throws CloudException,
			InternalException {
		
		List<MQMessageReceipt> receipts = new ArrayList<MQMessageReceipt>();
		
		for (int i = 0; i < count; i++) {
			
			AliyunRequestBuilder builder = AliyunRequestBuilder.get()
	                .provider(getProvider())
	                .category(AliyunRequestBuilder.Category.MQS)
	                .path("/" + mqId + "/messages");
			if (waitTime != null) {
				builder = builder.parameter("waitseconds", waitTime.intValue());
			}
			if (visibilityTimeout != null) {
				changeQueueVisibilityTimeout(mqId, visibilityTimeout.intValue());
			}
			HttpUriRequest request = builder.build();
			
			//also handle message not exist exception
			ResponseHandler<Message> responseHandler = new AliyunResponseHandler<Message>(
					new XmlStreamToObjectProcessor<Message>(),
					Message.class) {
				public Message handleResponse(HttpResponse httpResponse) throws ClientProtocolException, IOException {
			        if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
			        	MqsError error = new XmlStreamToObjectProcessor<MqsError>().read(httpResponse.getEntity().getContent(), MqsError.class);
			        	if ("MessageNotExist".equals(error.getCode())) {	//no more messages
			        		return null;
			        	}
			        }
			        return super.handleResponse(httpResponse);
				}
			};
		
			Message recievedMessage = new AliyunRequestExecutor<Message>(getProvider(),
	                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
	                request,
	                responseHandler).execute();
			
			if (recievedMessage == null) {
				break;
			}
			
			deleteMessage(mqId, recievedMessage.getReceiptHandle());
			
			receipts.add(MQMessageReceipt.getInstance(
					new MQMessageIdentifier(recievedMessage.getMessageId(), recievedMessage.getMessageBodyMD5()),
					recievedMessage.getMessageBody(),
					recievedMessage.getEnqueueTime().getTime()));
		}

		return receipts;
	}

	@Override
	public MQMessageIdentifier sendMessage(String mqId, String message)
			throws CloudException, InternalException {
		Message sendMessage = new Message();
		sendMessage.setMessageBody(message);
		
		HttpUriRequest request = AliyunRequestBuilder.post()
                .provider(getProvider())
                .category(AliyunRequestBuilder.Category.MQS)
                .path("/" + mqId + "/messages")
                .entity(sendMessage, new XmlStreamToObjectProcessor<Message>())
                .build();
		
		ResponseHandler<Message> responseHandler = new AliyunResponseHandler<Message>(
				new XmlStreamToObjectProcessor<Message>(),
				Message.class);
		
		Message recievedMessage = new AliyunRequestExecutor<Message>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                responseHandler).execute();
		
		return new MQMessageIdentifier(recievedMessage.getMessageId(), recievedMessage.getMessageBodyMD5());
		
	}
	
	private void deleteMessage(String queueId, String receiptHandle) throws InternalException, CloudException {
		
		HttpUriRequest request = AliyunRequestBuilder.delete()
                .provider(getProvider())
                .category(AliyunRequestBuilder.Category.MQS)
                .path("/" + queueId + "/messages")
                .parameter("ReceiptHandle", receiptHandle)
                .build();
		
		new AliyunRequestExecutor<String>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                new AliyunValidateEmptyResponseHandler()).execute();
	}
	
	private String generateHost(String accountNumber, String regionId, String queueName) {
		return accountNumber + ".mns." + regionId + ".aliyuncs.com/" + queueName;
	}
	
	private String parseQueueNameFromLocation(String location) {
		return location.substring(location.lastIndexOf("/") + 1, location.length());
	}
	
	private void changeQueueVisibilityTimeout(String queueName, Integer visibilityTimeout) 
			throws InternalException, CloudException {
		
		Queue queue = new Queue();
		queue.setVisibilityTimeout(visibilityTimeout);
		
		HttpUriRequest request = AliyunRequestBuilder.get()
                .provider(getProvider())
                .category(AliyunRequestBuilder.Category.MQS)
                .path("/" + queueName)
                .parameter("metaoverride", "true")
                .entity(queue, new XmlStreamToObjectProcessor<Queue>())
                .build();
		
		new AliyunRequestExecutor<String>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                new AliyunValidateEmptyResponseHandler()).execute();
	}
	
}
