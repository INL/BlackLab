package nl.inl.blacklab.instrumentation;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * MetricsProvider provides a meaningful MeterRegistry object
 * that will be used to gather metrics.
 */
public interface MetricsProvider {
    MeterRegistry getRegistry();

    default  boolean metricsEnabled() {
        String result = System.getProperty("metrics.enabled", "true");
        boolean disabled = result.equalsIgnoreCase("false");
        return !disabled;
    }
}
