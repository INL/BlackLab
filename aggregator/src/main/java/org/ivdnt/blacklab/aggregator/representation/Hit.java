package org.ivdnt.blacklab.aggregator.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class Hit {

    private String docPid = "";

    private long start = 0;

    private long end = 0;

    //private List

    // required for Jersey
    public Hit() {}
}
