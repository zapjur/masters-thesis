# Implementation roadmap

Documentation/planning only. Future implementation requires explicit user approval.
Accepted host/strategy requirements and proposed stack/tool choices are distinguished in
D042–D050. Phases are independently verifiable gates; no benchmark results are claimed.
Artifacts below are expected future outputs, not files already present. Preserve existing
wire suites and all successful/failed raw attempts throughout.

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

- Objective: minimal reproducible synthetic backend on M1.
- Prerequisites/dependencies: phases 1–2; D047 approval before new backend implementation.
- Tasks: fixed bodies/status/sizes, optional delay, multiple instances, bounded HTTP contract,
  version/config/hash capture and inexpensive diagnostics.
- Acceptance: deterministic body/status tests and clean lifecycle; initial D038-compatible
  mode verified; backend readiness and capacity measurable.
- Artifacts: backend source/spec, functional evidence and build/version records.
- Risks: framework framing defaults, logging/storage overhead, controller/backend contention.

## Phase 4 — k6 feasibility and headroom

- Objective: validate generator remote-control contract and achievable offered load.
- Prerequisites/dependencies: phases 2–3; D049 pilot plan; sufficient host diagnostics.
- Tasks: pin k6/scripts, M1 → M4 supervision/recovery, open arrival-rate direct-backend
  pilots and low-load restricted-firewall pilots; inspect errors, dropped iterations,
  VUs, connection behavior and generator/backend utilization.
- Acceptance: repeatable output retrieval including interruption; offered/achieved load
  understood; headroom bounds recorded; final generator adoption decision supported.
- Artifacts: pilot results, hashes, remote lifecycle evidence and capacity envelope.
- Risks: generator limits, connection churn, output overhead, sequential firewall saturation.

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
