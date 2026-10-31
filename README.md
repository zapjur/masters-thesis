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

The final benchmark methodology will be defined before implementation begins.

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
verified AlmaLinux 10.2 and three RTL8125 NICs. The M4 Max MacBook generates traffic,
runs the Clojure `mt` orchestrator, and collects artifacts. The M1 Pro MacBook runs
controlled backends/receivers; the main vLB benchmark uses a minimal Clojure backend.
AWS/EC2 may support development or auxiliary experiments, but is not the primary
measurement environment.

Ansible prepares hosts, `mt deploy` deploys application artifacts, and `mt run` automates
experiments using EDN scenarios, common environment lifecycles, identical traffic profiles,
active readiness probes, controlled resources, and immutable raw results.

## Design documents

- [L7 firewall milestone](docs/l7-firewall.md): proposed behavior, correctness cases, and implementation gates.
- [DUT inventory](docs/dut-inventory.md): verified hardware/software and remaining checks.
- [Architecture](docs/architecture.md): machines, execution environments, and network path.
- [Methodology](docs/methodology.md): fairness, metrics, repetitions, and analysis.
- [Experimental framework](docs/experimental-framework.md): `mt`, Ansible, EDN, and future layout.
- [Decision log](docs/decisions.md): accepted choices and proposals.
- [Research](docs/research.md): research questions and falsifiable hypotheses.

## Current status and unresolved decisions

Specification and DUT inspection are underway on `initial-l7-firewall`; application
implementations and provisioning have not started. The user authorized the staged
firewall plan. Podman with Quadlet replaces Docker (D033); verified i5-12400 hardware
corrects the earlier i7 inventory (D034).

Still unresolved: socket/WASI interface and detailed HTTP semantics, NIC topology and capacity,
traffic generator and collectors, software versions, CPU/memory allocation, traffic sizes
and rates, warm-up/duration/cooldown, final repetition count, latency boundaries, startup
boundaries, scalability design, and statistical/outlier procedures.

NIC role assignment, TRex, candidate sizes/load levels, and approximately 30 repetitions
are proposals. Runtime and source-language comparisons are optional secondary experiments.
No illustrative values constitute final methodology.
