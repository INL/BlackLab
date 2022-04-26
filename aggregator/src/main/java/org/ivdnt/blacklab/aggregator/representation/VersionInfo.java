package org.ivdnt.blacklab.aggregator.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class VersionInfo {

    private String blacklabBuildTime = "";

    private String blacklabVersion = "";

    private String indexFormat = "3.1";

    private String timeCreated = "";

    private String timeModified = "";

    // required for Jersey
    public VersionInfo() {}
}
