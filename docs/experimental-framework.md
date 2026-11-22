# Experimental Framework

## Status and purpose

**Accepted conceptual design — TCP feasibility and HTTP correctness commands implemented.**

`mt` means master-thesis. It is a custom Clojure CLI/framework for fully automated
experiment control, lifecycle management, metadata generation, and artifact collection.
It operates outside the measured VNF datapath. EDN scenario definitions are accepted.
Physical topology is specified in [architecture.md](architecture.md); scientific procedures
in [methodology.md](methodology.md); decision status in [decisions.md](decisions.md).

## Control and execution

The M1 Pro hosts the Babashka CLI, JVM controller and central storage.
The M4 agent initiates communication for typed k6 jobs. DUT execution uses
existing SSH/runtime mechanisms and Ansible remains the provisioning tool.
See the [accepted control plane design](control-plane.md), including component
boundaries, agent protocol and proposed repository evolution.

## Intended CLI

```text
mt doctor
mt hosts
mt deploy
mt run <scenario>
mt status
mt results
mt analyze
```

The commands above are intended interfaces, not available commands. The initial
`bb mt feasibility tcp --ssh-host ALIAS` command is implemented in `mt/` for the
bounded development spike; see [tcp-feasibility.md](tcp-feasibility.md).
The additional `bb mt firewall check --ssh-host ALIAS` command runs the first HTTP
correctness suite; see [firewall-correctness.md](firewall-correctness.md).
Its `--with-podman` option manages rootless Quadlet user services, verifies binary
identity, and compares the same cases across native, WASIp2, and Podman. See
[podman-correctness.md](podman-correctness.md). `--with-vm` adds the validated KVM functional adapter; see [vm-correctness.md](vm-correctness.md). General benchmark adapters remain future work.
`doctor` checks prerequisites;
`hosts` exposes availability/configuration; `deploy` builds/deploys artifacts; `run` executes
scenarios; `status` reports lifecycle state; `results` locates artifacts; `analyze` integrates
reproducible analysis. Exact options and behavior remain to be designed.

## Environment adapters

The common lifecycle is `prepare`, `start`, `wait-ready`, `stop`, `cleanup`. A conceptual
Clojure protocol could expose readiness as a predicate polled by the orchestration layer:

```clojure
(defprotocol ExecutionEnvironment
  (prepare [env context])
  (start [env context])
  (ready? [env context])
  (stop [env context])
  (cleanup [env context]))
```

This is a design sketch, not source code to implement now. The wait-ready operation uses
active probes through `ready?`, with timeouts and equivalent correctness criteria.
Adapter details must preserve documented resource limits and readiness/startup boundaries.
Failure/timeout handling must collect evidence and clean up rather than silently discarding
iterations; recovery follows the [execution lifecycle](control-plane-domain.md).

## Responsibility boundaries

| Component | Lifecycle | Responsibilities |
| --- | --- | --- |
| Ansible | Machine / operating system | Packages, users, SSH, runtime installation, directories, networks, tuning, services, prerequisites |
| `mt deploy` | Applications / artifacts | Compile Rust, copy Wasm/native builds, build/update images, deploy backend and experiment artifacts |
| `mt run` | Experiments | Validate, start backend/VNF, probe readiness, warm up, measure, generate traffic, collect, stop, clean, persist |

Ansible makes machines ready to run experiments. It is not the primary per-benchmark
orchestration engine. Preparation/deployment must be kept outside measurement boundaries
unless explicitly included by a documented startup experiment.

## Single-run execution

The [execution lifecycle](control-plane-domain.md) specifies phase transitions,
readiness, timing, resource reservations, cleanup and recovery. Startup-only
scenarios arm collectors before the start action and distinguish VNF-only readiness
from environment boot, deployment, cache state and compilation.

## Planned EDN scenario contract

Design only; no schema implementation or example values are accepted here. Cover VNF
kind, technology, host roles, backend workload/instances/database reset and seed, traffic
profile/script/version/hash, offered arrival rate, request/response sizes and status mix,
HTTP version/reuse/timeouts, CPU affinity/quota/vCPUs and memory limits, warm-up,
measurement/cooldown, repetitions, order/randomization seed, collector set/intervals and
required validity checks. Resolve units and defaults explicitly; validate unsupported
HTTP combinations before deployment. Startup scenarios need separate timing/cache fields.

## Results and reproducibility

Record `git rev-parse HEAD` for every campaign/run and preserve resolved scenario,
source hashes and dirty-tree state, environment versions/configuration, hardware/network details, compiler/flags, traffic parameters,
resource allocation, readiness timestamps, durations, repetition, ordering, k6 outputs, Prometheus exports, backend diagnostics, process logs, timing metadata, error conditions and cleanup outcome. Use run_id to join all sources as specified in [monitoring.md](monitoring.md).
The conceptual required fields are in [methodology.md](methodology.md). A clean-working-tree
requirement for final thesis campaigns is proposed, not implemented.

Raw output and run metadata must be immutable, including unsuccessful attempts. Processed
summaries and figures must be generated separately by scripts with provenance. See
[persistence and recovery](control-plane-persistence.md) for storage and integrity. `mt analyze` integrates analysis without overwriting source data.
