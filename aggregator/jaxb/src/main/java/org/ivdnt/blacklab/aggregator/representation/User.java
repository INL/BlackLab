package org.ivdnt.blacklab.aggregator.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import com.fasterxml.jackson.annotation.JsonInclude;

@XmlAccessorType(XmlAccessType.FIELD)
public class User {

    public boolean loggedIn = false;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String id;

    public boolean canCreateIndex = false;

    // required for Jersey
    @SuppressWarnings("unused")
    public User() {}

    public User(boolean loggedIn, String id, boolean canCreateIndex) {
        this.loggedIn = loggedIn;
        this.id = id;
        this.canCreateIndex = canCreateIndex;
    }

    @Override
    public String toString() {
        return "User{" +
                "loggedIn=" + loggedIn +
                ", id='" + id + '\'' +
                ", canCreateIndex=" + canCreateIndex +
                '}';
    }
}
