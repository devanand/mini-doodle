# Mini Doodle

A meeting scheduling backend built for Doodle's take-home assignment: users
define available time slots on their own calendar, slots can be booked as
meetings, and cancelling a meeting frees the slot again. Built in the
4-hour window the assignment specifies, with an emphasis on design and
technical decision-making over exhaustive feature coverage - per the
assignment brief, that's the primary evaluation criterion.

## Tech Stack

| Category | Technology |
|-----------|------------|
| **Language** | ☕ Java 25 |
| **Framework** | 🍃 Spring Boot 4.1.0 |
| **Build Tool** | ⚙️ Gradle (Groovy DSL) |
| **Database** | 🐘 PostgreSQL |
| **Persistence** | 🗃️ Hibernate, Spring Data JPA |
| **Database Migrations** | 🛫 Flyway |
| **Containerization** | 🐳 Docker Compose |
| **Observability** | 🩺 Spring Boot Actuator (health, info) |
| **Testing** | 🧪 JUnit 5, Mockito, AssertJ |
| **API Documentation** | 📖 Spring REST Docs |
| **Code Quality** | ✨ Spotless |

Single-module Gradle build; Spring REST Docs for API documentation,
Spotless for formatting.

## Running it

### Environment

Create a `.env` file in the project root:

```
DB_NAME=doodle
DB_USER=doodle
DB_PASSWORD=doodle
DB_HOST=localhost
DB_PORT=5432
```

A `.env` file is needed to run this locally at all - `docker compose`
reads it automatically and injects the values as container environment
variables, and without it the `db`/`app` containers have no credentials
to start with.

Using it is a deliberate choice, not just convenience: it keeps
credentials out of `docker-compose.yml` and out of version control
(`.env` is gitignored) while still being the *same* mechanism a real
deployment would use. Spring Boot reads `DB_NAME`/`DB_USER`/etc. as plain
OS environment variables regardless of who sets them - locally, that's
`.env`; in a real deployment, the identical variable names would be set
by CI/CD, the container orchestrator's secrets store, or a cloud
provider's config service instead. The application code has no awareness
of, or dependency on, which one supplied them - so local dev and
production configuration follow the same pattern rather than diverging.

Note `DB_HOST=localhost` here, not `db`: the `app` container's own
`docker-compose.yml` environment block sets `DB_HOST=db` explicitly,
overriding whatever `.env` says, since inside the compose network the
database's hostname is the service name, not `localhost`. This `.env`
value only matters if you run the app outside compose (e.g. from an IDE)
against the same compose-managed Postgres.

**`./gradlew bootRun` will not work on its own.** `.env` is read and
injected by `docker compose` specifically - it is not something Gradle,
Spring Boot, or the JVM knows how to read natively. Running the app
outside `docker compose` means `DB_NAME`/`DB_USER`/etc. are simply unset,
and Spring Boot fails to start with no database configuration. Use
`docker compose up` (below), or export the same variables into your shell
manually first if you specifically need to run outside a container.

### Start the app

```
docker compose up --build
```

`--build` matters on every run where the source changed - `docker compose
up` alone reuses the existing image if one exists, so code changes
without `--build` silently run against stale, already-built code.

The API is available at `http://localhost:8080`. Confirm it's actually up
by opening [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- should return `{"status":"UP"}`.

### Database schema

Flyway migrations live at `src/main/resources/db/migration/`, applied
automatically on startup.

- `V1__create_initial_schema.sql` - all four tables (`users`,
  `time_slots`, `meetings`, `meeting_participants`).
- `V2__add_slot_overlap_constraint.sql` - the PostgreSQL exclusion
  constraint (`no_overlapping_slots_per_owner`) that prevents overlapping
  slots for the same owner, added once `slot`'s rule layer existed to
  depend on it.

## API documentation

Full REST API documentation is generated from the test suite itself via
Spring REST Docs - every request/response example in the docs is verified
against real controller behavior as part of the build, so it can't drift
out of sync with the code the way hand-written documentation can.

### Quick reference

| Method | Path | Description |
|--------|------|--------------|
| `POST` | `/users` | Create a user |
| `GET` | `/users/{userId}` | Fetch a user |
| `POST` | `/users/{userId}/slots` | Create a slot |
| `GET` | `/users/{userId}/availability` | Query availability (`from`, `to`, optional `status`) |
| `PUT` | `/slots/{slotId}` | Modify a slot |
| `DELETE` | `/slots/{slotId}` | Delete a slot |
| `POST` | `/meetings` | Book a meeting |
| `GET` | `/meetings/{id}` | Fetch a meeting |
| `DELETE` | `/meetings/{id}` | Cancel a meeting |

```bash
# Create a user
curl -X POST http://localhost:8080/users -H 'Content-Type: application/json' \
  -d '{"name":"Ada Lovelace","email":"ada@example.com"}'

# Create a slot (userId from the response above)
curl -X POST http://localhost:8080/users/{userId}/slots -H 'Content-Type: application/json' \
  -d '{"startTime":"2026-09-01T09:00:00Z","durationMinutes":60}'

# Book a meeting (slotId from the response above)
curl -X POST http://localhost:8080/meetings -H 'Content-Type: application/json' \
  -d '{"slotId":"{slotId}","title":"Planning sync","participantEmails":["a@example.com"]}'

# Cancel a meeting
curl -X DELETE http://localhost:8080/meetings/{meetingId}
```

Full request/response field descriptions, validation rules, and error
examples for every endpoint are in the generated docs below - this table
is a lookup aid, not a substitute for them.

### Generate the full docs

```
./gradlew test asciidoctor
```

This copies the generated HTML into Spring's static resources, so once
the app is running (`docker compose up`), view it at:

```
http://localhost:8080/docs/api-guide.html
```

Or skip the docs and exercise the API directly:

```
./scripts/smoke-test-user.sh
./scripts/smoke-test-slot.sh
./scripts/smoke-test-meeting.sh
```

Each script is self-contained (creates its own prerequisite data) and can
be run independently against a running instance.

One more script proves the meeting double-booking guarantee under real
load rather than just the happy path:
```
./scripts/race-test-meeting.sh
```

This fires N simultaneous requests to book the same slot and asserts exactly
one succeeds. See "Concurrency" below for what each is actually proving.

## Architecture decisions

### Package structure: proportional depth, not uniform architecture

The three bounded contexts - `user`, `slot`, `meeting` - do not share one
architectural style. `user` and `slot` are flat CRUD: a single `@Service`,
a package-private Spring Data repository, and DTOs at the HTTP boundary.
`meeting` is full hexagonal - separate `domain`, `application`, and
`infrastructure` packages, with `MeetingRepositoryPort` as the seam
between them.

This was a deliberate choice, not an oversight. `user` and `slot` have no
real invariants that need protecting from the persistence framework - a
`User` is a name and an email, a `TimeSlot` is a time range and a status
(though `slot` does carry real validation logic, isolated in its own
`rule` subpackage as a composable rule set rather than requiring a full
hexagonal split). `Meeting` is the one aggregate with actual domain logic
worth isolating (constructor-level validation, running on every path to a
`Meeting` instance including reconstruction from the database) and the
one place where a use-case-per-interface split (`BookMeetingUseCase`,
`FindMeetingUseCase`, `CancelMeetingUseCase`) earns its keep rather than
adding ceremony.

### Cross-context boundaries

Every cross-context reference is by UUID only - no JPA relationships span
package boundaries, and no bounded context imports another's JPA entity or
repository. Where one context needs to ask something of another, it does
so through a narrow, purpose-built interface rather than a wide one:

- `UserExistenceChecker` - `slot`'s `OwnerExistsRule` needs to know if a
  user exists, nothing more. It does not get a `User`.
- `SlotBookingPort` - `meeting` needs to mark a slot busy or free. It gets
  back `BookedSlot`, a two-field record with just the id and owner - not
  the full `TimeSlot` aggregate with its status, version, and time range,
  which would leak `slot`'s internal domain model across the boundary for
  no reason.

### Concurrency: two layers, closing two different races

Two invariants in this system cannot be guaranteed by an application-level
check alone, because the check and the write are not atomic: two
concurrent requests can both pass a check that reads "this is fine" before
either one commits.

**Overlapping slots for the same owner.** `NoOverlapRule` performs the
check that gives clients a fast, meaningful `409` in the common case, but
it reads before it writes. A PostgreSQL exclusion constraint
(`EXCLUDE USING gist`, on `owner_id` and a `tstzrange` of the slot's time
range) is what actually guarantees the invariant - unlike the meeting
double-booking race below, this one has no dedicated script proving it
under concurrent load; the constraint's correctness rests on the
exclusion-constraint semantics themselves plus the unit-level
constraint-translation test, not a fired-concurrent-requests proof. See
Known Limitations.

**Double-booking the same slot.** `TimeSlot` carries a `@Version` column;
`SlotService.markBusy` and the meeting insert happen in one transaction,
so whichever concurrent request loses the version race at commit time gets
`ObjectOptimisticLockingFailureException`, translated to `409`. The
`meetings.slot_id` unique constraint is a backstop behind that. Proved
under real concurrent load by `scripts/race-test-meeting.sh`.

Both constraint violations are translated to the same `ConflictException`
a client would see from the application-level check, via a shared
`ConstraintViolationTranslator` - it matches on the actual constraint name
from the JDBC exception's cause chain (via Hibernate's
`ConstraintViolationException.getConstraintName()`), not just on exception
type, so an unrelated constraint violation on either table surfaces as a
real error instead of being silently mislabeled as a booking conflict.

One real bug this caught during development, worth noting: `meetings`'
`slot_id` column has no explicit `CONSTRAINT` clause in the migration
(`UNIQUE` inline on the column), so Postgres auto-generates the constraint
name following its `<table>_<column>_key` convention -
`meetings_slot_id_key`, not a hand-chosen name. The translator's string
match had briefly used a placeholder name before the migration existed to
confirm the real one - a mismatch here fails silently under normal testing
(the happy path never touches this code) and only surfaces under genuine
concurrent load, which is exactly why `race-test-meeting.sh` exists rather
than trusting the constraint-translation logic by inspection alone.

### Why PostgreSQL, not MySQL/MariaDB

The exclusion constraint described above is the load-bearing reason.
Exclusion constraints combined with range types are a PostgreSQL feature;
neither MySQL nor MariaDB has an equivalent, and the closest alternative -
a trigger - is weaker both in practice (bypassable by bulk operations) and
as something to reason about and explain under concurrent load. Secondary
reasons: a native `UUID` column type and native `tstzrange`, which the
exclusion constraint's overlap operator depends on directly.

The honest tradeoff: PostgreSQL uses a process-per-connection model, more
expensive per connection than MySQL/MariaDB's thread-per-connection model.
Not an issue at the scale of a single application instance, since HikariCP
opens a small number of long-lived connections once and reuses them. See
Known Limitations for what changes at multi-instance scale.

### Spring Data Specifications over `@Query`/JPQL

Availability queries compose multiple optional filters (owner, time
window, status). Specifications with the Hibernate static metamodel
(`hibernate-processor`, generating `TimeSlot_`) keep these type-safe and
composable, catching a renamed field at compile time rather than a typo'd
JPQL string failing at runtime. `SlotSpecifications.hasStatus()` and
`excludingId()` return `cb.conjunction()` for "no filter" rather than
`null`, since Spring Data JPA 4.x's `Specification.and()` rejects a null
argument - this was an actual bug hit and fixed during development, not a
defensive pattern applied speculatively.

## Testing strategy

Unit tests (JUnit 5 + Mockito, AssertJ assertions) cover services, domain
validation, rules, and the persistence adapters' constraint-translation
logic, with collaborators mocked. Controller/HTTP-contract tests use
`@WebMvcTest` with the service or use-case layer mocked, doubling as the
source for the generated REST Docs snippets.

Deliberately not included: Testcontainers, or any test that hits a real
database. Whether a query predicate selects the correct rows, whether a
database constraint actually fires under concurrent load, and whether the
full Spring context wires correctly end to end are all questions answered
instead by the shell scripts (`smoke-test-*.sh`, `race-test-*.sh`) against
a real running instance - a deliberate choice to keep the unit test suite
fast and the concurrency proof honest (an in-process test can assert two
threads *called* a method concurrently; it can't prove a real database
constraint under real network and disk latency the way firing actual
simultaneous HTTP requests can).

## Known limitations / what I'd do with more time

- **Connection pooling at scale.** PostgreSQL's per-connection cost only
  matters once multiple application instances are running. The standard
  fix is PgBouncer (or a managed equivalent) between the app tier and the
  database. Not needed at this project's scale, so not built.
- **Pagination.** `GET /users/{userId}/availability` is limit-only (capped
  at 500 results) rather than paginated. Cursor (keyset) pagination on
  `startTime` is the natural upgrade if dense windows become common.
- **No authentication/authorization.** Any client can act as any user,
  book any slot, or cancel any meeting. Out of scope for this assignment,
  but a real deployment would need this before anything else.
- **No deep request/latency/error-rate metrics.** `/actuator/health` and
  `/actuator/info` are exposed for basic liveness, enough for a container
  orchestrator's healthcheck, but nothing beyond that. Micrometer + a
  Prometheus registry is the standard next step; scoped out as one
  dependency plus config lines beyond what the time budget warranted, per
  the assignment brief's own framing of metrics as a "plus."
- **No dedicated test for `ConstraintViolationTranslator` in isolation.**
  Its logic is exercised indirectly through both of its real call sites
  (`SlotService`, `MeetingPersistenceAdapter`), covering both the match
  and no-match branches.
- **No concurrency-load proof for the slot-overlap constraint.**
  `race-test-meeting.sh` proves the `meetings.slot_id` race under real
  simultaneous requests; the equivalent for `no_overlapping_slots_per_owner`
  does not exist. The constraint itself is sound (exclusion constraints are
  a well-established PostgreSQL mechanism for exactly this, and the
  identical mechanism did get proven under load for `meeting`'s race), but
  it's asserted rather than proven under load for `slot` specifically.

## Scaling

The application tier is stateless - every request is self-contained, so
horizontal scaling (more replicas behind a load balancer) works today
with zero code changes. The single PostgreSQL instance is what actually
becomes the ceiling as load grows, and the fix depends on which part of
"the database" is the bottleneck:

**If it's compute/storage capacity, not the technology itself** - swap
Postgres for a different store. Every repository (`SlotRepository`,
`MeetingRepositoryPort`, etc.) is already the seam services depend on,
not the database directly - a service never sees `JpaRepository` or SQL,
only the interface. Replacing the implementation behind that interface
doesn't touch `SlotService`, `BookMeetingService`, or any other caller.

**If it's read load specifically** - introduce a cache in front of the
read-heaviest endpoint (`GET .../availability`). Same seam, same
reasoning: wrap `SlotRepository` in a decorator that checks a cache first
and falls through to the real implementation on a miss, with a short TTL
rather than precise invalidation. Safe specifically because a stale read
can't cause a bad booking - the exclusion constraint and optimistic lock
described above are the actual source of truth, so a stale cache read
just means an occasional `409` on booking instead of corrupted data.

Neither is built, since nothing here runs at a scale where either is a
measured bottleneck - this describes the shape the fix would take, not a
speculative build ahead of evidence it's needed.