package org.ivdnt.blacklab.aggregator.representation;

import java.util.Date;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import nl.inl.anw.User;

@XmlAccessorType(XmlAccessType.FIELD)
@SuppressWarnings("unused")
@XmlRootElement(name="exception")
public class LogRecord {
    
    private int level;
    
    private Date time;
    
    private String user;
    
    private String lemma;

    private int pid;
    
    private String actie;
    
    private String info;

    // Required for Jersey
    private LogRecord() {}

    public LogRecord(int level, Date time, User user, String lemma, int pid, String actie, String info) {
        super();
        this.level = level;
        this.time = time;
        this.user = user == null ? "" : user.getUserName();
        this.lemma = lemma;
        this.pid = pid;
        this.actie = actie;
        this.info = info;
    }
    
}
