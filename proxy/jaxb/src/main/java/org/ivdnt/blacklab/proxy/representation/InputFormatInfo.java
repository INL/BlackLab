package org.ivdnt.blacklab.proxy.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder={"formatName", "configFileType", "configFile" })
public class InputFormatInfo {

    public String formatName;

    public String configFileType;

    public String configFile;

    InputFormatInfo() {}

}
