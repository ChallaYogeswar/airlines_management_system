# ams-backend

Spring Boot 3 / Java 17 backend for the Meridian Air management system.

## Auth

Ported from the "Enterprise Model-2" design in your AUTHENTICATION repo
(originally NestJS/TypeScript) onto Spring Security — same architecture,
different language. Full details of what maps to what are in the code
comments, but the shape:

- **JWT access + refresh tokens** (`JwtService`, `jjwt` 0.12.6, HS256).
  Access tokens are short-lived (15m default) and carry roles + the
  session ID; refresh tokens (7d default) are opaque bearer credentials
  whose validity is checked against a stored **Argon2 hash**, never the
  raw token.
- **Session tracking** (`Session` entity, `SessionService`) — one row per
  device/login, so "view my active sessions" and "log out everywhere
  else" are real features, not aspirational ones.
- **Refresh token rotation** (`SessionService.rotate`) — a documented fix
  over the reference implementation, which issued new refresh tokens on
  `/refresh` but never updated the stored session hash, silently
  breaking the session on the next refresh.
- **MFA (TOTP)** — RFC 6238 implemented from spec in `TotpService`, no
  external library (nothing here could be compile-checked in the sandbox
  that wrote it, so a spec-traceable implementation beats trusting a
  library API from memory). Compatible with any standard authenticator
  app. Backup codes are stored as Argon2 hashes — the reference
  implementation stored them in plaintext; this doesn't.
- **Account lockout** — 5 failed attempts locks the account for 15
  minutes (both configurable).
- **RBAC** — `PASSENGER` / `STAFF` / `ADMIN` roles, seeded on startup
  (`RoleSeeder`), enforced via `@PreAuthorize` (e.g. `AuditController`
  is admin-only).
- **Audit log** — every security-relevant action writes an `AuditLog`
  row (login success/failure/lockout, MFA setup/enable/disable, session
  revocation, etc.), queryable via `GET /api/audit` (admin only).

Auth endpoints (`/api/auth/**`) are public; everything else requires a
valid access token except the live-ops endpoints
(`/api/flights`, `/api/radar`, `/ws/**`), which stayed open since they're
public-facing operational data, not account data.

## Booking & seat inventory

- **Seat-level concurrency, not an aggregate counter.** Each flight has
  real `Seat` rows (`SeatLayoutGenerator` builds a 4-across layout, one
  business row if capacity justifies it). `SeatService` holds/releases/
  confirms seats under the same pessimistic-lock pattern as everything
  else here - `findByIdForUpdate` per seat, always locked in sorted-ID
  order across every code path so two concurrent multi-seat requests
  can't deadlock each other.
- **Hold → confirm flow**: a passenger holds seats (5-minute TTL) while
  filling in passenger details, then booking creation converts the hold
  to a real booking - or fails cleanly if the hold expired or was taken,
  telling the passenger to reselect rather than silently booking the
  wrong seat. Expired holds nobody explicitly released are swept every
  60s (`SeatService.sweepExpiredHolds`).
- **Business class carries a real 1.6x price multiplier** - the seat
  map's class distinction means something, not just a color on a grid.
- **Admin capacity changes reconcile actual seats**, not a number: growing
  capacity appends new (economy) seats with correct continued numbering;
  shrinking re-locks and re-checks each candidate seat individually
  before deleting it (a seat can be booked in the instant between "this
  looks free" and actually removing it), and refuses to shrink below
  however many seats are genuinely free to remove.



Two live feeds, both broadcast over STOMP-over-SockJS:

- `/topic/flights` — the departure-board state machine (`FlightStatusEngine`).
  Server-authoritative twin of the Angular fallback simulation: same states,
  same transition rules. Ticks every `ams.flight-engine.tick-interval-ms`
  (default 3.2s).
- `/topic/radar` — live aircraft positions from OpenSky Network's free,
  keyless `/states/all` endpoint, polled every
  `ams.opensky.poll-interval-ms` (default 20s) and re-broadcast.

REST fallbacks for initial page load (before the WebSocket handshake
completes):

- `GET /api/flights` — current board snapshot
- `GET /api/radar` — last cached aircraft snapshot

## Payment

- **No real gateway integration** - that needs an account and API keys
  only you can provision. Instead, `PaymentGateway` is a clean interface
  with one implementation, `SimulatedPaymentGateway`, that does real
  validation (Luhn checksum on the card number, actual expiry-date
  checking) without moving real money. Two test numbers behave like
  Stripe's well-known test cards: `4242 4242 4242 4242` always succeeds,
  `4000 0000 0000 0002` always declines; anything else Luhn-valid
  succeeds ~90% of the time.
- **To go live**: implement `PaymentGateway` against a real provider,
  wire it in with `@Primary` (or drop `SimulatedPaymentGateway`'s
  `@Component`), and nothing in `PaymentService` or the booking flow
  needs to change - they only depend on the interface.
- **Booking lifecycle**: `PENDING_PAYMENT` → (charge succeeds) →
  `CONFIRMED`, or → (charge declines) → `PAYMENT_FAILED`, retryable via
  the same endpoint. Seats stay `HELD` (not `BOOKED`) through the whole
  pending-payment window and only convert on a successful charge - an
  abandoned checkout never permanently locks a seat, it just rides out
  the normal 5-minute hold expiry.
- **Card data is never persisted** - `Payment` stores only the last 4
  digits, a provider reference, and the outcome. Full card number,
  expiry, and CVV live for exactly the duration of the charge request
  and nowhere else, including audit logs (only last-4 is ever logged).

## Running it

Requires Java 17+ and Maven with normal internet access (Maven Central).
This was **not** built/run inside the chat sandbox — that environment can't
reach Maven Central — so build it locally:

```bash
mvn spring-boot:run
```

Server starts on `http://localhost:8080` with an in-memory H2 database
(data resets on every restart — fine for development). H2's web console
is at `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:ams`,
user `sa`, empty password).

WebSocket endpoint is `http://localhost:8080/ws` (SockJS handles the
upgrade).

CORS and the WebSocket's allowed origin are both read from
`ams.cors.allowed-origins` in `application.yml` — defaults to
`http://localhost:4200` (the Angular dev server). Add more origins
comma-separated if you deploy the frontend elsewhere.

### Production profile

`application-prod.yml` switches to PostgreSQL and requires
`AMS_JWT_SECRET` to be set (no dev fallback in prod). Activate with:

```bash
AMS_JWT_SECRET=$(openssl rand -base64 48) \
DB_URL=jdbc:postgresql://localhost:5432/ams \
DB_USERNAME=ams DB_PASSWORD=... \
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

Once there's real user data, switch `spring.jpa.hibernate.ddl-auto` from
`update` to `validate` and introduce Flyway/Liquibase for deliberate
schema migrations instead of Hibernate auto-DDL.

## OpenSky notes

- No API key needed for anonymous access, but it's rate-limited
  (roughly a few hundred requests/day). A failed or rate-limited poll logs
  a warning and keeps serving the last good snapshot — it never blanks
  the radar or crashes the scheduler.
- The bounding box in `application.yml` covers the Chennai/Hyderabad/
  Bengaluru triangle by default. Widen `ams.opensky.bounding-box` for more
  traffic, or register a free OpenSky account and add Basic Auth to
  `OpenSkyClient` if you need a higher rate limit later.

## Next additions this is designed for

- `FlightSearchController` / `FlightSearchService` for the passenger
  search-by-route flow (from/to/date), reusing the same `Flight` domain
  shape.
- A booking + passenger domain (now that auth exists, bookings can
  actually be tied to a real `User`).
- Flyway/Liquibase migrations once `ddl-auto: update` needs retiring.
