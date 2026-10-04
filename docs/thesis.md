# Thesis Overview

## Title

### Polish

**Analiza porównawcza wydajności technologii WebAssembly, konteneryzacji i wirtualizacji w realizacji funkcji sieciowych**

### English

**Comparative analysis of the performance of WebAssembly, containerization, and virtualization technologies in implementing network functions**

## General idea

Modern network services are increasingly implemented as software and executed in virtualized
or cloud environments.

Traditional implementations commonly rely on:

- virtual machines,
- operating-system-level containers.

WebAssembly introduces another execution model that may offer:

- lightweight isolation,
- small deployment artifacts,
- fast startup,
- reduced memory overhead,
- portability across execution environments.

The thesis investigates whether these characteristics provide measurable benefits when
implementing network functions.

## Compared technologies

The study will compare:

1. WebAssembly,
2. containers,
3. virtual machines.

These technologies represent different isolation and execution models.

The goal is not merely to benchmark generic computation.

The goal is to evaluate them in the context of practical network functions.

## Initial network functions

The initial experimental scope includes:

### Firewall

A network function that processes traffic according to a defined filtering policy.

The final functionality and packet-processing model are still to be selected.

### Load balancer

A network function that distributes traffic between multiple backend destinations.

The final balancing algorithm and traffic model are still to be selected.

## Performance dimensions

The initial evaluation dimensions are:

- latency,
- throughput,
- CPU utilization,
- memory utilization,
- startup time,
- scalability.

Additional metrics may be added if they provide research value and do not unnecessarily
expand the scope.

## Motivation

WebAssembly has evolved beyond browser execution and is increasingly considered for:

- server-side workloads,
- cloud systems,
- edge computing,
- lightweight sandboxing,
- plugin systems,
- network-related workloads.

At the same time, containers and virtual machines remain mature and widely deployed
technologies.

A controlled comparison may help determine where WebAssembly provides measurable benefits
and where traditional execution technologies remain preferable.

## Expected outcome

The expected contribution of the thesis is:

1. a reproducible experimental environment,
2. equivalent or comparable implementations of selected network functions,
3. systematic performance measurements,
4. statistical analysis of the results,
5. identification of strengths and limitations of each execution technology,
6. conclusions regarding suitable application areas.

The study must remain evidence-driven.

Any expected WebAssembly advantage is treated as a research hypothesis rather than
an assumed result.
