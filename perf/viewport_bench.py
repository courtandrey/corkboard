#!/usr/bin/env python3
"""Viewport-query latency benchmark against a PerfSeedRunner-loaded API.

Usage:
  1. docker run -d --rm --name corkboard-perfdb -e POSTGRES_DB=corkboard \
       -e POSTGRES_USER=corkboard -e POSTGRES_PASSWORD=corkboard -p 5434:5432 \
       postgis/postgis:16-3.4-alpine
  2. cd server && SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5434/corkboard \
       SPRING_DATASOURCE_USERNAME=corkboard SPRING_DATASOURCE_PASSWORD=corkboard \
       SERVER_PORT=8090 ./gradlew bootRun --args='--spring.profiles.active=perfseed'
  3. same command without the profile to serve the API, then:
  4. python3 perf/viewport_bench.py [http://localhost:8090]
"""

import json
import statistics
import sys
import time
import urllib.parse
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8090"
WARMUP = 3
RUNS = 30

SCENARIOS = [
    ("NYC metro, z12",        dict(bbox="-74.30,40.45,-73.60,41.00", zoom=12)),
    ("NYC core, z13",         dict(bbox="-74.05,40.62,-73.85,40.85", zoom=13)),
    ("NYC block, z16",        dict(bbox="-73.995,40.740,-73.965,40.760", zoom=16)),
    ("Tokyo core, z13",       dict(bbox="139.55,35.55,139.85,35.80", zoom=13)),
    ("Oslo (medium), z13",    dict(bbox="10.60,59.85,10.90,59.97", zoom=13)),
    ("Pacific (empty), z13",  dict(bbox="-150.2,-10.1,-149.9,-9.9", zoom=13)),
    ("Europe, z5",            dict(bbox="-11.0,35.0,30.0,60.0", zoom=5)),
    ("Whole world, z2",       dict(bbox="-180,-85,180,85", zoom=2)),
    ("NYC + 2 types",         dict(bbox="-74.05,40.62,-73.85,40.85", zoom=13, types="help,giveaway")),
    ("NYC + applyable",       dict(bbox="-74.05,40.62,-73.85,40.85", zoom=13, applyable="true")),
    ("NYC + text search",     dict(bbox="-74.05,40.62,-73.85,40.85", zoom=13, q="Perf note 424")),
    ("NYC + tag filter",      dict(bbox="-74.05,40.62,-73.85,40.85", zoom=13, tags="board-games")),
    ("World + text search",   dict(bbox="-180,-85,180,85", zoom=2, q="Reykjavik")),
]


def fetch(params):
    url = f"{BASE}/api/v1/events?{urllib.parse.urlencode(params)}"
    start = time.perf_counter()
    with urllib.request.urlopen(url) as res:
        body = json.load(res)
    elapsed = (time.perf_counter() - start) * 1000
    return elapsed, body


def main():
    print(f"| scenario | items | total | p50 ms | p95 ms | max ms |")
    print(f"|---|---|---|---|---|---|")
    for name, params in SCENARIOS:
        for _ in range(WARMUP):
            fetch(params)
        times, body = [], None
        for _ in range(RUNS):
            elapsed, body = fetch(params)
            times.append(elapsed)
        times.sort()
        p50 = statistics.median(times)
        p95 = times[int(len(times) * 0.95) - 1]
        print(
            f"| {name} | {len(body['items'])} | {body['total']} "
            f"| {p50:.1f} | {p95:.1f} | {times[-1]:.1f} |"
        )


if __name__ == "__main__":
    main()
