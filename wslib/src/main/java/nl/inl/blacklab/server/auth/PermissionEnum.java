package nl.inl.blacklab.server.auth;

import java.util.EnumSet;

import org.apache.commons.lang3.NotImplementedException;

public interface PermissionEnum<T extends Enum<T>> {
    EnumSet<BlPermission> implies();

    EnumSet<BlPermission> mayGrant();

    boolean implies(BlPermission other);

    boolean mayGrant(BlPermission other);

    boolean implies(EnumSet<BlPermission> others);

    boolean mayGrant(EnumSet<BlPermission> other);

    /** Workaround for values() function not existing on generic Enum class. (enums are weird). */
    static PermissionEnum<?>[] getValues() {
        throw new NotImplementedException("This method should be overridden by the implementing enum.");
    }
}
