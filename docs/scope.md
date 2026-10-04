# Thesis Scope

## Core scope

The thesis compares three execution technologies:

- WebAssembly,
- containers,
- virtual machines.

The comparison is performed using selected network functions.

The initial functions are:

- firewall,
- load balancer.

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
- Go or TinyGo.

This is a secondary experiment.

It must not delay the primary WebAssembly vs container vs virtual-machine comparison.

### Multiple WebAssembly runtimes

Comparing multiple runtimes may be useful if runtime selection proves to have a significant
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
