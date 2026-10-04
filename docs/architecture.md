# Experimental Architecture

## Status

**Draft — major decisions still open**

This document will describe the final experimental architecture.

No architecture should be considered final until it is explicitly recorded in
`docs/decisions.md`.

## Architectural objective

The environment must allow a fair and reproducible comparison of:

- WebAssembly,
- containers,
- virtual machines.

The environment must support at least:

- firewall experiments,
- load-balancer experiments,
- controlled traffic generation,
- latency measurement,
- throughput measurement,
- CPU measurement,
- memory measurement,
- startup-time measurement.

## High-level logical topology

A generic benchmark topology may consist of:

```text
Traffic generator
        |
        v
Network function under test
        |
        v
Destination / backend
```

For the load balancer:

```text
Traffic generator
        |
        v
   Load balancer
      /      \
     v        v
Backend A   Backend B
```

The exact topology is not decided yet.

## Required roles

The experimental environment will likely require the following logical roles:

### Traffic generator

Produces controlled traffic.

### Device / function under test

Runs one of the tested implementations:

- WebAssembly,
- container,
- virtual machine.

### Backend or receiver

Receives forwarded traffic and may generate application responses.

### Measurement and control component

Starts experiments, records environment metadata, collects system metrics, and stores results.

These roles do not necessarily require separate physical machines.

## Open decision: physical deployment

Possible approaches include:

### Option A — single physical host

Advantages:

- easy to reproduce,
- easy to control,
- no cloud cost,
- low environmental variability.

Risks:

- resource contention,
- shared kernel or network stack effects,
- traffic generator competing with the tested workload,
- weaker separation between components.

### Option B — multiple physical machines

Advantages:

- stronger workload isolation,
- more realistic network path,
- easier separation of traffic generation and tested workload.

Risks:

- additional hardware requirements,
- differences between machines,
- synchronization and configuration complexity.

### Option C — public cloud

For example, virtual machines connected using an isolated virtual network.

Advantages:

- easy creation of multiple hosts,
- infrastructure repeatability,
- flexible instance sizes.

Risks:

- shared cloud infrastructure noise,
- virtualization already present underneath experiments,
- variable performance,
- cost,
- possible difficulty when evaluating VM overhead fairly.

## Available hardware

Known available machines include:

- Apple Silicon laptop with M1 Pro,
- Apple Silicon laptop with M4 Max,
- older x86 desktop:
  - Intel Core i7-6700K,
  - 16 GB RAM,
  - SSD.

These machines are not equivalent.

A mixed-hardware experiment should therefore be avoided unless the role of each machine
is carefully controlled.

The final benchmark host has not yet been selected.

## Architecture requirements

The final design should prioritize:

1. fairness,
2. repeatability,
3. measurement isolation,
4. low environmental variance,
5. automation,
6. practical feasibility.

## Architecture questions to resolve

Before implementation starts, decide:

- What machine runs the tested network function?
- What machine generates traffic?
- Is physical host separation necessary?
- Is the main experiment x86-64 or ARM64?
- Which operating system will be used?
- Which hypervisor will be used?
- Which container runtime will be used?
- Which WebAssembly runtime will be used?
- How will networking be configured?
- How will CPU and memory isolation be achieved?
- Will CPU affinity or CPU pinning be used?
- How will background system activity be minimized?
- How will clocks be handled for latency measurements?
- Will the benchmark measure packet forwarding or application-level request processing?
- Will the function operate at Layer 3/4 or application level?

## Principle

Choose the simplest architecture that provides scientifically credible measurements.

Do not introduce distributed systems complexity unless it improves experimental validity.
