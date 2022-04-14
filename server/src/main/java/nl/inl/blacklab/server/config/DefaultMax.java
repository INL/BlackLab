package nl.inl.blacklab.server.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import nl.inl.blacklab.search.results.HitsInternal;

/**
 * Integer setting with a default and maximum value.
 * 
 * Please note that setting a max of -1 is interpreted as HitsInternal.MAX_ARRAY_SIZE,
 * effectively setting no limit.
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
        setMax(max);
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
        this.max = max == -1 ? HitsInternal.MAX_ARRAY_SIZE : max;
    }
    
    
}