package nl.inl.blacklab.server.auth;

import java.util.Set;

public interface PermissionEnum<T extends PermissionEnum<T>> {
    Set<T> implies();

    Set<T> mayGrant();

    boolean implies(T other);

    boolean mayGrant(T other);

    boolean implies(Set<T> others);

    boolean mayGrant(Set<T> other);
}
