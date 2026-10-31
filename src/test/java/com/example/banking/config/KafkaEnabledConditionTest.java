package com.example.banking.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaEnabledConditionTest {

    private boolean matches(MockEnvironment environment) {
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        return new KafkaEnabledCondition().matches(context, null);
    }

    @Test
    void enabledByDefault() {
        assertTrue(matches(new MockEnvironment()));
    }

    @Test
    void disabledWhenPropertyIsFalse() {
        assertFalse(matches(new MockEnvironment().withProperty("banking.kafka.enabled", "false")));
    }

    @Test
    void enabledWhenPropertyIsTrue() {
        assertTrue(matches(new MockEnvironment().withProperty("banking.kafka.enabled", "true")));
    }
}
