package org.dasein.cloud.aliyun.platform.model;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.junit.Ignore;
import org.junit.Test;

public class ObjectSerializeTests {

	public static <T> void marshal(T cls, OutputStream os) throws JAXBException {
		JAXBContext context = JAXBContext.newInstance(cls.getClass());
		Marshaller marshaller = context.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
		marshaller.marshal(cls, os);
	}

	@SuppressWarnings("unchecked")
	public static <T> T unmarshal(InputStream is, Class<T> cls)
			throws JAXBException {
		JAXBContext context = JAXBContext.newInstance(cls);
		Unmarshaller unmarshaller = context.createUnmarshaller();
		return (T) unmarshaller.unmarshal(is);
	}
	
	public static Queue makeQueue(boolean urlOnly) {
		Random random = new Random();
		Queue queue = new Queue();
		if (!urlOnly) {
			queue.setActiveMessages(random.nextInt());
			queue.setDelayMessages(random.nextInt());
			queue.setDelaySeconds(random.nextInt());
			queue.setInactiveMessages(random.nextInt());
			queue.setMaximumMessageSize(65535);
			queue.setMessageRetentionPeriod(1024);
			queue.setPollingWaitSeconds(5);
			queue.setQueueName("q-test-" + random.nextInt());
			queue.setVisibilityTimeout(2048);
			queue.setCreateTime(new Date());
			queue.setLastModifyTime(new Date());
		} else {
			queue.setQueueURL("http://ownertest.mqs-cn-hangzhou.aliyuncs.com/q-test" + random.nextInt());
		}
		return queue;
	}
	
	public static Message makeMessage() {
		Random random = new Random();
		Message message = new Message();
		message.setEnqueueTime(new Date());
		message.setMessageBody("XJIidjid");
		message.setMessageBodyMD5("JIDXXJJIDID688WJDL");
		message.setMessageId("msg-" + random.nextInt());
		message.setNextVisibleTime(new Date());
		message.setFirstDequeueTime(new Date());
		message.setPriority(random.nextInt());
		message.setReceiptHandle("some handle " + random.nextInt());
		return message;
	}
	
	@Test
	@Ignore
	public void testSerializeQueue() throws JAXBException {
		marshal(makeQueue(false), System.out);
	}
	
	@Test
	public void testSerializeMessage() throws JAXBException {
		marshal(makeMessage(), System.out);
	}
	
	@Test
	@Ignore
	public void testUnserializeMessage() throws JAXBException {
		Message message = unmarshal(this.getClass().getResourceAsStream("/platform/message-input.xml"), Message.class);
		System.out.println(message.getMessageId() + ", " + message.getPriority() + ", " + message.getEnqueueTime().getTime());
	}

	@Test
	@Ignore
	public void testSerializeQueues() throws JAXBException {
		Queues queues = new Queues();
		List<Queue> queueList = new ArrayList<Queue>();
		queueList.add(makeQueue(true));
		queueList.add(makeQueue(true));
		queues.setQueue(queueList);
		marshal(queues, System.out);
	}

}
