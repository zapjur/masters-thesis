# Distributed experiment control plane

Phase 3.5 defines the target design. Phase 4A implements the local JVM controller
foundation on M1 Pro. It provides authenticated loopback health and status, a
SQLite operational store, a single state writer and bounded fixture effects.
See the [controller guide](../mt/controller/README.md) for the executable
configuration and limits. Campaign execution, native agents, remote k6 pilots
and deployed monitoring remain planned.

The functional `mt feasibility tcp` and `mt firewall check` entry points,
including Quadlet and KVM options, stage pinned source/tools over SSH, exercise
DUT loopback fixtures, collect run-owned evidence and perform scoped cleanup.
KVM's functional adapter uses SLIRP forwarding. The synthetic Rust backend has
local development evidence.

## Deployment and boundaries

| Plane | Location and responsibility |
| --- | --- |
| Control | M1 Pro runs the Babashka CLI, long-running JVM controller, SQLite and central research artifact filesystem. CLI connects to its authenticated loopback Management API. |
| Execution | M4 Max runs the unprivileged native Rust agent and local k6. M1 Pro runs native Rust backends. The controller uses local process adapters on M1 and existing scoped SSH/runtime lifecycle mechanisms for DUT execution. Ansible provisions hosts outside the experiment loop. |
| Data | M4/k6 sends HTTP directly to the M1 backend for controls or to the selected VNF on the common DUT, which forwards to M1. Benchmark traffic never traverses the controller. |
| Observability | A separate planned Raspberry Pi hosts preferred Prometheus/Grafana. Scrapes and controller exports use the management network. Hardware and physical data links still require verification. |

The agent opens every connection to the HTTPS Agent API over the management network.
Ordinary k6 execution requires neither an inbound listener nor inbound SSH nor administrator
privileges. LAN pilots can precede validation of the isolated Ethernet data segments.
Final campaigns and final headroom conclusions require validated data-path capacity.
Build, deploy and bulk collect outside measured intervals where practical. Measure
controller/backend contention on the shared M1 and agent/output overhead on M4.

## Controller modules

| Module | Responsibility |
| --- | --- |
| Domain | Pure invariants, transitions and outcome classification |
| Scenario and planner | Validate EDN, resolve defaults, expand and order immutable campaign plans |
| Scheduler | Scan the run queue and acquire all declared exclusive resources atomically |
| State coordinator | Serialize commands, ticks and effect outcomes; own transition ordering and writes |
| Effect executor | Run bounded I/O outside transactions and return typed outcomes |
| Agent registry | Bind verified identities, authorized capabilities and health to dispatch eligibility |
| Management and Agent APIs | Authenticate, validate, translate and return bounded responses; keep HTTP types outside the domain |
| Persistence and artifacts | Migrate and transact operational state; stream, verify and finalize filesystem evidence |
| Execution adapters | Run-owned local processes, scoped SSH/runtime actions and typed agent jobs |
| Component lifecycle | Start dependencies, reconcile durable state, enable dispatch, drain or stop on shutdown |

Use a modular monolith with Functional Core, Imperative Shell. A pure
`transition(state, event) -> {state, effects}` accepts domain values and explicit
timestamps and IDs supplied at the boundary. Domain code imports no HTTP, SQL,
transport DTOs or process execution. Persist the updated state, relevant event and
effect intents together before exposing success. A read snapshot is a cache of
committed state. SQLite is authoritative. External effects execute after commit
and report outcomes back through the coordinator. See [persistence](control-plane-persistence.md).

Phase 4A uses Java 25 LTS on Apple Silicon. The controller stack is Clojure
on JVM with deps.edn, Integrant lifecycle, Malli boundary validation, Ring/Jetty,
Reitit, Java BlockingQueue, virtual-thread executors, next.jdbc and SQLite JDBC in
WAL mode. The Phase 4A implementation pins and exercises the local JVM stack.
Agent TLS and wider deployment compatibility remain Phase 4 work. Exclude
gRPC, WebSockets, brokers, consensus,
microservices and generic workflow/DAG scheduling.

Use bounded event admission with capacity reserved for effect outcomes and urgent
control events. Limit and expire long-poll waiters separately. A waiting HTTP claim
or upload never holds the coordinator or a database transaction. Coalesce internal
ticks and bound payloads. Reject overloaded requests with retryable responses.
Bound effect permits globally and by class (local process, SSH, artifact I/O),
with separate control capacity so heartbeats/cancellation survive bulk uploads.
A virtual thread still consumes process, socket and storage capacity. Start effects
only with permits. Reserve outcome capacity before dispatch; persist pending intents
if the system has no capacity. Apply operation deadlines, bounded retries with jitter
for safe effects, cancellation and correlated failure reporting. A timeout signals
uncertainty until the adapter verifies termination. On shutdown stop admitting work,
retain reservations, drain bounded work and leave unresolved intents for reconciliation.

## Native agent

A portable native Rust binary with configuration; identity/TLS; controller client;
registration/reconnection; independent heartbeat and lease watchdog; job state
machine; executor interface; local spool; uploads/reconciliation; and shutdown/cleanup.
Preferred dependencies are Tokio, Reqwest with Rustls, Serde/serde_json, Clap, Tracing,
Thiserror and SHA-2, subject to compatibility checks and version pins. Initial operator
capability is `traffic.k6`. A harmless bounded fixture executor is test-only.

Accept typed workload/target references and bounded resolved parameters. The pinned workload defines request classes, methods, paths and expected statuses.
Run the configured k6 binary and locally trusted versioned script, verify workload hash,
start with explicit arguments and sanitized environment, journal run ownership,
supervise the process group, and enforce the shorter of job deadline and lease
authority. Confirm owned descendants stop before reporting termination. Preserve
partial output and keep spooled evidence until durable acknowledgement. Reconcile
unfinished attempts before claiming again. Executor extensions are a future design
seam. Backend, container, WASM and VM agent executors are outside Phase 4. Measure
idle CPU, process memory and artifact volume on target hosts.

## Repository evolution

- Keep `mt/bin/mt`, `mt/bb.edn`, `mt/src` and existing tests and fixtures. Existing
  local commands retain their entry points and evidence layout.
- Phase 4A adds `mt/deps.edn`, `mt/controller/src` and `mt/controller/resources` for JVM-only
  source and migrations. Put new portable code under `mt/shared/src` only when
  consumed by both runtimes. Never put JVM-only deps on the Babashka classpath.
- Add the Rust crate under `agents/mt-agent` when implementing it. Keep shared
  OpenAPI, JSON Schema and test fixtures in [protocol/v1](../protocol/v1/README.md).
- Gradually delegate campaign status, submission and cancellation through the
  Management API. Preserve local correctness and existing runtime/bootstrap paths.

See [domain and execution](control-plane-domain.md), [agent protocol](agent-protocol.md),
[security](control-plane-security.md), [persistence and recovery](control-plane-persistence.md)
and the [implementation roadmap](implementation-roadmap.md).

## Planned verification

- Unit-test pure transitions, deterministic expansion and seeded order. Use
  property-based sequences of scheduling, cancellation, expiry and duplicate events
  to verify unique reservations, terminal outcome stability and preservation of
  uncertain ownership.
- Exercise coordinator concurrency, admission backpressure and bounded effects with
  a controllable clock and small configured limits. Inject SQLite rollback and restart
  around transition/effect commits and local process launches.
- Run shared contract fixtures in both languages and integration tests with valid
  and rejected mTLS identities, duplicate/stale messages, expired leases, cancellation
  and verified process-group termination.
- Interrupt artifact transfers and lose commit acknowledgements. Restart each side
  before/after dispatch, start and upload. Partition the control network while a
  bounded child runs; assert deadline termination or durable quarantine, recovery
  reconciliation, and preservation of incomplete measurements. Inspect both storage
  and process state, including lost or absent artifacts.

Management API campaign DTOs, EDN schema and randomization
algorithm, retry and admission budgets, process-group crash containment on macOS,
SQLite/filesystem durability and backup procedures, and generator workload settings
remain implementation or pilot work. Final network, monitoring hardware, headroom,
tool adoption and scientific measurement parameters remain campaign gates.
