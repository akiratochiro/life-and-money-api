# CLAUDE.md — Life and Money API

API REST de finanças pessoais: categorias, transações, orçamento mensal com vigência, dashboards
e metas de poupança. O front fica em `../life-and-money-web`.

Leia [README.md](README.md) para a visão geral e [docs/decisions.md](docs/decisions.md) para o
porquê das escolhas antes de mudar algo estrutural. O deploy está em [docs/deploy.md](docs/deploy.md).

## Stack

Java 25 · Spring Boot 4.1 · Spring Security (OAuth2 Resource Server, JWT) · Spring Data JPA /
Hibernate · PostgreSQL 16 · Flyway · Bean Validation · springdoc-openapi · JUnit 5 · Mockito ·
AssertJ · Testcontainers · Maven.

Deploy: API em Docker no **Render** (free), banco no **Neon** (free), front no **Cloudflare Workers**.

## Comandos

```bash
./mvnw test                      # unitários + integração (Docker precisa estar rodando; JDK 25)
docker compose --profile app up --build   # API + Postgres em containers
```

Para desenvolver, rode `LifeAndMoneyApiApplication` com `SPRING_PROFILES_ACTIVE=dev`: o Spring
sobe o Postgres pelo Docker Compose e lê `JWT_SECRET` do `.env`.

Tudo responde sob `/api` (`server.servlet.context-path`): OpenAPI em
`http://localhost:8080/api/v3/api-docs` · Swagger UI em `/api/swagger-ui/index.html` · health em
`/api/health`. Os controllers e os testes com MockMvc usam os caminhos sem o prefixo.

## Arquitetura

- **Package by feature** em `com.akiratochiro.life_and_money_api`: `auth`, `user`, `category`,
  `transaction`, `budget`, `goal`, `dashboard`, com `config`, `web` e `shared` para o que é
  transversal. Não crie pacotes por camada (`controllers/`, `services/`...).
- **Repositórios são package-private.** Outra feature só alcança os dados através do service
  público da feature dona. Nunca torne um repositório `public` para "facilitar".
- **Entidades referenciam outras features por id** (`Long userId`, `Long categoryId`), não por
  `@ManyToOne`. A integridade fica nas foreign keys do banco (decisão 1).
- **Sem dependência circular entre pacotes.** Quando duas features precisam conversar nos dois
  sentidos, use evento de domínio (ex.: `CategoryArchivedEvent`, `UserRegisteredEvent`) ou
  inversão de dependência, como entre `transaction` e `goal` (decisão 3).
- **Flyway é o dono do schema.** Toda mudança de banco é uma nova migration em
  `src/main/resources/db/migration` (`V7__...sql`, `V8__...`). Hibernate só valida
  (`ddl-auto: validate`). Nunca edite uma migration já commitada.
- **Toda query é escopada pelo usuário autenticado.** Recurso de outro usuário responde `404`
  (não `403`), para não revelar que existe.
- **Erros em RFC 9457** (`ProblemDetail`), com o `detail` em português e escrito para o usuário
  final: o front mostra esse texto como está.
- **Dinheiro em `BigDecimal` / `NUMERIC`**, nunca `double`.
- **Orçamento com vigência:** mudar um orçamento fecha o item atual e abre outro; meses passados
  nunca são reescritos (decisão 2).
- **Sessão:** access token JWT de 15 min no corpo da resposta; refresh token opaco em cookie
  `HttpOnly`, `SameSite=Strict`, com path `/api/auth`, salvo como hash SHA-256 e rotacionado a
  cada uso. `POST /auth/refresh` exige o header `X-Refresh-Request`, segunda barreira contra
  CSRF (decisões 4 e 5).
- **E-mail atrás de `EmailSender`** (pacote `email`): `brevo` em produção, `log` no dev (só loga o
  link). `app.email.provider` não tem padrão, de propósito. Nos testes de integração, o
  `RecordingEmailSender` guarda as mensagens e o `MutableClock` controla o tempo.
- **Tokens opacos** (refresh e redefinição de senha) usam `OpaqueTokens`: 32 bytes do
  `SecureRandom` e só o SHA-256 no banco. Queries `@Modifying` usam `flushAutomatically = true`
  junto com `clearAutomatically`, senão alterações pendentes da transação são descartadas.
- **Nada que dependa de a conta existir pode mudar a resposta**, nem no corpo nem no tempo: o
  `forgot-password` faz todo o trabalho fora da requisição (`PasswordResetQueue`), e o limite de
  1 pedido por minuto por conta descarta o pedido em silêncio, sem `429`.
- **Front e API na mesma origem, sem CORS.** O Worker do front faz proxy de `/api/*` para a API,
  e o proxy do Vite faz o mesmo no dev (decisão 5). Não reintroduza CORS nem uma lista de
  origens: as requisições do proxy chegam com o `Origin` do front, e uma lista as recusaria.

## Regras de trabalho

- **Sou dev júnior** e uso este projeto como portfólio e preparação para entrevistas. Explique
  o porquê das escolhas e os trade-offs, para eu conseguir defendê-las numa entrevista.
- **Passos pequenos, um commit por passo**, em Conventional Commits (`feat:`, `fix:`, `test:`,
  `docs:`, `build:`, `ci:`, `chore:`, `refactor:`). As mensagens do histórico estão em
  português; mantenha o padrão.
- **Toda regra de negócio nova precisa de teste**: unitário para a regra e, quando tocar o banco,
  integração com Testcontainers (Postgres real, não H2). Se o usuário pode violar a regra,
  teste também o status e a mensagem do erro.
- **Nenhum serviço ou dependência paga.** Se algo tiver plano gratuito com limite, me informe o
  limite atual (conferido na fonte oficial, com a data) antes de adotar.
- **Se o contrato da API mudar** (endpoint, campo, status), atualize o OpenAPI (anotações do
  springdoc) e rode `npm run api:types` em `../life-and-money-web` com a API no ar, commitando o
  `schema.d.ts` gerado junto com a mudança que o consome.
- **Novas decisões relevantes vão para [docs/decisions.md](docs/decisions.md)**, no formato
  Contexto / Decisão / Consequências, numeradas em sequência.

## Limites dos planos gratuitos

Conferidos em 23/10/2026 (Render, Neon) e 29/10/2026 (Workers), ver [docs/deploy.md](docs/deploy.md);
reconfira antes de depender deles.

- **Render:** 750 h de instância/mês; hiberna após 15 min sem tráfego (primeira requisição leva
  ~1 min). O Postgres gratuito do Render expira em 30 dias, por isso o banco fica no Neon.
- **Neon:** plano gratuito permanente, 1 GB por projeto, 100 CU-hours de compute por projeto.
- **Brevo** (e-mail, conferido em 30/10/2026): 300 e-mails/dia, sem limite mensal; envia sem
  domínio próprio, trocando o remetente. O Render gratuito bloqueia SMTP, então o envio é pela API
  HTTP (`email/BrevoEmailSender`).
- **Cloudflare Workers:** 100.000 requisições/dia (zera à meia-noite UTC) e 10 ms de CPU por
  requisição, só para requisições que executam o script. Só `/api/*` executa (o proxy); os
  estáticos são gratuitos e ilimitados. Esperar a API não conta como CPU.
