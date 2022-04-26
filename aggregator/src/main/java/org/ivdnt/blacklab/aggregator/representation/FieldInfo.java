package org.ivdnt.blacklab.aggregator.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class FieldInfo {

    private String pidField = "";

    private String titleField = "";

    private String authorField = "";

    private String dateField = "";

    // required for Jersey
    public FieldInfo() {}

    public FieldInfo(String pidField, String titleField) {
        this.pidField = pidField;
        this.titleField = titleField;
    }
}
