package com.example.banking.customer.controller;

import com.example.banking.account.dto.CreateCustomerAccountRequest;
import com.example.banking.customer.entity.Customer;
import com.example.banking.customer.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<Customer> registerCustomer(
            @Valid @RequestBody CreateCustomerAccountRequest request
    ) {
        Customer registeredCustomer = customerService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(registeredCustomer);
    }
}