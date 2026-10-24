# Banking System

A Spring Boot banking backend backed by Oracle Database. The application provides customer accounts, transaction requests, account-to-account transfers, transaction history, and asynchronous transaction processing.

## Technology Stack

- Java
- Spring Boot
- Maven
- Oracle Database
- Flyway database migrations
- Spring Security with HTTP Basic authentication
- Springdoc OpenAPI / Swagger UI
- JUnit and Spring Boot testing support

## Prerequisites

- A compatible JDK installed
- Docker with Docker Compose
- PowerShell on Windows, or an equivalent shell on other platforms

## Configuration

The application reads configuration from environment variables. Copy `.env.example` to `.env` and replace the sample values with your own local credentials.

**Do not commit `.env` or put real passwords in `.env.example`.** The `.env` file is not automatically loaded into the Spring Boot process just because it exists. Make sure the variables are available in the shell or runtime that starts the application.

Important variables:

| Variable | Purpose |
| --- | --- |
| `ORACLE_PASSWORD` | Password used to initialize the Oracle container |
| `DB_URL` | JDBC URL for the application |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `BANKING_API_USERNAME` | HTTP Basic username for protected API endpoints |
| `BANKING_API_PASSWORD` | HTTP Basic password for protected API endpoints |

The default JDBC URL is `jdbc:oracle:thin:@//localhost:1521/FREEPDB1`.

## Start Oracle

From the project root, make sure the environment variables required by `compose.yaml` are set in the shell, then start the database:

```powershell
docker compose up -d oracle
```

Check the container status with:

```powershell
docker compose ps
```

The first startup may take some time while Oracle initializes.

## Run the Application

On Windows, use the Maven wrapper:

```powershell
.\mvnw.cmd spring-boot:run
```

On macOS or Linux:

```bash
./mvnw spring-boot:run
```

Ensure the database and required application environment variables are configured before starting the application.

## API Documentation

When the application is running, Swagger UI is available at:

- `http://localhost:8080/swagger-ui/index.html`

The OpenAPI document is available at:

- `http://localhost:8080/v3/api-docs`

Swagger/OpenAPI routes are configured to be publicly accessible; other application endpoints require HTTP Basic authentication.

## Main Transaction Endpoints

- `POST /api/v1/transactions` — submit a transaction request for asynchronous processing.
- `GET /api/v1/transactions/{requestCode}` — retrieve a transaction request by its request code.
- `GET /api/v1/transactions/history` — retrieve paginated transaction history.

The transaction-history endpoint supports filters such as account number, transaction type, source and destination account numbers, amount range, and date range. Pagination is controlled by `page` and `size`.

## Run Tests

Run the test suite with:

```powershell
.\mvnw.cmd test
```

On macOS or Linux:

```bash
./mvnw test
```

Some integration tests require a reachable Oracle database with the expected schema and configuration.

## Database Migrations

Flyway manages database schema migrations. Migration scripts are located under:

`src/main/resources/db/migration`

## Security Notes

- Use strong, unique credentials for local or deployed environments.
- Do not commit `.env`, database passwords, or API passwords.
- Do not use sample credentials in a shared or production environment.
- HTTP Basic authentication should be used only over HTTPS outside a trusted local development setup.

## Scope

This repository contains the backend service and Oracle database setup. The supplied Docker Compose configuration starts Oracle; it does not build or start the Spring Boot application container.
