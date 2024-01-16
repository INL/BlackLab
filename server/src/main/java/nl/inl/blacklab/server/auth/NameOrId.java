package nl.inl.blacklab.server.auth;

import java.util.Objects;

/**
 * Piece of interop code to convert between the ID and the name of a user or resource.
 * <br>
 * The ID is always the item's ID in the Authorization Server, the Name what identifies the item in the application.
 * These are not always the same, as the ID in the Authorization Server is typically a UUID, so not useful to the application.
 * We store things under a regular name, for users, an email address, for resources, a name the user picked.
 * Lots of operations come in with the name, but we need the ID to talk to the Authorization Server.
 * So we need to be able to convert between the two.
 * To prevent confusion when passing things around in the code, we wrap the two in this class.
 */
public class NameOrId {
    private final String id;
    private final String name;

    public NameOrId(String id, String name) {
        this.id = id;
        this.name = name;
        if (id == null && name == null) {
            throw new IllegalArgumentException("Either id or name must be non-null");
        }
    }

    public static NameOrId id(String id) {
        return new NameOrId(id, null);
    }

    public static NameOrId name(String name) {
        return new NameOrId(null, name);
    }

    public boolean isId() {
        return id != null;
    }

    public boolean isName() {
        return name != null;
    }

    /** Get the id. Throws an exception if it doesn't exist. */
    public String getId() throws NullPointerException {
        Objects.requireNonNull(id);
        return id;
    }

    /** Get the name. Throws an exception if it doesn't exist. */
    public String getName() throws NullPointerException {
        Objects.requireNonNull(name);
        return name;
    }

    /** Get the id if it exists, otherwise the name */
    public String getEither() {
        return isId() ? id : name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        NameOrId nameOrId = (NameOrId) o;
        return Objects.equals(id, nameOrId.id) && Objects.equals(name, nameOrId.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }
}
