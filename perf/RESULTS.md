# Viewport query benchmark — 100k events worldwide

Fixture: `PerfSeedRunner` (Spring profile `perfseed`), 100,000 events — 85% clustered
around 45 world cities with weighted density (NYC/Tokyo ≈ 5–6k each, gaussian spread),
15% uniform worldwide; ~8% resolved, ~5% expired; scores 0–40 skewed low. Localhost,
sequential requests, 30 runs per scenario after 3 warmups, via `viewport_bench.py`.

## Current numbers (clustered viewport, `work_mem=32MB`) — 2026-07-19

The viewport endpoint returns full coverage: isolated notes as `items`, dense cells as
`clusters` (count + centroid + member envelope), aggregated on a world-anchored grid
of ~64-px cells (`45/2^zoom` degrees). `total` is exact (sum over cells).

| scenario | items | clusters | total | p50 ms | p95 ms | max ms |
|---|---|---|---|---|---|---|
| NYC metro, z12 | 496 | 888 | 4289 | 75.0 | 90.0 | 94.4 |
| NYC core, z13 | 546 | 619 | 2163 | 57.8 | 64.2 | 72.4 |
| NYC block, z16 | 42 | 1 | 44 | 9.3 | 11.7 | 19.0 |
| Tokyo core, z13 | 901 | 604 | 2366 | 81.4 | 90.0 | 120.9 |
| Oslo (medium), z13 | 219 | 60 | 353 | 19.2 | 20.4 | 21.1 |
| Pacific (empty), z13 | 0 | 0 | 0 | 4.4 | 4.9 | 5.9 |
| Europe, z5 | 170 | 65 | 17125 | 88.3 | 91.2 | 93.2 |
| Whole world, z2 | 8 | 504 | 89041 | 364.4 | 371.8 | 380.2 |
| NYC + 2 types | 428 | 83 | 601 | 33.8 | 35.4 | 36.2 |
| NYC + applyable | 512 | 249 | 1066 | 42.1 | 46.6 | 59.4 |
| NYC + text search | 2 | 0 | 2 | 21.4 | 22.6 | 22.9 |
| NYC + tag filter | 0 | 0 | 0 | 4.8 | 14.7 | 31.1 |
| World + text search | 0 | 1 | 162 | 485.1 | 500.2 | 501.5 |

Note on the bench bboxes: the "core" scenarios use generous city rectangles, several
times wider than a real 1280-px viewport at that zoom, so their items/clusters counts
overstate what a browser ever requests. A true viewport is bounded by
`(width/64) × (height/64)` grid cells (≈ 200–300 features), regardless of data size.

Plans (`EXPLAIN ANALYZE`, world z2): Seq Scan (correct — the bbox covers everything)
into a HashAggregate, 3.3 MB in memory, ~133 ms in Postgres; the rest of the ~360 ms
is JSON serialization + HTTP. Dense-city queries ride the GiST index (Bitmap Index
Scan) as before. With the default 4 MB `work_mem` the planner misestimates the
distinct `floor()` groups (87k est vs 512 actual), falls back to GroupAggregate and
spills a 6 MB sort to disk — hence `work_mem=32MB` in both compose files.

## History: the pre-clustering design (score-ranked top-N + per-viewport thinning)

| scenario | p50 ms | p95 ms |
|---|---|---|
| NYC core, z13 | 26.5 | 32.3 |
| Europe, z5 | 122.5 | 129.2 |
| Whole world, z2 | 651.8 | 676.6 |
| World + text search | 1041.6 | 1252.7 |

It was faster at city scale (it returned at most 60 pins and skipped the singles
detail fetch) but was replaced because its thinning grid was derived from the
viewport itself: panning or zooming changed which pins survived inside the unchanged
part of the viewport. The clustered design trades ~30 ms at city scale for stable,
complete coverage — and wins at wide zooms (world: 652 → 364 ms, window sort with
disk spill → in-memory hash aggregate).

## Remaining known costs

- Whole-world requests aggregate every active row (~360 ms at 100k). Bounded and
  spill-free now; if it ever matters: clamp the map's `minZoom` (UI), or pre-aggregate
  coarse zoom levels into a summary table refreshed by the expiry sweep.
