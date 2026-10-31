package com.example.banking.account.service;

import com.example.banking.account.entity.AccountStatus;

import java.math.BigDecimal;

/** Read model returned by the balance query. */
public record AccountBalanceView(String accountNumber, AccountStatus status, BigDecimal balance) { }
