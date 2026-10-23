
package com.example.banking.customer.service;

import com.example.banking.customer.entity.Customer;
import com.example.banking.customer.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.banking.common.exception.ConflictException;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public Customer register(Customer customer) {
        validateRequiredFields(customer);

        String identityType = customer.getIdentityType().trim();
        String identityNumber = customer.getIdentityNumber().trim();

        if (customerRepository.existsByIdentityTypeAndIdentityNumber(
                identityType,
                identityNumber
        )) {
            throw new ConflictException(
                    "A customer with this identity document already exists."
            );
        }

        customer.setIdentityType(identityType);
        customer.setIdentityNumber(identityNumber);
        customer.setCustomerType(customer.getCustomerType().trim());
        customer.setFullName(customer.getFullName().trim());

        return customerRepository.save(customer);
    }

    private void validateRequiredFields(Customer customer) {
        if (customer == null) {
            throw new IllegalArgumentException(
                    "Customer data must not be null."
            );
        }

        if (isBlank(customer.getCustomerType())) {
            throw new IllegalArgumentException(
                    "Customer type is required."
            );
        }

        if (isBlank(customer.getIdentityType())) {
            throw new IllegalArgumentException(
                    "Identity type is required."
            );
        }

        if (isBlank(customer.getIdentityNumber())) {
            throw new IllegalArgumentException(
                    "Identity number is required."
            );
        }

        if (isBlank(customer.getFullName())) {
            throw new IllegalArgumentException(
                    "Full name is required."
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
