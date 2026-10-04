# Experimental Methodology

## Status

**Draft — methodology not finalized**

This document will define how experiments are executed and how results are interpreted.

No benchmark result should be treated as thesis evidence until the relevant methodology
has been documented here.

## Objectives

The methodology must produce results that are:

- repeatable,
- comparable,
- statistically meaningful,
- reproducible,
- resistant to obvious measurement bias.

## General experiment structure

A benchmark run should eventually follow a procedure similar to:

1. prepare the environment,
2. verify configuration,
3. start the tested network function,
4. wait for initialization if required,
5. perform warm-up,
6. start measurement,
7. generate controlled traffic,
8. record application/network metrics,
9. record CPU and memory utilization,
10. stop the workload,
11. store results and metadata,
12. restore the environment to a known state.

The exact procedure remains to be specified.

## Metrics

### Latency

Open questions:

- one-way or round-trip time,
- network-level or application-level latency,
- mean vs median vs percentiles,
- p50,
- p95,
- p99,
- possibly p99.9.

Latency distributions should be retained when possible instead of storing only averages.

### Throughput

Potential representations include:

- packets per second,
- requests per second,
- bits per second.

The final unit depends on the selected network-function model.

### CPU utilization

CPU consumption should be measured in a way that allows comparison across technologies.

Questions to resolve:

- whole-system CPU vs process-level CPU,
- CPU time vs percentage,
- host overhead,
- hypervisor overhead.

### Memory utilization

Potential measurements include:

- process RSS,
- runtime memory,
- container memory,
- VM allocated memory,
- host memory impact.

A fair cross-technology definition must be established.

### Startup time

Startup time must have a precise start and end event.

Potential definition:

> Time between issuing the start request and the network function becoming ready to
> correctly process traffic.

This requires an objective readiness test.

### Scalability

Scalability may be tested by changing:

- offered traffic,
- number of connections,
- number of instances,
- number of CPU cores.

The final definition remains open.

## Warm-up

Experiments may require a warm-up period because of:

- JIT compilation,
- caches,
- runtime initialization,
- connection establishment,
- memory allocation.

The warm-up procedure must be identical or explicitly justified for each technology.

Special care is required if the selected WebAssembly runtime uses JIT compilation.

## Repetitions

Every scenario should be repeated multiple times.

The final number of repetitions should be selected based on:

- variance,
- execution cost,
- statistical confidence.

Do not rely on a single benchmark run.

## Randomization

If environmental drift is observable, experiment order should be randomized or interleaved.

Avoid running all tests of one technology first and another technology hours later if this
could introduce time-dependent bias.

## Resource isolation

Potential controls include:

- fixed CPU allocation,
- CPU pinning,
- disabling unnecessary background processes,
- fixed power/performance mode,
- fixed memory limits,
- fixed network configuration.

The final controls depend on the selected hardware and operating system.

## Raw measurements

Raw benchmark results must be stored separately from processed output.

Raw measurements must never be manually edited.

Derived metrics, statistical summaries, and figures should be reproducible from scripts.

## Experiment metadata

Each benchmark execution should eventually record metadata similar to:

```yaml
experiment_id:
timestamp:
git_commit:

host:
  hostname:
  cpu:
  memory:
  os:
  kernel:

implementation:
  function:
  technology:
  version:
  compiler:
  compiler_flags:

runtime:
  name:
  version:
  configuration:

traffic:
  generator:
  profile:
  packet_size:
  connections:
  rate:

benchmark:
  duration:
  warmup:
  repetition:
```

The exact schema will be implemented later.

## Statistical analysis

The final analysis should consider:

- distribution of results,
- variance,
- median,
- arithmetic mean where appropriate,
- percentile latency,
- confidence intervals.

Possible statistical significance tests should only be selected after examining the
measurement distributions.

Do not choose tests purely for complexity.

Effect size and practical relevance are more important than reporting only a p-value.

## Outliers

Outlier handling must be defined before the final analysis.

Raw observations should never be silently deleted.

If observations are excluded, the reason and procedure must be documented.

## Benchmark fairness

Whenever possible:

- identical function logic should be used,
- identical workload should be used,
- identical host resources should be allocated,
- identical benchmark duration should be used.

Technology-specific differences that cannot be eliminated must be documented.

## Required decisions before benchmarking

The following must be defined before collecting final thesis measurements:

- benchmark host,
- target architecture,
- operating system,
- WebAssembly runtime,
- container runtime,
- virtualization technology,
- reference implementation language,
- network-function interface,
- traffic generator,
- traffic model,
- measurement tools,
- warm-up duration,
- benchmark duration,
- number of repetitions,
- system resource controls,
- statistical analysis procedure.
