package org.ivdnt.blacklab.aggregator.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class VersionInfo {

    private String blackLabBuildTime = "";

    private String blackLabVersion = "";

    private String indexFormat = "3.1";

    private String timeCreated = "";

    private String timeModified = "";

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
