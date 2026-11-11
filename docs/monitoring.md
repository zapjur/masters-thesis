# Monitoring and resource accounting plan

## Status and deployment

Preferred Prometheus/Grafana (D048), not deployed or validated. The Raspberry Pi is
planned; no model is selected. Keep deployment portable to another Linux host. See
[architecture](architecture.md) for physical roles. Grafana visualizes; immutable
controller exports, direct measurements and provenance form the evidence.

## Candidate collection and interpretation

| Measurement | Candidate mechanism | Accounting requirement |
| --- | --- | --- |
| Total/per-core DUT CPU | Node Exporter CPU counters | Record CPU modes, host kernel work, affinity and idle baseline |
| Wasmtime/native process CPU and RSS | Process exporter or scoped process sampling | Track process identity/lifetime; exporter grouping must not mix runs |
| Container CPU/memory | cgroup v2 counters, Podman statistics cross-check | Record payload cgroup, limits, memory scope and systemd/runtime helpers |
| VM CPU/memory | QEMU process/cgroup, libvirt metrics, guest instrumentation | Include QEMU/helpers and host costs; guest VNF RSS is a separate diagnostic |
| Network | Linux interface counters and kernel TCP metrics | Bytes, packets, errors/drops, retransmissions; map host/bridge/TAP/guest interfaces and avoid double counting |
| Lifecycle/peaks | Direct monotonic event/probe timing and high-resolution process/cgroup sampling | Scrapes alone miss short startup intervals and peaks |

Select a minimal collector set after checking version support, privileges, PID/cgroup
lifetime behavior and measured overhead. Do not assume exporters have equivalent metrics.
Measure VNF cost, runtime/environment cost and total host cost separately. For CPU report
CPU-seconds and normalize derived percent as 100% per busy logical CPU, with allocated
CPU capacity stated; optionally also report fraction of allocation. Host minus process
CPU is not automatically attributable to a single VNF. Report process RSS/peak RSS,
cgroup memory/peak where supported, QEMU RSS, VM allocation versus residency, guest RSS,
and incremental total-host memory relative to matched idle baselines. Shared pages/cache
and sampling granularity prevent simple summation/subtraction claims.

HTTP RPS/latency and bytes from k6 complement interface throughput, retransmits and active
connections; their protocol layers differ. Record actual CPU pinning, quota, vCPU allocation
and memory limits with every run. Keep collector CPU/memory visible in host accounting.

## Reliability and constrained monitoring host

Pilot scrape intervals against overhead and resolution; retain interval, timeout, missed
scrapes, target health, collector versions/config and query step. No interval is accepted
yet. Budget active series/cardinality, samples/s, retention time/size, Prometheus head
memory, Grafana memory, free disk and query/export peaks. Evaluate a possible 2 GB budget
without treating it as a hardware commitment. Compare external SSD and microSD endurance,
I/O and reliability. Prometheus local storage has configurable retention; size the actual
workload rather than infer fit from hardware specifications
([storage documentation](https://prometheus.io/docs/prometheus/latest/storage/), reviewed 2026-11-11).
Avoid request IDs, URLs with unbounded parameters or run IDs as persistent high-cardinality
labels. Do not run intensive unrelated homelab workloads during campaigns.

Verify clock synchronization on all hosts; record UTC offset/uncertainty before and after
runs. Prometheus sample timestamps, controller event timestamps and local monotonic
durations have different meanings. Preserve command dispatch/acknowledgement times and
measurement windows rather than claiming simultaneous starts over SSH.

## Per-run association and exports

`mt` assigns a unique run_id and keeps a manifest mapping it to stable target labels,
process/container/domain identities, k6/backend instances, allocations and exact UTC
windows. Query a window including pre-run baseline, warm-up, measurement and cooldown;
retain original timestamped samples, query text/parameters, response, scrape config,
export timestamp, units and coverage assessment. Record counter resets and process turnover.
Start collectors before VNF startup for lifecycle tests and before warm-up for load tests.

The M1 retrieves run-specific Prometheus data after measurement and stores it alongside
k6, runtime diagnostics, logs and metadata in immutable raw artifacts. Dashboard screenshots
are supplementary. Detect missing/stale series, scrape failures, collector restarts and
export failures. Mark incomplete runs explicitly and retain all evidence; do not silently
accept a run missing required resource metrics. Retry collection while data remains
available, record retries, and stop further campaign runs if required monitoring cannot
be restored. Optional diagnostic failure must not masquerade as required-metric success.
