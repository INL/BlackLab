package nl.inl.blacklab.config;

import java.util.Collections;
import java.util.Map;

public class BLConfigPlugins {
    boolean delayInitialization = false;

    Map<String, Map<String, String>> plugins = Collections.emptyMap(); 
    
    public boolean isDelayInitialization() {
        return delayInitialization;
    }

    @SuppressWarnings("unused")
    public void setDelayInitialization(boolean delayInitialization) {
        this.delayInitialization = delayInitialization;
    }

    public Map<String, Map<String, String>> getPlugins() {
        return plugins;
    }

    @SuppressWarnings("unused")
    public void setPlugins(Map<String, Map<String, String>> plugins) {
        if (plugins != null) {
            this.plugins = plugins;
        }
    }
}