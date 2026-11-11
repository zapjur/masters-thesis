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

## Working hypotheses

The supervisor suggests that WebAssembly may provide high network-function performance
with lower server resource consumption. The following claims are falsifiable hypotheses,
not results.

### H1 — Network performance

WebAssembly enables the selected network functions to achieve network performance that
is at least competitive with equivalent container and virtual-machine implementations.
The methodology must define a competitiveness threshold and workload-specific latency
and sustainable-throughput criteria before final measurements.

### H2 — Resource efficiency

WebAssembly requires lower execution-environment resource overhead for the selected
network functions than containers and virtual machines. The methodology must specify
CPU and memory definitions, including runtime/host and total VM costs where appropriate.

### H3 — Startup and scaling overhead

WebAssembly provides lower instance startup overhead and lower resource cost when scaling
network-function instances than container and virtual-machine environments. Startup uses
active readiness; scaling measurements and equal-resource constraints remain to be defined.

A finding of worse performance, equal resource cost, or no practical difference may refute
these claims within the tested conditions. Final operational thresholds and analysis rules
must be recorded before final campaigns, rather than chosen to favor measured outcomes.

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

## Accepted experimental context

The baseline compares Rust vFW/vLB logic through Wasmtime, Podman, and
QEMU/KVM/libvirt on one x86 Linux DUT. The current direction is Layer 7,
with a firewall first and load balancer second. Exact application semantics remain open. Separate physical machines
provide identical traffic profiles and minimal backends. Clojure orchestration is outside
the measured datapath; backend behavior remains controlled rather than an independent
variable. Optional runtime/language comparisons do not change the primary experiment.

## Revised experimental context

D042–D050 preserve these research questions and hypotheses. Synthetic HTTP isolates
execution-environment effects; Social Feed API provides complementary application context.
Backend language/database comparisons and edge scheduling are outside scope. Host roles,
reference-path interpretation and pilot-driven statistics are authoritative in
[architecture.md](architecture.md) and [methodology.md](methodology.md).
