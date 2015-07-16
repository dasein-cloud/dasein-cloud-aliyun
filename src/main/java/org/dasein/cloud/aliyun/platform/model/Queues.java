package org.dasein.cloud.aliyun.platform.model;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="Queues", namespace="http://mqs.aliyuncs.com/doc/v1/")
@XmlAccessorType(XmlAccessType.FIELD)
public class Queues {

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
