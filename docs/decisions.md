# Design decisions

## 1. Reference other features by id, not by JPA relationship

**Context.** Categories, transactions, budgets and goals all belong to a user, and transactions
belong to a category.

**Decision.** Entities store plain ids (`Long userId`, `Long categoryId`) instead of
`@ManyToOne` relationships. Integrity is still enforced by foreign keys in the database.

**Consequences.** Feature packages do not depend on each other's entities, and there is no lazy
loading to cause `LazyInitializationException` outside transactions. The trade-off is losing
navigation (`category.getUser()`): queries that need data from two tables use explicit joins.

## 2. Effective-dated budgets

**Context.** Budgets repeat every month until changed, but changing a budget must not alter
the dashboards of past months.

**Decision.** Each budget item is valid from a start month until an optional end month. Changing
a value closes the current item at the previous month and opens a new one. An item created in
the current month has no history yet, so it is deleted instead of closed.

**Consequences.** Past months are immutable and always show what was planned at the time. Queries
need a validity filter, and replacing an item requires an explicit `flush()`: Hibernate executes
inserts before updates and deletes, which would briefly create two active items and violate the
partial unique index.
## 3. Events between features, instead of calls in both directions

**Context.** Archiving a category has to end the budget of that category: an archived category
takes no new transactions, so an active limit would only leave a line stuck at zero in the
budget and in the dashboard.

**Decision.** `CategoryService` publishes `CategoryArchivedEvent` and the `budget` package
listens to it. The listener is synchronous and runs inside the publisher's transaction.

**Consequences.** The obvious alternative — `CategoryService` calling `BudgetService` — would
create a cycle between the two packages, because `BudgetService` already depends on
`CategoryService` to validate the category. With the event, `category` only announces a fact
and whoever cares subscribes, which is the same shape already used by `UserRegisteredEvent`
and the default categories.

Being synchronous and in the same transaction is deliberate: if ending the budget fails, the
archiving is not committed either. The cost is that `category` now owns an event that is part
of its public contract, and a listener that throws takes the archiving down with it.

## 4. Opaque refresh token in an `HttpOnly` cookie

**Context.** The access token used to last an hour and lived in `sessionStorage`. Two problems:
any XSS could read it, and after an hour the user was logged out in the middle of using the app.

**Decision.** The session became two pieces. The access token is a 15-minute JWT, returned in
the response body and kept only in the front-end's memory. The refresh token is a random
256-bit value in an `HttpOnly` cookie scoped to `/auth`, stored in the database as a SHA-256
hash and rotated on every use.

**Consequences.** The token that matters is unreachable from JavaScript, and the front renews
it on its own, so a long session no longer ends abruptly.

The refresh token is not a JWT because it has to be revocable, and a self-contained JWT would
only be revocable with a server-side list — which is exactly the table that now exists. Only
the hash is stored, for the same reason the password is only stored hashed.

Rotation makes a leaked copy detectable: if an already-used token shows up again, every session
of that user is revoked. That revocation needs `noRollbackFor` on `rotate`, because the
exception thrown right after it would otherwise roll back the very revocation that was just
made.

The cookie has to be `SameSite=None` in production, since the front and the API live on
different domains, so the browser would also send it on a POST triggered by another site.
`POST /auth/refresh` therefore requires the `X-Refresh-Request` header: a header outside the
CORS safelist forces a preflight, and the preflight only passes for the origins the API allows.

*Update:* the cookie is now `SameSite=Strict` and the API no longer has CORS, because the front
and the API moved to the same origin (decision 5). The `X-Refresh-Request` header stays as a
second layer.

## 5. Same origin for the front and the API

**Context.** The front lives on `*.workers.dev` and the API on `*.onrender.com`. Both suffixes are
on the Public Suffix List, so each subdomain is a separate site and the refresh cookie was a
third-party cookie. Safari blocks third-party cookies by default, and on iOS every browser uses
its engine: login worked, but the session was lost on every reload.

**Decision.** The Worker that serves the front also proxies `/api/*` to the API, and the API is
served under `/api` (`server.servlet.context-path`). In development, the Vite proxy plays the same
role. The refresh cookie became `SameSite=Strict`, and CORS was removed.

**Consequences.** For the browser there is a single origin: the cookie is first-party, the
browser never sends it on a request started by another site, and no origin needs to be allowed.

The prefix lives in the API, instead of the Worker stripping it, because a cookie's `Path` is
matched against the path the browser sees. Had the Worker turned `/api/auth/refresh` into
`/auth/refresh`, the API would issue `Path=/auth` and the browser would never send the cookie
back, unless the Worker also rewrote `Set-Cookie`. With the prefix in the API, the path is the
same through the proxy and when the API is called directly.

Removing CORS was required, not only possible: the Worker forwards the browser's `Origin`, which
is the front's domain, and the old allow-list rejected those requests with 403 whenever the
origin did not match exactly. Without any CORS configuration the request goes through, and a
preflight from another site gets no `Access-Control-Allow-Origin`.

The cost is one more hop on every call, and each one counts against the Workers free plan
(100,000 requests a day). Static files stay outside that count, since only `/api/*` runs the
Worker script. Numbers are in [deploy.md](deploy.md).
