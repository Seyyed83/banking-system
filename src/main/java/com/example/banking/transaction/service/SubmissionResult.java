package com.example.banking.transaction.service;

import com.example.banking.transaction.entity.TransactionRequest;

public record SubmissionResult(TransactionRequest request, boolean created) {
}
