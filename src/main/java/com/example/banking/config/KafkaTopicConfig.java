package com.example.banking.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the events topic; Spring's KafkaAdmin creates it on startup if it does not exist.
 * Skipped when Kafka is disabled, so tests and broker-less runs do not wait for a broker.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    @Conditional(KafkaEnabledCondition.class)
    public NewTopic transactionEventsTopic(
            @Value("${banking.kafka.topic:banking.transaction-events}") String topic,
            @Value("${banking.kafka.topic-partitions:3}") int partitions) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }
}
