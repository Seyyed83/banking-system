package com.example.banking.account.cache;

import com.example.banking.account.entity.AccountStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AccountBalanceCacheTest {

    private static final String ACCOUNT = "12345678901234";
    private static final String KEY = "banking:balance:" + ACCOUNT;

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private AccountBalanceCache cache;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        cache = new AccountBalanceCache(redis, true, 30);
    }

    @Test
    void getReturnsCachedStatusAndBalance() {
        when(values.get(KEY)).thenReturn("ACTIVE|1500.2500");

        Optional<AccountBalanceCache.CachedBalance> result = cache.get(ACCOUNT);

        assertTrue(result.isPresent());
        assertEquals(AccountStatus.ACTIVE, result.get().status());
        assertEquals(new BigDecimal("1500.2500"), result.get().balance());
    }

    @Test
    void getReturnsEmptyOnMissAndOnGarbage() {
        when(values.get(KEY)).thenReturn(null);
        assertTrue(cache.get(ACCOUNT).isEmpty());

        when(values.get(KEY)).thenReturn("not-a-valid-entry");
        assertTrue(cache.get(ACCOUNT).isEmpty());

        when(values.get(KEY)).thenReturn("ACTIVE|abc");
        assertTrue(cache.get(ACCOUNT).isEmpty());
    }

    @Test
    void getFallsBackWhenRedisFails() {
        when(values.get(KEY)).thenThrow(new IllegalStateException("redis down"));

        assertTrue(cache.get(ACCOUNT).isEmpty());
    }

    @Test
    void putStoresPlainStringWithTtl() {
        cache.put(ACCOUNT, AccountStatus.ACTIVE, new BigDecimal("10.5000"));

        verify(values).set(KEY, "ACTIVE|10.5000", Duration.ofSeconds(30));
    }

    @Test
    void putSwallowsRedisFailure() {
        org.mockito.Mockito.doThrow(new IllegalStateException("redis down"))
                .when(values).set(KEY, "ACTIVE|1", Duration.ofSeconds(30));

        cache.put(ACCOUNT, AccountStatus.ACTIVE, BigDecimal.ONE);
    }

    @Test
    void disabledCacheNeverTouchesRedis() {
        AccountBalanceCache disabled = new AccountBalanceCache(redis, false, 30);

        assertTrue(disabled.get(ACCOUNT).isEmpty());
        disabled.put(ACCOUNT, AccountStatus.ACTIVE, BigDecimal.ONE);
        disabled.evictAfterCommit(List.of(ACCOUNT));

        verifyNoInteractions(redis);
    }

    @Test
    void evictWithoutTransactionDeletesImmediately() {
        cache.evictAfterCommit(List.of(ACCOUNT));

        verify(redis).delete(List.of(KEY));
    }

    @Test
    void evictInsideTransactionWaitsForCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.evictAfterCommit(List.of(ACCOUNT));

            verify(redis, never()).delete(anyCollection());

            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            verify(redis).delete(List.of(KEY));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void evictionFailureDoesNotPropagate() {
        when(redis.delete(List.of(KEY))).thenThrow(new IllegalStateException("redis down"));

        cache.evictAfterCommit(List.of(ACCOUNT));
    }
}
