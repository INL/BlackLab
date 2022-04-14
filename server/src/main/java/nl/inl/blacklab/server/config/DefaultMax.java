package nl.inl.blacklab.server.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import nl.inl.blacklab.search.BlackLab;

/**
 * Integer setting with a default and maximum value.
 * 
 * Please note that setting a max of -1 is interpreted as HitsInternal.MAX_ARRAY_SIZE,
 * effectively setting no limit.
 */
public class DefaultMax {
    
    public static DefaultMax get(long def, long max) {
        return new DefaultMax(def, max);
    }
    
    @JsonProperty("default")
    long defaultValue;

    long max;

    DefaultMax() {
        defaultValue = 0;
        max = 0;
    }
    
    DefaultMax(long def, long max) {
        this.defaultValue = def;
        setMax(max);
    }

    public long getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(long defaultValue) {
        this.defaultValue = defaultValue;
    }

    public long getMax() {
        return max;
    }

    public void setMax(long max) {
        this.max = max == -1 ? BlackLab.JAVA_MAX_ARRAY_SIZE : max;
    }
}