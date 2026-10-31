# Banking System

A Spring Boot financial transaction management backend backed by Oracle Database. The system provides unified customer registration and account creation, customer profile & account status management with full audit history, balance and customer inquiries, asynchronous & atomic transaction processing (deposit, withdrawal, and account-to-account transfer with configurable transfer fees), and dynamic paginated transaction history.

## Technology Stack

- **Java 17**
- **Spring Boot 4.1.1** (Spring Web MVC, Spring Data JPA, Spring Validation, Spring Security)
- **Oracle Database** (`gvenzl/oracle-free:23.9-slim-faststart`)
- **Flyway** database schema migrations (`V1` to `V5`)
- **Springdoc OpenAPI / Swagger UI** (`3.1.1`)
- **JUnit 5 & Mockito** (Unit tests + real Oracle concurrency & integration tests)

## Prerequisites

- **JDK 17+** (compatible with JDK 17, 21, and newer)
- **Docker & Docker Compose**

## Configuration & Environment Variables

Copy `.env.example` to `.env` and set your local passwords before starting the services.

| Variable | Default / Example | Purpose |
| --- | --- | --- |
| `ORACLE_PASSWORD` | *(required)* | Oracle container sys/system initialization password |
| `DB_URL` | `jdbc:oracle:thin:@//localhost:1521/FREEPDB1` | JDBC URL for local execution (`@//oracle:1521/FREEPDB1` in Docker) |
| `DB_USERNAME` | `banking_app` | Oracle schema username |
| `DB_PASSWORD` | *(required)* | Oracle schema password |
| `BANKING_API_USERNAME` | `banking-admin` | HTTP Basic Auth username |
| `BANKING_API_PASSWORD` | *(required)* | HTTP Basic Auth password |
| `BANKING_SYSTEM_ACCOUNT_NUMBER` | `99999999999999` | 14-digit system bank account that receives transfer fees |
| `BANKING_TRANSFER_FEE_RATE` | `0.001` (0.1%) | Transfer fee percentage (`x`) |
| `BANKING_TRANSFER_FEE_MINIMUM` | `500` | Minimum transfer fee floor (`y`) |
| `BANKING_TRANSFER_FEE_MAXIMUM` | `10000` | Maximum transfer fee ceiling (`z`) |
| `BANKING_WORKER_POLL_INTERVAL_MS` | `500` | Worker poll delay in milliseconds |
| `BANKING_WORKER_MAX_BATCH_SIZE` | `50` | Maximum requests processed per worker poll cycle |
| `BANKING_WORKER_ENABLED` | `true` | Set to `false` to run an API-only instance that does not process the queue |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis used for the balance cache |
| `BANKING_CACHE_ENABLED` | `true` | Set to `false` to disable the Redis balance cache |
| `BANKING_CACHE_BALANCE_TTL_SECONDS` | `30` | Balance cache entry lifetime |

## Running the System

### Option 1: Run Everything via Docker Compose (Oracle + App)

1. Create `.env` from `.env.example` and set strong passwords for `ORACLE_PASSWORD`, `DB_PASSWORD`, and `BANKING_API_PASSWORD`.
2. Start both Oracle and the Spring Boot application:

```bash
docker compose up -d --build
```

### Option 2: Run Oracle in Docker and Spring Boot Locally (Maven / IDE)

#### In PowerShell (Windows):

```powershell
$env:ORACLE_PASSWORD="YourOraclePassword123"
$env:DB_PASSWORD="YourDbPassword123"
$env:BANKING_API_USERNAME="banking-admin"
$env:BANKING_API_PASSWORD="YourApiPassword123"

docker compose up -d oracle
.\mvnw.cmd spring-boot:run
```

#### In Bash (Linux / macOS):

```bash
export ORACLE_PASSWORD="YourOraclePassword123"
export DB_PASSWORD="YourDbPassword123"
export BANKING_API_USERNAME="banking-admin"
export BANKING_API_PASSWORD="YourApiPassword123"

docker compose up -d oracle
./mvnw spring-boot:run
```

## Swagger UI & API Authentication

- **Swagger UI:** `http://localhost:8080/swagger-ui/index.html`
- **OpenAPI JSON:** `http://localhost:8080/v3/api-docs`

Swagger UI is publicly accessible for convenient testing; all `/api/v1/**` endpoints require **HTTP Basic Authentication** using `BANKING_API_USERNAME` and `BANKING_API_PASSWORD`.

## REST API Endpoints Summary

### 1. Customer & Account Creation (Section 2-1)
- `POST /api/v1/accounts` — **Unified Operation:** Validates all 7 mandatory customer fields (`customerType`, `identityType`, `identityNumber`, `fullName`, `birthOrRegDate`, `mobile`, `address`, `postalCode`), registers the customer, and creates a unique 14-digit `ACTIVE` account in one atomic transaction. Enforces **one account per customer** (returns `409 Conflict` if the customer already exists or has an account).
  - Validates 10-digit Iranian national code with check-digit for `NATIONAL_ID`, 11-digit legal ID for `LEGAL_ID`, and 5–50 alphanumeric characters for `FOREIGN_ID` / `PASSPORT`.
- `POST /api/v1/customers` & `POST /api/v1/customers/{customerId}/accounts` — Retained for modular compatibility, enforcing the same validation and single-account rules.

### 2. Updating Account & Customer Information (Section 2-2)
- `PUT /api/v1/accounts/{id}/details` — Updates customer registration fields associated with the account (account number and creation date are immutable). Records field-level change history in `ACCOUNT_AUDIT` using the authenticated username (`Authentication.getName()`).
- `PATCH /api/v1/accounts/{id}/status` — Changes account status (`ACTIVE`, `INACTIVE`, `BLOCKED`) and records the change in `ACCOUNT_AUDIT` with the authenticated username.
- `GET /api/v1/accounts/{id}/audit` — Returns the audit trail of changes for an account.

### 3. Retrieving Account Details & Balance (Sections 2-3 & 2-4)
- `GET /api/v1/accounts/number/{accountNumber}` (and `/api/v1/accounts/number/{accountNumber}/details`) — Returns complete customer registration details, account status, current balance, creation date, and last updated timestamp.
- `GET /api/v1/accounts/by-identity?identityNumber={id}&identityType={type}` — Looks up a customer's 14-digit account number and full account details by National ID / Legal ID / Foreign ID.
- `GET /api/v1/accounts/number/{accountNumber}/balance` — Returns the account balance and status by 14-digit account number.
- `GET /api/v1/accounts/{id}` & `GET /api/v1/accounts/{id}/details` — Retrieves account summary or full customer+account details by internal ID.

### 4. Financial Transactions & Status Inquiry (Sections 2-5, 2-6 & 2-8)
- `POST /api/v1/transactions` — Submits a `DEPOSIT`, `WITHDRAWAL`, or `TRANSFER` request and returns `202 Accepted` (new request) or `200 OK` (idempotent replay) with a tracking code (`requestCode`) and a `Location` header.
  - Validates upfront that referenced accounts exist, are distinct, and are currently `ACTIVE`.
  - Requires `clientId` and `idempotencyKey` to guarantee **idempotency** so network retries never duplicate financial operations.
- `GET /api/v1/transactions/{requestCode}` — Queries the status of a transaction request (`PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`).
  - When `FAILED`, returns both `failureCode` (e.g., `INSUFFICIENT_FUNDS`) and a human-readable `failureMessage`.
  - When `COMPLETED`, returns the list of executed `financialTransactions` (`TRACKING_CODE`, `TRANSACTION_TYPE`, `AMOUNT`, `CREATED_AT`).

### 5. Paginated & Dynamic Transaction History (Section 2-7)
- `GET /api/v1/transactions/history` — Supports dynamic filtering by `accountNumber`, `type` (`DEPOSIT`, `WITHDRAWAL`, `TRANSFER`, `FEE`), `sourceAccountNumber`, `destinationAccountNumber`, `minAmount`, `maxAmount`, `from` (ISO-8601), `to` (ISO-8601), and pagination (`page`, `size`).

## Architectural & Design Highlights

1. **Atomic Execution & Pessimistic Locking (Consistency):**
   - During execution in `TransactionExecutionService`, all accounts involved in a transaction (including source, destination, and the bank fee system account) are locked in **ascending Account ID order** using `SELECT ... FOR UPDATE` (`PESSIMISTIC_WRITE`) to prevent deadlocks and lost updates across concurrent instances.
   - Balance deduction (`amount + fee`), destination credit, bank fee credit, `FINANCIAL_TRANSACTIONS` records, and double-entry `LEDGER_ENTRIES` (`DEBIT` / `CREDIT`) are committed within a **single atomic database transaction**.
2. **Transfer Fee Representation (`FEE` Transaction Type):**
   - For every `TRANSFER`, the transfer fee (`clamp(amount * rate, minimum, maximum)`) is recorded as a linked child `FINANCIAL_TRANSACTIONS` record with `TRANSACTION_TYPE = 'FEE'` (linked via `PARENT_TRANSACTION_ID`), debiting the customer's source account and crediting the configured bank system account (`99999999999999`).
   - Note: When querying `/api/v1/transactions/history`, omitting `type` returns all movements including `FEE`; filtering specifically by `type=FEE` isolates transfer fee deductions, while `type=WITHDRAWAL` filters direct cash withdrawals.
3. **Scalable Multi-Instance Worker Processing:**
   - `TransactionWorker` processes up to `banking.worker.max-batch-size` (default `50`) requests per poll cycle and uses atomic lease-based claiming (`STATUS = 'PROCESSING'`, `WORKER_ID`, `LEASE_UNTIL`) with exponential backoff retry for transient errors and automatic lease recovery if a worker instance crashes.
4. **Event Logging & Masking:**
   - Key lifecycle events (customer registration, account creation, profile/status updates, transaction submission, worker claiming, completion, and business/transient failures) are logged at `INFO`/`WARN` levels with masked account and identity numbers (`LogMasker`), while internal stack traces are never exposed in HTTP responses (`GlobalExceptionHandler`).

## Running Tests

### Fast Unit Tests (No Database Required):

```bash
./mvnw test
```

### Full Oracle Integration & Concurrency Tests:

With the Oracle container running and environment variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) set:

```bash
./mvnw test -Doracle.integration=true
```

## Redis balance cache

`GET /api/v1/accounts/number/{accountNumber}/balance` is served cache-aside from Redis (`banking:balance:<accountNumber>`, value `STATUS|balance`, TTL 30s by default). Oracle remains the only source of truth: money movements are still committed atomically in Oracle, and cached entries are evicted **after** the Oracle commit (and after account status changes). If Redis is down or slow (500 ms timeout) the endpoint transparently reads Oracle. `docker compose up` starts a `redis` service automatically.

## Transactional outbox

When a transaction request reaches a final state, a row is written to `OUTBOX_EVENTS` **in the same Oracle transaction**: `TRANSACTION_COMPLETED` (together with the money movement) or `TRANSACTION_FAILED` (business failure, retries exhausted, or lease-expired at the attempt limit). The row therefore exists if and only if the outcome committed. Payloads are JSON with identifiers and amounts only (no names or account numbers). Rows start as `PENDING`; `OutboxKafkaPublisher` drains them into Kafka (see below).

## Kafka event publishing

`OutboxKafkaPublisher` polls `OUTBOX_EVENTS`, atomically claims one due row at a time (lease + fencing token, same pattern as the transaction worker), sends it to topic `banking.transaction-events` (key = `requestCode`, headers `eventId` / `eventType`, `acks=all`, idempotent producer) and marks it `PUBLISHED` only after the broker acknowledged it. Failures are retried with exponential backoff (max 10 attempts, then `FAILED`). Delivery is **at-least-once**; Kafka being down never affects money movement - events simply wait in Oracle.

`TransactionEventConsumer` (group `banking-audit`) stores every event in `TRANSACTION_EVENT_LOG` under `UNIQUE(EVENT_ID)` so duplicate deliveries are ignored, and logs a notification line for new events. Settings: `KAFKA_BOOTSTRAP_SERVERS`, `BANKING_KAFKA_ENABLED`, `BANKING_KAFKA_CONSUMER_ENABLED`, `BANKING_KAFKA_TOPIC`. Maven tests run with Kafka switched off. `docker compose up` starts a single-node KRaft broker.
