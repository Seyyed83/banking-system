package com.example.banking.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** True unless {@code banking.kafka.enabled=false}; keeps Kafka admin work out of broker-less runs. */
class KafkaEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return context.getEnvironment().getProperty("banking.kafka.enabled", Boolean.class, Boolean.TRUE);
    }
}
