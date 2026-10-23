# Life and Money API

[![CI](https://github.com/akiratochiro/life-and-money/actions/workflows/ci.yml/badge.svg)](https://github.com/akiratochiro/life-and-money/actions/workflows/ci.yml)

A personal finance REST API built with Java and Spring Boot: custom categories, transactions,
monthly budgets with effective dating, dashboards and savings goals.

Built as a real tool for my own finances and as a portfolio project focused on clean
architecture, explicit design decisions and automated tests.

## Features

- **Authentication** with JWT (stateless), BCrypt password hashing and per-user data isolation
- **Categories** (income, expense, saving), archived instead of deleted to preserve history
- **Transactions** with exact decimal arithmetic (`BigDecimal` / `NUMERIC`)
- **Monthly budgets** by fixed amount or percentage of realized income, with **effective dating**:
  changing a budget never rewrites past months
- **Dashboards**: expenses by category, monthly totals with averages over closed months only,
  and budget tracking with `OK` / `WARNING` / `EXCEEDED` status
- **Savings goals** linked to saving transactions, with automatic monthly contribution calculation

## Tech stack

Java 21 · Spring Boot 4.1 · Spring Security (OAuth2 Resource Server, JWT) · Spring Data JPA / Hibernate ·
PostgreSQL 16 · Flyway · Bean Validation · springdoc-openapi ·
JUnit 5 · Mockito · AssertJ · Testcontainers · Docker / Docker Compose · Maven

## Architecture highlights

- **Package by feature** (`category`, `transaction`, `budget`, `goal`, `dashboard`...), with
  package-private repositories so that each feature's rules can only be reached through its service
- **Schema owned by Flyway**; Hibernate only validates (`ddl-auto: validate`)
- **Every query is scoped by the authenticated user**; resources from other users return `404`
- **No circular dependencies** between packages, including a dependency inversion between
  transactions and savings goals
- Errors follow **RFC 9457** (`ProblemDetail`)

The reasoning behind the main decisions is documented in [docs/decisions.md](docs/decisions.md).

## Running with Docker

Requirements: Docker.

```bash
git clone https://github.com/akiratochiro/life-and-money.git
cd life-and-money
cp .env.example .env              # then set JWT_SECRET (see below)
docker compose --profile app up --build
```

Generate a secret for `JWT_SECRET` with:

```bash
openssl rand -base64 32
```

The API runs at `http://localhost:8080`.

## API documentation

Interactive documentation (Swagger UI): `http://localhost:8080/swagger-ui/index.html`

1. Call `POST /auth/register`, then `POST /auth/login`
2. Copy the `accessToken`, click **Authorize** and paste it
3. All protected endpoints can now be tried from the browser

| Area | Endpoints |
|---|---|
| Auth | `POST /auth/register` · `POST /auth/login` |
| User | `GET /users/me` |
| Categories | `POST /categories` · `GET /categories` · `PATCH /categories/{id}` · `POST /categories/{id}/archive` |
| Transactions | `POST /transactions` · `GET /transactions?month=YYYY-MM` · `PUT /transactions/{id}` · `DELETE /transactions/{id}` |
| Budgets | `GET /budgets?month=YYYY-MM` · `PUT /budgets/{categoryId}` · `DELETE /budgets/{categoryId}` |
| Goals | `POST /goals` · `GET /goals` · `PUT /goals/{id}` · `DELETE /goals/{id}` |
| Dashboard | `GET /dashboard/expenses-by-category?month=` · `GET /dashboard/monthly-totals?months=` · `GET /dashboard/budget?month=` |

## Running locally for development

Requirements: JDK 21 and Docker.

1. Create the `.env` file as described above
2. Run `LifeAndMoneyApiApplication` with the environment variable `SPRING_PROFILES_ACTIVE=dev`

Spring Boot starts PostgreSQL automatically through Docker Compose, and the `dev` profile
reads `JWT_SECRET` from `.env` and logs SQL statements.

## Tests

```bash
./mvnw test
```

Unit tests cover domain rules (budget effective dating, goal progress, rounding), service tests use
Mockito, and integration tests run against a real PostgreSQL container via Testcontainers
(Docker must be running). The error contract is tested too: every rule that the user can break
has an expected status and message.

The same suite runs on GitHub Actions on every push and pull request
([ci.yml](.github/workflows/ci.yml)).

## Roadmap

- [x] Web front-end ([life-and-money-web](https://github.com/akiratochiro/life-and-money-web))
- [x] Continuous integration with GitHub Actions
- [ ] Withdrawals from savings goals