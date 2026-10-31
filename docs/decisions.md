# Research and Engineering Decision Log

This file records significant decisions that affect:

- thesis scope,
- experimental methodology,
- architecture,
- implementation,
- interpretation of results.

Use sequential identifiers:

`D001`, `D002`, `D003`, ...

Possible statuses:

- proposed,
- accepted,
- rejected,
- superseded.

---

## D001 — Thesis topic

**Status:** accepted

### Decision

The thesis topic is:

**Analiza porównawcza wydajności technologii WebAssembly, konteneryzacji i wirtualizacji w realizacji funkcji sieciowych**

English:

**Comparative analysis of the performance of WebAssembly, containerization, and virtualization technologies in implementing network functions**

### Rationale

The topic provides a clear experimental comparison between modern lightweight execution
technology and established virtualization approaches.

---

## D002 — Compared execution technologies

**Status:** accepted

### Decision

The primary comparison will cover:

1. WebAssembly,
2. containers,
3. virtual machines.

### Rationale

These technologies represent three different approaches to workload isolation and execution
with different expected resource and startup overhead.

---

## D003 — Initial network functions

**Status:** accepted

### Decision

The primary network functions will be:

- firewall,
- load balancer.

### Rationale

They provide two useful and understandable network-processing workloads while remaining
small enough to implement experimentally.

The baseline behavior is refined by D017 and D018. Transport/interface details remain open.

---

## D004 — Primary performance dimensions

**Status:** accepted

### Decision

The initial evaluation will include:

- latency,
- throughput,
- CPU utilization,
- memory utilization,
- startup time,
- scalability.

### Rationale

Together these metrics cover both network performance and execution-environment overhead.

---

## D005 — WebAssembly source-language comparison

**Status:** proposed

### Decision

A secondary experiment may compare WebAssembly modules compiled from different source
languages.

Possible candidates:

- Rust,
- C/C++,
- TinyGo.

### Rationale

The source-language toolchain may affect generated WebAssembly code and therefore influence
runtime performance.

### Constraint

This comparison is optional and must not delay the primary comparison between WebAssembly,
containers, and virtual machines.

---

## D006 — Benchmark host and physical roles

**Status:** accepted

### Decision

Use the bare-metal Intel Core i7-6700K desktop (16 GB RAM, SSD) as the primary DUT. The MacBook M4 Max generates traffic, runs the central orchestrator, and collects final artifacts; the MacBook M1 Pro runs backends/receivers. All technologies use the same DUT; Macs are not primary DUTs.

### Rationale

Stable separated roles limit generator/backend contention on the DUT and avoid changing the physical platform between technologies.

---

## D007 — Target CPU architecture

**Status:** accepted

### Decision

Native VNF builds and the common DUT target x86_64. Do not compare Wasm on Apple Silicon against containers/VMs on another architecture.

### Rationale

A common CPU platform reduces architecture differences as a confounding variable.

---

## D008 — Preferred DUT operating system

**Status:** superseded

Superseded by D031 (workloads) or D032 (DUT inventory/OS), as applicable.
Historical rationale is retained below.

### Decision

Ubuntu Server 24.04 LTS is the preferred Linux DUT OS. This accepts the preference, not a claim of completed installation. Exact kernel, guest OS, package versions, and tuning remain to be verified.

### Rationale

A dedicated bare-metal Linux host provides a common baseline for the selected environments.

---

## D009 — Primary WebAssembly runtime

**Status:** accepted

### Decision

Wasmtime is the baseline for the main experiment. Exact version, WASI target/socket integration, and compilation mode remain open.

### Rationale

One baseline runtime keeps the primary comparison manageable; optional runtime comparison is D027.

---

## D010 — Baseline container environment

**Status:** accepted

### Decision

Docker Engine on Linux runs the native Rust VNF logic. CPU affinity/cpuset and explicit memory control will be configured; exact limits remain open.

### Rationale

Native Rust in a controlled container supports shared implementation and resource comparison.

---

## D011 — Baseline virtualization environment

**Status:** accepted

### Decision

Use QEMU/KVM/libvirt with a Linux guest and virtio networking. Run the same native executable or an equivalent build from the same source tree. Account for total VM overhead where appropriate.

### Rationale

A defined VM stack enables reproducible comparison beyond guest process RSS.

---

## D012 — Reference implementation language

**Status:** accepted

### Decision

Rust is the primary VNF language. Share function logic and compile to WebAssembly and native x86_64 where technically feasible. Networking adapters require validation.

### Rationale

Experimental fairness, efficient native code, WebAssembly/WASI portability, low runtime overhead, and mature tooling motivate this choice. Rust is not claimed to be universally the fastest WebAssembly language.

---

## D013 — Network-function processing model

**Status:** superseded

Superseded by D031 (workloads) or D032 (DUT inventory/OS), as applicable.
Historical rationale is retained below.

### Decision

Layer 4 / socket-based networking is the initial preferred model. HTTP is not the primary workload. Raw Ethernet, DPDK, AF_XDP, and eBPF are not baseline requirements. Exact transport/WASI interfaces remain unresolved.

### Rationale

A manageable L4 workload reduces HTTP/framework and low-level packet-processing confounds.

---

## D014 — Traffic-generation tooling

**Status:** proposed

### Decision

TRex is a candidate. Verify NIC/hardware compatibility, workload semantics, and feasibility on the accepted M4 generator role before acceptance. Simpler development tools may be used.

### Rationale

Traffic capacity and compatibility must be demonstrated rather than assumed.

---

## D015 — Measurement tooling and resource allocation

**Status:** proposed

### Decision

Select CPU/memory/latency collectors, process and total-environment accounting, readiness timing, and exact CPU/memory allocations after feasibility/pilot work. No CPU IDs or limits are accepted yet.

### Rationale

Equivalent resources and consistent accounting are necessary for fairness; collectors can introduce overhead.

---

## D016 — Benchmark repetition strategy

**Status:** proposed

### Decision

Multiple repetitions are mandatory. Approximately 30 per scenario is a starting proposal; final count depends on variance and statistical methodology. Use randomized order, preferably randomized/interleaved blocks.

### Rationale

Repeated, interleaved measurements limit variance and environmental drift; 30 is not statistically mandatory.

---

## D017 — Baseline virtual firewall

**Status:** superseded

Superseded by D031 (workloads) or D032 (DUT inventory/OS), as applicable.
Historical rationale is retained below.

### Decision

Use an intentionally small stateless L4 vFW with source/destination addresses, ports, protocol, and allow/drop decisions as potential fields. Exclude DPI, IDS/IPS, connection tracking, NAT, and complex policy engines. Exact rule representation/fields and drop semantics remain open.

### Rationale

Simple filtering supports comparable measured workloads across environments.

---

## D018 — Baseline virtual load balancer

**Status:** superseded

Superseded by D031 (workloads) or D032 (DUT inventory/OS), as applicable.
Historical rationale is retained below.

### Decision

Use a simple L4 round-robin vLB. Exclude TLS termination, weighted routing, sticky sessions, complex health checks, and application-aware routing. Transport/connection handling and backend count remain open.

### Rationale

The study compares execution environments rather than algorithm sophistication.

---

## D019 — mt framework and scenario format

**Status:** accepted

### Decision

Create a fully automated custom master-thesis framework named mt in Clojure. Use EDN for scenario definitions and reusable traffic profiles. Environment adapters expose a common prepare/start/readiness/stop/cleanup lifecycle.

### Rationale

Configuration handling, process/SSH control, metadata, and collection operate outside the measured VNF datapath. See experimental-framework.md.

---

## D020 — Ansible and application/experiment boundaries

**Status:** accepted

### Decision

Ansible configures/provisions hosts: packages, users, SSH, runtimes, networks, services, prerequisites, and tuning. mt deploy handles application/artifact deployment; mt run handles experiment lifecycle. Do not use Ansible as the primary per-benchmark engine.

### Rationale

Separate machine, application, and experiment lifecycles avoid provisioning work inside measurements.

---

## D021 — Controlled backend services

**Status:** accepted

### Decision

Clojure is the planned backend language on the M1 Pro. Design one configurable service; minimal mode is the primary vLB benchmark. Deterministic delayed and cpu-light modes are optional secondary scenarios. vFW may use a simpler TCP/UDP receiver.

### Rationale

Backend language is not the independent variable; deterministic behavior and sufficient capacity limit backend influence.

---

## D022 — Identical traffic profiles and automation

**Status:** accepted

### Decision

Apply the same resolved traffic definition to Wasmtime, Docker, and VM variants. Separate traffic profiles from environment adapters and automate complete experiment execution with mt.

### Rationale

Workload equivalence and automation improve fairness and reproducibility.

---

## D023 — Active readiness and startup measurement

**Status:** accepted

### Decision

Use active correctness-oriented readiness probes. Conceptually startup_time = first_successful_ready_probe - start_command_timestamp. Fixed sleeps do not define readiness. Exact probe and startup boundaries remain open.

### Rationale

Comparable usability events make startup measurements meaningful.

---

## D024 — Raw data and source traceability

**Status:** accepted

### Decision

Raw outputs/logs/metadata are immutable, including failed attempts. Store processed data separately and generate analysis/figures from scripts. Record git rev-parse HEAD for each campaign/run plus environment and scenario metadata.

### Rationale

Measurements must be auditable and reproducible without manual modification.

---

## D025 — Primary cloud usage boundary

**Status:** accepted

### Decision

Use the controlled bare-metal DUT for final primary measurements. AWS/EC2 is auxiliary for development, CI, functional tests, reproducibility trials, or secondary experiments. Changing the primary platform requires evidence and a new decision.

### Rationale

Cloud virtualization, noisy neighbours, and limited CPU/network control can obscure VM and environment overhead.

---

## D026 — Second DUT NIC

**Status:** superseded

Superseded by D031 (workloads) or D032 (DUT inventory/OS), as applicable.
Historical rationale is retained below.

### Decision

Evaluate separate ingress and egress NICs to physically separate generator/backend traffic. A second NIC is not known to exist and is not a hard requirement yet.

### Rationale

Hardware inventory, compatibility, cost, and experimental benefits require evaluation.

---

## D027 — Secondary runtime comparison

**Status:** proposed

### Decision

Optionally compare Wasmtime and WasmEdge if time permits and equivalent interfaces are feasible. This is not mandatory core scope.

### Rationale

Runtime dependence may be useful after the primary comparison works.

---

## D028 — Traffic values and timing matrix

**Status:** proposed

### Decision

Candidate sizes are 64, 256, 512, 1024, and 1500 bytes; TCP/UDP, steady/bursty, and possibly IMIX-like profiles may be evaluated. Exact payload/wire definitions, load levels, rates, warm-up, duration, and cooldown are not finalized. EDN example values are illustrative.

### Rationale

Pilot results and research relevance should determine the final workload matrix.

---

## D029 — Statistical and scalability design

**Status:** proposed

### Decision

Define H1–H3 thresholds, confidence procedures, tests, outlier/exclusion rules, and scaling dimensions before final campaigns. Exact tests and instance/core/connection matrices remain open.

### Rationale

Effect size and practical relevance matter; analysis must suit distributions and avoid retrospective favorable choices.

---

## D030 — Clean Git working tree for final campaigns

**Status:** proposed

### Decision

mt may refuse final thesis campaigns with uncommitted changes. The requirement and exceptions must be specified before implementation. Commit recording remains mandatory under D024.

### Rationale

A commit alone does not identify uncommitted source changes.

---

## D031 — Layer 7 workload direction and implementation order

**Status:** accepted

### Decision

On 2026-10-31 the user selected a Layer 7 firewall first, followed by a Layer 7
load balancer. This replaces the L4 baseline in D013, D017, and D018. The three
execution technologies, Rust reference language, and physical host roles remain accepted.
The user's expectation that L7 will be easier is a planning assumption, not a finding.

### Scope and open details

A minimal HTTP request-filtering proxy and HTTP per-request round-robin load balancer
are proposed interpretations. HTTP version, policy fields, rejection behavior, parser
limits, backend protocol, connection reuse, and Wasmtime integration remain open.
No production WAF, IDS/IPS, TLS termination, or broad body inspection is added.

### Research impact

The change retains RQ1–RQ7 but changes the measured workload to application-message
processing. Parsing, proxying, connection handling, and backend work must be controlled
and reported; conclusions cannot be generalized to raw-packet/L4 forwarding.
This authorizes planning updates, not dependency installation or subsystem implementation.

---

## D032 — Reported DUT OS, NIC inventory, and existing homelab tooling

**Status:** accepted

### Decision and evidence

On 2026-10-31 the user reported AlmaLinux 10.2 installed on the DUT and three NICs.
Use the existing AlmaLinux installation as the current baseline instead of the Ubuntu
preference in D008. D026's unknown second-NIC premise is superseded. These are user
reports, not independently collected host facts. Exact release/kernel, NIC models,
link speeds, active configuration, guest OS, and runtime compatibility remain unverified.

### Existing tooling

The sibling `../homelab` repository contains a Babashka `hl` CLI for power/SSH and
`infra/ansible/playbooks/inspect.yml` for read-only host inspection. Its documentation
states that it does not yet provision or reproduce the host configuration. Reuse this
inspection workflow as appropriate; do not claim DUT provisioning is implemented.
Record the homelab commit and relevant configuration provenance when used for research.
Private inventory and diagnostic output must remain outside committed thesis documents.

### Proposed topology and next gate

Management / generator-facing ingress / backend-facing egress are proposed roles for
the three NICs, subject to adapter, cabling, link-capacity, and routing verification.
No NIC assignment or host configuration is performed by this decision. Resolve the
minimal L7 behavior and common Wasmtime/native interface before implementation.

---

## Remaining decisions

D005 remains the optional Rust vs C/C++ vs TinyGo WebAssembly source-language proposal.
Do not assign a second ID to the same proposal or promote optional work to core scope.
Historical D006–D016 preserve the topics previously reserved in this log; D017 onwards extend it.

Unresolved details include networking/WASI target and APIs, software/guest versions,
NIC topology, backend protocol/capacity, exact resource allocation, generators/collectors,
traffic/timing matrix, readiness/latency boundaries, scaling design, and analysis policy.
Acceptance of a baseline does not imply completed deployment or proven feasibility.
