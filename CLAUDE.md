# CLAUDE.md — Life and Money API

API REST de finanças pessoais: categorias, transações, orçamento mensal com vigência, dashboards,
metas de poupança e contas a pagar e a receber. O front fica em `../life-and-money-web`.

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
  `transaction`, `budget`, `goal`, `bill`, `holiday`, `dashboard`, `account`, com `config`, `web` e `shared` para o que é
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
- **Contas previstas** (`bill`, decisões 9 e 10): a conta é a identidade, os termos (nome,
  categoria, valor, dia) têm vigência como o orçamento, e só o pagamento é gravado. As ocorrências
  e o status são calculados no `BillCalendar`; uma ocorrência é `(billId, mês)`, por isso toda
  recorrência tem no máximo uma por mês. Valor e data pagos moram só na transação. Excluir uma
  transação publica `TransactionDeletingEvent` antes do DELETE, e o pagamento ligado é solto.
- **Prazo no próximo dia útil** (decisão 11): `payableUntil` pula fins de semana e feriados
  nacionais (`holiday/HolidayCalendar`), e é dele que sai o `OVERDUE`. Carnaval e Corpus Christi
  contam como não úteis. Os feriados vêm da BrasilAPI por um `HolidaySource` (`RestClient`, timeouts
  de 2 s e 3 s, `@Cacheable("holidays")` por ano). **Nada pode quebrar se ela cair**: o
  `HolidayService` devolve `available=false` e o prazo considera só fins de semana. Os testes de
  integração usam o `FixedHolidaySource`, nunca a rede.
- **Cor e ícone de categoria** (decisão 12): enums `CategoryColor` e `CategoryIcon`, guardados por
  nome, repetidos em `CHECK` no banco. Valor novo entra no enum, numa migration nova que muda o
  `CHECK`, e precisa de desenho no front (o tipo gerado obriga). Campo novo num contrato que o front
  em produção já usa entra **opcional, com padrão no servidor**, para a janela entre os deploys.
- **Corpo ilegível** (`HttpMessageNotReadableException`) responde 400 em português, com o campo em
  `errors` quando o Jackson diz qual foi.
- **Ordem alfabética com `Collator` pt-BR** (`BillCalendar.byName()`), não
  `String.CASE_INSENSITIVE_ORDER`, que põe "Água" depois de "internet".
- **Sessão:** access token JWT de 15 min no corpo da resposta; refresh token opaco em cookie
  `HttpOnly`, `SameSite=Strict`, com path `/api/auth`, salvo como hash SHA-256 e rotacionado a
  cada uso. `POST /auth/refresh` exige o header `X-Refresh-Request`, segunda barreira contra
  CSRF (decisões 4 e 5).
- **IP do cliente só pelo `ClientIpResolver`**: `X-Client-IP` com o segredo do Worker
  (`X-Proxy-Auth`), senão `CF-Connecting-IP`, senão o endereço da conexão. Nunca use
  `X-Forwarded-For` (falsificável atrás do Render) nem mude `server.forward-headers-strategy` de
  `none` (decisão 7).
- **Rate limit em memória** (`ratelimit`, Bucket4j + Caffeine): limites no enum `RateLimit`, 429
  com `Retry-After` e `retryAfterSeconds`. Nunca limite por conta algo que revele se ela existe.
  Os testes de integração rodam com `app.rate-limit.enabled=false`; os limites têm o
  `RateLimitIntegrationTest`, com IPs próprios por teste.
- **Toda tabela nova com `user_id` entra no `account/AccountService`**: um `deleteAllOf(userId)`
  na feature, chamado na ordem das FKs, e os dados no export. As FKs não têm `ON DELETE CASCADE`
  de propósito (decisão 8): se esquecer, a exclusão da conta falha em vez de deixar órfãos, e o
  `AccountIntegrationTest` (que lê as tabelas do `information_schema`) acusa.
- **Senha errada de quem já está logado é 403** (`IncorrectPasswordException`), nunca 401: o front
  trata 401 como sessão encerrada.
- **E-mail atrás de `EmailSender`** (pacote `email`): `brevo` em produção, `log` no dev (só loga o
  link). `app.email.provider` não tem padrão, de propósito. Nos testes de integração, o
  `RecordingEmailSender` guarda as mensagens e o `MutableClock` controla o tempo
  (`setTo(Instant)` para fixar um dia do calendário).
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
- **Commits direto na `main`, sem branches nem pull requests.** Cada passo vira um commit na
  `main`, e o push é feito ao fim da tarefa, depois de os testes e o build passarem na máquina.
  Push na `main` vai para o ar (a API no Render, o front no Cloudflare Workers) e a CI só roda
  depois, então nada quebrado pode ser enviado. Quando a mudança atravessa os dois repositórios,
  o push da API vem primeiro, e o do front só depois que o deploy da API terminar.
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
- **BrasilAPI** (feriados, conferido em 01/11/2026): gratuita, sem chave, open source; não publica
  limite de requisições nem SLA. O cache por ano limita as chamadas a uma por ano consultado a cada
  vez que a instância sobe.
- **Cloudflare Workers:** 100.000 requisições/dia (zera à meia-noite UTC) e 10 ms de CPU por
  requisição, só para requisições que executam o script. Só `/api/*` executa (o proxy); os
  estáticos são gratuitos e ilimitados. Esperar a API não conta como CPU.
