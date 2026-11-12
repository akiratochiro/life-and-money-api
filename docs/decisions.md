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

## 8. Account deletion orchestrated by an `account` package, without `ON DELETE CASCADE`

**Context.** Users must be able to delete their account and everything in it, in one transaction,
and to export all their data. Every table points to `users`, transactions point to categories and
goals, and budget items point to categories. `user` depends on no other package, and `category`
already depends on `user`, so `user` cannot call the features without creating a cycle.

**Decision.** A new `account` package owns `DELETE /users/me` and `GET /users/me/export`. Like
`dashboard`, it depends on every feature and no feature depends on it. Deletion calls each
feature's `deleteAllOf(userId)` in foreign key order (transactions, budget, goals, categories,
sessions and reset tokens, then the user) inside a single transaction; each feature deletes its
own rows through its own repository. The export reuses the response DTOs the API already
publishes, and leaves out the password hash and the tokens, which are credentials rather than the
person's data.

**Consequences.** The options considered were:

- `ON DELETE CASCADE` on every foreign key: the least code, but the most important rule of the
  feature would live only in the schema, any stray `DELETE FROM users` would silently take
  everything with it, and no feature code would run on deletion;
- an event that each feature listens to: respects package boundaries, but the foreign key order
  would be scattered across `@Order` annotations in different packages;
- an orchestrating package (chosen): boundaries respected, order explicit in one method.

The foreign keys stay `NO ACTION` on purpose, as a safety net: if a future feature forgets to
delete its rows, deleting the user fails with an integrity error instead of leaving orphans. This
was verified by removing goals from the orchestration, which made the deletion fail on
`fk_savings_goals_user`. The integration test lists every table with a `user_id` column from
`information_schema`, so a new table is checked without anyone remembering to add it.

A wrong password answers `403`, not `401`: the front treats any `401` as an ended session, and a
typo must not log the user out. Password attempts are limited to 5 per 15 minutes per user, so a
stolen access token cannot be used to guess the password here. The password travels in the body
of a `DELETE`, which HTTP allows without defining its meaning; it was checked end to end through
the Vite proxy and the Worker.


## 9. Scheduled bills: computed occurrences, effective-dated terms, only payments stored

**Context.** Transactions record what already happened. Users also need what is expected: rent,
the power bill, a client who pays every month, the yearly car tax. Each of these repeats, its
amount can change over time, and each month it is either paid, pending or overdue.

**Decision.** Three tables:

- `scheduled_bills` is the identity: recurrence (`ONCE`, `MONTHLY`, `YEARLY`), first month and an
  optional last month. Recurrence and first month never change; changing them means ending the
  bill and creating another one.
- `scheduled_bill_terms` holds what can change (name, category, expected amount, due day), with
  effective dating exactly like the budget (decision 2): an edit closes the current terms at the
  previous month and opens new ones.
- `bill_payments` links one occurrence, `(bill_id, due_month)`, to the real transaction created
  when it was paid.

Occurrences are not stored. They are computed on every read by `BillCalendar`, from the bill and
the terms valid in each month. Status is computed too: paid if there is a payment, overdue if the
due date is before today (São Paulo time), pending otherwise. Whether a bill is a payable or a
receivable comes from its category type, so the two can never disagree.

**Consequences.** The options considered were:

- generating one row per occurrence: simple queries and natural per-month exceptions ("skip
  March"), but it needs a job to generate the rows (unreliable on a free instance that sleeps), a
  horizon for bills without an end, and every edit would have to rewrite the future rows;
- computing on demand and storing only payments (chosen): no job, edits naturally apply from
  then on, and the data mirrors the facts. The cost is that expansion runs in Java, which is fine
  for the one or two months a request covers, and that per-month exceptions would need another
  table if they are ever wanted.

Every recurrence has at most one occurrence per month, which is what makes `(bill_id, month)` a
natural key. A weekly bill would break that, so it was left out. A due day the month does not have
falls on its last day: the 31st is due on April 30 and on February 28 (29 in leap years), instead
of spilling into the next month.

The paid amount and date live only in the transaction, not in the payment, so editing the
transaction in the statement also corrects the payment. A unique constraint on
`(bill_id, due_month)` stops a month from being paid twice, even with two simultaneous clicks. A
bill with payments is ended rather than deleted, so its history stays. Saving categories are not
accepted for now, since a saving transaction may need a goal.

## 10. Deleting a payment's transaction undoes the payment, through an event

**Context.** `bill` depends on `transaction` to create the payment's transaction. Deleting that
transaction from the statement would hit the `bill_payments` foreign key, and `transaction` cannot
call `bill` without a cycle.

**Decision.** `TransactionService.delete` publishes `TransactionDeletingEvent` before the `DELETE`,
and `bill` listens and removes the payment. The listener is synchronous and runs in the same
database transaction, like the budget's listener (decision 3). Undoing a payment in the bills
screen takes the same path: it deletes the transaction, and the listener removes the payment.

**Consequences.** The occurrence goes back to pending, or overdue, whichever screen the user
deletes it from, and there is a single way for a payment to disappear. The alternative, refusing
the deletion with a `409` and asking the user to undo it in the bills screen, was simpler but
pushed the package boundary onto the user.

The event is named for the moment before the deletion, not after it, because listeners must act
while the row still exists. The cost is that `transaction` now publishes an event any future
feature can hang logic on, and a listener that throws prevents the deletion.

## 11. A bill is only overdue after the next business day, with holidays from the BrasilAPI

**Context.** A bill due on a Sunday was shown as overdue on that same Sunday, while the bank rule
for boletos lets it be paid on the next business day without penalty. Showing "pay by Monday"
next to an "overdue" badge would contradict itself, and the home screen would count as overdue
bills that can still be paid on time.

**Decision.** Each occurrence gets `payableUntil`: the due date, or the next business day when the
due date falls on a weekend or a national holiday. `OVERDUE` is computed from it, in the API, so
every screen agrees. National holidays come from the BrasilAPI (`/api/feriados/v1/{year}`),
through a `HolidaySource` called with `RestClient` and short timeouts (2 s to connect, 3 s to
read), and cached per year with Spring Cache and Caffeine. `GET /holidays?year=` exposes them for
the calendar.

The BrasilAPI also returns Carnival and Corpus Christi as national. Legally they are optional days
off, not holidays, but banks do not open on Carnival, so both count as non-business days.

**Consequences.** The options considered were:

- changing only the display: no external dependency in the business rule, but the screen would
  say "overdue" and "pay by Monday" at the same time;
- computing the deadline in the API (chosen): one rule, one place. The cost is that the status now
  depends on an external service.

That dependency is contained. The holidays of a year never change, since the BrasilAPI computes
them (fixed dates plus the Easter-based ones, from 1900 to 2199), so each year is fetched at most
once per instance lifetime. A failure is not cached, so the next request tries again. If the
BrasilAPI is down and the year is not cached, the deadline falls back to weekends only and
`/holidays` answers `available: false`: the screen never breaks, and in the worst case a bill
whose deadline would cross a holiday shows as overdue one day early. The deadline needs the
following year too, because a bill due on Saturday, December 31 can only be paid in January.

The rule applies to every bill, although only boletos follow it: Pix and direct debit work on
holidays. A per-bill switch would fix that if it ever matters. State and municipal holidays are
not covered by the BrasilAPI and are not considered.

The integration tests never call the BrasilAPI: a fixed `HolidaySource` replaces it. The client is
tested with `MockRestServiceServer`, and the timeout against a slow local `HttpServer`, since the
mock replaces the HTTP layer and cannot be slow.

## 12. Category color and icon from closed lists, stored by name

**Context.** Categories needed a color and an icon, picked by the user, that the front uses in the
transaction chips, the statement and the expenses donut.

**Decision.** `CategoryColor` (12 tones) and `CategoryIcon` (20 icons) are enums. The API stores
the name, not a hex value or an SVG, and the front owns how each one looks. Both lists are repeated
as `CHECK` constraints in the database. Migration V10 backfills existing categories: the default
ones by name (Moradia gets `PEACH`/`HOME`), the rest by type. Color and icon are optional when
creating (the type default applies), and `PATCH /categories/{id}` became a partial update of name,
color and icon.

A body that cannot be read, such as a value outside a closed list, now answers RFC 9457 in
Portuguese with the field marked, instead of Spring's default English response.

**Consequences.** Storing names keeps the visual identity free to change: a new shade of "mint" is
a front change, not a data migration, and no category can hold a color the app cannot draw. The
OpenAPI publishes the lists, so the generated TypeScript forces the front to define every value; a
new icon in the API breaks the front's build until it gets a drawing, which is the point.

Being optional on create, with a server default, kept the deployed front working during the window
between the API deploy and the front deploy, the same kind of window that once broke the bills
screen. The cost is a second way to create a category (with and without color), which the
integration tests cover. The backfill is tested against a schema migrated to V9 with old-format
rows, since an empty test database would not exercise it.

## 13. A device cookie keeps the per-email login limit from locking out the account owner

**Context.** Login had two limits: per IP and per email. The per-email one is what stops someone
testing passwords against one account from many IPs, but it let anyone lock any account: ten wrong
passwords with someone else's email, and the owner could not sign in for the next minutes. Raising
the limit would make password guessing cheaper; removing it would leave distributed guessing open.

**Decision.** A successful login sets `loginDevice`, an `HttpOnly`, `SameSite=Strict` cookie scoped
to `/api/auth/login` and valid for a year (OWASP's "device cookie"). Its value is a random id plus an
HMAC-SHA256 of `id:email`, so nothing is stored in the database. A login that presents a valid
cookie for that email skips the per-email bucket and uses one keyed by the device id
(`LOGIN_PER_DEVICE`, also 10 per 15 minutes); every other login shares the per-email bucket as
before. The HMAC key is derived from `JWT_SECRET` with a fixed label instead of being a new secret.

**Consequences.** An attacker can still exhaust the per-email bucket, but that only blocks browsers
that never signed in to the account; the owner's usual browser keeps working. A stolen device cookie
does not give unlimited attempts, since it has its own bucket. A cookie from one account is useless
for another, and a tampered one counts as no cookie. The trade-off: an owner on a new device during
an attack is still blocked until the bucket refills, and deriving the key from `JWT_SECRET` means
rotating that secret also invalidates every device cookie (they just fall back to the shared limit
until the next successful login). The per-IP limit is unchanged and applies to everyone.

## 14. Access tokens carry a credentials version, so a password reset ends them too

**Context.** Resetting the password revoked every refresh token, but an access token is a
self-contained JWT: whoever had stolen one kept access until it expired, up to 15 minutes after the
owner reset the password because of that very theft. The same held after deleting the account.

**Decision.** `users.credentials_version` (migration V11) goes up on every password change. Access
tokens carry the version they were issued at in a `cv` claim, and a `CredentialsVersionValidator`,
added to the JWT decoder next to the issuer check, rejects a token whose version is not the current
one, or whose user no longer exists. The current versions sit in a Caffeine cache
(`CredentialsVersions`) that is invalidated after the commit of a password change or an account
deletion, through `CredentialsChangedEvent`. A token without the claim counts as version 0, which is
where every user starts.

**Consequences.** A password reset now ends every session at once, access tokens included, and
an account deletion does the same. The cost is one cached lookup per authenticated request, and a
database query only on a cache miss, instead of a purely stateless check: the trade-off any
revocable JWT ends up making. The cache is local, which is exact with a single instance; with more
than one, the others would see the new version only after the five-minute expiry, the same caveat
as the in-memory rate limiter. Treating a missing claim as 0 kept the deploy from logging everyone
out, since the front ends the session on a 401 instead of retrying. The `config` package receives
the validator as a generic `OAuth2TokenValidator<Jwt>` bean, so it does not depend on `auth`.

## 15. Passwords found in known breaches are refused, through the Pwned Passwords API

**Context.** The only password rule was a length between 8 and 72: "12345678" and "password123"
were accepted. With sign-up open to anyone, those are exactly the passwords an attacker tries first,
and the per-email rate limit only slows that down.

**Decision.** Registration and password reset also refuse any password present in Have I Been
Pwned's Pwned Passwords, as NIST SP 800-63B recommends, instead of composition rules (an uppercase
letter, a symbol), which the same guideline advises against. The API is free, needs no key and has
no rate limit. Through k-anonymity only the first 5 characters of the password's SHA-1 leave the
server; the response lists the suffixes with that prefix (with `Add-Padding`, so its size reveals
nothing either) and the match happens locally. The check runs after the length validation and, on a
reset, before the token is consumed, so a refused password does not spend the link. The client has
2 s and 3 s timeouts and **fails open**: if the service is down, the password is accepted.

**Consequences.** The most guessed passwords are gone without forcing arbitrary rules on people. The
cost is a network call during sign-up and reset (a few hundred milliseconds), and a window where a
weak password can get through while the service is down: failing closed would mean nobody could
sign up or reset a password because of a third party, which is worse. Integration tests use a fixed
list (`KnownBreachedPasswords`) and never touch the network; the client is tested against simulated
responses, including the padding lines with a count of 0.

## 16. Sign-up confirms the email before the account exists

**Context.** Anyone could create an account with someone else's email, and the sign-up answered
`409 E-mail já cadastrado` for an email that already had an account: a free lookup of who uses the
app, which the password reset had been designed not to give (decision on `forgot-password`).

**Decision.** `POST /auth/register` always answers `202` with the same message. The request does
the same work for everyone (validation, rate limit, breached-password check and BCrypt) and hands
the rest to a queue, as the password reset does, so neither the body nor the response time depends
on the email. In the background:

- a new email gets a row in `pending_registrations` (migration V12) and a link valid for 24 hours;
  the account is only created by `POST /auth/confirm-email`, which consumes the row in a single
  `DELETE` so the link works once;
- an email that already has an account gets a warning instead ("someone tried to sign up with this
  email"), pointing to the password reset; the account and its password do not change;
- a second request for the same email within a minute sends nothing, and a later one replaces the
  pending name, password and link.

The front's confirmation page asks for a click instead of confirming on load, because mail
scanners open links: if loading the page confirmed, an attacker who signed up with a victim's email
and the attacker's own password would get the account the moment the victim's antivirus opened the
link.

**Consequences.** Every account now has an email someone actually controls, and sign-up no longer
reveals who is registered. The price is friction: a new user has to open their inbox before using
the app, each sign-up spends one of Brevo's 300 free emails a day, and a pending row with a BCrypt
hash lives for up to 24 hours (expired rows are deleted on the next sign-up). The integration tests'
`register()` helper goes through the email link like a real user, so every test exercises the flow.

## 17. Companies with an effective-dated tax regime that accepts past months

**Context.** The "Minha Empresa" module needs each company's tax regime (legal nature, regime,
Simples annex setting and MEI activity) month by month, because taxes are computed per month of
competence and a company changes regime over its life (an MEI that grows into a Simples company). A
user who registers today a company opened in 2023 also needs its past to be right, or the 12-month
revenue and the taxes of old months would be computed with the wrong regime.

**Decision.** `companies` is the identity and `company_regimes` holds the regime with effective
dating, like the budget (decision 2) and the bill terms (decision 9). Unlike the budget, which always
starts in the current month, a regime change takes a `validFrom` month that may be in the past or in
the future, with two rules that keep the history from being rewritten:

- the history only grows at the end: a change must start after the current regime started, and it
  closes the current one in the month before;
- the only correction is undoing the latest change (`DELETE /companies/{id}/regimes/latest`), which
  reopens the previous one. Fixing an older period means undoing, one by one, the changes after it.

The first regime starts in the month of the opening date. The opening date can still be edited while
nothing depends on it: while the regime never changed and no invoice exists. Invoices arrive in phase
2, so `company` declares an `OpeningDateLock` interface that other features implement, the same
dependency inversion as `GoalOwnershipChecker` (decision 3). The response carries
`openingDateEditable` so the form can warn about the rule and lock the field.

The allowed combinations live in `RegimeRules`, a pure function with its own tests, and again in
`CHECK` constraints: MEI if and only if SIMEI, an annex setting if and only if Simples, an activity if
and only if MEI (it sets the DAS-MEI amount). Companies are archived, never deleted, since their
history feeds taxes and reports; an archived company is read-only and can be unarchived.

**Consequences.** Past months always keep the regime of their time, and the user can still register
a long history after the fact. The options considered were editing any period in place (flexible,
but every past tax computation could silently change) and only allowing changes from the current
month on, like the budget (simpler, but a company with history could never be registered correctly).
The cost is that a mistake in an old period takes several undos to fix. Undoing deletes the latest row
and flushes before reopening the previous one, because Hibernate runs updates before deletes and
the partial unique index would briefly see two open regimes.

The CNPJ is stored as `CHAR(14)` and validated with the new rule for alphanumeric CNPJs, issued since
July 2026: each character is worth its ASCII code minus 48, and the same modulo 11 then validates the
old numeric ones too.

## 18. CNPJ lookup through the API, never blocking the registration

**Context.** Typing a CNPJ could fill the company's legal name, opening date and legal nature. The
BrasilAPI (`/api/cnpj/v1/{cnpj}`) serves the Receita's open data for free, without a key, and
publishes no rate limit or SLA.

**Decision.** The front calls `GET /cnpj/{cnpj}` on our API, which calls the BrasilAPI with
`RestClient` and short timeouts (2 s to connect, 3 s to read), the same shape as the holidays
(decision 11). The answer is always `200`: `found=false` when the BrasilAPI does not know the CNPJ,
and `available=false` when it failed, so the form simply goes on by hand. Only found results are
cached, a limit of 10 lookups per minute per user protects the BrasilAPI from a form in a loop, and
the integration tests use a fixed `CnpjLookup`, never the network.

The Receita's legal nature code only fills the form when it is unambiguous: 213-5 is MEI or EI
according to the MEI option, 206-2 is SLU with one partner and LTDA with more, the old EIRELI (230-5)
is an SLU, and both simple societies map to `SOCIEDADE_SIMPLES`. Anything else is left for the user,
because a wrong legal nature means wrong taxes.

**Consequences.** Going through the API keeps third parties out of the browser (the privacy page
stays true with one more line), lets the lookup be cached and limited, and keeps the front's rule of
calling only `/api`. The cost is one more external dependency, contained like the holidays: nothing
breaks when it is down. On 11/11/2026 the BrasilAPI answered `404` for the Receita's own alphanumeric
example, so it is not confirmed to know the new format yet; the form says so instead of implying the
CNPJ is wrong. The CNPJ travels without the slash, which would split the URL path.

## 19. Files stored in Postgres (`bytea`), in a reusable attachments table, with a per-user quota

**Context.** Invoices (and, in phase 3, expense receipts) keep their original file: the XML or PDF
of the NFS-e. Files must be deleted with the account, exported, and served back only to their owner.

**Decision.** An `attachments` table holds the bytes in `bytea`, with the name, the detected type,
the size and a SHA-256. Features that use files (`invoices`, later `company_expenses`) store only the
attachment id, so listing invoices never loads a byte. Three limits live in `AttachmentService`:
2 MB per file, the types each caller accepts, and **50 MB per user**. The type is detected from
the first bytes of the file (`%PDF-`, the PNG and JPEG signatures, `<` after an optional BOM), never
from the browser's `Content-Type` or the extension, which anyone can change. Downloads are always
`Content-Disposition: attachment` with `nosniff`, so the browser never renders a user's file inside
our origin. Spring's multipart limits match: `max-file-size: 2MB`, `max-request-size: 21MB`, and the
413 is answered in Portuguese.

**Consequences.** The options considered were object storage (S3, Cloudflare R2) and the database.
Object storage scales better and keeps the database small, but it is one more service with its own
keys, its own deletion path for account removal and no shared transaction: a crash between the
upload and the insert leaves an orphan on one side or the other. In the database, an invoice and its
file are saved, exported and deleted in the same transaction, with nothing new to configure. The cost
is space in Neon's free 1 GB: an NFS-e XML has 10–30 KB and a DANFSe 100–300 KB, so typical use is
small, and the quota stops a single user from filling the database. If the quota ever has to grow,
the attachments table is the single place to move to object storage.

## 20. NFS-e: the XML is the source of truth, the PDF a confirmed fallback, in two steps

**Context.** The company's revenue comes from the invoices (NFS-e) it issues. The national standard
(gov.br/nfse) has an official XML layout; the PDF (DANFSe) is a printout whose layout is not a
contract.

**Decision.**
- **Two steps.** `POST /invoices/parse` reads up to 10 files and answers, per file, what was found
  (`READY`, `NEEDS_INPUT`, `DUPLICATE`, `REJECTED`), saving nothing. Saving is a second request,
  per file. An abandoned preview leaves no orphan file and needs no cleanup job.
- **The XML locks the values.** On save, the server parses the XML again and ignores any fields
  sent; afterwards only the status changes. Paths were checked against the official XSDs 1.00 and
  1.01 (package of 09/02/2026): ISS counts as withheld only when `tpRetISSQN` is 2 or 3, PIS and COFINS
  only when `tpRetPisCofins` says so (a 10-code table whose 1 and 2 match the old codes), and the net
  and withheld amounts are kept as issued, because the official Anexo I contradicts itself on how to
  recompute them. The unconditional discount is stored separately: it is not gross revenue (LC 123,
  art. 3, §1).
- **The PDF is a suggestion.** PDFBox extracts the text and the user confirms or completes the
  fields; a PDF with nothing recognized (scanned, protected) is filled by hand with the file
  attached.
- **Rules:** a note from another CNPJ is refused when the company has one; a homologation note
  (`tpAmb = 2`) is refused; a competence before the opening date is refused; the same access key
  cannot enter twice (checked in the preview and by a `UNIQUE`); a note that replaces another
  (`chSubstda`) cancels the replaced one if it is registered. Cancellation is otherwise manual, since
  it is a separate event that never changes the note's XML.
- **Parser hardening:** any `DOCTYPE` is refused, which closes external entities (XXE) and entity
  expansion at once; the parser is namespace-aware and matches elements by namespace and local
  name, so prefixed XML works too.
- **The digital signature is not verified.** That would require the ICP-Brasil certificate chain.
  The notes are the user's own, for their own control, and never leave the app as fiscal documents,
  so a forged XML would only mislead its author.
- Invoices implement the `OpeningDateLock` from decision 17: with any invoice, the opening date is
  locked.

**Consequences.** Values from the XML are exact and cannot drift by typing; the PDF path keeps users
without the XML covered, at the cost of manual checking. Until a real DANFSe is available to confirm
its labels, the PDF parser only recognizes the access key, whose format does not depend on the
layout.

*Update (11/11/2026):* invoices are accepted **only as XML** for now. A PDF is still recognized by its
first bytes, but only to answer with the guidance to download the XML from the NFS-e portal. The PDF
parser, manual entry, field correction (`PUT`) and the PDFBox dependency were removed. `PDF` and
`MANUAL` stay in `InvoiceSource` and in the database `CHECK`, so accepting PDFs again needs no
migration. The reason: a parser built without a real DANFSe would pass its tests and fail on real
files, and every value from the XML is exact and locked.

## 21. Company expenses: recurring ones computed like bills, one-offs as facts, system-owned "Taxes"

**Context.** Companies spend on rent, an accountant, software, equipment. Some expenses repeat
every month with an amount that changes over time; others happen once. Phase 7's balance subtracts
only what was actually paid, and phase 9's calendar needs what is still pending.

**Decision.**
- **Recurring expenses repeat decision 9.** `company_recurring_expenses` is the identity (first and
  optional last month), `company_recurring_expense_terms` holds name, category, expected amount and
  due day with effective dating, months are computed by `ExpenseCalendar` and only the **payment**
  becomes a row in `company_expenses`, with the real amount and date. Editing applies from the
  current month; deleting removes an expense that was never paid and ends one that was, at the
  previous month or the last paid one. A payment copies the name and category of the month's terms,
  so a paid month never changes afterwards. Month views group payments by the due month (October's
  rent paid on November 3rd belongs to October).
- **A one-off expense is a fact:** it was paid on its date, so the date cannot be in the future.
- **Overdue counts by the calendar day**, without the business-day rule of personal bills: a company
  expense is not a bank slip with a grace period.
- **Categories per company**, seeded by a `CompanyCreatedEvent` that `company` now publishes
  (`company` cannot call `companyexpense`, which depends on it), the same shape as
  `UserRegisteredEvent` and the default personal categories; V15 seeds companies that already
  existed. "Salários e encargos" counts as payroll for the Fator R. **"Impostos" belongs to the system**
  (`system_key = TAXES`): it cannot be chosen for a manual expense, renamed or archived, because tax
  payments (phase 4) land there and a manual entry would break the "expected × paid" report.
  Archiving a category ends its recurring expenses, through an event, as with personal bills.
- **Receipts** reuse the attachments of decision 19, accepting PDF, JPEG and PNG.

**Consequences.** The behavior and the code shape match personal bills, so the reasoning is defended
once. An expense that is "automatically paid every month" would be simpler, but the balance would
subtract rent that has not left the account yet. The cost is a click to mark each month as paid, and
a second table pair to keep in sync with the bills design if that design ever changes.

## 22. Revenue series: by competence, minus unconditional discounts, ISO weeks, bounded ranges

**Context.** The company overview shows revenue by day, week or month.

**Decision.** Revenue is the sum of `service − unconditional discount` of **active** invoices, by
competence date (`RevenueSeries`, pure). Weeks are ISO (Monday to Sunday), identified by their Monday,
and the requested range is widened to whole periods, so a week that starts in October and ends in
November is shown whole. Periods without invoices come as zero. A request is limited to 93 days,
53 weeks or 36 months, answered with a `400` in Portuguese otherwise.

**Consequences.** The chart never skips a month and every bar covers a full period. The bounds keep
responses small on the free instance; longer views can page through ranges.

## 23. A pure tax engine with effective-dated rules and parameters

**Context.** Phase 4 computes the DAS of the Simples Nacional (Anexos III and V), the fixed DAS-MEI,
the INSS and IRRF of a partner's pró-labore, the Fator R and the due dates. The numbers change every
year (minimum wage, INSS ceiling, IRRF table), and on 01/01/2027 the rules themselves change (Res.
CGSN 190/2026 and LC 214/2025: CBS/IBS in the Simples, a new RBT12 window, Fator R and DAS-MEI rules).

**Decision.**
- **`taxengine` is pure domain code**: no Spring, no JPA. It receives a `TaxTable` (the parameters
  valid on a date) and plain values, and returns value objects carrying the calculation memory
  (bracket, effective rate, RBT12, Fator R, annex). It is tested with the hand-made examples of the
  phase plan, and against the Receita's official example for Lei 15.270 (salary 6,000 → IRRF 382.88).
  A test reads the package's imports and fails on any framework import (no ArchUnit dependency).
- **Parameters live in tables with effective dating** (`tax_parameters`, `simples_brackets`,
  `irrf_brackets`), each row with its legal source; `taxparam` builds the `TaxTable` for a month. A
  test checks that the seeded table is exactly the one the engine tests use.
- **Rules are versioned too.** The engine implements the 2018–2026 rules and refuses competences
  from 01/2027 with a clear message (`TaxRules`); the 2027 rules come in their own delivery, before
  the first 2027 DAS (20/02/2027), read from the official text. Calculations start at the 01/2026
  competence; earlier months show no expected value.
- **Choices worth knowing:** the partner's INSS is a flat 11% capped at the ceiling (not the
  employee's progressive table); the IRRF reduction of Lei 15.270 uses the gross income, as in the
  Receita's examples; INSS, table tax and reduction are rounded to cents (HALF_UP) and rates keep 10
  significant digits; withheld ISS leaves the DAS, its share capped at 5%; above the R$ 3.6 million
  sublimit nothing is computed; the Fator R payroll excludes the CPP (inside the DAS in Anexos III and
  V), so it never overstates; no IRRF dependents yet.

**Consequences.** The heart of the module is testable in milliseconds and readable as a set of
formulas. Rules and values are data plus a versioned code path, so 2027 is an addition, not a rewrite.
The price is a duplicated 2026 table in the tests, kept honest by the seed-equality test.

## 24. Rules read only in an unofficial compilation are tracked until checked

**Context.** On 11/11/2026 the official text of Res. CGSN 140/2018 could not be opened (the
Receita's normas site renders only with JavaScript, and Planalto refused automated access), so some
details came from an unofficial compilation: the start-up RBT12 and Fator R, withheld ISS in the DAS,
the DAS due-date shift, the DEFIS and DASN dates, and the MEI 20% tolerance (LC 123, art. 18-A, §7).

**Decision.** Each of them is an entry of `UnverifiedLegalSource`, referenced from the code that
implements it. A test tagged `pre-deploy` fails while the list is not empty; it is excluded from the
normal suite (`surefire.excludedGroups`) and run before pushing with
`./mvnw test -Dgroups=pre-deploy -Dsurefire.excludedGroups=`. Checking a rule against the official
text means deleting its entry.

**Consequences.** Work goes on without pretending the sources were verified, and the gap cannot be
forgotten at deploy time. The normal suite and CI stay green.

**Update (11/11/2026, Phase 6).** With browser access, all entries were checked against the official
texts and removed: Res. CGSN 140, arts. 22 §§2º-4º, 25 §9º II, 26 §§6º-7º, 40 §3º (and art. 102 §2º for the
DAS-MEI), 72 §1º, 109 and 145 (Receita's consolidated text); LC 123, arts. 14 and 18-A §7º, and Lei 9.250,
art. 6º-A (Planalto); and the Receita's Perguntas e Respostas of 19/12/2025, question 10, which applies the
10% withholding to the Simples. Two details surfaced: LC 123, art. 14 §1º subtracts "the amount due under
the Simples" while Res. CGSN 140, art. 145 §1º narrows it to the IRPJ part (the app follows the
resolution); and art. 6º-A §3º keeps profits from results up to 2025, approved by 31/12/2025, out of the
withholding (now in the warning). The list is empty and the pre-deploy test passes; a rule read only in a
compilation goes back into it.

## 25. Holidays fall back to a local calculation instead of weekends only

**Context.** Decision 11 made the deadline of personal bills fall back to weekends only when the
BrasilAPI fails. Phase 4 needs business days for tax due dates (the DAS moves forward, the DARF
backward), where a wrong day is a late payment.

**Decision.** `LocalHolidays` computes the same national holidays the BrasilAPI returns, with the same
names: 9 fixed dates (Black Consciousness Day only from 2024, Lei 14.759/2023) and 5 Easter-based ones
(Carnival Monday and Tuesday, Good Friday, Easter, Corpus Christi), Easter by the Meeus/Jones/Butcher
algorithm. `HolidayService` uses it when the BrasilAPI fails, and `/holidays` now says where the list
came from (`source`: `BRASIL_API` or `LOCAL`); `available` is false only outside 1900–2199.
`HolidayCalendar` gained `previousBusinessDayFrom` for the DARF.

**Consequences.** Without the BrasilAPI a bill due on Saturday, October 31st, 2026 is payable until
Tuesday, November 3rd (Monday is Finados), instead of showing overdue a day early. The BrasilAPI stays
the primary source: if a holiday is ever moved by law, it is the one that knows, and a test keeps
the local list equal to the BrasilAPI's for 2026 and 2027.

## 26. Tax obligations computed per month; only payments are stored

**Context.** Phase 4 shows every month what the company owes (DAS, DAS-MEI, the partner's DARF, the
yearly declarations, the user's own obligations), with an expected amount and how it was computed,
and lets the user record what was paid. Regimes change over time, pró-labore is registered and
planned, and revenue keeps arriving after a month closes.

**Decision.** Like bills (decision 9), an obligation (`tax_obligations`) is the identity and its
occurrences are computed: a system obligation applies in a competence when the company's regime in
that month matches it (`ObligationKind.appliesTo`), so a regime change needs no synchronization.
The five system obligations are created with the company (`CompanyCreatedEvent`, and V20 for existing
ones), can only be deactivated, and carry their own due rule: DAS postpones to the next business day,
DARF anticipates, declarations keep 31/03 and 31/05. The DARF exists only in months with pró-labore.
The expected value is recomputed on read (`ExpectedTaxCalculator` feeds the pure engine); only the
payment is stored (`tax_payments`, one per obligation and competence). Paying creates a company
expense in the system "Taxes" category, where the paid amount and date live, and freezes the expected
value of that moment so the difference does not drift as invoices arrive later. A declaration only
records the delivery date. Deleting that expense in the Expenses tab releases the payment
(`CompanyExpenseDeletingEvent`, as in decision 10). Custom obligations with payments cannot be deleted,
only deactivated. Competences the engine does not calculate (before 2026, from 2027) show no expected
value and still accept payments.

**Consequences.** The month view always reflects the latest revenue and payroll without jobs or
stored forecasts, and a paid tax appears in expenses and totals with no double entry. The cost is
recomputing per request (a handful of small queries per obligation), acceptable at this scale. The
expected values are estimates; the response says so (`notice`).

## 27. The company overview reads, never stores: taxes by competence, the tax expense left out

**Context.** Phase 5 shows the month of a company at a glance: revenue, expenses, taxes, result and
withdrawals, plus what is due soon. Everything already exists in other features; the question is how
to add it up without counting anything twice or mixing periods.

**Decision.** A read-only package, `companyoverview`, like the personal `dashboard`, reads the public
services of `invoice`, `companyexpense`, `tax` and `withdrawal` (none depends on it).
- **Taxes count by competence**: November's DAS is the tax on November's revenue, even though it is due
  in December (`TaxService.ofCompetence`). A paid tax counts by the amount paid, an unpaid one by the
  expected amount; one without an expected amount counts as zero and is reported (`withoutExpected`).
- **Expenses leave out the "Taxes" category** (`CompanyExpenseService.monthTotals`): a paid tax becomes an
  expense there, and counting it again would count it twice. Pending recurring expenses of the month
  enter by their expected amount, so the current month (`open`) is a forecast.
- **Result = revenue − expenses − taxes.** Withdrawals only reduce cash and are shown apart, by payment
  date; their INSS and IRRF are already in the taxes, through the DARF.
- **Upcoming** is relative to today: unpaid taxes and recurring expenses overdue up to 3 due months back
  and due within 30 days, overdue first. The 3-month window bounds the cost (each month of taxes
  recomputes the DAS). Phase 9 generalizes this source into the fiscal calendar.
  A tax whose expected amount is exactly zero (the DAS of a month without revenue) is left out: there is
  nothing to pay, and listing it as overdue only alarms. The Taxes tab still shows it.

**Consequences.** One request per screen block, with the rules tested against Postgres instead of
re-implemented in the front. A month's result closes with that month's revenue. The cost is recomputing
taxes on every read, a few small queries per obligation and month.

## 28. Withdrawals reach personal finances with one source of truth; profit distribution only warns

**Context.** Phase 6 links a partner's withdrawal to the user's personal finances and adds profit
distribution, whose taxation changed with Lei 15.270/2025 and is disputed for the Simples Nacional.

**Decision.**
- **Launching is explicit**: after saving, one tap launches the *net* amount as an income transaction on
  the payment date, only for the "me" partner. The default category ("Pró-labore" or "Lucros
  distribuídos") is created on first use, reusing an active one with the same name.
- **The withdrawal is the source of the amount and the date.** Editing it updates the transaction
  (`syncFromOrigin`); editing them in the personal statement is refused with 422, while category and
  description stay free. Going back from the net pró-labore to its gross would mean inverting the
  progressive IRRF, and two sources of truth drift apart.
- **Deleting asks about the other side.** Deleting the withdrawal takes the transaction only with
  `deletePersonal`; deleting the transaction releases the link (`TransactionDeletingEvent`).
- **No package cycle**: `transaction` defines `TransactionOrigins` (where a transaction came from), which
  `withdrawal` implements, as with goals (decision 3). Services that need an implementer that depends on
  them take an `ObjectProvider`, avoiding a bean cycle; the same holds for `ProfitExemptionLimits`, which
  `tax` implements for `withdrawal`.
- **Profit distribution** has no INSS and no payroll month, takes the IRRF the user informs, and never
  counts toward the Fator R. The MEI may distribute profit.
- **Two warnings, never blocks.** Above R$ 50,000 in a month from the company to the same partner, the
  10% withholding on the *total* is suggested (Lei 9.250, art. 6º-A); the Receita applies it to the
  Simples, which is disputed in court, so the app does not withhold by itself. Beyond the estimated
  exemption limit without bookkeeping (presumption of Lei 9.249, art. 15 — 32% services, 8% trade, 8% for
  a mixed MEI — minus the IRPJ inside the DAS, for the year), it warns that only bookkeeping showing a
  larger profit keeps it exempt. The rates are parameters (V21); the rules read only in secondary
  sources are in `UnverifiedLegalSource`.

**Consequences.** The personal statement never disagrees with the company. The cost is that a linked
amount can only be changed on the company page, which the statement explains. The warnings depend on
estimates, which they say.

## 29. The company cash: net amounts, paid taxes as expenses, and a provision for every tax already owed

**Context.** Phase 7 shows how much money the company really has: the account balance from the opening
balance the user informed, minus what already belongs to the government. The specification's formula
("opening + revenue − paid expenses − paid taxes − withdrawals") and provision ("effective rate × this
month's revenue + overdue taxes") would count some money twice and miss some taxes.

**Decision.** A read-only endpoint, `GET /companies/{id}/cash`, in `companyoverview`, always as of today
(apart from `overview`, which follows the month on screen).
- **Balance** = opening balance (from the start of its day) + **net** amount (`vLiq`) of active invoices by
  **issue date** − expenses by date, **including** the "Taxes" category − **net** withdrawals, up to today.
  - No separate "− paid taxes": paying a tax already creates a "Taxes" expense (Phase 0).
  - Net invoices: the taker pays the withheld taxes straight to the government; only the net reaches the
    account. Revenue for the DAS and the revenue card stays service − discount.
  - Net withdrawals: the partner's INSS and IRRF leave the account later, in the DARF, which is a tax
    expense.
  - Issue date approximates when the money arrives (Phase 0, decision 9); the response says so.
- **Provision** = every unpaid tax with an expected amount whose competence has started, in three parts:
  the current competence, past competences still within the deadline (October's DAS until its due date
  in November, which the specification left out) and overdue ones. It reads the expected amounts of the
  tax engine instead of redoing "effective rate × revenue". The window is the one of the upcoming card (due
  up to 3 months back), so both cards agree; zero taxes are left out and those without an expected amount
  are counted (`withoutExpected`).
- **Available** = balance − provision; it may be negative.
- **"Already the government's"** is the DAS (or DAS-MEI) of this month's revenue, by competence, paid or
  expected; the pró-labore DARF is on the payroll, not on revenue, and stays only in the provision. The
  rate is amount ÷ revenue, so the sentence adds up even with ISS withheld. Without a DAS (Lucro Presumido
  or Real) there is no sentence.

**Consequences.** No migration: everything is already stored. The cash is recomputed on every read, like
the overview, so a new invoice changes it at once. A tax forgotten for more than 3 months leaves both the
provision and the upcoming card (the fiscal calendar of Phase 9 shows the full history), and the balance
is only as good as the opening balance and the issue-date approximation.
