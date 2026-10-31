package com.paynest.notification.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import tools.jackson.databind.json.JsonMapper;

import com.paynest.events.EventCodec;
import com.paynest.events.Topics;

@Configuration(proxyBeanMethods = false)
public class MessagingConfig {

    @Bean
    EventCodec eventCodec(JsonMapper jsonMapper) {
        return new EventCodec(jsonMapper);
    }

    @Bean
    NewTopic transferEventsTopic(@Value("${paynest.kafka.partitions:3}") int partitions,
                                 @Value("${paynest.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(Topics.TRANSFER_EVENTS).partitions(partitions).replicas(replicas).build();
    }
}
