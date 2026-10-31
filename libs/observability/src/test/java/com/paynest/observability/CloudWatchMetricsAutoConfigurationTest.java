package com.paynest.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.prometheus.PrometheusMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataResponse;

/** T-MET-1 to T-MET-3. The AWS client is a mock, so no call leaves the machine. */
class CloudWatchMetricsAutoConfigurationTest {

    private final CloudWatchAsyncClient client = mock(CloudWatchAsyncClient.class);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            // SpringApplication sets this conversion service in a real run; it turns "1m" into a Duration.
            .withInitializer(context -> context.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withConfiguration(AutoConfigurations.of(
                    MetricsAutoConfiguration.class,
                    PrometheusMetricsExportAutoConfiguration.class,
                    CompositeMeterRegistryAutoConfiguration.class,
                    CloudWatchMetricsAutoConfiguration.class));

    private final ApplicationContextRunner awsRunner = runner
            .withBean(CloudWatchAsyncClient.class, () -> client)
            .withPropertyValues("spring.profiles.active=aws", "paynest.cloudwatch.step=100ms");

    @BeforeEach
    void setUp() {
        when(client.putMetricData(any(PutMetricDataRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(PutMetricDataResponse.builder().build()));
    }

    @Test
    void localRunHasNoCloudWatchBeans() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(CloudWatchMeterRegistry.class);
            assertThat(context).doesNotHaveBean(CloudWatchAsyncClient.class);
            assertThat(context).hasSingleBean(PrometheusMeterRegistry.class);
        });
    }

    @Test
    void awsProfileKeepsOnlyAllowListedMetricsOnCloudWatch() {
        awsRunner.run(context -> {
            MeterRegistry all = context.getBean(MeterRegistry.class);
            MeterRegistry cloudWatch = context.getBean(CloudWatchMeterRegistry.class);

            all.counter("paynest.transfer.completed");
            all.timer("http.server.requests", "uri", "/wallets/me", "status", "200");
            all.gauge("jvm.memory.used", List.of(
                    Tag.of("area", "heap"),
                    Tag.of("id", "Eden")), 1);
            all.gauge("jvm.memory.used", List.of(
                    Tag.of("area", "nonheap"),
                    Tag.of("id", "Metaspace")), 1);
            all.counter("kafka.consumer.fetch");

            assertThat(cloudWatch.find("paynest.transfer.completed").counter()).isNotNull();
            assertThat(cloudWatch.find("http.server.requests").timer()).isNotNull();
            assertThat(cloudWatch.find("jvm.memory.used").tag("area", "heap").gauge()).isNotNull();
            assertThat(cloudWatch.find("jvm.memory.used").tag("area", "nonheap").gauge()).isNull();
            assertThat(cloudWatch.find("kafka.consumer.fetch").counter()).isNull();
        });
    }

    @Test
    void allowListDoesNotAffectPrometheus() {
        awsRunner.run(context -> {
            context.getBean(MeterRegistry.class).counter("kafka.consumer.fetch");

            assertThat(context.getBean(PrometheusMeterRegistry.class).find("kafka.consumer.fetch").counter()).isNotNull();
        });
    }

    @Test
    void costlyHttpTagsAreDropped() {
        awsRunner.run(context -> {
            context.getBean(MeterRegistry.class).timer("http.server.requests",
                    "uri", "/wallets/me", "status", "200", "method", "GET", "outcome", "SUCCESS", "exception", "none");

            var id = context.getBean(CloudWatchMeterRegistry.class).get("http.server.requests").timer().getId();
            assertThat(id.getTag("uri")).isEqualTo("/wallets/me");
            assertThat(id.getTag("status")).isEqualTo("200");
            assertThat(id.getTag("method")).isNull();
            assertThat(id.getTag("outcome")).isNull();
            assertThat(id.getTag("exception")).isNull();
        });
    }

    @Test
    void publishesToPayNestNamespaceWithServiceDimension() {
        awsRunner.withPropertyValues("management.metrics.tags.application=transfer-service").run(context -> {
            context.getBean(MeterRegistry.class).counter("paynest.transfer.completed").increment();

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                ArgumentCaptor<PutMetricDataRequest> sent = ArgumentCaptor.forClass(PutMetricDataRequest.class);
                verify(client, atLeastOnce()).putMetricData(sent.capture());

                assertThat(sent.getAllValues()).allSatisfy(request -> assertThat(request.namespace()).isEqualTo("PayNest"));
                MetricDatum completed = sent.getAllValues().stream()
                        .flatMap(request -> request.metricData().stream())
                        .filter(datum -> datum.metricName().equals("paynest.transfer.completed.count"))
                        .filter(datum -> datum.value() > 0)
                        .findFirst().orElseThrow();
                assertThat(completed.dimensions()).anySatisfy(dimension -> {
                    assertThat(dimension.name()).isEqualTo("application");
                    assertThat(dimension.value()).isEqualTo("transfer-service");
                });
            });
        });
    }
}
