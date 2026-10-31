package com.example.banking.account.repository;

import com.example.banking.account.entity.Account;
import com.example.banking.account.entity.AccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository
        extends JpaRepository<Account, Long> {

    boolean existsByAccountNumber(String accountNumber);

    boolean existsByCustomerId(Long customerId);

    Optional<Account> findByAccountNumber(String accountNumber);

    Optional<Account> findFirstByCustomerIdOrderByIdAsc(Long customerId);

    /**
     * Scalar lookup: returns only the ID, so no Account entity is loaded into the
     * persistence context before the row lock is taken.
     */
    @Query("select a.id from Account a where a.accountNumber = :accountNumber")
    Optional<Long> findIdByAccountNumber(@Param("accountNumber") String accountNumber);

    List<Account> findByCustomerId(Long customerId);

    Page<Account> findByCustomerId(
            Long customerId,
            Pageable pageable
    );

    Page<Account> findByCustomerIdAndStatus(
            Long customerId,
            AccountStatus status,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select a
        from Account a
        where a.id = :id
        """)
    Optional<Account> findByIdForUpdate(
            @Param("id") Long id
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select a
        from Account a
        where a.accountNumber = :accountNumber
        """)
    Optional<Account> findByAccountNumberForUpdate(
            @Param("accountNumber") String accountNumber
    );
}