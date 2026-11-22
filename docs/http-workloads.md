# HTTP benchmark workloads

## Status

Two workload levels are accepted (D046). D052 selects native Rust/Axum/Tokio for
Level A. D047 remains proposed for the broader backend architecture and Social Feed.
The M1 Pro hosts backends outside the DUT.

## Level A: synthetic HTTP

The [synthetic backend](../backends/synthetic/README.md) implements HTTP/1.1
`GET /health`, `GET /ready`, and `GET /echo?size=N` with fixed-length bodies.
Instances have independent addresses and can expose an instance identifier.
Each instance shares one precomputed payload across requests. Successful
bodyless GETs use the D038 contract. Response delay and additional configurable
statuses remain outside this milestone.

Use the reference paths in [methodology](methodology.md) to measure sustainable
HTTP RPS, latency, CPU, memory, bytes/s, connection cost, startup and scaling.
Record configuration, body hashes, request/response sizes, instance count and
per-instance diagnostics. Development sizes and bounds remain separate from
the final benchmark matrix.

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

D060 selects native Rust for M1 Pro application backends. The Social Feed
framework and storage controls remain subject to D047 verification. The
[Phase 4 roadmap](implementation-roadmap.md) gates direct-backend and firewall pilots.
