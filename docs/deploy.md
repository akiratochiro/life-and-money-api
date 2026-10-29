# Publicação

Guia para colocar o Life and Money no ar: o front, a API e o PostgreSQL, com HTTPS.

> **Os planos gratuitos mudam.** As informações abaixo foram conferidas nas páginas oficiais
> em **23/10/2026** (Render e Neon) e **29/10/2026** (Cloudflare Workers). Antes de seguir, vale reconferir os limites de cada serviço, porque é
> exatamente esse tipo de informação que envelhece rápido.

## Onde cada parte vai

| Parte | Serviço | Por quê |
|---|---|---|
| Front (SPA estática) e proxy de `/api` | **Cloudflare Workers** | Plano gratuito permanente, requisições a arquivos estáticos ilimitadas, sem hibernação, HTTPS e domínio próprio incluídos. O proxy deixa o front e a API na mesma origem |
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
3. Preencha `JWT_SECRET` e as três variáveis do banco;
4. Espere o build. O health check bate em `/api/health`, e o deploy só é considerado bom quando
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
| `API_ORIGIN` | front, no `wrangler.jsonc` | sim | — |

## Se algo não funcionar

| Sintoma | Causa provável |
|---|---|
| Login funciona e a sessão cai ao recarregar | O cookie não está voltando: confira que o front chama a API por `/api` na própria origem, e não pela URL do Render, e que `REFRESH_COOKIE_SECURE` não foi sobrescrita para `false` |
| Erro de CORS no console do navegador | Alguma chamada está indo direto para a URL do Render. A API não libera nenhuma origem: tudo passa pelo `/api` do front |
| Toda chamada à API responde 404 ou 502 | `API_ORIGIN` no `wrangler.jsonc` errado, ou com barra no final |
| A API não sobe, com erro do Flyway | Faltou `?sslmode=require` na URL, ou as credenciais do banco estão erradas |
| Abrir `/goals` direto dá 404 | O `not_found_handling` saiu do `wrangler.jsonc`, ou o `assets.directory` não aponta para `dist` |
| A primeira requisição demora um minuto | Instância gratuita hibernando. É o comportamento esperado |

## Documentação da API em produção

O Swagger UI continua ligado em produção, em `/api/swagger-ui/index.html` (pela URL do Render ou
pela do front, que repassa `/api/*`). Para um projeto de
portfólio isso é desejável: quem recebe o link pode experimentar a API sem instalar nada.
Num produto real, o caminho seria desligar com `springdoc.api-docs.enabled=false` ou deixar a
documentação atrás de autenticação.
