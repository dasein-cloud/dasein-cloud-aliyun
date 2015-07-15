package org.dasein.cloud.aliyun.platform;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.aliyun.platform.model.Queue;
import org.dasein.cloud.aliyun.platform.model.Queues;
import org.dasein.cloud.aliyun.util.requester.AliyunHttpClientBuilderFactory;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestBuilder;
import org.dasein.cloud.aliyun.util.requester.AliyunRequestExecutor;
import org.dasein.cloud.aliyun.util.requester.AliyunResponseHandler;
import org.dasein.cloud.platform.AbstractMQSupport;
import org.dasein.cloud.platform.MQCreateOptions;
import org.dasein.cloud.platform.MQMessageIdentifier;
import org.dasein.cloud.platform.MQMessageReceipt;
import org.dasein.cloud.platform.MQState;
import org.dasein.cloud.platform.MessageQueue;
import org.dasein.cloud.util.requester.streamprocessors.StreamToStringProcessor;
import org.dasein.cloud.util.requester.streamprocessors.XmlStreamToObjectProcessor;
import org.dasein.util.uom.storage.Storage;
import org.dasein.util.uom.time.Second;
import org.dasein.util.uom.time.TimePeriod;

public class AliyunMessageQueue extends AbstractMQSupport<Aliyun> {

	private static final Integer RequestPageSize = 1000;
	
	private static final Logger stdLogger = Aliyun.getStdLogger(AliyunMessageQueue.class);
	
	public AliyunMessageQueue(Aliyun provider) {
		super(provider);
	}
	
	private transient volatile AliyunMessageQueueCapabilities capabilities;
	
	public AliyunMessageQueueCapabilities getCapabilities() {
		if (capabilities == null) {
			capabilities = new AliyunMessageQueueCapabilities(getProvider());
		}
		return capabilities;
	}
	
	/**
	 * @param options MQCreateOptions
	 * @return message queue url (http://$QueueOwnerId.mqs-<Region>.aliyuncs.com /$queueName)
	 * @exception CloudException
	 * @exception InternalException
	 */
	@Override
	public String createMessageQueue(MQCreateOptions options)
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
		
		ResponseHandler<String> responseHandler = new AliyunResponseHandler<String>(
                new StreamToStringProcessor(),
                String.class);
		
		return new AliyunRequestExecutor<String>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                responseHandler).execute();
		
		//TODO fetch Location from the response header
	}

	@Override
	public MessageQueue getMessageQueue(String mqId) throws CloudException,
			InternalException {
		
		HttpUriRequest request = AliyunRequestBuilder.get()
                .provider(getProvider())
                .category(AliyunRequestBuilder.Category.MQS)
                .path("/" + mqId)
                .build();
		
		ResponseHandler<MessageQueue> responseHandler = new AliyunResponseHandler<MessageQueue>(
				new XmlStreamToObjectProcessor<MessageQueue>(),
                MessageQueue.class);
		
		return new AliyunRequestExecutor<MessageQueue>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                responseHandler).execute();
	}

	@Override
	public Iterable<ResourceStatus> listMessageQueueStatus()
			throws CloudException, InternalException {
		List<ResourceStatus> statuses = new ArrayList<ResourceStatus>();
		Iterator<MessageQueue> queueIter = listMessageQueues().iterator();
		while (queueIter.hasNext()) {
			statuses.add(new ResourceStatus(queueIter.next().getName(), MQState.ACTIVE));
		}
		return statuses;
	}

	@Override
	public MQMessageReceipt receiveMessage(String mqId) throws CloudException,
			InternalException {
		// TODO Auto-generated method stub
		return super.receiveMessage(mqId);
	}

	/**
	 * delete message queue by name and also delete all the messages in the queue.
	 * @param mqId message queue id
	 * @param reason unused in aliyun
	 * @exception CloudException
	 * @exception InternalException
	 */
	@Override
	public void removeMessageQueue(String mqId, String reason)
			throws CloudException, InternalException {
		
		HttpUriRequest request = AliyunRequestBuilder.delete()
                .provider(getProvider())
                .category(AliyunRequestBuilder.Category.MQS)
                .path("/" + mqId)
                .build();
		
		ResponseHandler<String> responseHandler = new AliyunResponseHandler<String>(
                new StreamToStringProcessor(),
                String.class);
		
		new AliyunRequestExecutor<String>(getProvider(),
                AliyunHttpClientBuilderFactory.newHttpClientBuilder(),
                request,
                responseHandler).execute();
	}

	@Override
	public String getProviderTermForMessageQueue(Locale locale) {
		return getCapabilities().getProviderTermForMessageQueue(locale);
	}

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}

	@Override
	public Iterable<MessageQueue> listMessageQueues() throws CloudException,
			InternalException {
		
		List<MessageQueue> messageQueues = new ArrayList<MessageQueue>();
		
		String nextMarker = null;
		do {

			AliyunRequestBuilder builder = AliyunRequestBuilder.delete()
					.provider(getProvider())
					.category(AliyunRequestBuilder.Category.MQS);
			if (!getProvider().isEmpty(nextMarker)) {
				builder.header("x-mqs-marker", nextMarker);
			}
			builder.header("x-mqs-ret-number", RequestPageSize.toString());
			
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
					for (Queue queue: queues.getQueue()) {
						messageQueues.add(toMessageQueue(queue));
					}
				}
				nextMarker = queues.getNextMarker();
			}
			
		} while (!getProvider().isEmpty(nextMarker));
		

		return messageQueues;
	}
	
	private MessageQueue toMessageQueue(Queue queue) throws InternalException {
		return MessageQueue.getInstance(getContext().getAccountNumber(), 
				getContext().getRegionId(), 
				"mq-" + queue.getQueueName(), 
				queue.getQueueName(), 
				MQState.ACTIVE, 
				"message queue " + queue.getQueueName(), 
				queue.getQueueURL(), 
				TimePeriod.valueOf(queue.getDelaySeconds(), "second"), 
				TimePeriod.valueOf(queue.getMessageRetentionPeriod(), "second"), 
				TimePeriod.valueOf(queue.getVisibilityTimeout(), "second"), 
				Storage.valueOf(queue.getMaximumMessageSize(), "byte"));
	}

	@Override
	public Iterable<MQMessageReceipt> receiveMessages(String mqId,
			TimePeriod<Second> waitTime, int count,
			TimePeriod<Second> visibilityTimeout) throws CloudException,
			InternalException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MQMessageIdentifier sendMessage(String mqId, String message)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return null;
	}
	
}
