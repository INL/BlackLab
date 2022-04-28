package org.ivdnt.blacklab.aggregator.representation;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonProperty;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class Server {

    private String blacklabBuildTime;

    private String blacklabVersion;

    @XmlElementWrapper(name="indices")
    @XmlElement(name = "index")
    @JsonProperty("indices")
    private List<IndexSummary> indices;

    private User user;

    // required for Jersey
    @SuppressWarnings("unused")
    private Server() {}

    public Server(String blacklabBuildTime, String blacklabVersion,
            List<IndexSummary> indices, User user) {
        this.blacklabBuildTime = blacklabBuildTime;
        this.blacklabVersion = blacklabVersion;
        this.indices = indices;
        this.user = user;
    }
}
