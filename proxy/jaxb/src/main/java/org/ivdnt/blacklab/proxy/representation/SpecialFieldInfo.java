package org.ivdnt.blacklab.proxy.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class SpecialFieldInfo {

    public String pidField = "";

    public String titleField = "";

    public String authorField = "";

    public String dateField = "";

    // required for Jersey
    public SpecialFieldInfo() {}

    public SpecialFieldInfo(String pidField, String titleField) {
        this.pidField = pidField;
        this.titleField = titleField;
    }

    @Override
    public String toString() {
        return "FieldInfo{" +
                "pidField='" + pidField + '\'' +
                ", titleField='" + titleField + '\'' +
                ", authorField='" + authorField + '\'' +
                ", dateField='" + dateField + '\'' +
                '}';
    }
}
