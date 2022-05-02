package org.ivdnt.blacklab.aggregator;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONArray;
import org.json.JSONObject;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class ResponseMessage {
	String message;
	Object content; //Either a string, JSONObject or JSONArray

	public ResponseMessage(Exception ex) {
		message = ex.getMessage();
		content = ExceptionUtils.getStackTrace(ex);
	}

    public ResponseMessage(String message, Exception ex) {
        message = message + ": " + ex.getMessage();
        content = ExceptionUtils.getStackTrace(ex);
    }

    public ResponseMessage(String message) {
        this.message = message;
        this.content = null;
    }

	public ResponseMessage(String message, String content) {
		this.message = message;
		this.content = content;
	}

	public ResponseMessage(String message, JSONObject content) {
		this.message = message;
		this.content = content;
	}

	public ResponseMessage(String message, JSONArray content) {
		this.message = message;
		this.content = content;
	}

	@SuppressWarnings("unused")
	private ResponseMessage() {
		//required for JAXB
	}
}
