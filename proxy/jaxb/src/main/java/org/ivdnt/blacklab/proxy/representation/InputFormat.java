package org.ivdnt.blacklab.proxy.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class InputFormat {
    @XmlAttribute
    String name;

    String displayName;

    String description;

    String helpUrl;

    String configurationBased;

    String isVisible;
}
