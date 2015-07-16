package org.dasein.cloud.aliyun.platform.model.mqs;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="Error")
@XmlAccessorType(XmlAccessType.FIELD)
public class MqsError {
	
	@XmlElement(name="Code")
	private String code;
	@XmlElement(name="Message")
	private String message;
	
	public String getCode() {
		return code;
	}
	public void setCode(String code) {
		this.code = code;
	}
	
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
}
