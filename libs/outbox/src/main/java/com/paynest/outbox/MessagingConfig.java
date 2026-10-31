package com.paynest.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import com.paynest.events.EventCodec;
import com.paynest.events.Topics;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class MessagingConfig {

    @Bean
    EventCodec eventCodec(JsonMapper jsonMapper) {
        return new EventCodec(jsonMapper);
    }

    /** Creates the saga topics on startup if they do not exist yet. */
    @Bean
    KafkaAdmin.NewTopics paynestTopics(@Value("${paynest.kafka.partitions:3}") int partitions,
                                        @Value("${paynest.kafka.replicas:1}") int replicas) {
        return new KafkaAdmin.NewTopics(Topics.ALL.stream()
                .map(name -> TopicBuilder.name(name).partitions(partitions).replicas(replicas).build())
                .toArray(NewTopic[]::new));
    }

    /**
     * Saga listeners use blocking retries so messages of one wallet / transfer stay in order.
     * Optimistic-lock conflicts are retried here. After the last attempt the record goes to
     * {@code <topic>.dlt} for manual handling.
     */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + ".dlt", -1));
        var backOff = new ExponentialBackOffWithMaxRetries(6);
        backOff.setInitialInterval(200);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(5_000);
        var handler = new DefaultErrorHandler(recoverer, backOff);
        // A message that cannot be parsed will never succeed: send it to the DLT at once.
        handler.addNotRetryableExceptions(JacksonException.class);
        return handler;
    }
}
