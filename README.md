# lamppostal 📍

A map-first community noticeboard — the corkboard by the lamppost, rebuilt for the browser with the warmth of the 2007 internet. Pin a note to a real spot in your neighborhood: a missing cat, a five-a-side game short four players, a free bookshelf, a water shutoff. Neighbors find it by wandering the map, respond privately, and the board quietly does its job.

Built as a portfolio project, milestone by milestone, from a [full technical specification](spec.md). The spec's internal codename is **Corkboard**; lamppostal is the face it wears.

<!-- screenshot: the seeded NYC board — docs/board.png -->

## Quickstart

```bash
cp .env.example .env
docker compose up -d          # PostGIS + API (Gradle bootRun) + Vite dev server
make seed                     # 26 residents, ~300 notes in NYC & Rotterdam
open http://localhost:5173
```

Sign in as `demo@corkboard.local` / `DemoPass123!` (from `SEED_DEMO_PASSWORD`) — the demo resident owns the missing-cat saga, has responses waiting, and a message thread that ends well. Reseed from scratch anytime with `SEED_FORCE=true make seed`.

Without `make`: the `Makefile` documents the underlying commands; each target is a one-liner you can paste.

## What it does

- **The board is a map.** MapLibre GL with hand-drawn pushpin sprites, one color per note type, spatially thinned server-side so a dense downtown never drowns out your quiet street.
- **Seven kinds of notes** — lost & found, activities, clubs, help, giveaways, happenings, notices — defined once on the server and served to every client from `GET /api/v1/meta`.
- **Respond privately.** Applyable notes open a 1:1 conversation with the author; accept/decline, unread counts, live delivery over a plain WebSocket.
- **Points, hides, reports.** Upvotes drive ranking; hiding is personal; five distinct reports take a note off the board for review automatically (a database trigger enforces it).
- **A lifecycle, not a feed.** Notes expire and can be renewed; a found cat gets a rubber-stamped **RESOLVED** and 48 more hours of glory before the board lets it go.
- **2007, lovingly.** Cork, paper and pushpins are the only textures; the chrome is disciplined retro-blue; note titles are handwritten (self-hosted Caveat); avatars are 5×5 pixel identicons.

## Architecture

```
Browser ── React 19 + Vite ── MapLibre GL ── TanStack Query / Zustand
   │  HTTPS /api/v1 (cookie or bearer)          │  WSS /ws
   ▼                                            ▼
Kotlin 2 + Spring Boot 3 (Java 21, virtual threads) ── one process
   auth · events · tags · applications · messaging · notifications · moderation
   springdoc OpenAPI (the contract) · jOOQ · Flyway · @Scheduled expiry sweep
   │  jOOQ (typed SQL, PostGIS via bound fragments)
   ▼
PostgreSQL 16 + PostGIS 3.4 — GiST spatial index, triggers for denormalized counters
```

**The OpenAPI document is the only shape source.** Kotlin DTOs generate `/api/v1/openapi.json`; the web client's `types.gen.ts` is generated from it and never edited by hand. Native mobile apps (the planned v2) consume the same contract — sessions already travel as bearer tokens, the WebSocket speaks plain JSON, and nothing in any payload is web-specific.

**Why one PostgreSQL and not a document store?** The defining read — top-N notes by score inside a bounding box, filtered by type/tag/expiry, excluding per-user hides — is a spatial index scan plus relational filters plus ranking: precisely PostGIS's home turf. Around it the data is stubbornly relational (unique votes and applications per user, many-to-many tags, a report threshold that is an aggregate over a child table), and the write paths want transactions ("apply" atomically creates an application, a conversation, a first message and a notification). The only genuinely schemaless data — notification payloads — gets a `jsonb` column, which is Postgres speaking fluent document-store exactly where warranted.

## Development

| Command | What it does |
|---|---|
| `docker compose up -d` | dev stack: db + API + web with hot reload |
| `make seed` | seed through the service layer (idempotent; `SEED_FORCE=true` wipes) |
| `make check` | server build + all e2e specs (Testcontainers) + web typecheck + smoke tests |
| `make types` | regenerate `types.gen.ts` from the running API |
| `make jooq` | regenerate jOOQ classes after a migration change |

Ten numbered end-to-end specs from the specification (§14.1) run as `@SpringBootTest` against a disposable PostGIS container — from "register → pin → appears in the right viewport" to "five reports take it off the board" — with a mutable `Clock` so expiry tests control time. Frontend testing is smoke-level by design; the visual language is reviewed by eye against spec §10.

## Production build

```bash
docker compose -f compose.prod.yml up --build
```

One container, one port: a multi-stage build compiles the SPA, bakes it into the Boot jar's static resources, and serves REST, WebSocket and the app from `:8080`. Run `./gradlew jooqCodegen` once on the host first (codegen needs Docker and can't run inside the build).

## Configuration

Every runtime variable lives in [`.env.example`](.env.example) with working dev defaults. Google sign-in is optional: leave `GOOGLE_CLIENT_ID` empty and the button simply doesn't exist.