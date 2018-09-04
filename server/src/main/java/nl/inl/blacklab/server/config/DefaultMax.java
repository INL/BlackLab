package nl.inl.blacklab.server.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Integer setting with a default and maximum value.
 */
public class DefaultMax {
    
    public static DefaultMax get(int def, int max) {
        return new DefaultMax(def, max);
    }
    
    @JsonProperty("default")
    int defaultValue;
    
    int max;

    DefaultMax() {
        defaultValue = 0;
        max = 0;
    }
    
    DefaultMax(int def, int max) {
        this.defaultValue = def;
        this.max = max;
    }

    public int getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(int defaultValue) {
        this.defaultValue = defaultValue;
    }

    public int getMax() {
        return max;
    }

    public void setMax(int max) {
        this.max = max;
    }
    
    
}