# Research Questions and Hypotheses

## Main research problem

The thesis investigates how the choice of execution technology affects the performance
and resource efficiency of software network functions.

The compared technologies are:

- WebAssembly,
- containers,
- virtual machines.

## Main research question

**How does the choice of execution technology affect the performance and resource efficiency
of selected network functions?**

## Research subquestions

### RQ1 — Latency

How does execution technology affect network-function processing latency?

### RQ2 — Throughput

How does execution technology affect the maximum sustainable throughput of the network
function?

### RQ3 — CPU efficiency

How does CPU utilization differ between equivalent WebAssembly, container, and
virtual-machine implementations?

### RQ4 — Memory efficiency

How does memory utilization differ between the three execution environments?

### RQ5 — Startup time

How much time is required to start a usable instance of the network function in each
execution environment?

### RQ6 — Scalability

How does each execution technology behave as the offered workload increases?

### RQ7 — Workload dependence

Are the observed differences consistent across different network-function types, or do they
depend on the workload characteristics?

## Working hypothesis

The current working hypothesis is:

> WebAssembly can provide lower execution-environment overhead and shorter startup time
> than traditional containers and virtual machines while maintaining competitive performance
> for selected network functions.

This hypothesis must be refined once:

- the execution architecture,
- implementation model,
- traffic model,
- and measurement methodology

have been selected.

## Important principle

The thesis must not assume that WebAssembly is superior.

Possible valid conclusions include:

- WebAssembly performs better,
- containers perform better,
- virtual machines perform better,
- differences are negligible,
- different technologies are optimal for different workload classes.

## Independent variables

Potential independent variables include:

- execution technology,
- network function,
- traffic intensity,
- packet or request size,
- number of connections,
- number of function instances.

Not all variables need to be included.

The final set must be defined before experiments begin.

## Dependent variables

The primary dependent variables are:

- latency,
- throughput,
- CPU utilization,
- memory utilization,
- startup time.

Scalability will be evaluated based on how these metrics change under increasing workload
or instance count.

## Controlled variables

Potential controlled variables include:

- hardware,
- operating system,
- network topology,
- network interface configuration,
- function logic,
- traffic pattern,
- benchmark duration,
- compiler configuration,
- runtime configuration.

The final experimental design should minimize uncontrolled differences between execution
technologies.

## Validity considerations

The methodology should explicitly consider:

### Internal validity

Could the observed difference originate from configuration differences rather than
execution technology?

### Construct validity

Do the selected metrics actually represent the performance characteristics claimed
in the thesis?

### External validity

To what extent can the results be generalized beyond the selected hardware, runtime,
network functions, and traffic profile?

### Reproducibility

Can another researcher reproduce the experimental environment and obtain comparable
results?
