# lamppostal 📍

A map-first community noticeboard — the corkboard by the lamppost, rebuilt for the browser with the warmth of the 2007 internet. Pin a note to a real spot in your neighborhood: a missing cat, a five-a-side game short four players, a free bookshelf, a water shutoff. Neighbors find it by wandering the map, respond privately, and the board quietly does its job.

## Quickstart

```bash
cp .env.example .env
docker compose up -d
make seed
open http://localhost:5173
```

Sign in as `demo@corkboard.local` / `DemoPass123!` (from `SEED_DEMO_PASSWORD`) — the demo resident owns the missing-cat saga, has responses waiting, and a message thread that ends well. Reseed from scratch anytime with `SEED_FORCE=true make seed`.

Without `make`: the `Makefile` documents the underlying commands; each target is a one-liner you can paste.

## What it does

- **The board is a map.** MapLibre GL with hand-drawn pushpin sprites, one color per note type. Every note in view is always represented: dense spots merge into a counted pin on a world-anchored grid, so nothing flickers in or out while you pan or zoom. Clicking a merged pin zooms until it splits — or, when its notes share one exact spot, opens a pick-list.
- **Seven kinds of notes** — lost & found, activities, clubs, help, giveaways, happenings, notices — defined once on the server and served to every client from `GET /api/v1/meta`, which also says which kinds belong to which board (a personal one trades most of them for plans and memories).
- **Respond privately.** Applyable notes open a 1:1 conversation with the author; accept/decline, unread counts, live delivery over a plain WebSocket.
- **Points, hides, reports.** Upvotes drive ranking; hiding is personal; five distinct reports take a note off the board for review automatically (a database trigger enforces it).
- **A lifecycle, not a feed.** Notes expire and can be renewed; a found cat gets a rubber-stamped **RESOLVED** and 48 more hours of glory before the board lets it go. A note may also stay up with no end date at all, until its author takes it down.
- **Three boards, and a board is a place.** The shared neighborhood board lives at `/`; your own personal board — plans, memories, a different vocabulary of note types — lives at `/boards/{you}` and is private until you show it to someone you know; what others have shown you arrives as one feed. The owner is in the path, never a query parameter beside the filters.
- **People, not just notes.** Everyone picks a permanent user ID at sign-up (`@handle`); you can search for someone by name or ID, ask to connect, and chat once they say yes — conversations are between two people, so answering a second note continues the dialogue you already have. A face is clickable wherever it appears and opens a card.
- **Find the street, not just the pin.** An address box on the map proxies a keyless OSM geocoder through the API, so the provider stays swappable and native clients get the same contract.
- **Run by permissions and toggles.** An account is read-only until its address is confirmed; roles carry permissions rather than statuses; keepers work a most-reported-first queue; and parts of the board can be switched off from the admin panel while it runs, reaching every open tab over the socket.
- **2007, lovingly.** Cork, paper and pushpins are the only textures; the chrome is disciplined retro-blue; note titles are handwritten (self-hosted Caveat); avatars are 5×5 pixel identicons.

## Architecture

```
Browser ── React 19 + Vite ── MapLibre GL ── TanStack Query / Zustand
   │  HTTPS /api/v1 (cookie or bearer)          │  WSS /ws
   ▼                                            ▼
api — Kotlin 2 + Spring Boot 3 (Java 21, virtual threads), one process
   auth · roles · events · boards · tags · connections · applications
   messaging · notifications · moderation · feature flags
   springdoc OpenAPI (the contract) · jOOQ · Flyway · @Scheduled expiry sweep
   │  jOOQ (typed SQL, PostGIS via bound fragments)
   ▼
PostgreSQL 16 + PostGIS 3.4 — GiST (scope, location), triggers for counters

   │  NotificationRequested — Avro single-object encoding, so no registry
   ▼
Kafka ── notifier — its own Spring Boot service, its own database, its own
         Flyway, and deliberately no shared module: it owns the email copy
         and the transport (log | SMTP | Resend)
```

**The OpenAPI document is the only shape source.** Kotlin DTOs generate `/api/v1/openapi.json`; the web client's `types.gen.ts` is generated from it and never edited by hand. Native mobile apps (the planned v2) consume the same contract — sessions already travel as bearer tokens, the WebSocket speaks plain JSON, and nothing in any payload is web-specific.

**Why one PostgreSQL and not a document store?** The defining read — every note inside a bounding box on one board, filtered by type/tag/expiry, excluding per-user hides, with dense spots merged into counted clusters — is a spatial index scan plus relational filters plus aggregation: precisely PostGIS's home turf. Around it the data is stubbornly relational (unique votes and applications per user, many-to-many tags, a report threshold that is an aggregate over a child table), and the write paths want transactions ("apply" atomically creates an application, a conversation, a first message and a notification). The only genuinely schemaless data — notification payloads — gets a `jsonb` column, which is Postgres speaking fluent document-store exactly where warranted.

## Development

| Command | What it does |
|---|---|
| `docker compose up -d` | dev stack: db + API + web with hot reload, plus Kafka, the notifier and Mailpit |
| `make seed` | seed through the service layer (idempotent; `SEED_FORCE=true` wipes) |
| `make check` | server + notifier builds and test suites (Testcontainers) + web typecheck + smoke tests |
| `make types` | regenerate `types.gen.ts` from the running API |
| `cd web && pnpm e2e` | Playwright browser regression suite (needs the running, seeded dev stack) |
| `make jooq` | regenerate jOOQ classes after a migration change |

Ten numbered end-to-end specs from the specification (§14.1) run as `@SpringBootTest` against a disposable PostGIS container — from "register → pin → appears in the right viewport" to "five reports take it off the board" — with a mutable `Clock` so expiry tests control time. A build-breaking test walks the live request mappings and fails on any `/api/v1` write endpoint that carries neither a `@PreAuthorize` nor an explicit exemption, which is the default-deny that per-endpoint annotations otherwise lack. On top sits a Playwright suite that drives a real browser through the board, the boards, connections, moderation and the feature toggles; the visual language is still reviewed by eye against spec §10.

## Deploying

```bash
cp .env.example .env      # set DOMAIN, ACME_EMAIL, WEB_ORIGIN, POSTGRES_PASSWORD
./deploy/deploy.sh
```

Single server, HTTPS included: a multi-stage build compiles the SPA and bakes it into the Boot jar, which serves REST, WebSocket and the app from one port; Caddy (`Caddyfile`) sits in front, gets a Let's Encrypt certificate for `DOMAIN` and is the only thing on the network, with the database, Kafka and the notifier behind it. The app answers on exactly one hostname — cookies are host-only and links are built from `WEB_ORIGIN` — so every other name you own goes in `DOMAIN_ALIASES` and is redirected to it. The script preflights the environment, generates the jOOQ sources if they're missing, builds, starts and waits for `/api/v1/health` — or run `docker compose -f compose.prod.yml up -d --build` yourself. Backups, updates, an nginx-instead-of-Caddy variant and the troubleshooting table live in `deploy/DEPLOY.md`.

## Configuration

Every runtime variable lives in [`.env.example`](.env.example) with working dev defaults. Google sign-in is optional: leave `GOOGLE_CLIENT_ID` empty and the button simply doesn't exist.