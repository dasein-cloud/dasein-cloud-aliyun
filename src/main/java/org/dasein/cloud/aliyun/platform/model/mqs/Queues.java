package org.dasein.cloud.aliyun.platform.model.mqs;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="Queues")
@XmlAccessorType(XmlAccessType.FIELD)
public class Queues {

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Queue {
		
		@XmlElement(name="QueueURL")
		private String queueURL;
		
		public String getQueueURL() {
			return queueURL;
		}
		public void setQueueURL(String queueURL) {
			this.queueURL = queueURL;
		}
	}
	
	@XmlElement(name="Queue")
	private List<Queue> queue;
	@XmlElement(name="NextMarker")
	private String nextMarker;

	public List<Queue> getQueue() {
		return queue;
	}
	public void setQueue(List<Queue> queue) {
		this.queue = queue;
	}
	public String getNextMarker() {
		return nextMarker;
	}
	public void setNextMarker(String nextMarker) {
		this.nextMarker = nextMarker;
	}
	
}
