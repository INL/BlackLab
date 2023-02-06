package nl.inl.blacklab.server.lib.results;

/** Versions of the BlackLab webservice API.
 *
 *  Right now, they only differ in small details.
 */
public enum ApiVersion {
    /** Maintain strict response compatibility with BLS v3. */
    V3("3.0"),

    /** Fix a few small annoyances such as inconsistent names, datatypes,
     *  field info where it doesn't belong, etc. */
    V4("4.0");

    private final String versionString;

    ApiVersion(String versionString) {
        this.versionString = versionString;
    }

    /**
     * Return the appropriate API version from the given value
     *
     * For "v3.0", "v3", "3.0" or "3", returns V3.
     * Specifically, ignores a "v" at the start, and will add ".0" if only
     * one digit is given.
     *
     * @param s
     * @return
     */
    public static ApiVersion fromValue(String s) {
        if (s.length() > 0 && s.toLowerCase().charAt(0) == 'v')
            s = s.substring(1);
        if (s.matches("\\d+"))
            s += ".0";
        ApiVersion[] versions = values();
        for (ApiVersion v: versions) {
            if (v.versionString.equals(s))
                return v;
        }
        // Just return the latest version
        return versions[versions.length - 1];
    }

    public String versionString() {
        return versionString;
    }
}
