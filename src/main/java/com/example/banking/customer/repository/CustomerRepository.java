package com.example.banking.customer.repository;

import com.example.banking.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByIdentityTypeAndIdentityNumber(
            String identityType,
            String identityNumber
    );

    Optional<Customer> findByIdentityTypeAndIdentityNumber(
            String identityType,
            String identityNumber
    );

    Optional<Customer> findFirstByIdentityNumberOrderByIdAsc(
            String identityNumber
    );
}