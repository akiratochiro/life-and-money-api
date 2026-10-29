# Publicação

Guia para colocar o Life and Money no ar: o front, a API e o PostgreSQL, com HTTPS.

> **Os planos gratuitos mudam.** As informações abaixo foram conferidas nas páginas oficiais
> em **23/10/2026** (Render e Neon) e **29/10/2026** (Cloudflare Workers). Antes de seguir, vale reconferir os limites de cada serviço, porque é
> exatamente esse tipo de informação que envelhece rápido.

## Onde cada parte vai

| Parte | Serviço | Por quê |
|---|---|---|
| Front (SPA estática) | **Cloudflare Workers** (static assets) | Plano gratuito permanente, requisições a arquivos estáticos ilimitadas, sem hibernação, HTTPS e domínio próprio incluídos. Netlify e Vercel servem igual |
| API (Docker) | **Render** | 750 horas de instância por mês, deploy direto de um Dockerfile, HTTPS automático |
| PostgreSQL | **Neon** | Plano gratuito **permanente**, 1 GB por projeto |

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

### Os limites do Workers, e por que eles quase não importam aqui

Plano gratuito do Workers, segundo a [página de limites](https://developers.cloudflare.com/workers/platform/limits/)
consultada em **29/10/2026**:

| Limite | Plano gratuito |
|---|---|
| Requisições | 100.000 por dia, zerando à meia-noite UTC |
| Tempo de CPU | 10 ms por requisição HTTP |
| Arquivos estáticos | 20.000 por versão, até 25 MiB cada |

Os dois primeiros limites valem para requisições que **executam um script** do Worker. O front
não tem script: o `wrangler.jsonc` só declara a pasta `dist` como assets, e a documentação de
[static assets](https://developers.cloudflare.com/workers/static-assets/billing-and-limitations/)
diz que requisições a arquivos estáticos são gratuitas e ilimitadas. Isso inclui o `index.html`
que o modo SPA devolve para rotas como `/goals`, porque sem script ele é servido pela própria
camada de assets.

Se um dia o front ganhar um script (para SSR, por exemplo), as 100.000 requisições por dia passam
a contar. Passado o limite, a Cloudflare responde com o erro 1027 até a meia-noite UTC.

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
3. Preencha `JWT_SECRET`, as três variáveis do banco e deixe `CORS_ALLOWED_ORIGINS` com um
   valor provisório — a URL do front só existe no passo 3;
4. Espere o build. O health check bate em `/health`, e o deploy só é considerado bom quando
   esse endpoint responde;
5. Guarde a URL da API, algo como `https://life-and-money-api.onrender.com`.

### 3. Front no Cloudflare Workers

O front é publicado com o `wrangler`, que já é dependência de desenvolvimento do
[life-and-money-web](https://github.com/akiratochiro/life-and-money-web). A configuração está
no `wrangler.jsonc` do repositório.

1. Crie uma conta gratuita em [dash.cloudflare.com](https://dash.cloudflare.com) e, dentro do
   repositório do front, autentique o `wrangler` (abre o navegador):

   ```bash
   npx wrangler login
   ```

2. Gere o build com a URL da API do passo 2, **sem barra no final**, e publique:

   ```bash
   VITE_API_URL=https://life-and-money-api.onrender.com npm run build
   npx wrangler deploy
   ```

   O `VITE_API_URL` é lido no momento do build, não em tempo de execução: mudar a URL da API
   exige um build e um deploy novos. A variável passada na linha de comando tem prioridade
   sobre o `.env`, mas **esquecê-la não dá erro**: o build usa o `localhost:8080` do `.env`
   local e publica um front que não acha a API;

3. Guarde a URL que o `wrangler deploy` imprime, algo como
   `https://life-and-money-web.<seu-subdominio>.workers.dev`.

Abrir `/goals` direto no navegador funciona porque o `wrangler.jsonc` declara
`"not_found_handling": "single-page-application"`: qualquer caminho que não seja um arquivo do
`dist` recebe o `index.html` com status 200, e o React Router resolve a rota no navegador. Sem
isso, daria 404, porque o servidor não conhece as rotas do React Router.

### 4. Fechar o círculo

1. No Render, troque `CORS_ALLOWED_ORIGINS` pela URL real do front e aguarde o redeploy;
2. Abra o front, crie uma conta e lance uma transação.

`REFRESH_COOKIE_SECURE` e `REFRESH_COOKIE_SAME_SITE` não precisam ser definidas: o padrão da
aplicação já é o de produção (`true` e `None`), que é o único par que o navegador aceita quando
o front e a API estão em domínios diferentes.

## Variáveis de ambiente

| Variável | Onde | Obrigatória | Padrão |
|---|---|---|---|
| `JWT_SECRET` | API | sim | — |
| `SPRING_DATASOURCE_URL` | API | sim | — |
| `SPRING_DATASOURCE_USERNAME` | API | sim | — |
| `SPRING_DATASOURCE_PASSWORD` | API | sim | — |
| `CORS_ALLOWED_ORIGINS` | API | sim em produção | `http://localhost:5173` |
| `REFRESH_COOKIE_SECURE` | API | não | `true` |
| `REFRESH_COOKIE_SAME_SITE` | API | não | `None` |
| `PORT` | API | não (o Render define) | `8080` |
| `VITE_API_URL` | front, no build | sim | — |

## Se algo não funcionar

| Sintoma | Causa provável |
|---|---|
| Login responde, mas toda requisição seguinte dá 401 | `CORS_ALLOWED_ORIGINS` não é exatamente a URL do front (barra no final, `http` em vez de `https`, ou domínio de preview em vez do de produção) |
| Login funciona e a sessão cai ao recarregar | O cookie não está sendo aceito: confira que o front está em HTTPS e que `REFRESH_COOKIE_SECURE` e `REFRESH_COOKIE_SAME_SITE` não foram sobrescritas para `false`/`Lax` |
| Erro de CORS no console do navegador | Mesma causa da primeira linha. Com `allowCredentials`, o navegador recusa `*` como origem: a lista precisa ser explícita |
| A API não sobe, com erro do Flyway | Faltou `?sslmode=require` na URL, ou as credenciais do banco estão erradas |
| Abrir `/goals` direto dá 404 | O `not_found_handling` saiu do `wrangler.jsonc`, ou o `assets.directory` não aponta para `dist` |
| O front abre, mas toda chamada à API falha | O build foi feito sem `VITE_API_URL` e o bundle ficou com o `localhost:8080` do `.env` |
| A primeira requisição demora um minuto | Instância gratuita hibernando. É o comportamento esperado |

## Documentação da API em produção

O Swagger UI continua ligado em produção, em `/swagger-ui/index.html`. Para um projeto de
portfólio isso é desejável: quem recebe o link pode experimentar a API sem instalar nada.
Num produto real, o caminho seria desligar com `springdoc.api-docs.enabled=false` ou deixar a
documentação atrás de autenticação.
