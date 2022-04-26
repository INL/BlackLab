package org.ivdnt.blacklab.aggregator.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class User {

    private boolean loggedIn = false;

    private String id = "";

    private boolean canCreateIndex = false;

    // required for Jersey
    @SuppressWarnings("unused")
    private User() {}

    public User(boolean loggedIn, String id, boolean canCreateIndex) {
        this.loggedIn = loggedIn;
        this.id = id;
        this.canCreateIndex = canCreateIndex;
    }
}
