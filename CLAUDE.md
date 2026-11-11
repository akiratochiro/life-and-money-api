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
  `transaction`, `budget`, `goal`, `bill`, `holiday`, `dashboard`, `account`, `company`, `attachment`, `invoice`, `companyexpense`, `taxengine`, `taxparam`, `withdrawal`, `tax`, `companyoverview`, com `config`, `web` e `shared` para o que é
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
  `HolidayService` cai para o `LocalHolidays` (Páscoa por Meeus, os mesmos nomes da BrasilAPI) e a
  resposta diz `source: LOCAL` (decisão 25). `previousBusinessDayFrom` serve ao DARF, que antecipa. Os testes de
  integração usam o `FixedHolidaySource`, nunca a rede.
- **Cor e ícone de categoria** (decisão 12): enums `CategoryColor` e `CategoryIcon`, guardados por
  nome, repetidos em `CHECK` no banco. Valor novo entra no enum, numa migration nova que muda o
  `CHECK`, e precisa de desenho no front (o tipo gerado obriga). Campo novo num contrato que o front
  em produção já usa entra **opcional, com padrão no servidor**, para a janela entre os deploys.
- **Minha Empresa** (`company`, decisões 17 e 18; plano em `../implementacao_pj*.md`): a empresa é a
  identidade, o enquadramento (`company_regimes`) tem vigência que **aceita meses passados e futuros**,
  mas o histórico só cresce no fim e só a última mudança se desfaz. Toda feature da empresa passa
  por `CompanyService.getOwned` (leitura) ou `getActiveOwned` (escrita: 409 se arquivada). As
  combinações ficam em `RegimeRules` (pura) e em `CHECK`. Empresa é arquivada, nunca excluída. A
  data de abertura só muda enquanto o enquadramento nunca mudou e nada a trava: feature nova que
  dependa dela implementa `OpeningDateLock`. CNPJ em `Cnpj` (alfanumérico, ASCII − 48), guardado
  sem pontuação. A consulta `GET /cnpj/{cnpj}` (BrasilAPI, `CnpjLookup`, cache só do encontrado,
  `CNPJ_LOOKUP_PER_USER`) nunca falha: `available=false` e o cadastro segue. Os testes usam o
  `FixedCnpjLookup`. Erro de campo da empresa é `InvalidCompanyDataException` (400 com `errors`).
- **Arquivos** (`attachment`, decisão 19): `bytea` numa tabela reutilizável; quem usa guarda só o id.
  Todo arquivo novo passa por `AttachmentService.store` (2 MB, tipo pelos **primeiros bytes** com
  `FileType.detect`, cota de 50 MB por usuário). Download sempre `attachment` + `nosniff`. O
  multipart do Spring está em 2 MB por arquivo e 21 MB por envio (413 em português).
- **Notas fiscais** (`invoice`, decisão 20): **só XML, por enquanto** (o PDF é reconhecido só para a
  recusa orientar a baixar o XML; `PDF` e `MANUAL` ficam no enum e no `CHECK` para voltar sem
  migration). Pré-visualização (`/parse`) não grava nada; salvar relê o XML (valores travados, só o
  status muda). `NfseXmlParser` é puro e recusa qualquer `DOCTYPE`. Caminhos conferidos nos XSDs
  oficiais 1.00/1.01: ISS só retido com `tpRetISSQN` 2 ou 3, PIS/COFINS pela tabela de
  `tpRetPisCofins`, valores como emitidos. A receita é `vServ − vDescIncond`. Os testes usam a fixture
  `src/test/resources/nfse/nfse-v101.xml` (construída do XSD, dados fictícios) e o PDF mínimo de
  `TestPdfs` (para comprovantes e para a recusa); dados reais de notas nunca entram no repositório.
- **Receita e despesas PJ** (decisões 21 e 22): a receita (`invoice/RevenueService`, `RevenueSeries`
  puro) é serviço − desconto incondicional das notas **ativas**, por competência, semana ISO, até
  93 dias, 53 semanas ou 36 meses. A despesa fixa (`companyexpense`) repete as contas pessoais:
  `ExpenseCalendar` calcula os meses, só o pagamento é gravado em `company_expenses` (copiando nome e
  categoria dos termos do mês), atrasada pelo dia do calendário. A avulsa é um fato (sem data futura).
  Categorias por empresa, semeadas pelo `CompanyCreatedEvent`; "Impostos" (`system_key = TAXES`) não é
  escolhível nem editável; arquivar encerra as fixas (`ExpenseCategoryArchivedEvent`). Comprovante:
  PDF, JPEG ou PNG pelo `AttachmentService`.
- **Impostos** (decisões 23 e 24): `taxengine` é **puro** (sem Spring/JPA; o `TaxEnginePurityTest`
  acusa) e recebe uma `TaxTable`; `taxparam` monta a tabela de uma competência a partir de
  `tax_parameters`, `simples_brackets` e `irrf_brackets` (vigência + fonte legal por linha; valor novo é
  linha nova numa migration nova). Calcula de 01/2026 a 12/2026; 2027 é recusado (`TaxRules`) até a
  entrega das regras novas. INSS do sócio = 11% até o teto; redução da Lei 15.270 sobre o bruto;
  dinheiro arredondado para centavos (HALF_UP), alíquotas com 10 dígitos. Os testes do motor usam
  `Tables2026`, e o `TaxParameterIntegrationTest` confere que a V18 é igual. Regra lida só em
  compilação não oficial entra em `UnverifiedLegalSource`.
- **Obrigações e pagamentos** (`tax`, decisão 26): ocorrências calculadas como as contas; a obrigação do
  sistema vale no mês pelo regime da competência (`ObligationKind.appliesTo`), sem sincronizar nada na
  mudança de enquadramento. O previsto é recalculado na leitura (`ExpectedTaxCalculator` → motor puro);
  só o pagamento é gravado, o valor e a data pagos moram na despesa de Impostos, e o previsto do momento
  fica congelado. Apagar a despesa solta o pagamento (`CompanyExpenseDeletingEvent`). Pró-labore em
  `withdrawal`: calculado de 2026 em diante, informado antes; MEI não tem.
- **Visão geral da empresa** (`companyoverview`, decisão 27): só lê e soma. Impostos pela **competência**
  (pago, senão previsto), despesas **sem a categoria Impostos** (o imposto pago já é despesa de Impostos),
  resultado = receita − despesas − impostos, retiradas à parte. Próximos vencimentos: atrasados de até 3
  meses e os próximos 30 dias.
- **Corpo ilegível** (`HttpMessageNotReadableException`) responde 400 em português, com o campo em
  `errors` quando o Jackson diz qual foi.
- **Ordem alfabética com `Collator` pt-BR** (`BillCalendar.byName()`), não
  `String.CASE_INSENSITIVE_ORDER`, que põe "Água" depois de "internet".
- **Sessão:** access token JWT de 15 min no corpo da resposta; refresh token opaco em cookie
  `HttpOnly`, `SameSite=Strict`, com path `/api/auth`, salvo como hash SHA-256 e rotacionado a
  cada uso. `POST /auth/refresh` exige o header `X-Refresh-Request`, segunda barreira contra
  CSRF (decisões 4 e 5).
- **Access token revogável** (decisão 14): o JWT leva a claim `cv` (versão das credenciais), e o
  `CredentialsVersionValidator` recusa versão antiga ou usuário excluído. Toda troca de senha passa
  por `User.changePasswordHash` (que sobe a versão) e publica `CredentialsChangedEvent`; uma forma
  nova de mudar credenciais precisa fazer o mesmo. Os testes com `asUser()` não passam pelo decoder.
- **Cookie de aparelho no login** (decisão 13): `loginDevice`, HMAC de `id:email`, só em
  `/api/auth/login`. Com ele válido, o login usa `LOGIN_PER_DEVICE` em vez de `LOGIN_PER_EMAIL`,
  para quem trava um e-mail não travar o dono da conta.
- **Cadastro com confirmação de e-mail** (decisão 16): `POST /auth/register` responde sempre `202`; a
  conta só nasce em `POST /auth/confirm-email`. O que depende de o e-mail ter conta roda na
  `RegistrationQueue`. Nos testes, o `register()` da base passa pelo link do e-mail e limpa a caixa
  do usuário no fim.
- **Senha vazada é recusada** (decisão 15): `PasswordPolicy.requireNotBreached` no cadastro e no
  reset (antes de gastar o token), pela Pwned Passwords (`PwnedPasswordsClient`, timeouts de 2 s e
  3 s, **falha aberta**). Toda forma nova de definir senha chama a mesma regra. Os testes usam o
  `KnownBreachedPasswords`, nunca a rede.
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
  link). O conteúdo é montado pelo `auth/PlainEmail`: parágrafos, link por extenso e assinatura, sem
  cores, tabelas nem botão (e-mail com cara de marketing pesa contra no filtro de spam). HTML e texto
  saem do mesmo conteúdo, escapado em UTF-8. `app.email.provider` não tem padrão, de propósito. Nos testes de integração, o
  `RecordingEmailSender` guarda as mensagens e o `MutableClock` controla o tempo
  (`setTo(Instant)` para fixar um dia do calendário).
- **Tokens opacos** (refresh e redefinição de senha) usam `OpaqueTokens`: 32 bytes do
  `SecureRandom` e só o SHA-256 no banco. Queries `@Modifying` usam `flushAutomatically = true`
  junto com `clearAutomatically`, senão alterações pendentes da transação são descartadas.
- **Nada que dependa de a conta existir pode mudar a resposta**, nem no corpo nem no tempo: o
  cadastro e o `forgot-password` fazem todo o trabalho fora da requisição (`RegistrationQueue` e `PasswordResetQueue`), e o limite de
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
- **Antes do push, rode também `./mvnw test -Dgroups=pre-deploy -Dsurefire.excludedGroups=`**: ele
  falha enquanto houver regra tributária não conferida no texto oficial (decisão 24).
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
- **BrasilAPI** (feriados, conferido em 01/11/2026; CNPJ, em 11/11/2026): gratuita, sem chave, open
  source; não publica limite de requisições nem SLA. O cache por ano limita as chamadas de feriados a
  uma por ano consultado a cada vez que a instância sobe; o CNPJ tem cache do que foi encontrado e
  10 consultas por minuto por usuário. Em 11/11/2026 ela respondeu 404 para o CNPJ alfanumérico de
  exemplo da Receita.
- **Cloudflare Workers:** 100.000 requisições/dia (zera à meia-noite UTC) e 10 ms de CPU por
  requisição, só para requisições que executam o script. Só `/api/*` executa (o proxy); os
  estáticos são gratuitos e ilimitados. Esperar a API não conta como CPU.
