package com.paynest.observability;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import io.micrometer.cloudwatch2.CloudWatchConfig;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

/**
 * Sends metrics to CloudWatch when the {@code aws} profile is active. Without it nothing here
 * loads, so local runs never touch AWS. Region and credentials come from the SDK's default chain;
 * on EC2 that is the instance's IAM role.
 * <p>
 * This is an auto-configuration, not a scanned component, because api-gateway and bank-adapter
 * only scan their own package.
 */
@AutoConfiguration(after = MetricsAutoConfiguration.class, before = CompositeMeterRegistryAutoConfiguration.class)
@Profile("aws")
public class CloudWatchMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    CloudWatchAsyncClient cloudWatchAsyncClient() {
        return CloudWatchAsyncClient.create();
    }

    @Bean
    CloudWatchMeterRegistry cloudWatchMeterRegistry(CloudWatchAsyncClient client, Clock clock,
                                                    @Value("${paynest.cloudwatch.namespace:PayNest}") String namespace,
                                                    @Value("${paynest.cloudwatch.step:1m}") Duration step) {
        CloudWatchConfig config = new CloudWatchConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String namespace() {
                return namespace;
            }

            @Override
            public Duration step() {
                return step;
            }
        };
        CloudWatchMeterRegistry registry = new CloudWatchMeterRegistry(config, clock, client);
        // Set on this registry and not as MeterFilter beans, which would also empty the Prometheus endpoint.
        registry.config()
                .meterFilter(MeterFilter.denyUnless(CloudWatchMetricsAutoConfiguration::isSent))
                .meterFilter(MeterFilter.ignoreTags("exception", "method", "outcome", "error"));
        return registry;
    }

    /** CloudWatch bills every combination of metric name and tags, so only what is needed is sent. */
    private static boolean isSent(Meter.Id id) {
        return id.getName().startsWith("paynest.")
                || id.getName().equals("http.server.requests")
                || (id.getName().equals("jvm.memory.used") && "heap".equals(id.getTag("area")));
    }
}
