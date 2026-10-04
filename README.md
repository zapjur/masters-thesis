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

- firewall,
- load balancer.

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

## Current status

Planning and research-design phase.

The following decisions are intentionally still open:

- final experimental host architecture,
- local hardware vs cloud,
- concrete WebAssembly runtime,
- container runtime,
- virtualization stack,
- programming language for reference implementation,
- traffic generator,
- benchmarking tools,
- exact statistical methodology.

These decisions should be made based on reproducibility, fairness, technical feasibility,
and academic value rather than convenience alone.
