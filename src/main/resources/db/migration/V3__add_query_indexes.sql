-- V3: indexes for transaction history, ledger lookups and identity lookups (task section 4-1).
-- V1 and V2 are intentionally left untouched.

-- Oracle does not index foreign keys automatically; the history query and the per-page
-- ledger lookup (findByFinancialTransactionIdIn) both filter LEDGER_ENTRIES by FINANCIAL_TRANSACTION_ID.
CREATE INDEX IDX_LEDGER_FTX
    ON LEDGER_ENTRIES (FINANCIAL_TRANSACTION_ID);

-- "transactions of account X" and "incoming (CREDIT) / outgoing (DEBIT) of account X" EXISTS filters.
CREATE INDEX IDX_LEDGER_ACCT_TYPE_FTX
    ON LEDGER_ENTRIES (ACCOUNT_ID, ENTRY_TYPE, FINANCIAL_TRANSACTION_ID);

-- Newest-first paging and date-range filters over FINANCIAL_TRANSACTIONS.
CREATE INDEX IDX_FINANCIAL_CREATED
    ON FINANCIAL_TRANSACTIONS (CREATED_AT, ID);

-- Lookup of a customer by identity number alone (GET /accounts/by-identity without identityType).
CREATE INDEX IDX_CUSTOMER_IDENTITY_NUMBER
    ON CUSTOMERS (IDENTITY_NUMBER);
