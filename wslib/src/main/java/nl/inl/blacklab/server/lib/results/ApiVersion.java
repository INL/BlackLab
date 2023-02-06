package nl.inl.blacklab.server.lib.results;

/** Versions of the BlackLab webservice API.
 *
 *  Right now, they only differ in small details.
 */
public enum ApiVersion {
    /** Maintain response compatibility with BLS v3. */
    V3("3.0"),

    /** Fixes a few small annoyances such as inconsistent names, datatypes,
     *  field info where it doesn't belong, etc. */
    V4("4.0");

    /** What's considered the current version */
    public static final ApiVersion CURRENT = V3;

    /** An experimental future version of the API, if there is one; the current one, otherwise */
    public static final ApiVersion EXPERIMENTAL = V4;

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
        s = s.toLowerCase();
        if (s.equals("current"))
            return CURRENT;
        if (s.equals("experimental"))
            return EXPERIMENTAL;
        if (s.length() > 0 && s.charAt(0) == 'v')
            s = s.substring(1);
        if (s.matches("\\d+"))
            s += ".0";
        ApiVersion[] versions = values();
        for (ApiVersion v: versions) {
            if (v.versionString.equals(s))
                return v;
        }
        // Not recognized; just use the current version
        return CURRENT;
    }

    public String versionString() {
        return versionString;
    }
}
