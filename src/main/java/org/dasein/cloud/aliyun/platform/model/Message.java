package org.dasein.cloud.aliyun.platform.model;

import java.util.Date;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlRootElement(name="Message")
@XmlAccessorType(XmlAccessType.FIELD)
public class Message {

	@XmlElement(name="MessageId")
	private String messageId;
	@XmlElement(name="MessageBody")
	private String messageBody;		
	@XmlElement(name="MessageBodyMD5")
	private String messageBodyMD5;	
	@XmlElement(name="DelaySeconds")
	private Integer delaySeconds;
	@XmlElement(name="Priority")
	private Integer priority;
	@XmlElement(name="ReceiptHandle")
	private String receiptHandle;
	@XmlElement(name="EnqueueTime")
	@XmlJavaTypeAdapter(DatetimeAdapter.class)
	private Date enqueueTime;
	@XmlElement(name="NextVisibleTime")
	@XmlJavaTypeAdapter(DatetimeAdapter.class)
	private Date nextVisibleTime;
	@XmlElement(name="FirstDequeueTime")
	@XmlJavaTypeAdapter(DatetimeAdapter.class)
	private Date firstDequeueTime;
	@XmlElement(name="DequeueCount")
	private Integer dequeueCount;
	
	public String getMessageBody() {
		return messageBody;
	}
	public void setMessageBody(String messageBody) {
		this.messageBody = messageBody;
	}
	public Integer getDelaySeconds() {
		return delaySeconds;
	}
	public void setDelaySeconds(Integer delaySeconds) {
		this.delaySeconds = delaySeconds;
	}
	public Integer getPriority() {
		return priority;
	}
	public void setPriority(Integer priority) {
		this.priority = priority;
	}
	public String getMessageId() {
		return messageId;
	}
	public void setMessageId(String messageId) {
		this.messageId = messageId;
	}
	public String getMessageBodyMD5() {
		return messageBodyMD5;
	}
	public void setMessageBodyMD5(String messageBodyMD5) {
		this.messageBodyMD5 = messageBodyMD5;
	}
	public String getReceiptHandle() {
		return receiptHandle;
	}
	public void setReceiptHandle(String receiptHandle) {
		this.receiptHandle = receiptHandle;
	}
	public Date getEnqueueTime() {
		return enqueueTime;
	}
	public void setEnqueueTime(Date enqueueTime) {
		this.enqueueTime = enqueueTime;
	}
	public Date getNextVisibleTime() {
		return nextVisibleTime;
	}
	public void setNextVisibleTime(Date nextVisibleTime) {
		this.nextVisibleTime = nextVisibleTime;
	}
	public Date getFirstDequeueTime() {
		return firstDequeueTime;
	}
	public void setFirstDequeueTime(Date firstDequeueTime) {
		this.firstDequeueTime = firstDequeueTime;
	}
	public Integer getDequeueCount() {
		return dequeueCount;
	}
	public void setDequeueCount(Integer dequeueCount) {
		this.dequeueCount = dequeueCount;
	}
}
