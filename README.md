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
automatically on startup. `V1__create_initial_schema.sql` creates all
four tables (`users`, `time_slots`, `meetings`, `meeting_participants`).
The PostgreSQL exclusion constraint that prevents overlapping slots is
not yet in this migration - it lands in its own migration once `slot`'s
rule layer is built, so the schema change and the code that depends on it
arrive together.

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
```

Self-contained (creates its own prerequisite data), runnable
independently against a running instance.

## Testing strategy

Unit tests (JUnit 5 + Mockito, AssertJ assertions) cover services with
collaborators mocked. Controller/HTTP-contract tests use `@WebMvcTest`
with the service layer mocked, doubling as the source for the generated
REST Docs snippets.

Deliberately not included: Testcontainers, or any test that hits a real
database. Whether the full Spring context wires correctly end to end is
answered instead by the shell scripts (`smoke-test-*.sh`) against a real
running instance - a deliberate choice to keep the unit test suite fast.



