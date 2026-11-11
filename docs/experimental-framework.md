# Experimental Framework

## Status and purpose

**Accepted conceptual design — TCP feasibility and HTTP correctness commands implemented.**

`mt` means master-thesis. It is a custom Clojure CLI/framework for fully automated
experiment control, lifecycle management, metadata generation, and artifact collection.
It operates outside the measured VNF datapath. EDN scenario definitions are accepted.
Physical topology is specified in [architecture.md](architecture.md); scientific procedures
in [methodology.md](methodology.md); decision status in [decisions.md](decisions.md).

## Four-host control model

`mt` runs centrally on the M1 Pro, supervising local backend instances, remote k6 on
the M4 Max, the common Linux DUT and the planned Raspberry Pi monitoring host.
All final artifacts reside on the M1 Pro. Management traffic uses the management
network; controller and backend share hardware but remain distinct logical roles.
Build, deploy and compress/export outside measured windows. See D042–D045.

The future generator interface should use authenticated SSH with pinned host identity
and a run-owned remote supervisor: stage hashed scripts/config, start with explicit
arguments/environment, record remote process identity, poll status and retrieve artifacts.
Preserve command/parameters, exit status, stdout/stderr, timestamps, k6 summary and
appropriate raw samples. A disconnected SSH client must not leave an untracked load
process: design reconnect/status, timeout, cancellation and scoped cleanup, including
partial-output retrieval. Do not disable host-key verification. This interface is planned,
not an implemented command. It must remain portable to another compatible generator.

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

## Conceptual modules

```text
mt
+-- cli
+-- scenario
+-- orchestrator
+-- hosts
+-- ssh/process execution
+-- environments
|   +-- wasm
|   +-- container
|   +-- vm
+-- traffic
+-- metrics
+-- storage
+-- analysis integration
```

Scenario parsing/validation resolves experiment configuration. Host/process modules handle
remote control. Environment adapters encapsulate technology-specific lifecycle details;
the orchestrator does not directly manage Wasmtime/Podman/VM startup internals.
Traffic profiles are reusable and independent of environment choice. Metrics collectors
and storage preserve both successful and failed run evidence.

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
iterations; retry policy and campaign recovery semantics remain open.

## EDN scenarios

The following is illustrative, not accepted final benchmark configuration:

```clojure
{:id :lb-baseline
 :function :load-balancer
 :technologies [:wasm :container :vm]
 :traffic {:protocol :tcp
           :payload-sizes [64 256 512 1024 1500]
           :load-levels [10 25 50 75 100]}
 :benchmark {:warmup-seconds 15
             :duration-seconds 60
             :cooldown-seconds 10
             :repetitions 30}}
```

Timings, size sets, percentage load levels, and repetitions remain proposals. Percentage
load needs a defined reference. The future schema must specify units, protocol/framing,
backend mode, resource settings, and valid combinations. The same resolved traffic profile
must apply to all environments. Scenario expansion and randomized/interleaved ordering
must be recorded, not inferred from filenames.

## Responsibility boundaries

| Component | Lifecycle | Responsibilities |
| --- | --- | --- |
| Ansible | Machine / operating system | Packages, users, SSH, runtime installation, directories, networks, tuning, services, prerequisites |
| `mt deploy` | Applications / artifacts | Compile Rust, copy Wasm/native builds, build/update images, deploy backend and experiment artifacts |
| `mt run` | Experiments | Validate, start backend/VNF, probe readiness, warm up, measure, generate traffic, collect, stop, clean, persist |

Ansible makes machines ready to run experiments. It is not the primary per-benchmark
orchestration engine. Preparation/deployment must be kept outside measurement boundaries
unless explicitly included by a documented startup experiment.

## Planned single-iteration lifecycle

1. Validate the resolved scenario and allocate unique run_id; persist initial metadata.
2. Check all hosts/prerequisites, clock offsets, stable DUT state and planned order.
3. Verify generator/backend capacity evidence and current headroom.
4. Reset state; prepare selected environment and stage artifacts outside measurement.
5. Start/reset local backend instances and verify their readiness.
6. Prepare and verify collectors, including target health and pre-start idle baseline.
7. Start the selected VNF and verify allowed/denied or balancing readiness actively.
8. Warm up with the specified profile while collectors remain active.
9. Record coordinated measurement boundaries and remotely start the configured k6 load.
10. Supervise generator, backend, DUT and collector health; retain events/errors continuously.
11. Stop measured traffic and record end boundaries; retrieve generator results/diagnostics.
12. Preserve resource samples through shutdown as required; export monitoring windows.
13. Stop VNF/backend and clean only run-owned resources, recording cleanup outcome.
14. Finalize immutable artifacts, completeness/validity status and manifest.
15. Apply cooldown/database reset and continue with the next randomized/interleaved repetition.

Persist partial artifacts on every failure, including generator loss or cleanup failure.
An error must not erase a run or trigger global container/VM/systemd cleanup. Separate
startup experiments arm direct timing and collectors before the start action; distinguish
VNF-only readiness from environment boot/deployment, cold/warm caches and compilation.
Collector verification cannot happen after the startup interval being measured.

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
summaries and figures must be generated separately by scripts with provenance. Future run
records may use `metadata.edn`; exact schema, collection protocol, and storage integrity
mechanisms remain open. `mt analyze` integrates analysis without overwriting source data.

## Intended future repository structure

This is the intended broader layout, not a claim of implemented modules. The `mt/`
feasibility command, TCP probe, EDN scenario, and isolated tool bootstrap now exist
under D036; the full lifecycle adapters and benchmark engine remain future work.

```text
masters-thesis/
├── AGENTS.md
├── README.md
├── .gitignore
├── docs/
│   ├── thesis.md
│   ├── scope.md
│   ├── research.md
│   ├── architecture.md
│   ├── methodology.md
│   ├── experimental-framework.md
│   ├── decisions.md
│   ├── literature.md
│   └── glossary.md
├── mt/
│   ├── deps.edn
│   ├── src/
│   │   └── mt/
│   │       ├── cli.clj
│   │       ├── orchestrator.clj
│   │       ├── scenario.clj
│   │       ├── hosts.clj
│   │       ├── ssh.clj
│   │       ├── storage.clj
│   │       ├── environments/
│   │       │   ├── wasm.clj
│   │       │   ├── container.clj
│   │       │   └── vm.clj
│   │       ├── traffic/
│   │       └── metrics/
│   └── test/
├── infra/
│   └── ansible/
│       ├── inventory/
│       ├── roles/
│       │   ├── common/
│       │   ├── generator/
│       │   ├── dut/
│       │   └── backend/
│       └── site.yml
├── implementations/
│   ├── firewall/
│   └── load-balancer/
├── backends/
│   └── rust/          # proposed backend direction, D047
├── experiments/
│   ├── scenarios/
│   ├── runs/
│   └── processed/
├── analysis/
└── thesis/
```

Existing personal notes may remain under `notes/`. Existing feasibility/correctness modules and infrastructure assets are implemented; the
broader campaign/backend tree remains planned and requires implementation authorization.
