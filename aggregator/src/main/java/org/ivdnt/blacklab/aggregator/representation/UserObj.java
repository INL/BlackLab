package org.ivdnt.blacklab.aggregator.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import nl.inl.anw.User;

@XmlAccessorType(XmlAccessType.FIELD)
@SuppressWarnings("unused")
@XmlRootElement(name="user")
public class UserObj {
    
    private int id;
    
    private String userName;
    
    private String fullName;

    private String initials;

    private String authority = "";

    private String role = "";

    private boolean active;
    
    // Required for Jersey
    private UserObj() {}

    public UserObj(User u) {
        super();
        this.id = u.getId();
        this.userName = u.getUserName();
        this.fullName = u.getFullName();
        this.initials = u.getInitials();
        this.authority = u.getAuthority().getName();
        this.role = u.getRole().getName();
        this.active = u.isActive();
    }
    
}
