# First HTTP Firewall: Correctness Evidence

## Outcome

The first restricted HTTP/1.1 firewall is implemented in shared Rust source. On
2026-10-31 it passed **47 wire cases per variant (94 total)** on the AlmaLinux DUT,
compiled for native Linux/musl and WASIp2/Wasmtime. All paired client response bytes
matched exactly. Five native Rust unit tests also passed. This establishes the
tested functional subset, not container/VM parity, concurrency, or performance.

The implementation decision is [D038](decisions.md). It refines the first functional
subset of [the original firewall plan](l7-firewall.md). The backend/client are
Clojure/Babashka fixtures on DUT loopback; final physical roles remain unchanged.

## Parser and shared I/O model

The only Rust dependency is httparse **1.10.1**, checksum-pinned in
[dependencies.lock.json](../implementations/firewall/dependencies.lock.json). Its
archive is verified against the official registry checksum, extracted on the
controller, retained with each attempt, and staged over SSH. Licenses are included
in the retained crate archive. No third-party parser code is manually rewritten.

Both targets compile httparse with no default features and its portable non-SIMD
path. Pinned `rustc` directly builds its rlib and the firewall; no Cargo build script
or C compiler is required. The Cargo manifest declares the same exact dependency
and features for tooling. This parser feature selection is a functional control,
not an accepted final optimization policy or a claim of maximum native performance.

The first server uses one thread and blocking `std::net` operations, handling
connections sequentially. The listener uses the proven nonblocking accept/short
sleep pattern to bound idle waits. No Tokio runtime or target-specific protocol
adapter is introduced. A final concurrency model remains open: current Tokio WASI
socket support requires `tokio_unstable`, and this milestone does not adopt it.

## Implemented contract

- HTTP/1.1 over TCP, with ASCII headers and origin-form request targets.
- Ordered, first-match allow/deny rules for exact method and raw path; `*` matches
  any method/path. Query strings do not affect matching and are retained upstream.
  Paths are not decoded, case-folded, or normalized. Default policy is deny.
- One valid Host is required; the fixed backend address replaces it when forwarding.
  Duplicate Content-Length is rejected even when values agree. Request bodies,
  Transfer-Encoding, Upgrade, Expect, trailers, CONNECT, and HEAD are unsupported.
- Hop-by-hop headers and headers named by Connection are removed. Connection tokens
  naming framing-critical headers are rejected. Upstream Content-Length is rebuilt
  as zero and both directions explicitly use Connection: close.
- Denied requests return 403, invalid/unsupported requests 400, request head/count
  overflow 431, and backend failures 502. Request read timeouts close the connection.
- The backend contract is HTTP/1.1 200 with one bounded Content-Length. Its complete
  declared body is buffered and validated before a successful client response.
  Other backend statuses/framing are rejected in this first subset.
- Exactly one message is processed per connection. Bytes after the first framed
  request/response are discarded when the connection closes, never forwarded as
  another message. This behavior does not depend on TCP coalescing/segmentation.

The resolved [EDN scenario](../experiments/scenarios/firewall-correctness.edn) uses
8192 head bytes, 64 headers, 65536 response-body bytes, a 500 ms exchange deadline,
a 2000 ms accept deadline, and a 20-second supervisor bound per case. A local/error
reply has a separate write budget capped at 250 ms, allowing a bounded 502 response
after backend timeout. These are functional test settings, not final benchmark values.

## Automated verification

`mt firewall check` stages a source snapshot, verifies deployed hashes, builds the
parser/firewall for both targets, runs unit tests, and supervises identical wire cases.
Before each case it sends an allowed `/ready` request through the backend and a
denied `/blocked` request that must not reach it. Backend counters reset after readiness.
Each process has a budget of those two probe connections plus one test connection.

The suite covers allow/deny/default/first-match behavior, raw/query/encoded paths,
method case, fragmented requests, Host validation, malformed request lines, ambiguous
lengths, unsupported protocols/features/bodies, exact and overflowing head/header
limits, hop-by-hop headers in both directions, backend refusal/timeouts/framing/body
errors, request EOF/timeouts, pipelined input, and a maximum-size binary response.
Blocked/invalid requests must not be observed by the backend. Allowed requests must
arrive exactly once with correct target, Host, length, and connection headers.

All cases compare expected status, complete body/framing, connection closure, backend
observations, and clean process exit. The final summary additionally requires matching
response SHA-256 values across native and WASIp2 for every paired case. Source code
for the exact case list is [http_check.clj](../mt/src/mt/http_check.clj).

## Retained attempts and provenance

| Attempt | Observed outcome |
| --- | --- |
| http-1793480217695-42b00488-285c-4362-8164-e0ca215a1616 | Native firewall compilation failed on an iterator borrow lifetime; no HTTP cases ran; logs/summary retained |
| http-1793480419501-26cfe7ad-382b-4f5b-8094-d68631226738 | 46 cases per variant passed; four Rust unit tests passed |
| http-1793480599551-b08231aa-e9fd-4c1d-a109-62f1f9f4df00 | 47 cases per variant passed, explicit response parity passed; five Rust unit tests passed |

The last attempt ran from 21:03:23 to 21:03:33 UTC on 2026-10-31, with Rust 1.99.1,
Wasmtime 50.0.1, and Babashka 1.12.218. The source commit was
`ba1b763d25c47aff953a6637cea99b4bbed5fbdd` with a dirty working tree. Retained source
snapshots and hashes identify the implementation, not the commit alone. Its summary
SHA-256 is `e15aaad8ce464550bc66a77ccf6f265e31ee99f1c68e82e5ea219e1efc29c97d`.

Raw evidence stays under ignored `experiments/local/<attempt>/` and matching temporary
DUT directories. It includes the dependency archive, sources, build commands/versions,
scenario/policy, compiled artifacts, request observations, response bytes/hashes,
case logs, metadata, unit-test output, and successful/failed summaries. Earlier attempts
are preserved without editing. Timestamps are provenance, not performance measurements.

## Reproduction

Prepare the isolated tool directory using [the existing instructions](../mt/README.md).
From `mt/`, using the same existing OpenSSH alias and root used for preparation:

```sh
bb test
bb mt firewall check --ssh-host DUT_ALIAS --remote-root /tmp/mt-tcp-feasibility-clojure
```

The controller needs curl/tar/OpenSSH and access to the pinned crate archive on a
first run. Cached archives are checksum-verified too. The DUT needs no new system
package, Python, container, VM, firewall rule, or service configuration for this check.

## Remaining work

Podman/Quadlet and VM functional parity, external NIC/backend traffic, the final
concurrency design, persistent connections, benchmark resource/measurement controls,
and final campaigns remain unimplemented. There are no throughput/latency/resource
claims from this correctness milestone.

Primary interface references consulted 2026-10-31:
[httparse 1.10.1](https://docs.rs/httparse/1.10.1/httparse/),
[official crate index](https://index.crates.io/ht/tp/httparse), and
[Tokio WASI support](https://docs.rs/tokio/latest/tokio/).
