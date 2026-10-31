# L7 Firewall: First Implementation Milestone

## Status and objective

L7 firewall first and L7 load balancer second are accepted under D031. The user
authorized proceeding with the staged work on 2026-10-31. This document specifies
the proposed first functional workload; it does not finalize the measurement matrix.
Protocol details below are implementation proposals until recorded as accepted in
[decisions.md](decisions.md). No performance results have been collected.

The first milestone is an HTTP filtering proxy with equivalent observable behavior
under Wasmtime, a Linux container, and a Linux VM on the common DUT. This is a
restricted research workload, not a production web application firewall.

## Proposed functional contract

| Aspect | First functional milestone |
| --- | --- |
| Protocol | Plain HTTP/1.1 over TCP; no TLS, HTTP/2, upgrades, or CONNECT tunneling |
| Topology | Generator -> firewall listener -> one fixed external HTTP backend |
| Request target | Origin form; match the raw path before `?`, without percent decoding or normalization |
| Policy | Ordered method/path rules; first match wins; explicit default deny |
| Matching | Case-sensitive exact method and exact path; an omitted field matches any value |
| Allowed request | Forward to the configured backend and relay its response |
| Blocked request | Return HTTP 403 with a fixed body; never contact the backend |
| Invalid request | Return HTTP 400 when possible, close the connection, never contact the backend |
| Unsupported feature | Return HTTP 400 and close; never silently reinterpret framing |
| Request body | No nonempty bodies in the first milestone; reject Transfer-Encoding and nonzero Content-Length |
| Connection model | One request per downstream connection and a new upstream connection per allowed request |
| Response | Backend returns a bounded, fixed-length HTTP 200 response; relay status/body with correct framing |
| State | Policy has no session history; transport and parser state still exist |
| Backend failure | Return HTTP 502 and close; record a forwarding error |
| Timeout | Apply explicit configured I/O deadlines; close timed-out connections and record the phase |

Reject conflicting/duplicate Content-Length, invalid Host, ambiguous request lines,
and malformed headers. Parse before policy evaluation. The actual HTTP parser must
be selected and tested after confirming Wasmtime compatibility; do not implement
a permissive ad-hoc parser just to avoid a dependency.

Proposed configurable guard values for functional tests are 8 KiB total request
head, 64 headers, 64 KiB response body, and a 5-second I/O timeout. They are not
accepted final benchmark settings. Specify inclusive boundary behavior in tests;
an oversized request head returns 431 and closes, with no backend request.
An oversized/invalid backend response is a forwarding error, never a successful
request. Enforce the response bound before committing a downstream success response.

Forwarding must use the configured backend, never a client-supplied destination.
Remove hop-by-hop headers, including headers named by Connection, and explicitly
close both connections. Define backend Host rewriting consistently across variants.
Do not add client identity headers or per-request logging to the measured path.

Closing every connection deliberately includes connection setup in the workload.
Persistent connections require a separately documented later profile; results from
this milestone cannot establish keep-alive or high-concurrency scalability.

## Initial correctness cases

| Case | Expected result | Backend observation |
| --- | --- | --- |
| GET /allowed with a matching allow rule | Backend HTTP 200 and exact fixed body | Exactly one request |
| GET /blocked with a deny rule | HTTP 403 | No request |
| Unmatched method/path | HTTP 403 | No request |
| Conflicting rules | First matching rule determines result | Consistent with decision |
| GET /allowed?x=1 | Same policy decision as /allowed | Query retained if allowed |
| Encoded path or different path case | Match raw path exactly | No accidental normalization |
| Malformed request, invalid framing, or body | HTTP 400 and close | No request |
| Oversized request head | HTTP 431 and close | No request |
| Request head fragmented across TCP writes | Same outcome as unfragmented input | No duplicate request |
| Backend unavailable/invalid/too large | HTTP 502 and close | Error retained |
| Slow/incomplete input | Deadline closes connection | No premature forwarding |

Assert backend observations, not just client status codes. Send identical wire
requests to all variants and compare status, body, close behavior, policy decision,
and backend request count. Preserve failed test evidence outside benchmark results.

Readiness requires an allowed request to reach the backend and return the expected
body, plus a denied request that does not reach it. Process existence, systemd
active state, and an open port are insufficient. Reset backend counters after probes.

## Implementation outline and gates

1. Verify DUT inventory and network feasibility; retain a sanitized inventory with
   source timestamp and tooling commit. Select listener/backend addresses privately.
2. Validate Rust `wasm32-wasip2` command components and TCP sockets through Wasmtime.
   First prove accept, connect, read, write, timeout, and clean shutdown. Pin versions
   only after checking the DUT and toolchain. Compilation is outside measurements.
3. Select a parser and shared event/I/O model supported by both targets. Shared Rust
   policy and protocol behavior are required; target adapters must remain explicit.
   A serial functional spike is allowed but is not a final throughput/scalability design.
4. Implement the proxy and minimal external backend, then run the correctness cases.
   Native builds target x86_64 Linux. Apple Silicon functional tests are development
   evidence only. No separate VNF implementation language is introduced.
5. Package the native artifact in the selected container environment and Linux VM;
   repeat equivalent correctness checks before collecting performance measurements.
6. Add the smallest `mt` scenario that automates the full functional lifecycle,
   including readiness, failures, cleanup, and immutable artifact collection.

Expected implementation files, once the feasibility gate passes: Rust workspace
under `implementations/firewall/`, controlled backend under `backends/`, deployment
assets under `infra/`, and Clojure lifecycle/scenario code under `mt/`. No new service
API is required beyond the restricted HTTP contract. Scenario schema must include
policy, endpoints, limits, timeouts, environment, and source/artifact identities.

## Container baseline: Podman and Quadlet

Podman with Quadlet is accepted in D033. Quadlet defines a systemd-managed container;
it does not replace `mt` as the campaign controller. `mt deploy` stages the pinned
image and definition; `mt run` starts/stops the generated service and performs its
own functional readiness probe. Ansible supplies host prerequisites.

Choose rootful/rootless operation and network mode explicitly. Host networking is
a candidate to avoid an additional user-mode packet path, but reduces network
isolation and still differs from a virtio-connected VM. Bridge networking is another
candidate with its own path overhead. Neither is selected here. Record network
helpers, conmon, OCI runtime, cgroup placement, and total environment resource cost.

For experimental units, propose no boot autostart, automatic image updates, or
automatic failure restart. Pre-stage images and generate units before timed starts.
Record the exact service definition and generated unit. Verify resource limits on
the actual container cgroup rather than assuming the service wrapper enforces them.
Startup ends at active readiness, not systemd's notification. Separate prepared-image
container startup from image pull/build and compare documented starting states.

## Verified documentation and open feasibility

Consulted 2026-10-31:

- [Rust target documentation](https://doc.rust-lang.org/rustc/platform-support/wasm32-wasip2.html)
  describes component output and WASIp2 runtime requirements. This supports choosing
  the target as a candidate, not a claim that this firewall has compiled or run.
- [Wasmtime CLI documentation](https://docs.wasmtime.dev/cli-options.html) distinguishes
  command components from `serve`/WASI HTTP components. Prefer validating command
  sockets first so HTTP parsing remains in the shared VNF. Using `serve` would move
  HTTP work into the host and requires a separate fairness decision.
- [Wasmtime network permissions](https://docs.wasmtime.dev/api/wasmtime_wasi/struct.WasiCtxBuilder.html)
  must be configured explicitly. Verify the pinned CLI's equivalent permission flags
  before running; target compilation alone does not grant network access.
- [Podman Quadlet documentation](https://docs.podman.io/en/stable/markdown/podman-systemd.unit.5.html)
  describes generated systemd services and requires cgroup v2.
- [Podman performance guide](https://github.com/podman-container-tools/podman/blob/main/docs/tutorials/performance.md)
  explains why rootless network helpers must be considered in the experiment.

Basic DUT inspection and the bounded serial TCP spike succeeded; see
[dut-inventory.md](dut-inventory.md) and [tcp-feasibility.md](tcp-feasibility.md). Open before
substantial implementation: data-link topology/capacity, exact software versions,
the final concurrency model, parser compatibility, resource controls, network
mode, and acceptance of the detailed protocol contract. Temporary pinned Rust/Wasmtime
tools now support the TCP spike; no images or VMs have been created.
