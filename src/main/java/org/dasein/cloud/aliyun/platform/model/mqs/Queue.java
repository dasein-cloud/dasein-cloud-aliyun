package org.dasein.cloud.aliyun.platform.model.mqs;

import java.util.Date;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlRootElement(name="Queue")
@XmlAccessorType(XmlAccessType.FIELD)
public class Queue {
	
	@XmlElement(name="QueueName")
	private String queueName;
	@XmlElement(name="CreateTime")
	@XmlJavaTypeAdapter(DatetimeAdapter.class)
	private Date createTime;
	@XmlJavaTypeAdapter(DatetimeAdapter.class)
	@XmlElement(name="LastModifyTime")
	private Date lastModifyTime;
	@XmlElement(name="InactiveMessages")
	private Integer inactiveMessages;
	@XmlElement(name="ActiveMessages")
	private Integer activeMessages;
	@XmlElement(name="DelayMessages")
	private Integer delayMessages;
	@XmlElement(name="DelaySeconds")
	private Integer delaySeconds;
	@XmlElement(name="MaximumMessageSize")
	private Integer maximumMessageSize;
	@XmlElement(name="MessageRetentionPeriod")
	private Integer messageRetentionPeriod;
	@XmlElement(name="VisibilityTimeout")
	private Integer visibilityTimeout;
	@XmlElement(name="PollingWaitSeconds")
	private Integer pollingWaitSeconds;
	@XmlElement(name="QueueURL")
	private String queueURL;
	
	public Integer getDelaySeconds() {
		return delaySeconds;
	}
	public void setDelaySeconds(Integer delaySeconds) {
		this.delaySeconds = delaySeconds;
	}
	public Integer getMaximumMessageSize() {
		return maximumMessageSize;
	}
	public void setMaximumMessageSize(Integer maximumMessageSize) {
		this.maximumMessageSize = maximumMessageSize;
	}
	public Integer getMessageRetentionPeriod() {
		return messageRetentionPeriod;
	}
	public void setMessageRetentionPeriod(Integer messageRetentionPeriod) {
		this.messageRetentionPeriod = messageRetentionPeriod;
	}
	public Integer getVisibilityTimeout() {
		return visibilityTimeout;
	}
	public void setVisibilityTimeout(Integer visibilityTimeout) {
		this.visibilityTimeout = visibilityTimeout;
	}
	public Integer getPollingWaitSeconds() {
		return pollingWaitSeconds;
	}
	public void setPollingWaitSeconds(Integer pollingWaitSeconds) {
		this.pollingWaitSeconds = pollingWaitSeconds;
	}
	public String getQueueName() {
		return queueName;
	}
	public void setQueueName(String queueName) {
		this.queueName = queueName;
	}
	public Date getCreateTime() {
		return createTime;
	}
	public void setCreateTime(Date createTime) {
		this.createTime = createTime;
	}
	public Date getLastModifyTime() {
		return lastModifyTime;
	}
	public void setLastModifyTime(Date lastModifyTime) {
		this.lastModifyTime = lastModifyTime;
	}
	public Integer getInactiveMessages() {
		return inactiveMessages;
	}
	public void setInactiveMessages(Integer inactiveMessages) {
		this.inactiveMessages = inactiveMessages;
	}
	public Integer getActiveMessages() {
		return activeMessages;
	}
	public void setActiveMessages(Integer activeMessages) {
		this.activeMessages = activeMessages;
	}
	public Integer getDelayMessages() {
		return delayMessages;
	}
	public void setDelayMessages(Integer delayMessages) {
		this.delayMessages = delayMessages;
	}
	public String getQueueURL() {
		return queueURL;
	}
	public void setQueueURL(String queueURL) {
		this.queueURL = queueURL;
	}
}
