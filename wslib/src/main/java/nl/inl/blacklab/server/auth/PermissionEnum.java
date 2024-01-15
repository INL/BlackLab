package nl.inl.blacklab.server.auth;

import java.lang.reflect.Constructor;
import java.security.Permission;
import java.util.EnumSet;
import java.util.Set;

import org.apache.commons.lang3.NotImplementedException;

public interface PermissionEnum<T extends PermissionEnum<T>> {
    Set<T> implies();

    Set<T> mayGrant();

    boolean implies(T other);

    boolean mayGrant(T other);

    boolean implies(Set<T> others);

    boolean mayGrant(Set<T> other);

    T valueOf(String name);
}
