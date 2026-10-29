# Life and Money API

[![CI](https://github.com/akiratochiro/life-and-money/actions/workflows/ci.yml/badge.svg)](https://github.com/akiratochiro/life-and-money/actions/workflows/ci.yml)

A personal finance REST API built with Java and Spring Boot: custom categories, transactions,
monthly budgets with effective dating, dashboards and savings goals.

Built as a real tool for my own finances and as a portfolio project focused on clean
architecture, explicit design decisions and automated tests.

## Features

- **Authentication** with a short-lived JWT access token plus a rotating refresh token in an
  `HttpOnly` cookie, BCrypt password hashing and per-user data isolation
- **Categories** (income, expense, saving), archived instead of deleted to preserve history
- **Transactions** with exact decimal arithmetic (`BigDecimal` / `NUMERIC`)
- **Monthly budgets** by fixed amount or percentage of realized income, with **effective dating**:
  changing a budget never rewrites past months
- **Dashboards**: expenses by category, monthly totals with averages over closed months only,
  and budget tracking with `OK` / `WARNING` / `EXCEEDED` status
- **Savings goals** linked to saving transactions, with automatic monthly contribution calculation
- **Archiving a category ends its budget**, through a domain event, so no dead line is left behind

## Tech stack

Java 25 · Spring Boot 4.1 · Spring Security (OAuth2 Resource Server, JWT) · Spring Data JPA / Hibernate ·
PostgreSQL 16 · Flyway · Bean Validation · springdoc-openapi ·
JUnit 5 · Mockito · AssertJ · H2 (test fallback) · Maven

## Architecture highlights

- **Package by feature** (`category`, `transaction`, `budget`, `goal`, `dashboard`...), with
  package-private repositories so that each feature's rules can only be reached through its service
- **Schema owned by Flyway**; Hibernate only validates (`ddl-auto: validate`)
- **Every query is scoped by the authenticated user**; resources from other users return `404`
- **No circular dependencies** between packages, including a dependency inversion between
  transactions and savings goals
- Errors follow **RFC 9457** (`ProblemDetail`)
- **Same origin as the front**: the API is served under `/api`, and the front's Cloudflare Worker
  proxies `/api/*` to it, so the refresh cookie is first-party (`SameSite=Strict`) and no CORS is
  needed

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

The API runs at `http://localhost:8080/api`.

## API documentation

Interactive documentation (Swagger UI): `http://localhost:8080/api/swagger-ui/index.html`

1. Call `POST /auth/register`, then `POST /auth/login`
2. Copy the `accessToken`, click **Authorize** and paste it
3. All protected endpoints can now be tried from the browser

The access token lasts 15 minutes. When it expires, either log in again or call
`POST /auth/refresh` with the header `X-Refresh-Request: 1` — the refresh token itself travels
in an `HttpOnly` cookie that the browser sends on its own.

Every path below is relative to `/api` (e.g. `POST /api/auth/login`).

| Area | Endpoints |
|---|---|
| Auth | `POST /auth/register` · `POST /auth/login` · `POST /auth/refresh` · `POST /auth/logout` |
| User | `GET /users/me` |
| Categories | `POST /categories` · `GET /categories` · `PATCH /categories/{id}` · `POST /categories/{id}/archive` |
| Transactions | `POST /transactions` · `GET /transactions?month=YYYY-MM` · `PUT /transactions/{id}` · `DELETE /transactions/{id}` |
| Budgets | `GET /budgets?month=YYYY-MM` · `PUT /budgets/{categoryId}` · `DELETE /budgets/{categoryId}` |
| Goals | `POST /goals` · `GET /goals` · `PUT /goals/{id}` · `DELETE /goals/{id}` |
| Dashboard | `GET /dashboard/expenses-by-category?month=` · `GET /dashboard/monthly-totals?months=` · `GET /dashboard/budget?month=` |
| Health | `GET /health` |

## Running locally for development

Requirements: JDK 25 and Docker for the local Postgres profile.

1. Create the `.env` file as described above
2. Run `LifeAndMoneyApiApplication` with the environment variable `SPRING_PROFILES_ACTIVE=dev`

Spring Boot starts PostgreSQL automatically through Docker Compose, and the `dev` profile
reads `JWT_SECRET` from `.env` and logs SQL statements.

## Tests

```bash
./mvnw test
```

Unit tests cover domain rules (budget effective dating, goal progress, rounding), service tests use
Mockito, and integration tests run against an H2 database in PostgreSQL compatibility mode so the
suite stays portable without a Docker daemon. The error contract is tested too: every rule that the
user can break has an expected status and message.

The same suite runs on GitHub Actions on every push and pull request
([ci.yml](.github/workflows/ci.yml)).

## Deployment

The API ships as a Docker image and is described by [`render.yaml`](render.yaml), so the deploy
is reproducible instead of a sequence of clicks. Step by step, including which free tiers were
chosen and why, in [docs/deploy.md](docs/deploy.md).

## Roadmap

- [x] Web front-end ([life-and-money-web](https://github.com/akiratochiro/life-and-money-web))
- [x] Continuous integration with GitHub Actions
- [x] Refresh tokens in an `HttpOnly` cookie
- [ ] Withdrawals from savings goals
- [ ] Error responses documented in the OpenAPI spec, so the front can type `ProblemDetail`
      from the generated schema instead of declaring it by hand