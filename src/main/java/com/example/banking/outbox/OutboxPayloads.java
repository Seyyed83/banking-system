package com.example.banking.outbox;

import com.example.banking.transaction.entity.FinancialTransaction;
import com.example.banking.transaction.entity.TransactionRequest;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Builds the JSON event bodies by hand (small, fixed shape) so the outbox does not depend on a
 * particular Jackson version. Contains identifiers and amounts only - no names or account numbers.
 */
final class OutboxPayloads {

    private OutboxPayloads() {
    }

    static String completed(
            String eventId,
            OffsetDateTime occurredAt,
            TransactionRequest request,
            List<FinancialTransaction> transactions) {

        StringBuilder json = header(eventId, OutboxEventService.TRANSACTION_COMPLETED, occurredAt, request);
        json.append(",\"failureCode\":null,\"financialTransactions\":[");
        for (int i = 0; i < transactions.size(); i++) {
            FinancialTransaction transaction = transactions.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append('{')
                    .append("\"trackingCode\":").append(quote(transaction.getTrackingCode()))
                    .append(",\"type\":").append(quote(transaction.getTransactionType().name()))
                    .append(",\"amount\":").append(quote(transaction.getAmount().toPlainString()))
                    .append('}');
        }
        return json.append("]}").toString();
    }

    static String failed(
            String eventId,
            OffsetDateTime occurredAt,
            TransactionRequest request) {

        StringBuilder json = header(eventId, OutboxEventService.TRANSACTION_FAILED, occurredAt, request);
        json.append(",\"failureCode\":").append(quote(request.getFailureCode()))
                .append(",\"financialTransactions\":[]}");
        return json.toString();
    }

    private static StringBuilder header(
            String eventId,
            String eventType,
            OffsetDateTime occurredAt,
            TransactionRequest request) {

        return new StringBuilder(256)
                .append("{\"eventId\":").append(quote(eventId))
                .append(",\"eventType\":").append(quote(eventType))
                .append(",\"occurredAt\":").append(quote(occurredAt.toString()))
                .append(",\"requestCode\":").append(quote(request.getRequestCode()))
                .append(",\"transactionType\":").append(quote(request.getTransactionType().name()))
                .append(",\"sourceAccountId\":").append(number(request.getSourceAccountId()))
                .append(",\"destinationAccountId\":").append(number(request.getDestinationAccountId()))
                .append(",\"amount\":").append(quote(request.getAmount().toPlainString()))
                .append(",\"requestStatus\":").append(quote(request.getStatus().name()));
    }

    private static String number(Long value) {
        return value == null ? "null" : value.toString();
    }

    static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
