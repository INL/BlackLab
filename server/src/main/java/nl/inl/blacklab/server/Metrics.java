package nl.inl.blacklab.server;

import io.micrometer.cloudwatch2.CloudWatchConfig;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.*;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.tomcat.TomcatMetrics;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.internal.util.EC2MetadataUtils;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataResponse;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeTagsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeTagsResponse;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.TagDescription;


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class Metrics {
    private static final Logger logger = LogManager.getLogger(Metrics.class);
    static final String CW_NAMESPACE = "Blacklab";
    static final String CW_NAMESPACE_PROPERTY = "metrics.cloudwatch.namespace";
    static final String METRICS_ENABLED = "metrics.enabled";

    /**
     * CustomCWClient injects base dimensions to all metrics published to CloudWatch
     */
    private static class CustomCWClient implements CloudWatchAsyncClient {
        private final CloudWatchAsyncClient client = CloudWatchAsyncClient.builder().build();
        private final List<Dimension> hostDimensions = new ArrayList<>();

        public CustomCWClient(Map<String, String> instanceTags) {
            hostDimensions.add(fromOptional("Application",
                    () -> Optional.ofNullable(System.getenv("APPLICATION")), "Blacklab"));
            hostDimensions.add(fromOptional("ContainerId",
                    () -> Optional.ofNullable(System.getenv("HOSTNAME")), "Unknown"));
            hostDimensions.add(fromOptional("Environment",
                    () -> Optional.ofNullable(instanceTags.get("Environment")), "Unknown"));
            hostDimensions.add(fromOptional("InstanceId",
                    () -> Optional.ofNullable(instanceTags.get("InstanceId")), "Unknown"));
            logger.info("Will publish CloudWatch metrics with the following dimensions: " + hostDimensions);
        }

        private Dimension fromOptional(String dimensionName, Supplier<Optional<String>> dimensionGetter, String defaultValue) {
            String value = dimensionGetter.get().orElse(defaultValue);
            return Dimension.builder().name(dimensionName).value(value).build();
        }

    @Override
        public String serviceName() {
            return client.serviceName();
        }

        @Override
        public void close() {
            client.close();
        }

        @Override
        public CompletableFuture<PutMetricDataResponse> putMetricData(PutMetricDataRequest putMetricDataRequest) {
            List<MetricDatum> newData = new ArrayList<>();
            for(MetricDatum m : putMetricDataRequest.metricData()) {
                ArrayList<Dimension> dimensions = new ArrayList<>(m.dimensions());
                dimensions.addAll(hostDimensions);
                MetricDatum newDatum = m.toBuilder()
                        .dimensions(dimensions)
                        .build();
                newData.add(newDatum);
            }

            PutMetricDataRequest req = PutMetricDataRequest.builder()
                    .namespace(Metrics.cloudWatchConfig().namespace())
                    .metricData(newData)
                    .build();

            return client.putMetricData(req);
        }
    }

    /**
     * Registry for metrics. Define to metrics backend
     **/
    final static CompositeMeterRegistry metricsRegistry = init();

    private static CompositeMeterRegistry init() {
        CompositeMeterRegistry registry = new CompositeMeterRegistry();
        if (!metricsEnabled()) {
            logger.info("Metrics are disabled. No metrics will be published.");
            return registry;
        }


        // Add cloudwatch metrics on ec2 instances only
        Optional<Map<String, String>> tags = getInstanceTags();
        if (!tags.isPresent()) {
            logger.info("No EC2 information. Will not publish metrics to CloudWatch");
            registry.add(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT));
            logger.info("Publishing metrics to Prometheus");
        } else {
            logger.info("Found EC2 information. Will publish to CloudWatch");
            registry.add(new CloudWatchMeterRegistry(cloudWatchConfig(), Clock.SYSTEM, new CustomCWClient(tags.get())));
        }

        new JvmMemoryMetrics().bindTo(registry);
        new JvmGcMetrics().bindTo(registry);
        new JvmHeapPressureMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new ProcessorMetrics().bindTo(registry);
        new TomcatMetrics(null, Tags.empty()).bindTo(registry);
        return registry;
    }

    public static boolean metricsEnabled() {
        String result = System.getProperty(METRICS_ENABLED, "true");
        boolean disabled = result.equalsIgnoreCase("false");
        return !disabled;
    }

    private static CloudWatchConfig cloudWatchConfig(){
        final Map<String, String> props = new HashMap<>();
        props.put("cloudwatch.namespace", System.getProperty(CW_NAMESPACE_PROPERTY, CW_NAMESPACE));
        props.put("cloudwatch.step", Duration.ofMinutes(1).toString());
        props.put("cloudwatch.batchSize", String.format("%d", CloudWatchConfig.MAX_BATCH_SIZE));

        return new CloudWatchConfig() {
            @Override
            public String get(String key) {
                return props.get(key);
            }
        };
    }

    private static Optional<Map<String, String>> getInstanceTags() {
        try {
            EC2MetadataUtils.InstanceInfo instanceInfo = EC2MetadataUtils.getInstanceInfo();
            if (instanceInfo == null) {
                logger.info("Instance info is not valid");
                return Optional.empty();
            }

            Ec2Client ec2 = Ec2Client.builder()
                    .region(Region.US_WEST_2)
                    .build();

            Filter filter = Filter.builder()
                    .name("resource-id")
                    .values(instanceInfo.getInstanceId())
                    .build();

            DescribeTagsResponse describeTagsResponse = ec2.describeTags(DescribeTagsRequest.builder().filters(filter).build());
            List<TagDescription> tags = describeTagsResponse.tags();
            Map<String, String> tagsMap = new HashMap<>();
            tagsMap.put("InstanceId", instanceInfo.getInstanceId());
            for (TagDescription tag: tags) {
                tagsMap.put(tag.key(), tag.value());
            }
            return Optional.of(tagsMap);
        } catch(Exception ex) {
            logger.info("Can not read instance info");
            return Optional.empty();
        }
    }

    protected static boolean handlePrometheus(HttpServletRequest request, HttpServletResponse responseObject) {
        // Metrics scrapping endpoint
        if (!request.getRequestURI().contains("/metrics")) {
            return false;
        }

        Optional<PrometheusMeterRegistry> reg = metricsRegistry.getRegistries().stream()
                .filter(r -> r instanceof PrometheusMeterRegistry)
                .map(t -> (PrometheusMeterRegistry) t)
                .findFirst();
        reg.ifPresent((PrometheusMeterRegistry registry) -> {
            try {
                registry.scrape(responseObject.getWriter());
                responseObject.setStatus(HttpServletResponse.SC_OK);
                responseObject.setCharacterEncoding(BlackLabServer.OUTPUT_ENCODING.name().toLowerCase());
                responseObject.setContentType(TextFormat.CONTENT_TYPE_004);
            } catch (IOException exception) {
                logger.error("Can't scrape prometheus metrics", exception);
            }
        });
        return true;
    }
}

