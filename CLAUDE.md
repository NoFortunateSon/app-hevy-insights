# CLAUDE.md - app-hevy-insights

Read this file first in every session. Owner: Alessio Palomba (GitHub account NoFortunateSon).

## What this project is
A personal service that syncs workout data from the Hevy public API (read-only) into a local Postgres database and computes metrics: loads, volume, frequency, recovery, body measurements. Single user for now, but the design must not prevent multi-user later (see "Data rules").
The decision record is `docs/adr/0003-primo-progetto-hevy-insights.md` (written in Italian). Treat it as the source of truth for scope.

## Language
- Code, identifiers, comments, commit messages, branch names, PR titles and PR descriptions: English.
- Everything you say to Alessio in chat: Italian, direct, no filler. Separate facts, opinions and hypotheses. Never invent data: if something is missing, ask.

## Working rules (non-negotiable)
- Never commit to `main`. GitHub does not enforce this on this private repo (free plan), so you must. Work only on branches named `claude/<issue>-<topic>` and open a pull request.
- A PR is not ready unless the CI is green. Every change comes with tests.
- Never write secrets, tokens, API keys or `.env` files to the repo, logs, tests or PR text. Only `.env.example` with empty placeholders.
- Non-trivial choices (new library, new dependency, data model change, anything costly to undo) are not yours to decide: write a proposal as a new ADR in `docs/adr/` with status `Proposta` (sections: Contesto, Opzioni, Decisione left empty, Conseguenze) and ask Alessio.
- Versions, APIs and prices change: verify them online before using them. Do not rely on memory.
- Do not delete files you did not create. Do not rewrite history.

## Status file (non-negotiable)
Alessio works with a project assistant on claude.ai that cannot see your chat, only what is on GitHub `main`. `docs/STATO.md` is how it knows the state of the project.
- Every PR that changes documentation (`CLAUDE.md`, `README.md`, `docs/`), adds or changes an ADR, changes dependencies, the data model, the CI, or finishes/starts a feature must also update `docs/STATO.md` in the same PR. A PR that changes only a typo or a comment does not need it.
- Keep it short and factual, in Italian (like the ADRs), with the sections already in the file. Replace stale lines instead of appending history. Date each update (YYYY-MM-DD).
- Facts only: what is merged, what was verified (and how), what was NOT verified, open PRs/issues, decisions waiting for Alessio. Mark hypotheses as such. Never include secrets.
- Do not claim "CI green" or "tested" unless you read the result of the run; write the PR/run you looked at.
- If you could not update it, say so in the PR description.

## Coding guidelines (non-negotiable)
Follow `docs/linee-guida.md` (design rules distilled from the books Alessio owns) in every change and tick its checklist before calling a PR ready. If a rule clashes with another rule or an ADR, do not pick: write an ADR with status `Proposta`. Extend the guidelines only from books you actually read, and say which.

## Stack baseline (ADR 0001 and 0002 of the workspace)
- Java 25 (OpenJDK builds only), Spring Boot 4.1, Maven (no Gradle), Postgres.
- Maven `groupId`: `io.github.nofortunateson`. Root package: `io.github.nofortunateson.hevyinsights`.
- Pin the exact Spring Boot 4.1.x and any Spring Modulith / Spring AI version only after checking the official project pages; the build must pass.
- Local ports: Spring apps use 8080-8099. Local services come from the shared compose in the `platform-infra` repo (one database and one user per project).

## Architecture
Three layers (ADR 0007), as packages under the root package. Dependencies only go downwards, `presentation` → `business` → `data`, without skipping a layer; `LayerDependencyTests` enforces it.
- `presentation`: read-only REST over the metrics.
- `business.sync`: incremental synchronisation (first full load, then only changes). Stores the original JSON and normalised rows through `data.db`.
- `business.metrics`: computes metrics from stored data only. Never calls Hevy (must not use `data.hevy`).
- `data.hevy`: calls to the Hevy API, pagination, HTTP 429 handling. No domain logic. The only package that knows the Hevy API shape, because Hevy states it may change or drop the API.
- `data.db`: access to the app's Postgres tables. Does not know `data.hevy`, and vice versa.
Keep each package deep with a small interface and hide design decisions inside it. Do not extract shared libraries until the same code is needed in at least two apps.

## Data rules
- Every table has a `user_id` column from day one.
- The Hevy API key comes from the environment variable `HEVY_API_KEY`. It is equivalent to a password: never log it, never commit it, never put it in test fixtures.
- Hevy access is read-only. Do not call create/update endpoints.
- Metric definitions are proposals until Alessio confirms them on his real data. "Recovery" depends on whether the API exposes rest times: check on real data before promising it.

## Testing
- Contract tests for `data.hevy` against recorded responses. No call to the real Hevy API in CI.
- Metric tests with invented data.
- Integration tests with a real Postgres via Testcontainers (ADR 0004). The shared compose from `platform-infra` is only for running the app locally, never for tests.
- Schema migrations with Flyway at the version managed by the Spring Boot BOM (ADR 0004). Never edit a migration that has already been applied: add a new one.

## Open points (do not decide alone)
- How Claude is integrated (MCP server, Anthropic API, or chat only): a later ADR.
- Whether it also runs in Kubernetes: not now, local only.
