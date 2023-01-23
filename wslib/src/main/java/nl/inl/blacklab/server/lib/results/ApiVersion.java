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

    public String versionString() {
        return versionString;
    }
}
