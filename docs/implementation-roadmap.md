# Implementation roadmap

Phases are independently verifiable gates. Decision statuses are recorded in the
[decision log](decisions.md). Preserve existing wire suites and all successful and
failed raw attempts throughout.

## Phase 1 — Finalize architecture and methodology

- Objective: resolve a bounded design for the three-technology comparison.
- Prerequisites/dependencies: current D038–D041 evidence and D042–D050 planning revision.
- Tasks: approve backend/monitoring/tool proposals; define HTTP subset, pilot questions,
  accounting boundaries, candidate resources, clocks and validity/exclusion rules.
- Acceptance: decision statuses explicit; no illustrative value silently accepted; remaining
  pilot-dependent questions have owners and gates.
- Artifacts: updated decision log, pilot protocol and resolved architecture checklist.
- Risks: scope growth; confusing accepted requirements with unverified feasibility.

## Phase 2 — Validate network topology

The [topology feasibility report](network-topology.md) records the read-only
inventory and proposed layout. Physical link and path verification remain open.


- Objective: establish reproducible M4 → VNF/DUT → M1 traversal and monitoring access.
- Prerequisites/dependencies: phase 1 host roles; existing inventory.
- Tasks: inspect cables/adapters/three NICs, negotiated speeds, routing/MTU and switches;
  compare separate ingress/egress/management against existing LAN; design TAP/bridge or
  explicit routed virtio; validate controls and deny/no-bypass observations.
- Acceptance: topology recorded and accepted; capacity verified; management path known;
  no unrecorded NAT/SLIRP forwarding in proposed performance path.
- Artifacts: inventory delta, diagram, reproducible configuration plan and connectivity evidence.
- Risks: historical 100 Mb/s link, missing carrier, shared bandwidth, privilege requirements.

## Phase 3 — Deterministic HTTP backend foundation

**Status:** complete for local development on the M1 Pro. Wire tests and native
D038 forwarding passed. The existing LAN IP was tested from the same host.

- Objective: minimal reproducible synthetic backend on the M1 Pro.
- Scope: localhost and opt-in home LAN development under D052. Final Phase 2
  topology is a campaign dependency.
- Implementation: bounded deterministic responses, readiness endpoints and
  concurrent requests across independently configured listeners.
- Acceptance: deterministic wire responses, clean lifecycle, initial D038
  compatibility, and interfaces for readiness and future capacity checks.
- Artifacts: [source, contract and validation instructions](../backends/synthetic/README.md).
- Capacity measurements and generator supervision are Phase 4 work.

## Phase 3.5 — Distributed control plane architecture

The accepted design and contract are documented in [control plane](control-plane.md)
and [protocol v1](../protocol/v1/README.md). Phase 3.5 authorizes documentation;
Phase 4 is implementation work. Phases 4A–4D use local addresses and fixtures and
need neither the final Ethernet topology nor dedicated monitoring hardware.

## Phase 4 — Distributed orchestration and k6 feasibility

### Phase 4A — Controller Foundation

- Prerequisites: Phase 3.5 decisions; verify Java 25 target and compatible JVM
  dependencies locally before pinning.
- Deliverables: JVM source root, Integrant lifecycle, configuration, SQLite WAL
  migrations, pure transitions, single-writer coordinator, bounded virtual-thread
  effects and authenticated loopback Management API. First management surface
  exposes service health and operational status and accepts test commands only
  through integration fixtures.
- Acceptance: fresh and existing stores migrate safely, concurrent commands serialize,
  saturated admission is bounded, failures are correlated and persisted, and
  unauthenticated or non-loopback management requests cannot operate the service.
- Risks: incompatible libraries or SQLite durability behavior; deadlock under saturation.
- Validation: run the pinned local integration suite with a fresh temporary store,
  replay migration against the prior schema fixture, saturate queue/effects with small
  configured limits, restart against the same store and compare durable outcomes.

Before implementing each corresponding endpoint in Phases 4B–4D, add shared
positive and negative protocol fixtures under `protocol/v1/fixtures`. Both
Clojure and Rust validation use those fixtures. Include request/response shapes
and ordered exchanges for behavior that depends on prior messages.

### Phase 4B — Agent Foundation

- Prerequisites: 4A; verify Rust/TLS/schema library compatibility and pin versions.
- Deliverables: native Rust skeleton, local certificate provisioning procedure,
  authenticated Agent API, registration, capability negotiation, heartbeat and idle
  long poll.
- Acceptance: correct agent authenticates; bad server identity, untrusted client,
  deactivated identity and unauthorized capabilities are rejected; heartbeats proceed
  while a claim waits; agent needs only outbound connections.
- Required fixtures before endpoints: valid and rejected registration, heartbeat
  and claim, including duplicate requests, conflicting key reuse and stale sessions.
- Risks: certificate mapping, TLS provider or incompatible decoders.
- Validation: generate disposable test credentials outside Git, run both runtimes
  on loopback, apply shared valid/invalid fixtures and identity cases, and record
  observed heartbeat/poll behavior.

### Phase 4C — Distributed Job Lifecycle

- Prerequisites: 4B and durable state; harmless bounded test executor.
- Deliverables: reservation, cancellation, typed dispatch, attempt/lease identities,
  process ownership, persisted effects, recovery and reconciliation.
- Acceptance: conflicts cannot dispatch, duplicate starts never spawn twice, lease
  expiry and cancellation stop owned processes, and uncertainty retains reservations.
- Required fixtures before endpoints or behavior changes: authorize-start and
  terminal/reconcile exchanges, duplicate starts, stale sessions and attempts,
  expiry and receipt pruning, reordered lease grants, and cancellation without renewal.
- Risks: crash between spawn and journal update; delayed or reordered grants.
- Validation: use local deterministic clocks and a bounded child process; inject
  dropped replies, restart each side across launch/exit transitions, cut the control
  connection, and verify child termination or durable recovery-required state.

### Phase 4D — k6 Execution

- Prerequisites: 4C; pin k6 binary and trusted synthetic workload after verification.
- Deliverables: local k6 executor, supervised run and failure paths, machine-readable
  results, bounded artifact upload, durable verified receipts and partial evidence.
- Acceptance: local smoke run has matching spool/server hashes and valid results;
  interruption retains partial files; retries do not overwrite; heartbeats remain
  responsive while uploading.
- Required fixtures before endpoints or behavior changes: k6 assignment with
  omitted, valid and invalid VU ceilings; successful and interrupted uploads,
  lost or duplicate durable acknowledgements, and conflicting artifact bytes.
- Risks: k6 output growth, spool exhaustion and filesystem durability differences.
- Validation: run against the local synthetic backend with small documented bounds;
  interrupt processes/transfers and drop receipts; compare bytes and manifests after
  retry or restart and measure control responsiveness.

### Phase 4E — Direct-backend Pilot

- Prerequisites: 4D, available M4/M1 hosts, Phase 3 backend and permitted LAN path.
- Deliverables: remote M4→M1 pilot plan, pinned binaries/scripts, diagnostics and raw runs.
- Acceptance: lifecycle and recovery work across hosts; reuse, offered/achieved rate,
  dropped iterations, VUs and generator/backend/link constraints are characterized
  for the tested path. Final headroom depends on validated data-network capacity.
- Risks: shared or underspeed link, output overhead, generator or backend saturation.
- Validation: record routes, link and environment provenance; repeat bounded open
  arrival-rate runs with specified reuse and rates; compare errors, utilization and
  throughput, and reproduce interruption and retrieval on a separate pilot run.

### Phase 4F — Restricted-firewall Pilot

- Prerequisites: 4E, preserved D038–D040 wire evidence, known permitted LAN path
  via DUT and a supported restricted HTTP profile.
- Deliverables: low-load M4→DUT→M1 pilots, allow/deny and body checks, supervision
  evidence, and preliminary performance feasibility findings for Phase 5.
- Acceptance: active readiness and path traversal verified, allowed/denied traffic
  matches supported semantics, cleanup is scoped and reproducible, and bottlenecks
  and limits are recorded. LAN observations alone cannot establish campaign readiness.
- Risks: single-request connection churn, sequential VNF saturation or backend bypass.
- Validation: replay the existing correctness commands independently; record explicit
  endpoint/version/reuse settings, verify backend sees forwarded traffic and no
  denied traffic, compare against 4E controls and preserve all pilot artifacts.

## Phase 5 — Firewall concurrency and HTTP compatibility

- Objective: comparable, sufficiently concurrent HTTP behavior across environments.
- Prerequisites/dependencies: phase 1 contract and phase 4 pilot findings; pinned toolchain.
- Tasks: feasibility-test candidate concurrency on native/WASIp2 first; select simplest
  shared bounded design; implement only approved body/status/framing/reuse requirements;
  retest Podman/KVM and external topology.
- Acceptance: old wire cases preserved; deterministic concurrent/slow-client/body/framing/
  deadline tests pass with parity; throughput feasibility and memory bounds demonstrated
  without claiming final performance results.
- Artifacts: new decision/spec, shared implementation and retained four-variant evidence.
- Risks: WASIp2 poll/readiness differences, thread/Tokio incompatibility, unbounded buffers.

## Phase 6 — Monitoring validation

- Objective: trustworthy resource samples and lifecycle timing.
- Prerequisites/dependencies: phase 1 D048 approval and available Linux monitoring host;
  phase 2 network. Can overlap phases 3–5; full load monitoring calibration needs phase 5.
- Tasks: select/pin minimal exporters; deploy stack only when authorized; test CPU/memory
  scopes, interval/overhead, direct startup timing, clock uncertainty, retention and exports.
- Acceptance: required metrics complete; constrained host fits measured RAM/storage budget;
  failure injection marks incomplete runs; run_id exports reproducible.
- Artifacts: collector configs, accounting dictionary, overhead/reliability evidence.
- Risks: hardware unavailable, high cardinality, microSD I/O, missed peaks or silent gaps.

## Phase 7 — Automated synthetic campaigns

- Objective: end-to-end mt lifecycle and immutable, analyzable synthetic runs.
- Prerequisites/dependencies: phases 2–6 and accepted pilot-driven measurement settings.
- Tasks: implement approved EDN contract, runtime adapters, remote generator, collectors,
  active readiness, randomized/interleaved order, cooldown/reset and failure preservation;
  separate startup lifecycle. Run pilot campaigns before final campaigns.
- Acceptance: all three technologies and reference paths reproducible; failed/interrupted
  runs retained; cleanup scoped; source/version/config/timing provenance complete.
- Artifacts: mt campaign implementation, scenario definitions, pilot manifests/raw results.
- Risks: launch skew, incomplete collection, unfair networking/resource limits.

## Phase 8 — L7 load balancer

- Objective: second comparable Rust VNF with minimal HTTP balancing behavior.
- Prerequisites/dependencies: phase 5 shared HTTP substrate; phases 3/7 multi-backend tooling.
- Tasks: approve selection/failure semantics, reuse parsing/transport, deterministic routing
  checks, parity across all environments and headroom diagnostics.
- Acceptance: backend selection and response correctness tested; resource/network paths
  documented; ready for synthetic campaigns with identical workload definitions.
- Artifacts: vLB spec/decision, implementation and correctness evidence.
- Risks: connection pooling or backend imbalance introducing environment-specific behavior.

## Phase 9 — Social Feed API

- Objective: bounded realistic workload complementing synthetic isolation.
- Prerequisites/dependencies: D047 approval; phases 3/5/7, and phase 8 for vLB scenarios.
- Tasks: justify endpoint subset; deterministic SQLite seeds/reset, per-instance isolation,
  WAL/checkpoint/storage controls, read-heavy/mixed/burst scripts and expected outcomes.
- Acceptance: chosen endpoints traverse VNFs correctly; reproducible state and writes;
  backend saturation detected and headroom demonstrated for each planned profile.
- Artifacts: API/data contract, seed hashes, backend/workload source and capacity evidence.
- Risks: database contention, nondeterministic write order, workload complexity masking VNF cost.

## Phase 10 — Full experimental campaigns

- Objective: repeated measurements of both VNFs under all three technologies.
- Prerequisites/dependencies: phases 7–9; pilot-justified repetition count, confidence goals,
  sustainable-throughput limits and frozen methodology/settings.
- Tasks: run randomized/interleaved campaigns and controls; verify headroom, collector
  completeness, stable host state and resets; preserve unsuccessful runs and deviations.
- Acceptance: predeclared coverage/validity criteria met; no correctness data relabeled as
  performance; reproducible raw manifests with complete provenance and cleanup status.
- Artifacts: immutable campaign data, exceptions/exclusions and environment snapshots.
- Risks: drift, thermal/power effects, network contention, insufficient independent repetitions.

## Phase 11 — Statistical analysis and thesis results

- Objective: answer unchanged research questions with supported comparisons.
- Prerequisites/dependencies: phase 10 valid datasets and predeclared analysis policy.
- Tasks: scripted validation, run-level confidence intervals/effect sizes, latency/throughput/
  resource-efficiency/scaling analysis; sensitivity checks and limitations.
- Acceptance: every table/plot reproducible; failures/exclusions disclosed; conclusions
  separate measured effects from interpretations and networking/backend confounders.
- Artifacts: analysis scripts, processed data, figures/tables and thesis results/limitations.
- Risks: pseudoreplication, percentile aggregation errors, retrospective selection bias.
