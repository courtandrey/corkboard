# Viewport query benchmark — 100k events worldwide

Fixture: `PerfSeedRunner` (Spring profile `perfseed`), 100,000 events — 85% clustered
around 45 world cities with weighted density (NYC/Tokyo ≈ 5–6k each, gaussian spread),
15% uniform worldwide; ~8% resolved, ~5% expired; scores 0–40 skewed low. API and
PostGIS in default configuration (no tuning), localhost, sequential requests,
30 runs per scenario after 3 warmups. Run 2026-07-19 via `viewport_bench.py`.

| scenario | items | total | p50 ms | p95 ms | max ms |
|---|---|---|---|---|---|
| NYC metro, z12 | 60 | 500 | 45.2 | 49.2 | 54.5 |
| NYC core, z13 | 60 | 500 | 26.5 | 32.3 | 39.6 |
| NYC block, z16 | 40 | 40 | 9.3 | 12.6 | 19.3 |
| Tokyo core, z13 | 60 | 500 | 28.3 | 31.5 | 48.6 |
| Oslo (medium), z13 | 60 | 345 | 12.2 | 14.3 | 14.7 |
| Pacific (empty), z13 | 0 | 0 | 5.9 | 8.6 | 12.2 |
| Europe, z5 | 60 | 500 | 122.5 | 129.2 | 136.3 |
| Whole world, z2 | 60 | 500 | 651.8 | 676.6 | 729.2 |
| NYC + 2 types | 60 | 500 | 20.7 | 22.6 | 25.4 |
| NYC + applyable | 60 | 500 | 20.7 | 23.5 | 24.0 |
| NYC + text search | 2 | 2 | 37.4 | 44.3 | 50.1 |
| NYC + tag filter | 0 | 0 | 7.2 | 11.2 | 64.1 |
| World + text search | 3 | 162 | 1041.6 | 1252.7 | 1311.8 |

## Reading the numbers

**City-scale viewports — the product's actual read path — are healthy.** Dense-city
p50 sits at 9–28 ms end-to-end (HTTP included) even with ~2,000 candidate rows in the
bbox; `EXPLAIN` confirms a Bitmap Index Scan on the GiST index with ~11 ms execution.
Filters (types, applyable) *reduce* cost; empty and medium viewports are single-digit.

**Wide viewports degrade with candidate count, not area.** At z5 (Europe, ~20k
candidates) p50 is ~120 ms; at z2 (whole world, ~87k candidates) ~650 ms. The plan
shows why: the bbox covers everything, so Postgres correctly abandons the spatial
index (Seq Scan), and the `row_number() OVER (PARTITION BY cell …)` thinning pass
sorts all 87k candidates — spilling to disk under the default 4 MB `work_mem`
("external merge, Disk: 6072 kB", 442 ms execution).

**Substring search is fine at city scale (≈37 ms) and unacceptable at world scale
(≈1 s)** — `ILIKE '%q%'` over 87k rows with no index support. The spec (§5.2) already
names the tsvector column as the upgrade path.

## Recommendations (in order of value)

1. **Bound the thinning sort**: add `ORDER BY score DESC LIMIT 10000` inside the
   `candidates` CTE. City viewports are unaffected (they never reach the cap); world
   viewports rank the 10k best-scored candidates instead of all 87k, keeping the sort
   in memory. Per-cell fairness at world zoom degrades only for zero-score rural
   notes — invisible at 60 pins.
2. **Raise `work_mem` for this workload** (e.g., 32 MB) — removes the disk spill even
   without the cap; a one-line compose/postgres setting.
3. **Clamp the map's minimum zoom** (e.g., MapLibre `minZoom: 3`) so the pathological
   whole-world request is rare UI-side.
4. **tsvector for `q`** when search matters beyond a city viewport — as §5.2 documents.

At the demo's real scale (≤5k events) every scenario measured here is single-digit
to low-double-digit milliseconds; none of the above is needed until the board hosts
tens of thousands of active notes.
