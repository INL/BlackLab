package org.ivdnt.blacklab.aggregator.representation;


import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class HitGroup {
    String identity = "id";

    String identityDisplay = "idDisp";

    long size = 0;

    List<Property> properties = new ArrayList<>();

    long numberOfDocs = 0;

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
