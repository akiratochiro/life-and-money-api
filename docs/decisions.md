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

## 6. Password reset by email

**Context.** Users who forget their password had no way back into their account. Any reset flow
also opens two attack surfaces: discovering which emails have an account (enumeration), and
reusing or guessing reset links.

**Decision.** `POST /auth/forgot-password` always answers `202` with the same message. When the
account exists, it stores a 256-bit `SecureRandom` token as a SHA-256 hash, valid for 30 minutes
and once, invalidates the user's previous tokens and emails a link to the front.
`POST /auth/reset-password` consumes the token, applies the registration password rules
(`@ValidPassword`), stores the new BCrypt hash and revokes every refresh token of the user.

Email goes through an `EmailSender` interface: Brevo's HTTP API in production, a logging
implementation in development.

**Consequences.** Enumeration is closed in time as well as in body. The first version sent the
email asynchronously but still looked up the account and wrote the token inside the request;
measured, a request for an existing account took about 2 ms longer (median 6.1 ms vs 4.1 ms).
Hidden in network noise for a single request, but separable by anyone sampling hundreds of
times. Now the request only validates the email format, and everything else runs on another
thread: 2.7 ms vs 2.8 ms. The cost is that a failure (database or provider) is only logged; the
user, who already got the generic answer, can simply ask again.

Single use is enforced by one conditional `UPDATE ... WHERE used_at IS NULL AND revoked_at IS
NULL AND expires_at > now`, not by reading the row and then writing it: two simultaneous clicks
on the same link would both pass a read-then-write check. The database serializes the two
updates, and the second one changes no row.

All four invalid cases (unknown, used, superseded, expired) get the same message: telling them
apart does not help the user, whose way out is always a new link, and it would help someone
probing tokens.

Brevo was chosen over Resend because Resend only delivers to arbitrary recipients from a
verified domain, and the project has none; buying one would break the free-only rule. Brevo
sends from a Gmail sender by rewriting the address, at the cost of a higher chance of landing in
spam. Render's free plan blocks SMTP ports, which is why the integration is HTTP. Limits and
sources are in [deploy.md](deploy.md).

A second request for the same account within one minute is dropped: nothing is written, no
email goes out, and the previous link stays valid. Without it, anyone could fire requests at
someone else's address, filling their inbox and draining the provider's daily quota. It is not a
`429`, for the same reason the answer never changes: a different response only for existing
accounts would reveal them. The check reads the user's latest token in the same table, so there
is no extra state. It does not cap requests spread across many different accounts; that would
take a global limit.

Building this exposed a latent bug: `@Modifying(clearAutomatically = true)` clears the
persistence context after the update, silently discarding pending changes of the same
transaction (here, the new password hash). Every `@Modifying` query now also flushes first.

## 7. Rate limiting in memory, keyed by an IP the Worker vouches for

**Context.** `/auth/login`, `/auth/register`, `/auth/forgot-password` and `/auth/refresh` could be
called without limit: password guessing, mass sign-ups and draining the email quota cost nothing.
A per-IP limit needs the client's real IP, and behind Render and the front's Worker neither
standard header carries it:

- `X-Forwarded-For` is spoofable: Render does not strip what the caller sends, it only appends
  the proxy address;
- `CF-Connecting-IP` cannot be spoofed (Cloudflare, in front of Render, overwrites it), but every
  app request comes through the Worker, and on a Worker subrequest to another Cloudflare zone the
  header carries a fixed Cloudflare address, the same for every user. Keying on it would put all
  users in one bucket: anyone failing a few logins would lock everybody out.

**Decision.** The Worker forwards the IP it received in `X-Client-IP`, with a shared secret in
`X-Proxy-Auth`, after dropping any version of both sent by the browser. The API trusts
`X-Client-IP` only when the secret matches (constant-time comparison); otherwise it uses
`CF-Connecting-IP`, which is the real IP of anyone calling Render directly; and, without either,
the connection address (development and tests). IPv6 addresses are grouped by their /64 prefix.
`server.forward-headers-strategy` is `none`, so Spring never reads `X-Forwarded-For`.

Limits use Bucket4j token buckets with gradual refill: login 20/min per IP and 10 per 15 min per
email; register 5/h, forgot-password 5 per 15 min and refresh 30/min, per IP. Exceeding one returns
`429` as `ProblemDetail`, with `Retry-After` and `retryAfterSeconds`.

**Consequences.** The buckets live in memory, which fits a single instance and has three costs:

- they reset on every deploy, restart and free-tier spin-down, so an attacker gets a fresh budget
  after each one;
- with more than one instance, each would keep its own buckets and the effective limit would
  multiply by the instance count; at that point they would move to shared storage such as Redis;
- memory grows with the number of keys, so buckets sit in a Caffeine cache capped at 100,000
  entries that expire after two idle hours; an attacker rotating keys evicts old buckets instead
  of exhausting the free instance's memory.

The per-email login limit can be turned against a user: someone can spend their 10 attempts and
keep them locked out for up to 15 minutes. The alternative, counting only per IP, leaves password
guessing from many IPs unchecked; for a personal finance app, a temporary lockout is the lesser
evil.

The secret is one more thing to keep in sync: it lives in Render and as a Worker secret, and if the
Worker lacks it, every user falls back to the shared Cloudflare address. `forgot-password` is
limited only per IP, never per account, so a `429` cannot reveal whether an account exists.

