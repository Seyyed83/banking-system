package com.example.banking.customer.controller;

import com.example.banking.customer.entity.Customer;
import com.example.banking.customer.service.CustomerService;
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
            @RequestBody Customer customer
    ) {
        Customer registeredCustomer = customerService.register(customer);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(registeredCustomer);
    }
}