# Experimental Framework

## Status and purpose

**Accepted conceptual design — only the TCP feasibility command is implemented.**

`mt` means master-thesis. It is a custom Clojure CLI/framework for fully automated
experiment control, lifecycle management, metadata generation, and artifact collection.
It operates outside the measured VNF datapath. EDN scenario definitions are accepted.
Physical topology is specified in [architecture.md](architecture.md); scientific procedures
in [methodology.md](methodology.md); decision status in [decisions.md](decisions.md).

## Three-machine control model

`mt` runs centrally on the M4 Max generator/orchestrator. It controls the x86 Linux DUT
(Wasmtime, Podman, or QEMU/KVM/libvirt VNF and resource measurements) and the M1 Pro
backend/receiver through host and SSH/process abstractions. Final artifacts are collected
on the M4. Macs are supporting machines, not primary DUTs. Verify control/collection
activity does not distort traffic generation or measured phases.

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
bounded development spike; see [tcp-feasibility.md](tcp-feasibility.md). `doctor` checks prerequisites;
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

## Single-iteration lifecycle

1. Validate experiment configuration.
2. Validate host availability.
3. Verify DUT state.
4. Reset experiment environment.
5. Start backend.
6. Prepare selected execution environment.
7. Start VNF.
8. Detect VNF readiness actively.
9. Perform warm-up.
10. Start metric collectors.
11. Start traffic generation.
12. Run benchmark.
13. Stop traffic generation.
14. Stop metric collectors.
15. Collect logs/results.
16. Stop VNF.
17. Clean up environment.
18. Wait for cooldown if required.
19. Persist metadata.
20. Continue with next repetition/scenario.

Capture timestamps/configuration throughout the iteration so final metadata includes
partial failures. Final timing, cooldown, repetition count, and ordering follow methodology.
The framework must support randomized ordering or preferably randomized/interleaved blocks.

## Results and reproducibility

Record `git rev-parse HEAD` for every campaign/run and preserve resolved scenario,
environment versions/configuration, DUT details, compiler/flags, traffic parameters,
resource allocation, readiness timestamps, durations, repetition, ordering, and failures.
The conceptual required fields are in [methodology.md](methodology.md). A clean-working-tree
requirement for final thesis campaigns is proposed, not implemented.

Raw output and run metadata must be immutable, including unsuccessful attempts. Processed
summaries and figures must be generated separately by scripts with provenance. Future run
records may use `metadata.edn`; exact schema, IDs, collection protocol, and storage integrity
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
│   └── clojure/
├── experiments/
│   ├── scenarios/
│   ├── runs/
│   └── processed/
├── analysis/
└── thesis/
```

Existing personal notes may remain under `notes/`. The future `mt/`, `backends/`, and
Ansible tree are to be introduced only when implementation is requested.
