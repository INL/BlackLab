package org.ivdnt.blacklab.aggregator.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class ErrorResponse {

    @XmlAccessorType(XmlAccessType.FIELD)
    static class Desc {
        String code;
        String message;
    }

    Desc error;

    public String getMessage() {
        return error.code + "||" + error.message;
    }

}
