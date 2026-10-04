# Experiments

## Accepted plan; not implemented

EDN scenarios and reusable traffic profiles will drive the Clojure `mt` orchestrator.
Apply the same traffic definition to Wasmtime, Docker, and VM variants. Fully automate
validation, environment reset, backend/VNF startup, active readiness, warm-up, measurement,
collection, cleanup, cooldown, and repeated execution. Support randomized ordering,
preferably randomized/interleaved blocks; avoid grouped fixed technology order.

See [experimental-framework.md](../docs/experimental-framework.md) for CLI, adapters, and
lifecycle, and [methodology.md](../docs/methodology.md) for scientific controls.

## Intended future result structure

```text
experiments/
├── scenarios/
├── runs/
│   └── <campaign>/
│       └── <experiment-id>/
│           ├── metadata.edn
│           ├── traffic.*
│           ├── cpu.csv
│           ├── memory.csv
│           ├── latency.*
│           ├── stdout.log
│           └── stderr.log
├── processed/
└── figures/
```

This is a proposed storage layout, not created directories or a finalized schema.
Raw run output/logs/metadata are immutable; processed summaries and figures are separate.
Analysis must never overwrite measurements. Preserve failed/incomplete runs with status
and reasons. Record Git commit, environment/source versions, resolved scenario, resource
allocation, traffic parameters, durations, ordering, and repetition for every run.

## Values still provisional

TRex is a candidate pending hardware/NIC and workload compatibility. Candidate sizes
64/256/512/1024/1500 bytes, possible IMIX-like traffic, exact load levels, timings, and
approximately 30 repetitions are proposals. Multiple repetitions are mandatory; final
count depends on variance and analysis. No final data or runner is introduced yet.
