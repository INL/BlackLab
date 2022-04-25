package org.ivdnt.blacklab.aggregator.representation;

import java.util.Date;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import nl.inl.anw.User;

@XmlAccessorType(XmlAccessType.FIELD)
@SuppressWarnings("unused")
@XmlRootElement(name="exception")
public class ExceptionObj {
    
    private Date time;
    
    private String editor;
    
    private String lemma;

    private String exceptionText;

    // Required for Jersey
    private ExceptionObj() {}

    public ExceptionObj(Date time, User editor, String lemma, String exceptionText) {
        super();
        this.time = time;
        this.editor = editor == null ? "" : editor.getUserName();
        this.lemma = lemma;
        this.exceptionText = exceptionText;
    }
    
}
