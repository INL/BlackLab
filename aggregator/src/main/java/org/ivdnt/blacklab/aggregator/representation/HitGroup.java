package org.ivdnt.blacklab.aggregator.representation;


import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class HitGroup {
    public String identity;

    public String identityDisplay;

    public long size;

    public List<Property> properties;

    public long numberOfDocs;

    public HitGroup() {}

    @Override
    public String toString() {
        return "HitGroup{" +
                "identity='" + identity + '\'' +
                ", identityDisplay='" + identityDisplay + '\'' +
                ", size=" + size +
                ", properties=" + properties +
                ", numberOfDocs=" + numberOfDocs +
                '}';
    }
}
