# Experimental Methodology

## Status and objectives

**Draft — baseline principles accepted; final measurement procedures remain open.**

Results become final thesis evidence only after the relevant methodology is documented.
Measurements must be repeatable, comparable, reproducible, statistically meaningful,
and resistant to measurement bias. H1–H3 are defined in [research.md](research.md).

## Fairness and experimental controls

Use the same x86 Linux DUT for Wasmtime, Docker Engine, and QEMU/KVM/libvirt. Use Rust
with as much shared VNF logic as technically feasible. Validate equivalent behavior before
performance experiments; document unavoidable build, API, and networking differences.
The reference workloads are now L7 vFW followed by L7 vLB (D031). HTTP request filtering
and per-request round-robin balancing are proposed, not finalized. Define protocol,
parsing/framing limits, connection reuse, successful forwarding, rejection semantics,
and request/payload units before benchmarking. Account for HTTP parsing and transport
work consistently across variants; raw Ethernet processing is not required.

Traffic profiles must be independent of environment adapters. Apply the same resolved
traffic definition to each technology, with equivalent resources, benchmark duration,
and backend behavior. The main vLB benchmark uses the `minimal` backend. Other backend
modes are secondary scenarios. Verify generator/backend headroom and report bottlenecks.

Attempt equivalent compute allocation using process CPU affinity for Wasmtime, Docker
cpuset/affinity and explicit memory controls, and pinned VM vCPUs with explicit memory
assignment. Exact CPU IDs, core/vCPU counts, limits, and host CPU reservations remain
proposed. Distinguish allocated resources from actual consumption; document host/kernel/
hypervisor costs rather than comparing guest VNF RSS to total Wasmtime memory.
Control power/performance mode, background processes, network configuration, software
versions, and compiler settings. Record controls and exceptions for every campaign.

## Automated execution and ordering

`mt` automates validation, reset, readiness, warm-up, measurement, collection, cleanup,
and cooldown. The complete iteration lifecycle is owned by
[experimental-framework.md](experimental-framework.md).

Multiple repetitions are mandatory. Approximately 30 repetitions per scenario is a
starting proposal, not a statistical requirement. Pilot variance, confidence goals,
effect sizes, and execution cost determine the final count.

Support randomized ordering, preferably randomized/interleaved blocks across technologies
within each scenario. Do not run all Wasm repetitions, then all Docker, then all VM
repetitions in a fixed sequence. Record the realized order and any randomization seed
for reproducibility. Reset state and account for carry-over between iterations.

Warm-up must address compilation/JIT where applicable, caches, runtime initialization,
connections, and allocation. Use identical or explicitly justified warm-up procedures.
Cold/warm startup and compilation-cache conditions need separate definitions. Warm-up,
measurement duration, cooldown, and final repetitions are not yet finalized.

## Metrics

### Throughput

Report successful processing per unit time: packets/s, bits/s, connections/s, or requests/s
where appropriate. Define the sustainable-throughput criterion, offered versus achieved
load, loss/error accounting, and saturation conditions. For vFW, intentional policy drops
must be distinguishable from overload losses. A socket operation or payload is not
necessarily one network packet.

### Latency

Retain distributions when possible and report p50, p95, and p99; arithmetic mean alone
is insufficient. One-way versus round-trip, measurement endpoints, protocol framing,
clock handling, error/timeout treatment, and optional p99.9 remain open. Quantify backend
and transport contribution when interpreting VNF latency.

### CPU

Measure execution/resource cost. Define process/runtime, host/kernel, container, and
hypervisor accounting and choose CPU time versus percentage with consistent normalization.
A possible derived efficiency metric is throughput / CPU usage; specify units and CPU
allocation before interpreting it. Collectors must have measured/controlled overhead.

### Memory

Distinguish VNF/process memory, runtime/container overhead, and total environment cost.
For VMs include appropriate guest/environment and host virtualization costs; guest VNF
RSS alone is not total VM cost. Define allocated, resident, and incremental memory clearly,
including idle baselines and any shared-memory accounting. Final metric/tool choices remain open.

### Startup time

Use active readiness detection, never a fixed sleep as the definition of readiness.

```text
startup_time = first_successful_ready_probe - start_command_timestamp
```

The probe must demonstrate correct traffic processing, not merely process existence.
Define equivalent starting states, whether VM boot/environment creation is included,
cache/image preparation, timing location, probe interval, timeout, and readiness criterion.
Probe granularity and remote-control overhead affect this measured boundary and must be
recorded. Warm-up follows readiness and is a separate phase.

### Scalability

Potential dimensions are offered load, VNF instance count, CPU resources, and connection
count. Select the final design before campaigns. H3 requires explicit instance-scaling
resource-cost measurements; offered-load scaling alone does not test that entire claim.

## Traffic proposals

Reusable profiles may cover TCP/UDP, payload/packet sizes, connection counts, offered rates,
and steady/bursty traffic. Candidate sizes are 64, 256, 512, 1024, and 1500 bytes; an
IMIX-like profile is optional. Specify payload versus wire size and framing overhead.
Exact sizes, load levels (including any percentage reference), and rates remain proposed.

TRex is a candidate, not the accepted generator. Verify hardware/NIC compatibility,
socket-workload semantics, and the ability to preserve the accepted M4 generator role.
Simpler development tools are permitted. Any required role change needs an explicit
proposal rather than moving traffic generation onto the DUT silently.

## Metadata and source traceability

Each result must be traceable to source and environment. The following is a conceptual
field schema, not a requirement to use YAML for scenario definitions (which use EDN):

```yaml
experiment_id:
timestamp:
git_commit:

function:
technology:
repetition:

dut:
  cpu:
  memory:
  os:
  kernel:

implementation:
  language:
  version:
  compiler:
  compiler_flags:

runtime:
  name:
  version:
  configuration:

traffic:
  protocol:
  payload_size:
  rate:
  connections:

benchmark:
  warmup:
  duration:
  cooldown:
```

Also record generator/backend versions and configuration, guest OS/kernel, host identities,
NIC/topology details, resource allocations, resolved scenario, environment adapter settings,
measurement/probe tools and intervals, ordering, and failures where applicable. The exact
schema may evolve. Record `git rev-parse HEAD` for every campaign/run. Refusing final
campaigns with uncommitted changes is proposed; the policy is not implemented yet.

## Raw data and statistical analysis

Raw results, logs, and metadata are immutable; processed data and figures are separate.
Never manually edit raw measurements or silently remove failed experiments/outliers.
Validate completeness, distinguish failures from completed runs, and document exclusions.
Analysis scripts must reproduce every derived table/figure and retain provenance.

Consider distributions, variance, median, mean where appropriate, percentile latency,
confidence intervals, effect sizes, and practical relevance. Exact significance tests
remain unresolved and must suit pilot distributions and the experimental design.
Define hypotheses' operational thresholds, outlier handling, exclusion rules, final
repetition count, and analysis procedures before final campaigns. Do not select favorable
tests or exclusions retrospectively from final results; retain all raw observations.

## Remaining decisions before final measurements

- Networking/WASI interface, transport semantics, and functional-equivalence tests.
- NIC topology, generator, collectors, and generator/backend capacity checks.
- Exact OS/guest/runtime/toolchain versions and build flags.
- Resource allocation, overhead accounting, startup boundaries, and clock/probe procedures.
- Traffic matrix, saturation criterion, durations, ordering, repetitions, and scaling design.
- Statistical thresholds, confidence procedures, tests, and outlier policy.

Accepted technology/host choices are in [decisions.md](decisions.md); they are not still
undecided. Deployment and feasibility verification remain future work.
