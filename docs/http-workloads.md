# HTTP benchmark workloads

## Status

Two levels are accepted (D046); Rust/Axum/SQLite is the preferred, proposed backend
architecture (D047), replacing D021's future Clojure backend direction only if accepted.
Existing Clojure wire fixtures stay intact. Backends are planned on the M1 Pro, outside
the DUT. This study compares execution technologies, not backend languages/databases.

## Level A: synthetic HTTP

Primary basis for isolating execution-environment differences: minimal processing,
configurable fixed-size deterministic bodies and status codes, optional deterministic
delay, and multiple independently addressed instances. Begin with bodyless HTTP/1.1
requests and 200/Content-Length responses compatible with D038. Broader settings remain
unavailable through the current firewall until validated. Retain config, body hashes,
request/response byte sizes, delay, instance count and per-instance diagnostics.

Measure sustainable HTTP RPS, latency distributions, CPU, memory, bytes/s, connection
cost, startup and scaling. Use the reference paths in [methodology](methodology.md).
A minimal deterministic Rust backend is the first implementation priority; Axum suitability
for explicit framing and connection controls must be checked before choosing dependencies.

## Level B: Social Feed API

A bounded research workload, not a complete social network. Preferred Rust + Axum +
SQLite on the M1 Pro; do not reuse its native async runtime as evidence of WASIp2 VNF
compatibility. Endpoint set remains proposed:

| Endpoint | Behavior | Workload value / decision gate |
| --- | --- | --- |
| GET `/feed` | Bounded timeline | Primary read-heavy and response-size case |
| GET `/posts/:id` | Single post | Simple lookup; compare with feed before retaining |
| GET `/users/:id` | Profile | Optional if it adds no distinct processing behavior |
| GET `/users/:id/posts` | User timeline | Optional if redundant with feed |
| GET `/search?q=...` | Bounded deterministic search | Processing-complexity variation; pin query plan/data |
| POST `/posts` | Create post | Mixed read/write; requires request-body forwarding |
| POST `/posts/:id/like` | Update likes | Optional second write pattern; define retry/idempotency semantics |

Start with feed, post lookup and one write; add search only if it adds useful diversity.
Evaluate endpoint necessity before implementation. No authentication platform, uploads,
recommendation engine, production deployment or distributed database is required.
Specify reproducible read-heavy/mixed weights, methods, request/response sizes, burst
schedules, deterministic IDs, random seeds and expected status/body checks. For concurrent
writes, validate invariants rather than assuming a fixed request completion order.

## Database controls and backend saturation

Investigate WAL, transaction size, read/write concurrency, busy timeouts, connection
pooling and checkpoints. WAL permits readers with a writer but still admits only one
writer at a time; it does not remove contention ([SQLite WAL](https://www.sqlite.org/wal.html),
reviewed 2026-11-11). Use local storage, not network-filesystem WAL. Pin the SQLite
version and review applicable upstream fixes before deployment.

Seed from versioned deterministic data; record seed/hash, schema, indexes and database
size. Reset from a verified clean baseline before each repetition outside measurement,
including associated WAL state; specify cache and checkpoint conditions. Prefer a separate
database per backend instance with identical initial contents; record divergence from
writes and avoid shared-database contention as an uncontrolled variable. Record storage
latency, CPU, memory, queue depth, lock/busy errors and per-endpoint timing. Direct-backend
load pilots establish headroom; reduce offered load or reject a configuration when backend
saturation limits the VNF comparison. Backend diagnostics must be inexpensive and stable.

## Firewall compatibility gate

Current source `implementations/firewall/src/http.rs` rejects nonzero Content-Length,
Transfer-Encoding, Expect, Upgrade, HEAD and CONNECT; forwards only bounded HTTP/1.1
200 responses with one Content-Length. `main.rs` processes sequentially, closes both
sides after one request and buffers a complete bounded response. Query strings are
forwarded but excluded from exact path policy matching; dynamic endpoint rules need
explicit policy design, not an assumed route-template matcher.

Essential for mixed Social Feed: bounded request-body forwarding, supported method/status
matrix (including chosen creation/error statuses), response framing and limits, deadlines,
and deterministic rejection of ambiguous/malformed messages. GET-only pilots may precede
these extensions. Keep-alive/multiple requests and connection concurrency require explicit
design gates in [l7-firewall.md](l7-firewall.md). Chunked transfers, pipelining, TLS,
HTTP/2, upgrades and production WAF/body inspection are optional or out of baseline scope;
configure backends/generator to the agreed subset. Reuse parsing/forwarding infrastructure
for vLB where feasible. No workload may silently depend on unsupported semantics.
