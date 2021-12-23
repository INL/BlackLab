package nl.inl.blacklab.instrumentation.impl;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmHeapPressureMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import nl.inl.blacklab.instrumentation.MetricsProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class PrometheusMetricsProvider implements MetricsProvider {
    private static final Logger logger = LogManager.getLogger(PrometheusMetricsProvider.class);
    private static final String DEFAULT_PROM_ENDPOINT = "/metrics";

    private final MeterRegistry theRegistry;

    public PrometheusMetricsProvider() {
        theRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        addSystemMetrics(theRegistry);
    }

    @Override
    public MeterRegistry getRegistry() {
        return theRegistry;
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

    /**
     * A simple request handler that responds to prometheus  metrics scrapping requests
     * @param registry must be of type {@link PrometheusMeterRegistry}
     * @param request
     * @param responseObject
     * @param charEncoding
     * @return
     */
    protected static boolean handlePrometheus(MeterRegistry registry, HttpServletRequest request, HttpServletResponse responseObject, String charEncoding) {
        if (!request.getRequestURI().contains(DEFAULT_PROM_ENDPOINT)) {
            return false;
        }
        if (!(registry instanceof PrometheusMeterRegistry)) {
            logger.warn("Can not respond to /metrics without a PrometheusRegistry");
            return true;
        }

        PrometheusMeterRegistry prometheusMeterRegistry = (PrometheusMeterRegistry) registry;

        try {
            prometheusMeterRegistry.scrape(responseObject.getWriter());
            responseObject.setStatus(HttpServletResponse.SC_OK);
            responseObject.setCharacterEncoding(charEncoding);
            responseObject.setContentType(TextFormat.CONTENT_TYPE_004);
        } catch (IOException exception) {
            logger.error("Can't scrape prometheus metrics", exception);
        }
        return true;
    }
}
