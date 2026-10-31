package com.paynest.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.logging.LoggingSystemProperty;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** T-LOG-1, T-LOG-2. Starts a real application, because logging is set up before the context exists. */
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingTest {

    /** Boot keeps the format in a system property and would reuse it for the next run in this JVM. */
    @AfterEach
    void clearLoggingFormat() {
        System.clearProperty(LoggingSystemProperty.CONSOLE_STRUCTURED_FORMAT.getEnvironmentVariableName());
    }

    @Test
    void awsProfileLogsOneJsonObjectPerLineWithMdcFields(CapturedOutput output) {
        run("aws", () -> {
            try (var ignored = MDC.putCloseable("transferId", "t-123")) {
                LoggerFactory.getLogger("test").info("Transfer completed");
            }
        });

        String line = output.getOut().lines()
                .filter(l -> l.contains("Transfer completed"))
                .findFirst().orElseThrow();
        JsonNode json = JsonMapper.builder().build().readTree(line);
        assertThat(json.path("message").asString()).isEqualTo("Transfer completed");
        assertThat(json.path("transferId").asString()).isEqualTo("t-123");
        assertThat(json.path("log").path("level").asString()).isEqualTo("INFO");
        assertThat(json.path("service").path("name").asString()).isEqualTo("structured-logging-test");
    }

    @Test
    void localRunKeepsPlainTextLogs(CapturedOutput output) {
        run("local", () -> LoggerFactory.getLogger("test").info("Plain line"));

        String line = output.getOut().lines()
                .filter(l -> l.contains("Plain line"))
                .findFirst().orElseThrow();
        assertThat(line).doesNotStartWith("{");
    }

    private static void run(String profile, Runnable body) {
        try (var ignored = new SpringApplicationBuilder(TestApplication.class)
                .web(WebApplicationType.NONE)
                .profiles(profile)
                .properties("spring.application.name=structured-logging-test", "paynest.cloudwatch.step=1h")
                .run()) {
            body.run();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        CloudWatchAsyncClient cloudWatchAsyncClient() {
            CloudWatchAsyncClient client = mock(CloudWatchAsyncClient.class);
            when(client.putMetricData(any(PutMetricDataRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(PutMetricDataResponse.builder().build()));
            return client;
        }
    }
}
