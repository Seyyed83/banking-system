package com.example.banking.customer.service;

import com.example.banking.account.dto.CreateCustomerAccountRequest;
import com.example.banking.common.exception.ConflictException;
import com.example.banking.common.logging.LogMasker;
import com.example.banking.customer.entity.Customer;
import com.example.banking.customer.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class CustomerService {

    private static final Logger log =
            LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public Customer register(Customer customer) {
        validateRequiredFields(customer);
        CustomerRules.validate(
                customer.getCustomerType(),
                customer.getIdentityType(),
                customer.getIdentityNumber(),
                customer.getBirthOrRegDate(),
                customer.getMobile(),
                customer.getPostalCode()
        );
        String customerType = customer.getCustomerType().trim().toUpperCase(Locale.ROOT);
        String identityType = customer.getIdentityType().trim().toUpperCase(Locale.ROOT);
        String identityNumber = customer.getIdentityNumber().trim();

        if (customerRepository.existsByIdentityTypeAndIdentityNumber(
                identityType,
                identityNumber
        )) {
            log.warn("Rejected duplicate customer registration for identityType={}, identity={}",
                    identityType, LogMasker.maskIdentity(identityNumber));
            throw new ConflictException(
                    "A customer with this identity document already exists."
            );
        }

        customer.setCustomerType(customerType);
        customer.setIdentityType(identityType);
        customer.setIdentityNumber(identityNumber);
        customer.setFullName(customer.getFullName().trim());
        customer.setMobile(customer.getMobile().trim());
        customer.setAddress(customer.getAddress().trim());
        customer.setPostalCode(customer.getPostalCode().trim());

        Customer saved = customerRepository.saveAndFlush(customer);
        log.info("Registered new customer customerId={}, customerType={}, identityType={}, identity={}",
                saved.getId(),
                saved.getCustomerType(),
                saved.getIdentityType(),
                LogMasker.maskIdentity(saved.getIdentityNumber()));
        return saved;
    }

    @Transactional
    public Customer register(CreateCustomerAccountRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Customer registration request is required.");
        }
        Customer customer = new Customer(
                request.customerType(),
                request.identityType(),
                request.identityNumber(),
                request.fullName(),
                request.birthOrRegDate(),
                request.mobile(),
                request.address(),
                request.postalCode()
        );
        return register(customer);
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

        if (customer.getBirthOrRegDate() == null) {
            throw new IllegalArgumentException(
                    "Birth or registration date is required."
            );
        }

        if (isBlank(customer.getMobile())) {
            throw new IllegalArgumentException(
                    "Mobile is required."
            );
        }

        if (isBlank(customer.getAddress())) {
            throw new IllegalArgumentException(
                    "Address is required."
            );
        }

        if (isBlank(customer.getPostalCode())) {
            throw new IllegalArgumentException(
                    "Postal code is required."
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
