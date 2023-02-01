package org.ivdnt.blacklab.proxy.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class VersionInfo {

    public String blacklabBuildTime = "";

    public String blacklabVersion = "";

    public String indexFormat = "3.1";

    public String timeCreated = "";

    public String timeModified = "";

    public String getBlackLabVersion() { return blacklabVersion; }

    public String getBlackLabBuildTime() { return blacklabBuildTime; }

    public void setBlackLabVersion(String v) { blacklabVersion = v; }

    public void setBlackLabBuildTime(String v) { blacklabBuildTime = v; }

    @Override
    public String toString() {
        return "VersionInfo{" +
                "blackLabBuildTime='" + blacklabBuildTime + '\'' +
                ", blackLabVersion='" + blacklabVersion + '\'' +
                ", indexFormat='" + indexFormat + '\'' +
                ", timeCreated='" + timeCreated + '\'' +
                ", timeModified='" + timeModified + '\'' +
                '}';
    }
}
