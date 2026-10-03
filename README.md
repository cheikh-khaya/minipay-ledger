# minipay-ledger

A small payments ledger REST API built with **Java 21** and **Spring Boot 3**.
It manages single-currency accounts, deposits and **idempotent money transfers**, and records every balance change in an append-only ledger.

The goal of the project is to show, in a code base small enough to read in one sitting, how a payments backend handles the basics correctly: exact money arithmetic, atomic transfers, safe retries and clear error responses.

> Personal learning project. All data is synthetic. It is not production software.

## Features

- Open accounts in a given currency (ISO 4217 code, e.g. `EUR`, `MAD`)
- Deposit funds (simulated external funding)
- Transfer money between two accounts of the same currency
- **Idempotency**: a client can safely retry a transfer with the same `Idempotency-Key`; money moves only once
- **Ledger**: every deposit and transfer writes immutable entries (`DEPOSIT`, `DEBIT`, `CREDIT`)
- Exact money handling with `BigDecimal` and `NUMERIC(19,2)` columns, amounts returned as strings
- Errors returned as RFC 7807 problem details (`400`, `404`, `409`, `422`)
- OpenAPI documentation with Swagger UI
- Unit and API tests run on every push with GitHub Actions

## Tech stack

| Area | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3 (Web, Data JPA, Validation) |
| Database | PostgreSQL (runtime), H2 in PostgreSQL mode (tests) |
| API docs | springdoc-openapi (OpenAPI 3, Swagger UI) |
| Tests | JUnit 5, Spring MockMvc, AssertJ |
| CI | GitHub Actions |

## API

Base path: `/api/v1`

| Method | Path | Description | Success |
|---|---|---|---|
| `POST` | `/accounts` | Open an account `{ownerName, currency}` | `201` |
| `GET` | `/accounts/{id}` | Account details and balance | `200` |
| `POST` | `/accounts/{id}/deposits` | Add funds `{amount}` | `200` |
| `GET` | `/accounts/{id}/entries` | Ledger entries of the account | `200` |
| `POST` | `/transfers` | Transfer `{fromAccountId, toAccountId, amount}`, header `Idempotency-Key` required | `201` new, `200` replay |
| `GET` | `/transfers/{id}` | Transfer details | `200` |

Interactive documentation: `http://localhost:8080/swagger-ui.html` (OpenAPI JSON at `/v3/api-docs`).

### Error responses

| Status | When |
|---|---|
| `400` | Invalid body (amount ≤ 0, more than 2 decimals, bad currency code) or missing `Idempotency-Key` |
| `404` | Unknown account or transfer |
| `409` | `Idempotency-Key` reused with a different payload, or a concurrent update of the same account |
| `422` | Insufficient funds, currency mismatch, transfer to the same account |

## Data model

```
accounts          id, owner_name, currency, balance NUMERIC(19,2) >= 0, version, created_at
transfers         id, idempotency_key UNIQUE, from_account_id, to_account_id, amount > 0, currency, created_at
ledger_entries    id, account_id, transfer_id (null for deposits), type, amount > 0, created_at
```

The schema lives in [`src/main/resources/schema.sql`](src/main/resources/schema.sql) and is applied at startup.

## Design decisions

**Money as `BigDecimal`, never `double`.** Floating point cannot represent `0.10` exactly (`0.1 + 0.2 = 0.30000000000000004`). Amounts are validated to at most 2 decimals, stored as `NUMERIC(19,2)` and serialized as strings so clients do not parse them as floats.

**One database transaction per transfer.** Debiting the source, crediting the destination, saving the transfer and writing both ledger entries happen in a single `@Transactional` method: either everything is saved or nothing is. Rules are checked before anything is written.

**Idempotency.** Networks fail; a client that does not receive a response will retry. The client sends a unique `Idempotency-Key` header (for example a UUID):
- first request: the transfer is executed and stored with its key → `201 Created`;
- retry with the same key and the same payload: the stored transfer is returned, no money moves → `200 OK` with `Idempotent-Replayed: true`;
- same key with a different payload: rejected → `409 Conflict`.

The key column is `UNIQUE`, so even two simultaneous requests with the same key cannot create two transfers: the second one fails on the constraint and is rolled back.

**Concurrency with optimistic locking.** `Account` has a `@Version` column. If two transactions update the same account at the same time, the second commit fails instead of overwriting the first, and the API returns `409` so the client can retry with the same key. This keeps the code simple; a high-contention system would rather lock rows (`SELECT ... FOR UPDATE`) in a fixed order.

**Append-only ledger.** Balances are stored on the account for fast reads, and every change is also written as a ledger entry that is never updated or deleted, so the history can be audited.

## Run locally

Requirements: JDK 21, Maven 3.9+, and a PostgreSQL database.

```bash
# 1. Start PostgreSQL (optional helper; any local PostgreSQL works)
docker compose up -d

# 2. Run the API (defaults: jdbc:postgresql://localhost:5432/minipay, user/password minipay)
mvn spring-boot:run
```

Override the connection with `DB_URL`, `DB_USER` and `DB_PASSWORD`.

### Try it

```bash
# Open two accounts
curl -s -X POST localhost:8080/api/v1/accounts -H 'Content-Type: application/json' \
  -d '{"ownerName":"Alice","currency":"EUR"}'
curl -s -X POST localhost:8080/api/v1/accounts -H 'Content-Type: application/json' \
  -d '{"ownerName":"Bob","currency":"EUR"}'

# Fund Alice
curl -s -X POST localhost:8080/api/v1/accounts/<ALICE_ID>/deposits -H 'Content-Type: application/json' \
  -d '{"amount":100.00}'

# Transfer 30.25 EUR; run it twice with the same key: money moves only once
curl -s -i -X POST localhost:8080/api/v1/transfers -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 7f9c2b1e-demo-0001' \
  -d '{"fromAccountId":"<ALICE_ID>","toAccountId":"<BOB_ID>","amount":30.25}'
```

## Tests

```bash
mvn verify
```

- `AccountTest`: unit tests of the money rules (exact decimals, no negative balance, invalid amounts).
- `TransferApiTest`: HTTP tests through the full stack on an in-memory H2 database using the same `schema.sql`: successful transfer and ledger entries, idempotent replay, key reuse conflict, insufficient funds, currency mismatch, validation errors, 404, OpenAPI document.

## Limitations and next steps

- Deposits are not idempotent (they only simulate external funding).
- No authentication; an API like this would sit behind an authenticated gateway.
- No pagination on ledger entries.
- Possible next steps: database migrations with Flyway, tests against a real PostgreSQL with Testcontainers, a concurrency test with parallel transfers, an Angular dashboard.

## License

MIT
