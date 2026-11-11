# Thesis Scope

## Core scope

The thesis compares three execution technologies:

- WebAssembly,
- containers,
- virtual machines.

The comparison is performed using selected network functions.

The initial functions are:

- Layer 7 virtual firewall (`vFW`),
- Layer 7 virtual load balancer (`vLB`).

## Primary metrics

The initial mandatory metrics are:

- latency,
- throughput,
- CPU utilization,
- memory utilization,
- startup time,
- scalability.

The exact definitions and measurement procedures will be specified in
`methodology.md`.

## In scope

The following are part of the intended thesis scope:

- design of a reproducible benchmark environment,
- implementation of selected network functions,
- WebAssembly-based execution,
- container-based execution,
- virtual-machine-based execution,
- controlled traffic generation,
- performance measurement,
- resource-utilization measurement,
- analysis of benchmark results,
- statistical comparison,
- identification of practical advantages and limitations.

## Potential extensions

The following topics may be investigated only after the primary comparison is operational.

### WebAssembly source-language comparison

It may be useful to determine whether the source language used to generate WebAssembly
has a measurable impact on performance.

Potential candidates include:

- Rust,
- C/C++,
- TinyGo.

This is a secondary experiment.

It must not delay the primary WebAssembly vs container vs virtual-machine comparison.

### Multiple WebAssembly runtimes

Wasmtime is the baseline. A Wasmtime vs WasmEdge comparison may be useful if runtime selection proves to have a significant
effect on network-function performance.

This should initially be treated as optional.

### Different traffic characteristics

Possible secondary dimensions include:

- packet size,
- request rate,
- connection count,
- short-lived vs persistent connections,
- bursty vs steady traffic.

Only dimensions that support the research questions should be included.

## Currently out of scope

Unless explicitly added later, the thesis does not aim to implement:

- a production NFV platform,
- a production SDN controller,
- a complete orchestration system,
- a custom hypervisor,
- a custom WebAssembly runtime,
- a custom container runtime,
- a general-purpose cloud platform,
- a service mesh,
- Kubernetes-based orchestration,
- automatic placement algorithms,
- production-grade firewall software,
- production-grade load balancer software.

The network functions are research workloads.

Their purpose is to provide representative and measurable packet or request processing
behavior rather than production completeness.

## Scope-control rule

Any proposal that introduces a major new technology, experimental dimension, or network
function should answer:

1. Which research question does it help answer?
2. Does it improve the validity of the study?
3. How much implementation and benchmarking complexity does it add?
4. Can the thesis answer its primary research question without it?

If the answer to question 4 is yes, the feature should generally remain optional.

## Accepted execution and automation scope

The primary VNF language is Rust, sharing logic across Wasmtime WebAssembly and native
x86_64 Podman / QEMU/KVM/libvirt variants where feasible. All run on the same
bare-metal x86 Linux DUT; AlmaLinux 10.2 is the verified installed OS and current baseline. Four host roles are defined in [architecture.md](architecture.md): M1 controller/backend/storage,
M4 generator, common DUT and planned monitoring host (Raspberry Pi model unspecified).

Clojure `mt` with EDN scenarios automates execution; Ansible configures hosts. Controlled
Clojure correctness fixtures remain. D047 proposes Rust/Axum/SQLite application backends
outside the DUT. Synthetic HTTP and complementary Social Feed workloads are defined in
[http-workloads.md](http-workloads.md).
AWS/EC2 may support development, CI, functional/reproducibility tests, or auxiliary
experiments, but not the primary final measurements without a new justified decision.

Layer 7 workloads are the current direction, firewall first and load balancer second.
A narrow HTTP/1.1 subset is implemented for the first firewall correctness milestone
(D038). The final concurrency model, deployment paths, and benchmark matrix remain open.
Raw Ethernet processing, DPDK, AF_XDP, and eBPF are not baseline requirements.
The baseline excludes IDS/IPS, NAT, complex firewall policy engines, TLS termination,
weighted routing, sticky sessions, and complex health checks. Minimal application-layer
inspection is now required; broad body inspection and production WAF features remain
out of scope. Transport connection state must be distinguished from policy state.

## Provisional design values

NIC role assignment, TRex, size/load sets, approximately 30 repetitions, precise resource
allocation, and statistical tests remain proposed. Consult [decisions.md](decisions.md).
Optional source-language or runtime experiments must not delay the primary comparison.
The user authorized the staged L7 firewall plan; inventory/specification and networking
feasibility precede substantial implementation. See [l7-firewall.md](l7-firewall.md).

Edge computing is application context for constrained resources, memory and startup, not
a new dimension of edge placement/distributed scheduling. Backend languages and databases
are controlled workload infrastructure, not independent comparison subjects.
