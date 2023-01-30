package org.ivdnt.blacklab.proxy.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class VersionInfo {

    public String blackLabBuildTime = "";

    public String blackLabVersion = "";

    public String indexFormat = "3.1";

    public String timeCreated = "";

    public String timeModified = "";

    @Override
    public String toString() {
        return "VersionInfo{" +
                "blackLabBuildTime='" + blackLabBuildTime + '\'' +
                ", blackLabVersion='" + blackLabVersion + '\'' +
                ", indexFormat='" + indexFormat + '\'' +
                ", timeCreated='" + timeCreated + '\'' +
                ", timeModified='" + timeModified + '\'' +
                '}';
    }
}
