package org.dasein.cloud.aliyun.platform.model;

import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.dasein.cloud.aliyun.platform.model.mqs.Message;
import org.dasein.cloud.aliyun.platform.model.mqs.Queue;
import org.dasein.cloud.aliyun.platform.model.mqs.Queues;
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
	
	public static Queue makeQueue() {
		Random random = new Random();
		Queue queue = new Queue();
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
		return queue;
	}
	
	public static org.dasein.cloud.aliyun.platform.model.mqs.Queues.Queue makeQueuesQueue() {
		Random random = new Random();
		org.dasein.cloud.aliyun.platform.model.mqs.Queues.Queue queue = new org.dasein.cloud.aliyun.platform.model.mqs.Queues.Queue();
		queue.setQueueURL("http://" + random.nextInt() + ".mqs-cn-hangzhou.aliyuncs.com/mq-" + random.nextInt());
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
	public void testSerializeQueue() throws JAXBException, FileNotFoundException {
		marshal(makeQueue(), new FileOutputStream("src/test/resources/platform/queue-output.xml"));
	}
	
	@Test
	public void testSerializeMessage() throws JAXBException, FileNotFoundException {
		marshal(makeMessage(), new FileOutputStream("src/test/resources/platform/message-output.xml"));
	}
	
	@Test
	public void testSerializeQueues() throws JAXBException, FileNotFoundException {
		Queues queues = new Queues();
		List<org.dasein.cloud.aliyun.platform.model.mqs.Queues.Queue> queueList = new ArrayList<org.dasein.cloud.aliyun.platform.model.mqs.Queues.Queue>();
		queueList.add(makeQueuesQueue());
		queueList.add(makeQueuesQueue());
		queues.setQueue(queueList);
		marshal(queues, new FileOutputStream("src/test/resources/platform/queues-output.xml"));
	}
	
	@Test
	public void testUnserializeMessage() throws JAXBException {
		Message message = unmarshal(this.getClass().getResourceAsStream("/platform/message-input.xml"), Message.class);
		assertNotNull(message);
		assertEquals(message.getMessageId(), "msg-1103313753");
		assertEquals(message.getMessageBody(), "XJIidjid");
		assertEquals(message.getPriority().intValue(), 12);
		assertEquals(message.getReceiptHandle(), "some handle 1044954046");
		assertEquals(message.getEnqueueTime().getTime(), 1436859834690l);
		assertEquals(message.getNextVisibleTime().getTime(), 1436859834690l);
		assertNull(message.getDelaySeconds());
		assertNull(message.getDequeueCount());
		assertNull(message.getFirstDequeueTime());
		assertNull(message.getMessageBodyMD5());
	}

	@Test
	public void testUnserializeQueue() throws JAXBException {
		Queue queue = unmarshal(this.getClass().getResourceAsStream("/platform/queue-input.xml"), Queue.class);
		assertNotNull(queue);
	}
	
	@Test
	public void testUnserializeQueues() throws JAXBException {
		Queues queues = unmarshal(this.getClass().getResourceAsStream("/platform/queues-input.xml"), Queues.class);
		assertNotNull(queues);
	}

}
