# Mini Doodle

A meeting scheduling backend built for Doodle's take-home assignment: users
define available time slots on their own calendar, slots can be booked as
meetings, and cancelling a meeting frees the slot again. Built in the
4-hour window the assignment specifies, with an emphasis on design and
technical decision-making over exhaustive feature coverage - per the
assignment brief, that's the primary evaluation criterion.

Status: in progress. user and slot contexts are complete and verified via smoke test. 
Unit tests and REST Docs for slot are pending (next up). meeting is not yet built - this 
README grows alongside the code rather than describing anything ahead of it.

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
DB_HOST=db
DB_PORT=5432
```

A `.env` file is needed to run this locally at all - `docker-compose`
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

**`./gradlew bootRun` will not work on its own.** `.env` is read and
injected by `docker-compose` specifically - it is not something Gradle,
Spring Boot, or the JVM knows how to read natively. Running the app
outside `docker-compose` means `DB_NAME`/`DB_USER`/etc. are simply unset,
and Spring Boot fails to start with no database configuration. Use
`docker-compose up` (below), or export the same variables into your shell
manually first if you specifically need to run outside a container.

### Start the app

```
docker-compose up --build
```
```--build``` matters on every run where the source changed - docker-compose up alone 
reuses the existing image if one exists, so code changes without --build silently run
against stale, already-built code.

The API is available at `http://localhost:8080`.

Confirm it is actually up by opening 
[http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
You should see `{"status":"UP"}`.

### Database schema

Flyway migrations live at `src/main/resources/db/migration/`, applied
automatically on startup.

- `V1__create_initial_schema.sql` - all four tables (`users`,
  `time_slots`, `meetings`, `meeting_participants`).
- `V2__add_slot_overlap_constraint.sql` - the PostgreSQL exclusion
  constraint (`no_overlapping_slots_per_owner`) that prevents overlapping
  slots for the same owner, added once `slot`'s rule layer existed to
  depend on it. See Concurrency below for why this is a separate
  migration rather than part of `V1`, and why the constraint exists at
  all alongside an application-level check.

## API documentation

Full REST API documentation is generated from the test suite itself via
Spring REST Docs - every request/response example in the docs is verified
against real controller behavior as part of the build, so it can't drift
out of sync with the code the way hand-written documentation can.

Generate it:

```
./gradlew test asciidoctor
```

This copies the generated HTML into Spring's static resources, so once
the app is running (`docker-compose up`), view it at:

```
http://localhost:8080/docs/api-guide.html
```

Currently, documents the `user` context only (`POST /users`,
`GET /users/{userId}`, including the duplicate-email and validation-error
cases). `slot` and `meeting` sections get added the same way, as those
contexts are built.

Or skip the docs and exercise the API directly:

```
./scripts/smoke-test-user.sh
./scripts/smoke-test-slot.sh
```

Self-contained (creates its own prerequisite data), runnable
independently against a running instance.

## Architecture decisions

### Package structure so far

`user` and `slot` are flat CRUD: a single `@Service`, a package-private
Spring Data repository, and DTOs at the HTTP boundary. Neither has a real
invariant complex enough to need isolating from the persistence framework
- a `User` is a name and an email, a `TimeSlot` is a time range and a
  status. `slot` does have real validation logic (five rules, composed),
  but that logic lives in its own `rule` subpackage rather than requiring a
  full hexagonal split - the rule composite pattern gives open/closed
  extension (`@Component` + an interface, no existing rule touched to add
  another) without ports, adapters, or mappers around a context that
  doesn't need them.

### Cross-context boundaries

Every cross-context reference is by UUID only - no JPA relationships span
package boundaries. `slot`'s `OwnerExistsRule` depends on
`UserExistenceChecker`, a narrow port that answers "does this user exist"
and nothing more - it never receives a `User`.

`SlotBookingPort` and `BookedSlot` are built already, ahead of having a
consumer: `meeting` will depend on `SlotBookingPort.markBusy`/`markFree`
once it exists, and will receive `BookedSlot` (id and owner only) rather
than the full `TimeSlot` aggregate - built this way from the start rather
than retrofitted, since the alternative (returning `TimeSlot` directly)
would leak `slot`'s internal domain model across the boundary for no
reason.

### Concurrency: the overlap race

`NoOverlapRule` performs the check that gives clients a fast, meaningful
`409` in the common case, but it reads before it writes - two concurrent
requests can both pass it before either commits. A PostgreSQL exclusion
constraint (`EXCLUDE USING gist`, on `owner_id` and a `tstzrange` of the
slot's time range) is what actually guarantees the invariant. Both the
rule's check and the constraint's violation surface as the same
`ConflictException` to a client, via `ConstraintViolationTranslator` -
matching on the actual constraint name from the JDBC exception's cause
chain, not just exception type, so an unrelated constraint violation
doesn't get mislabeled as an overlap conflict.

### Why PostgreSQL, not MySQL/MariaDB

The exclusion constraint above is the load-bearing reason. Exclusion
constraints combined with range types are a PostgreSQL feature; neither
MySQL nor MariaDB has an equivalent, and the closest alternative - a
trigger - is weaker both in practice (bypassable by bulk operations) and
as something to reason about under concurrent load. Secondary reasons: a
native `UUID` column type and native `tstzrange`, which the exclusion
constraint's overlap operator depends on directly.

### Spring Data Specifications over `@Query`/JPQL

Availability queries compose multiple optional filters (owner, time
window, status). Specifications with the Hibernate static metamodel
(`hibernate-processor`, generating `TimeSlot_`) keep these type-safe and
composable, catching a renamed field at compile time rather than a
typo'd JPQL string failing at runtime. `SlotSpecifications.hasStatus()`
and `excludingId()` return `cb.conjunction()` for "no filter" rather than
`null`, since Spring Data JPA 4.x's `Specification.and()` rejects a null
argument.

## Testing strategy

Unit tests (JUnit 5 + Mockito, AssertJ assertions) cover services with
collaborators mocked - currently written for `user` only. Controller/
HTTP-contract tests use `@WebMvcTest` with the service layer mocked,
doubling as the source for the generated REST Docs snippets - also
`user` only so far. `slot`'s unit tests and REST Docs are pending.

Deliberately not included: Testcontainers, or any test that hits a real
database. Whether a database constraint actually fires under concurrent
load, and whether the full Spring context wires correctly end to end, are
questions answered instead by the shell scripts (`smoke-test-*.sh`,
and eventually `race-test-*.sh`) against a real running instance - a
deliberate choice to keep the unit test suite fast and the concurrency
proof honest.

## Known limitations (so far)

- **Pagination.** `GET /users/{userId}/availability` is limit-only
  (capped at 500 results) rather than paginated. Cursor (keyset)
  pagination on `startTime` is the natural upgrade if dense windows
  become common.
- **No authentication/authorization.** Any client can act as any user or
  manage any slot. Out of scope for this assignment, but a real
  deployment would need this before anything else.
  This section grows as `meeting` is built and remaining gaps become known.



