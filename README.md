# Master's Thesis

## Topic

### Polish

**Analiza porównawcza wydajności technologii WebAssembly, konteneryzacji i wirtualizacji w realizacji funkcji sieciowych**

### English

**Comparative analysis of the performance of WebAssembly, containerization, and virtualization technologies in implementing network functions**

## Overview

This repository contains the research environment, network-function implementations,
benchmarking infrastructure, experimental results, analysis tools, and supporting materials
for the master's thesis.

The thesis investigates the performance characteristics of three execution technologies:

- WebAssembly,
- containers,
- virtual machines,

when used to implement selected network functions.

The initial functions considered in the study are:

- Layer 7 virtual firewall (`vFW`),
- Layer 7 virtual load balancer (`vLB`).

## Main evaluation dimensions

The initial set of performance metrics includes:

- latency,
- throughput,
- CPU utilization,
- memory utilization,
- startup time,
- scalability.

Final benchmark settings require pilot validation before performance campaigns; functional implementations already exist.

## Research principle

The purpose of the study is not to demonstrate that one technology is inherently superior.

The purpose is to determine experimentally:

- where each technology performs well,
- what overhead each execution model introduces,
- how resource consumption differs,
- how workload characteristics affect the results,
- what practical trade-offs exist.

## Repository structure

```text
docs/
```

Research documentation, thesis scope, methodology, architecture decisions,
literature notes, and terminology.

```text
implementations/
```

Implementations of the selected network functions.

```text
experiments/
```

Benchmark scenarios, experiment execution tooling, and eventually measurement data.

```text
infra/
```

Infrastructure and execution-environment configuration.

```text
analysis/
```

Scripts and tooling used to process measurements and generate statistics and figures.

```text
thesis/
```

Materials directly related to writing the final thesis.

## Accepted baseline

Rust provides shared VNF logic, compiled to WebAssembly for Wasmtime and native x86_64
for Podman with Quadlet on Linux and QEMU/KVM/libvirt Linux guests with virtio networking.
Sharing logic reduces implementation differences; networking equivalence still requires
validation. Rust is not assumed to be universally the fastest WebAssembly language.

All technologies run on the same bare-metal i5-12400 desktop (16 GB RAM, SSD), with
verified AlmaLinux 10.2 and three RTL8125 NICs. The M1 Pro (32 GB) is the primary
Clojure/Babashka mt controller, backend host and immutable artifact store. The M4 Max
is dedicated to remotely supervised traffic generation. A Raspberry Pi of unspecified
model is planned for monitoring; it is not documented as purchased or operational.
Synthetic HTTP is the primary workload; a bounded Social Feed API adds realism.
Rust/Axum/SQLite backends and Prometheus/Grafana monitoring remain preferred proposals.
AWS/EC2 may support development or auxiliary experiments, but is not the primary
measurement environment.

Ansible prepares hosts, `mt deploy` deploys application artifacts, and `mt run` automates
experiments using EDN scenarios, common environment lifecycles, identical traffic profiles,
active readiness probes, controlled resources, and immutable raw results.

## Design documents

- [TCP feasibility results](docs/tcp-feasibility.md): native/WASIp2 checks, tools, reproduction, and limits.
- [HTTP firewall correctness](docs/firewall-correctness.md): implementation, 94 DUT checks, reproduction, and limits.
- [Podman/Quadlet correctness](docs/podman-correctness.md): container controls, shared-binary checks, and three-variant parity.
- [VM correctness](docs/vm-correctness.md): 188/188 four-variant cases with exact response parity; functional evidence only.
- [Implementation roadmap](docs/implementation-roadmap.md): gated phases, dependencies and acceptance criteria.
- [HTTP workloads](docs/http-workloads.md): synthetic and Social Feed backend plans.
- [Monitoring](docs/monitoring.md): planned collectors, resource accounting and reliable exports.
- [k6 evaluation](docs/k6-evaluation.md): proposed primary HTTP generator and pilot controls.
- [L7 firewall milestone](docs/l7-firewall.md): proposed behavior, correctness cases, and implementation gates.
- [DUT inventory](docs/dut-inventory.md): verified hardware/software and remaining checks.
- [Architecture](docs/architecture.md): machines, execution environments, and network path.
- [Methodology](docs/methodology.md): fairness, metrics, repetitions, and analysis.
- [Experimental framework](docs/experimental-framework.md): `mt`, Ansible, EDN, and future layout.
- [Decision log](docs/decisions.md): accepted choices and proposals.
- [Research](docs/research.md): research questions and falsifiable hypotheses.

## Current status and unresolved decisions

The TCP feasibility milestone is implemented: identical Rust source passes 12 functional
cases per native/WASIp2 variant on the DUT through `mt feasibility tcp` (D036).
The custom bootstrap and functional fixtures now use Clojure/Babashka (D037), with
no Python required for direct preparation or test execution. Ansible is optional.
The first HTTP/1.1 firewall passes 47 correctness cases per native/WASIp2 variant
(D038), and its opt-in Podman/Quadlet adapter runs the same cases with the identical
native executable (D039). D040 establishes KVM correctness: 188/188 cases across four
variants with exact response parity. The full campaign framework and final benchmark
deployment remain unimplemented. Temporary tools are prepared through
the Clojure bootstrap or its optional Ansible wrapper.
Podman with Quadlet is the accepted container baseline (D033), and the verified
i5-12400 is the accepted DUT (D034).

Still unresolved: the final networking/concurrency interface and detailed HTTP semantics,
NIC topology and capacity,
k6 pilot adoption and collector selection, final campaign software versions, CPU/memory allocation, traffic sizes
and rates, warm-up/duration/cooldown, final repetition count, latency boundaries, startup
boundaries, scalability design, and statistical/outlier procedures.

NIC role assignment, TRex, candidate sizes/load levels, and approximately 30 repetitions
are proposals. Runtime and source-language comparisons are optional secondary experiments.
No illustrative values constitute final methodology.

D042–D050 record revised host roles, accepted two-level workloads and conditional stack/tool
proposals. See the roadmap for approval and validation gates; no performance campaign
or monitoring deployment is claimed.
