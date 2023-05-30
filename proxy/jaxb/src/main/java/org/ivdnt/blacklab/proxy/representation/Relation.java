package org.ivdnt.blacklab.proxy.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class Relation {
    public String type;

    public int sourceStart;

    public int sourceEnd;

    public int targetStart;

    public int targetEnd;

    @Override
    public String toString() {
        return "Relation{" +
                "type='" + type + '\'' +
                ", sourceStart=" + sourceStart +
                ", sourceEnd=" + sourceEnd +
                ", targetStart=" + targetStart +
                ", targetEnd=" + targetEnd +
                '}';
    }
}
