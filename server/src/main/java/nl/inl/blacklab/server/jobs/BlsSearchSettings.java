package nl.inl.blacklab.server.jobs;

/**
 * Some general settings that control how BlackLab Server executes a search
 * request.
 */
public class BlsSearchSettings {

    private boolean debugMode;

    private int fiMatchNfaFactor;

    private boolean useCache;

    public BlsSearchSettings(boolean debugMode, int fiMatchNfaFactor, boolean useCache) {
        super();
        this.debugMode = debugMode;
        this.fiMatchNfaFactor = fiMatchNfaFactor;
        this.useCache = useCache;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public int getFiMatchNfaFactor() {
        return fiMatchNfaFactor;
    }

    public boolean isUseCache() {
        return useCache;
    }

    @Override
    public String toString() {
        return "SearchSettings [debugMode=" + debugMode + ", fiMatchNfaFactor=" + fiMatchNfaFactor + ", useCache="
                + useCache + "]";
    }

}
