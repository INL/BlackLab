package org.ivdnt.blacklab.aggregator.representation;


import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class HitGroup {
    String identity = "id";

    String identityDisplay = "idDisp";

    long size = 0;

    List<Property> properties = List.of(new Property());

    long numberOfDocs = 0;

    public HitGroup() {}
}
