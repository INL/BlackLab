package nl.inl.blacklab.server.auth;

import java.util.EnumSet;
import java.util.Set;

public enum BlPermission implements PermissionEnum<BlPermission> {
    READ,
    WRITE,
    DELETE,
    SHARE,
    ADMIN;

    public Set<BlPermission> implies() {
        switch (this) {
        case READ:
            return EnumSet.of(READ);
        case WRITE:
            return EnumSet.of(READ, WRITE);
        case DELETE:
            return EnumSet.of(READ, WRITE, DELETE);
        case SHARE:
            return EnumSet.of(READ, SHARE);
        case ADMIN:
            return EnumSet.allOf(BlPermission.class);
        default:
            return EnumSet.of(this);
        }
    }

    /** Which permissions may the holder of this permission grant to others */
    public Set<BlPermission> mayGrant() {
        switch (this) {
        case SHARE:
            return EnumSet.of(BlPermission.READ, BlPermission.SHARE);
        case ADMIN:
            return EnumSet.allOf(BlPermission.class);
        default:
            return EnumSet.noneOf(BlPermission.class);
        }
    }

    public boolean mayGrant(BlPermission other) {
        return this.mayGrant().contains(other);
    }

    public boolean mayGrant(Set<BlPermission> other) {
        return this.mayGrant().containsAll(other);
    }

    public boolean implies(BlPermission other) {
        return this.implies().contains(other);
    }
    public boolean implies(Set<BlPermission> others) {
        return this.implies().containsAll(others);
    }

    @Override
    public String toString() {
        return this.name().toLowerCase();
    }
}
