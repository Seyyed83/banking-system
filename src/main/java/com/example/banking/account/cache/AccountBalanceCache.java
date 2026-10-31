package com.example.banking.account.cache;

import com.example.banking.account.entity.AccountStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Redis cache-aside for the read-only balance endpoint.
 *
 * <p>Oracle stays the single source of truth: every money movement is still committed
 * atomically in Oracle, and this cache is only ever read by the balance query. Safety rules:
 * <ul>
 *   <li>Redis failures never break a request - every method swallows errors and falls back to Oracle.</li>
 *   <li>Entries are evicted only AFTER the Oracle transaction commits, never before.</li>
 *   <li>Entries also expire after a short TTL, which bounds staleness if an eviction is lost.</li>
 * </ul>
 * Values are stored as plain strings ({@code STATUS|balance}) - no serializer is involved.
 */
@Component
public class AccountBalanceCache {

    private static final Logger log = LoggerFactory.getLogger(AccountBalanceCache.class);

    static final String KEY_PREFIX = "banking:balance:";
    private static final char SEPARATOR = '|';

    public record CachedBalance(AccountStatus status, BigDecimal balance) { }

    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final Duration ttl;

    public AccountBalanceCache(
            StringRedisTemplate redis,
            @Value("${banking.cache.balance.enabled:true}") boolean enabled,
            @Value("${banking.cache.balance.ttl-seconds:30}") long ttlSeconds) {
        this.redis = redis;
        this.enabled = enabled;
        this.ttl = Duration.ofSeconds(Math.max(1, ttlSeconds));
    }

    public Optional<CachedBalance> get(String accountNumber) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            String raw = redis.opsForValue().get(key(accountNumber));
            if (raw == null) {
                return Optional.empty();
            }
            int index = raw.indexOf(SEPARATOR);
            if (index <= 0) {
                return Optional.empty();
            }
            return Optional.of(new CachedBalance(
                    AccountStatus.valueOf(raw.substring(0, index)),
                    new BigDecimal(raw.substring(index + 1))));
        } catch (RuntimeException exception) {
            log.warn("Balance cache read failed, falling back to the database: {}", exception.toString());
            return Optional.empty();
        }
    }

    public void put(String accountNumber, AccountStatus status, BigDecimal balance) {
        if (!enabled) {
            return;
        }
        try {
            redis.opsForValue().set(
                    key(accountNumber),
                    status.name() + SEPARATOR + balance.toPlainString(),
                    ttl);
        } catch (RuntimeException exception) {
            log.warn("Balance cache write failed: {}", exception.toString());
        }
    }

    /**
     * Evicts the given accounts once the surrounding database transaction has committed.
     * If no transaction is active, evicts immediately.
     */
    public void evictAfterCommit(Collection<String> accountNumbers) {
        if (!enabled || accountNumbers == null || accountNumbers.isEmpty()) {
            return;
        }
        List<String> keys = accountNumbers.stream().map(AccountBalanceCache::key).toList();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete(keys);
                }
            });
        } else {
            delete(keys);
        }
    }

    private void delete(List<String> keys) {
        try {
            redis.delete(keys);
        } catch (RuntimeException exception) {
            log.warn("Balance cache eviction failed (entries expire by TTL): {}", exception.toString());
        }
    }

    private static String key(String accountNumber) {
        return KEY_PREFIX + accountNumber;
    }
}
