# Master's Thesis Repository Instructions

This repository contains research, implementations, infrastructure,
experiments, data-analysis tooling, and thesis materials for the master's thesis:

**Analiza porównawcza wydajności technologii WebAssembly, konteneryzacji i wirtualizacji w realizacji funkcji sieciowych**

**Comparative analysis of the performance of WebAssembly, containerization, and virtualization technologies in implementing network functions**

## Primary objective

The objective of the project is to experimentally compare three execution technologies:

1. WebAssembly
2. containers
3. virtual machines

in the implementation of selected network functions.

The initial network functions are:

- firewall
- load balancer

The primary metrics are:

- latency
- throughput
- CPU utilization
- memory utilization
- startup time
- scalability

## Repository knowledge

Before making significant architectural, experimental, or implementation changes,
read the relevant documentation.

Important files:

- `docs/thesis.md` — thesis overview and motivation
- `docs/scope.md` — project boundaries
- `docs/research.md` — research questions and hypotheses
- `docs/architecture.md` — experimental environment architecture
- `docs/methodology.md` — benchmarking and experimental methodology
- `docs/decisions.md` — important project and research decisions
- `docs/literature.md` — literature notes and source tracking
- `docs/glossary.md` — terminology used in the project

## General rules

### Research integrity

Treat this as an academic research repository.

Never:

- fabricate citations,
- fabricate benchmark results,
- invent experimental data,
- present assumptions as measured results,
- silently remove unsuccessful experiments,
- alter raw benchmark data manually.

Clearly distinguish between:

- measured facts,
- literature findings,
- assumptions,
- hypotheses,
- interpretations.

### Reproducibility

Reproducibility is a primary requirement.

Whenever experiments are introduced, they should eventually record enough information
to reproduce a run, including where applicable:

- Git commit,
- timestamp,
- hardware,
- operating system,
- kernel version,
- runtime version,
- compiler version,
- implementation version,
- benchmark configuration,
- traffic parameters,
- measurement duration,
- warm-up duration,
- number of repetitions.

Prefer automated experiment execution over manual procedures.

### Scope discipline

Do not expand the thesis scope without explicitly documenting the proposal.

Before introducing major technologies or research dimensions such as:

- Kubernetes,
- distributed orchestration,
- service mesh,
- eBPF,
- DPDK,
- multiple WebAssembly runtimes,
- multiple programming languages,
- public cloud deployments,
- multi-node clusters,

check `docs/scope.md`.

If something is outside the current scope, propose it first instead of implementing it immediately.

### Experimental fairness

The comparison between WebAssembly, containers, and virtual machines must be as fair as possible.

The implementations should aim to provide equivalent network-function behavior.

Avoid introducing unnecessary differences between variants.

When equivalence cannot be achieved, document the difference and explain its likely impact.

### Raw data

Once benchmark data is introduced:

- raw measurement files must be immutable,
- processed data must be stored separately,
- plots and statistics must be generated from scripts,
- manual editing of measurements is not allowed.

### Documentation

Important design or methodology decisions should be recorded in:

`docs/decisions.md`

Do not allow important decisions to exist only in source code or chat history.

### Citations

Never invent bibliographic information.

When a source has not been verified, mark it as:

`TODO: verify source`

instead of constructing a citation from memory.

### Language

Repository technical documentation should normally be written in English.

The final thesis itself may be written in Polish.

Use precise technical terminology.

Prefer the terms:

- WebAssembly technology
- WebAssembly runtime
- container
- virtual machine
- network function

Avoid calling WebAssembly itself a virtual machine unless discussing a specific runtime architecture.

## Current project state

The repository is currently in the planning stage.

Do not start implementing the network functions unless explicitly requested.

The immediate priority is:

1. define the research design,
2. select the experimental architecture,
3. define benchmark methodology,
4. select concrete technologies,
5. only then implement the test environment.
