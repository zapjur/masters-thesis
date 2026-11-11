# Experimental Methodology

## Status and objectives

**Draft — baseline principles accepted; final measurement procedures remain open.**

Results become final thesis evidence only after the relevant methodology is documented.
Measurements must be repeatable, comparable, reproducible, statistically meaningful,
and resistant to measurement bias. H1–H3 are defined in [research.md](research.md).

## Fairness and experimental controls

Use the same x86 Linux DUT for Wasmtime, Podman, and QEMU/KVM/libvirt. Use Rust
with as much shared VNF logic as technically feasible. Validate equivalent behavior before
performance experiments; document unavoidable build, API, and networking differences.
The reference workloads are now L7 vFW followed by L7 vLB (D031). The restricted D038 firewall is accepted and functionally validated through D040;
per-request round-robin balancing remains proposed. Define protocol,
parsing/framing limits, connection reuse, successful forwarding, rejection semantics,
and request/payload units before benchmarking. Account for HTTP parsing and transport
work consistently across variants; raw Ethernet processing is not required.

Traffic profiles must be independent of environment adapters. Apply the same resolved
traffic definition to each technology, with equivalent resources, benchmark duration,
and backend behavior. Synthetic HTTP is the primary isolation workload; Social Feed adds complementary
realism (D046). See [http-workloads.md](http-workloads.md) for backend controls. Verify generator/backend headroom and report bottlenecks.

Attempt equivalent compute allocation using process CPU affinity for Wasmtime, Podman
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
within each scenario. Do not run all Wasm repetitions, then all Podman, then all VM
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

## HTTP workloads and reference paths

Use [two-level HTTP workloads](http-workloads.md) with identical definitions across
technologies. [k6](k6-evaluation.md) is the proposed primary L7 generator pending pilots;
TRex and historical packet-size sets are not current requirements. Record HTTP body,
header and wire sizes separately. Open arrival-rate profiles control offered load;
closed VU models need an explicit question about a fixed client population.

| Path | Included overhead / interpretation |
| --- | --- |
| Direct M4 → M1 backend | Client, backend and direct transport capacity; does not include DUT traversal |
| No-VNF path through DUT, where feasible | Explicitly configured routing/bridging and transport control; may differ from a terminating HTTP proxy |
| Native VNF on DUT | Application parsing/forwarding and host kernel/transport; reference, not fourth primary technology |
| Wasmtime/WASIp2 | VNF plus Wasmtime/WASI and host networking |
| Podman/Quadlet | VNF plus container/runtime/cgroup and selected network mode |
| KVM | VNF plus guest kernel, virtio, QEMU/hypervisor and selected host network path |

Document hop/link/interface differences for each path. Do not subtract direct-backend
latency as though its physical route matched a VNF-through-DUT route. Keep backend
configuration/seed, request checks, policy, resource allocation and host configuration
stable; record unavoidable differences and collector overhead. The M1's orchestration
must not compete with backend measurement; stage preparation first and defer expensive
collection. Monitor both M1 backend and M4 generator headroom, network/storage capacity,
and background activity. Lack of headroom invalidates attribution to VNF saturation.

Separate offered iterations/s, actual HTTP requests/s, successful business requests,
intentional firewall denials, unexpected statuses, transport errors/timeouts and overload.
Predeclare sustainable-throughput criteria using achieved load, latency/error bounds,
dropped iterations, duration/stability and generator/backend headroom. Numerical bounds
and search procedure require pilots; maximum observed RPS alone is insufficient.

## Monitoring and timing boundaries

[monitoring.md](monitoring.md) defines candidate collectors, CPU normalization, memory
scopes and failure policy. Record VNF, environment and total-host resource costs;
export run-specific Prometheus samples to immutable controller storage. A run_id manifest
joins k6, resource samples, backend diagnostics, configuration and timing windows.
Prometheus timestamps are sample times; controller events and direct monotonic lifecycle
timings remain separate. Clock uncertainty and remote launch skew must be quantified.
Prometheus scraping cannot resolve short startup or initialization peaks by itself.

## Metadata and source traceability

Each result must be traceable to source and environment. The following is a conceptual
field schema, not a requirement to use YAML for scenario definitions (which use EDN):

```yaml
experiment_id:
run_id:
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
undecided. Four-variant firewall correctness is established; benchmark deployment, capacity pilots and monitoring validation remain future work.

## Statistical plan and scope boundary

Use pilot variance and a target confidence-interval precision/effect size to justify
repetitions and measurement duration; approximately 30 is only a historical suggestion.
Treat independent repetitions as the experimental units; HTTP requests within a run are
not independent repetitions. Plan paired/interleaved-block comparisons where appropriate,
report per-run p50/p95/p99 and throughput with uncertainty, and choose interval estimation
(e.g. run/block bootstrap) and tests after pilot distribution/dependence checks. Do not
average percentiles as though they were a pooled latency distribution. Predeclare outlier
flags, exclusion/failed-run rules and multiple-comparison handling; retain unsuccessful
runs and report counts/reasons. Distinguish statistical significance, effect size and
practical relevance. Scaling must specify load, CPU or instances and resource efficiency,
with units such as successful requests per CPU-second and per allocated/resident memory.

Edge computing motivates startup, memory efficiency, resource constraints and deployment
overhead only. Edge placement algorithms, distributed scheduling and orchestration are
outside this comparison. Original research questions and hypotheses remain unchanged.
