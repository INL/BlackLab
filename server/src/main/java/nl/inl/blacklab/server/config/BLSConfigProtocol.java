package nl.inl.blacklab.server.config;

public class BLSConfigProtocol {

    @Deprecated
    private boolean useOldElementNames = false;

    private boolean omitEmptyProperties = false;

    public boolean isOmitEmptyProperties() {
        return omitEmptyProperties;
    }

    public void setOmitEmptyProperties(boolean omitEmptyProperties) {
        this.omitEmptyProperties = omitEmptyProperties;
    }

    private String accessControlAllowOrigin = "*";

    private String defaultOutputType = "XML";

    public String getDefaultOutputType() {
        return defaultOutputType;
    }

    public void setDefaultOutputType(String defaultOutputType) {
        this.defaultOutputType = defaultOutputType;
    }

    public String getAccessControlAllowOrigin() {
        return accessControlAllowOrigin;
    }

    public void setAccessControlAllowOrigin(String accessControlAllowOrigin) {
        this.accessControlAllowOrigin = accessControlAllowOrigin;
    }

    @Deprecated
    public boolean isUseOldElementNames() {
        return useOldElementNames;
    }

    @Deprecated
    public void setUseOldElementNames(boolean useOldElementNames) {
        this.useOldElementNames = useOldElementNames;
    }

}