# Publicação

Guia para colocar o Life and Money no ar: o front, a API e o PostgreSQL, com HTTPS.

> **Os planos gratuitos mudam.** As informações abaixo foram conferidas nas páginas oficiais
> em **23/10/2026** (Render e Neon), **29/10/2026** (Cloudflare Workers) e **30/10/2026**
> (Brevo e o bloqueio de SMTP do Render). Antes de seguir, vale reconferir os limites de cada serviço, porque é
> exatamente esse tipo de informação que envelhece rápido.

## Onde cada parte vai

| Parte | Serviço | Por quê |
|---|---|---|
| Front (SPA estática) e proxy de `/api` | **Cloudflare Workers** | Plano gratuito permanente, requisições a arquivos estáticos ilimitadas, sem hibernação, HTTPS e domínio próprio incluídos. O proxy deixa o front e a API na mesma origem |
| API (Docker) | **Render** | 750 horas de instância por mês, deploy direto de um Dockerfile, HTTPS automático |
| PostgreSQL | **Neon** | Plano gratuito **permanente**, 1 GB por projeto |
| E-mail (redefinição de senha) | **Brevo** | 300 e-mails por dia, sem prazo, e envia sem domínio próprio |

### O detalhe que decide o banco

O PostgreSQL gratuito do Render **expira 30 dias depois de criado** (com 14 dias de carência
antes da remoção definitiva). Para um link de portfólio, que precisa continuar funcionando
meses depois, isso é inviável: o app voltaria a cair sozinho. O plano gratuito do Neon é
permanente e não pede cartão, então o banco fica nele e o Render hospeda só a API.

### O detalhe que decide a expectativa

A instância gratuita do Render **hiberna depois de 15 minutos sem tráfego**, e a primeira
requisição depois disso leva cerca de um minuto para responder. Quem abrir o link num
momento de ociosidade espera esse minuto.

Duas saídas, nenhuma gratuita de graça:

- Deixar como está e avisar no README que a primeira carga pode demorar. É honesto e não
  custa nada;
- Manter a instância acordada com um ping periódico. As 750 horas por mês dão 744 horas num
  mês de 31 dias, então um ping a cada 10 minutos consome praticamente toda a cota — e um mês
  mais cheio pode suspender o serviço antes do fim. O compute do Neon também é contado
  (100 CU-hours por projeto), então o ping gasta os dois.

A escolha aqui foi a primeira: avisar.

### O detalhe que decide a origem

O front fica em `*.workers.dev` e a API em `*.onrender.com`. Os dois sufixos estão na Public
Suffix List, então cada subdomínio é um site diferente para o navegador, e o cookie de refresh da
API seria de terceiros. O Safari bloqueia cookies de terceiros por padrão, e no iPhone todo
navegador usa o motor dele: a sessão cairia a cada recarregamento.

Por isso o Worker do front também faz proxy de `/api/*` para o Render, e a API responde sob
`/api`. Para o navegador existe uma origem só: o cookie é primário, pode ser `SameSite=Strict`, e
a API não precisa de CORS. A decisão está registrada em [decisions.md](decisions.md).

### Os limites do Workers, e se eles comportam o proxy

Plano gratuito do Workers, segundo a [página de limites](https://developers.cloudflare.com/workers/platform/limits/)
consultada em **29/10/2026**:

| Limite | Plano gratuito |
|---|---|
| Requisições | 100.000 por dia, zerando à meia-noite UTC |
| Tempo de CPU | 10 ms por requisição HTTP |
| Arquivos estáticos | 20.000 por versão, até 25 MiB cada |

Os dois primeiros limites valem só para requisições que **executam o script** do Worker, e o
`run_worker_first: ["/api/*"]` faz com que só as chamadas à API o executem. Os arquivos estáticos,
inclusive o `index.html` que o modo SPA devolve para rotas como `/goals`, saem da camada de assets,
que a [documentação](https://developers.cloudflare.com/workers/static-assets/billing-and-limitations/)
diz ser gratuita e ilimitada.

Para o proxy, a conta fecha com folga:

- **Requisições:** abrir a home dispara umas 5 ou 6 chamadas à API, então 100.000 por dia são
  cerca de 16 mil aberturas. O uso pessoal fica na casa das centenas;
- **CPU:** repassar uma requisição gasta bem menos que 1 ms. Esperar a resposta do Render não
  conta como CPU, e não há limite de duração para Workers HTTP, então o minuto de hibernação do
  Render não derruba o proxy;
- **Subrequests:** o limite é 50 por requisição, e o proxy faz 1.

Passado o limite diário, só `/api/*` falha (com `429`) até a meia-noite UTC; o front continua
carregando.

### O detalhe que decide o e-mail

O Render gratuito **bloqueia a saída nas portas de SMTP** (25, 465 e 587)
([docs](https://render.com/docs/free)), então o e-mail sai pela API HTTP do provedor, na porta
443. Entre os provedores com plano gratuito e API HTTP:

| | Resend | Brevo |
|---|---|---|
| Limite | 3.000/mês e 100/dia ([preços](https://resend.com/pricing)) | 300/dia, sem limite mensal ([FAQ](https://help.brevo.com/hc/en-us/articles/208580669-FAQs-What-are-the-limits-of-the-Free-plan)) |
| Sem domínio próprio | **Não envia**: o `resend.dev` só entrega para o e-mail da própria conta ([docs](https://resend.com/docs/knowledge-base/403-error-resend-dev-domain)) | Envia: com remetente Gmail, troca o endereço por um do Brevo para passar no DMARC ([docs](https://help.brevo.com/hc/en-us/articles/14925263522578-Comply-with-Gmail-Yahoo-and-Microsoft-s-requirements-for-email-senders)) |

O projeto não tem domínio próprio (`workers.dev` e `onrender.com` não são nossos), e comprar um é
custo. Por isso o Brevo. O preço é a entregabilidade: com o remetente trocado, o e-mail pode cair
no spam, e a tela de pedido avisa isso. Passado o limite diário, o Brevo segura até 1.000
e-mails transacionais numa fila e descarta o resto.

## Passo a passo

### 1. Banco no Neon

1. Crie uma conta em [neon.com](https://neon.com) e um projeto na região mais próxima
   (`aws-us-east-1` é a mais barata em latência para o Render em Oregon; se o Render ficar em
   Oregon, prefira uma região da costa oeste);
2. Copie os dados de conexão. O Neon entrega uma URL no formato
   `postgresql://usuario:senha@host/banco?sslmode=require`;
3. Monte as três variáveis que a API espera, trocando o esquema para `jdbc:postgresql`:

   ```
   SPRING_DATASOURCE_URL=jdbc:postgresql://<host>/<banco>?sslmode=require
   SPRING_DATASOURCE_USERNAME=<usuario>
   SPRING_DATASOURCE_PASSWORD=<senha>
   ```

   O `sslmode=require` não é opcional: o Neon só aceita conexão cifrada.

As migrations **não precisam ser rodadas à mão**. O Flyway roda na inicialização e cria o
schema do zero na primeira subida.

### 2. API no Render

1. Gere o segredo do JWT:

   ```bash
   openssl rand -base64 32
   ```

2. Em [render.com](https://render.com), crie um **Blueprint** apontando para este repositório.
   O [`render.yaml`](../render.yaml) já descreve o serviço, e o Render vai pedir o valor das
   variáveis marcadas com `sync: false`;
3. Preencha `JWT_SECRET`, as três variáveis do banco e as do e-mail (passo 2.1). Sem
   `BREVO_API_KEY` e `EMAIL_FROM`, a API **não sobe** — de propósito, para não cair num envio
   que só registraria os links no log;
4. Espere o build. O health check bate em `/api/health`, e o deploy só é considerado bom quando
   esse endpoint responde;
5. Guarde a URL da API, algo como `https://life-and-money-api.onrender.com`.

#### 2.0. Segredo entre o Worker e a API

Os limites de tentativas são por IP, e o IP real do usuário só chega à API se o Worker do front
provar que é ele (ver a decisão 7). Gere um segredo e cadastre **o mesmo valor** nos dois lados:

```bash
openssl rand -base64 32
```

- no Render: variável `PROXY_SHARED_SECRET` no serviço da API. Sem ela, a API não sobe;
- no Cloudflare: Workers & Pages → `life-and-money-web` → **Settings → Variables and Secrets** →
  **Add**, tipo **Secret**, nome `PROXY_SHARED_SECRET`. Ou, no repositório do front,
  `npx wrangler secret put PROXY_SHARED_SECRET`.

Se só um lado tiver o segredo, nada quebra de imediato, mas todo usuário passa a dividir um único
limite de tentativas: o de um endereço fixo da Cloudflare.

#### 2.1. E-mail no Brevo

1. Crie uma conta gratuita em [brevo.com](https://www.brevo.com);
2. Em **Senders, Domains & Dedicated IPs → Senders**, adicione o e-mail remetente (um Gmail
   serve) e confirme pelo link que o Brevo envia para ele;
3. Em **SMTP & API → API Keys**, gere uma chave;
4. No Render, preencha `BREVO_API_KEY` com a chave e `EMAIL_FROM` com o remetente confirmado.
   `APP_EMAIL_PROVIDER` e `APP_FRONT_URL` já vêm do `render.yaml`;
5. Autorize as faixas de IP de saída do Render no Brevo. Depois dos primeiros dias de uso, o Brevo
   bloqueia chamadas de IPs que ele não conhece e manda um e-mail pedindo confirmação
   ([docs](https://help.brevo.com/hc/en-us/articles/5740111683858-Authorize-and-block-IP-addresses-for-API-and-SMTP-security)).
   O Render gratuito não tem IP fixo: a API sai por qualquer endereço de faixas compartilhadas
   pela região ([docs](https://render.com/docs/outbound-ip-addresses)), então autorizar só o IP do
   alerta quebraria no próximo deploy.
   - No Render, abra o serviço → **Connect** (canto superior direito) → aba **Outbound** e copie
     as faixas. Em 30/10/2026 eram `74.220.49.0/24` e `74.220.57.0/24`;
   - No Brevo, em **Settings → Security → Authorized IPs**, adicione as faixas, uma por linha. O
     Brevo aceita notação CIDR ([docs](https://developers.brevo.com/docs/ip-security)). Deixe o
     bloqueio de IPs desconhecidos ligado: uma chave vazada só funciona de dentro do Render;
   - Se um alerta trouxer um IP **fora** dessas faixas, não é a API: negue, apague a chave no
     Brevo, gere outra e atualize `BREVO_API_KEY` no Render.

### 3. Front no Cloudflare Workers

O front é publicado com o `wrangler`, que já é dependência de desenvolvimento do
[life-and-money-web](https://github.com/akiratochiro/life-and-money-web). A configuração está
no `wrangler.jsonc` do repositório.

1. Crie uma conta gratuita em [dash.cloudflare.com](https://dash.cloudflare.com) e, dentro do
   repositório do front, autentique o `wrangler` (abre o navegador):

   ```bash
   npx wrangler login
   ```

2. Confira se `vars.API_ORIGIN` no `wrangler.jsonc` é a URL da API do passo 2, **sem barra no
   final**. É para lá que o Worker repassa `/api/*`, e o valor é lido em tempo de execução:
   trocar a API pede só um `wrangler deploy`, sem build novo;
3. Gere o build e publique:

   ```bash
   npm run build
   npx wrangler deploy
   ```

4. Guarde a URL que o `wrangler deploy` imprime, algo como
   `https://life-and-money-web.<seu-subdominio>.workers.dev`.

Abrir `/goals` direto no navegador funciona porque o `wrangler.jsonc` declara
`"not_found_handling": "single-page-application"`: qualquer caminho que não seja um arquivo do
`dist` recebe o `index.html` com status 200, e o React Router resolve a rota no navegador. Sem
isso, daria 404, porque o servidor não conhece as rotas do React Router.

### 4. Fechar o círculo

1. Abra o front, crie uma conta e lance uma transação;
2. Recarregue a página: a sessão tem que continuar. É o teste de que o cookie de refresh está
   chegando, e vale fazer também no Safari.

`REFRESH_COOKIE_SECURE` e `REFRESH_COOKIE_SAME_SITE` não precisam ser definidas: o padrão da
aplicação já é o de produção (`true` e `Strict`).

Como o front novo chama `/api` e a API nova só responde sob `/api`, os dois sobem juntos: um front
antigo não conversa com uma API nova, nem o contrário. Publique a API e, assim que o health check
passar, o front.

## Variáveis de ambiente

| Variável | Onde | Obrigatória | Padrão |
|---|---|---|---|
| `JWT_SECRET` | API | sim | — |
| `SPRING_DATASOURCE_URL` | API | sim | — |
| `SPRING_DATASOURCE_USERNAME` | API | sim | — |
| `SPRING_DATASOURCE_PASSWORD` | API | sim | — |
| `REFRESH_COOKIE_SECURE` | API | não | `true` |
| `REFRESH_COOKIE_SAME_SITE` | API | não | `Strict` |
| `PORT` | API | não (o Render define) | `8080` |
| `APP_EMAIL_PROVIDER` | API | sim | — (`brevo` no `render.yaml`, `log` no dev) |
| `BREVO_API_KEY` | API | sim, com `brevo` | — |
| `EMAIL_FROM` | API | sim, com `brevo` | — |
| `APP_FRONT_URL` | API | sim | — (base dos links enviados por e-mail) |
| `PROXY_SHARED_SECRET` | API e Worker (secret) | sim | — (o mesmo valor nos dois) |
| `API_ORIGIN` | front, no `wrangler.jsonc` | sim | — |

## Se algo não funcionar

| Sintoma | Causa provável |
|---|---|
| Login funciona e a sessão cai ao recarregar | O cookie não está voltando: confira que o front chama a API por `/api` na própria origem, e não pela URL do Render, e que `REFRESH_COOKIE_SECURE` não foi sobrescrita para `false` |
| Erro de CORS no console do navegador | Alguma chamada está indo direto para a URL do Render. A API não libera nenhuma origem: tudo passa pelo `/api` do front |
| Toda chamada à API responde 404 ou 502 | `API_ORIGIN` no `wrangler.jsonc` errado, ou com barra no final |
| A API não sobe, com erro do Flyway | Faltou `?sslmode=require` na URL, ou as credenciais do banco estão erradas |
| Abrir `/goals` direto dá 404 | O `not_found_handling` saiu do `wrangler.jsonc`, ou o `assets.directory` não aponta para `dist` |
| A API não sobe, com erro sobre `app.client-ip.proxy-secret` | Faltou `PROXY_SHARED_SECRET` no Render |
| Usuários diferentes recebem "Muitas tentativas" ao mesmo tempo | O Worker está sem o secret `PROXY_SHARED_SECRET`, ou com um valor diferente do Render: todos caem no mesmo limite |
| A API não sobe, com erro sobre `app.email` | Faltou `BREVO_API_KEY` ou `EMAIL_FROM` no Render |
| O e-mail não chega e o Brevo mandou um alerta de IP novo | A faixa de saída do Render mudou ou não foi autorizada: confira a aba **Outbound** do serviço no Render e atualize **Authorized IPs** no Brevo (passo 2.1). O pedido bloqueado se perde; peça o link de novo |
| O e-mail de redefinição não chega | Confira o spam. Depois, o log da API (falha no envio aparece como erro com o id do usuário) e o painel do Brevo (cota diária e remetente confirmado) |
| A primeira requisição demora um minuto | Instância gratuita hibernando. É o comportamento esperado |

## Documentação da API em produção

O Swagger UI continua ligado em produção, em `/api/swagger-ui/index.html` (pela URL do Render ou
pela do front, que repassa `/api/*`). Para um projeto de
portfólio isso é desejável: quem recebe o link pode experimentar a API sem instalar nada.
Num produto real, o caminho seria desligar com `springdoc.api-docs.enabled=false` ou deixar a
documentação atrás de autenticação.
