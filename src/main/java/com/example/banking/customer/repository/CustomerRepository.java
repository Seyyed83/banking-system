package com.example.banking.customer.repository;

import com.example.banking.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByIdentityTypeAndIdentityNumber(
            String identityType,
            String identityNumber
    );
}