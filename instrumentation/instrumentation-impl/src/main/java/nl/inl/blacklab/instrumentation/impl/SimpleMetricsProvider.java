package nl.inl.blacklab.instrumentation.impl;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmHeapPressureMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import nl.inl.blacklab.instrumentation.MetricsProvider;

/**
 * Creates a registry that tracks via
 * micrometer's SimpleRegistry
 */
public class SimpleMetricsProvider implements MetricsProvider {
    @Override
    public MeterRegistry getRegistry() {
        SimpleMeterRegistry simpleMeterRegistry = new SimpleMeterRegistry();
        addSystemMetrics(simpleMeterRegistry);
        return simpleMeterRegistry;
    }

    /**
     * Adds metrics to measure the behaviour of the underlying JVM.
     * @param registry the registry
     */
    private void addSystemMetrics(MeterRegistry registry) {
        new JvmMemoryMetrics().bindTo(registry);
        new JvmGcMetrics().bindTo(registry);
        new JvmHeapPressureMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new ProcessorMetrics().bindTo(registry);
    }


}
