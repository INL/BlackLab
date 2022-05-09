package org.ivdnt.blacklab.aggregator.representation;


import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class HitGroup {
    public String identity;

    public String identityDisplay;

    public long size;

    public List<Property> properties;

    public long numberOfDocs;

    public HitGroup() {}

    public HitGroup(String identity, String identityDisplay, long size, List<Property> properties, long numberOfDocs) {
        this.identity = identity;
        this.identityDisplay = identityDisplay;
        this.size = size;
        this.properties = properties;
        this.numberOfDocs = numberOfDocs;
    }

    @Override
    public String toString() {
        return "HitGroup{" +
                "identity='" + identity + '\'' +
                ", identityDisplay='" + identityDisplay + '\'' +
                ", size=" + size +
                ", properties=" + properties +
                ", numberOfDocs=" + numberOfDocs +
                '}';
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public String getIdentityDisplay() {
        return identityDisplay;
    }

    public void setIdentityDisplay(String identityDisplay) {
        this.identityDisplay = identityDisplay;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public List<Property> getProperties() {
        return properties;
    }

    public void setProperties(List<Property> properties) {
        this.properties = properties;
    }

    public long getNumberOfDocs() {
        return numberOfDocs;
    }

    public void setNumberOfDocs(long numberOfDocs) {
        this.numberOfDocs = numberOfDocs;
    }
}
