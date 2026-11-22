# Experiments

## Accepted plan; not implemented

EDN scenarios and reusable traffic profiles will drive the Clojure `mt` orchestrator.
Apply the same traffic definition to Wasmtime, Podman, and VM variants. Fully automate
validation, environment reset, backend/VNF startup, active readiness, warm-up, measurement,
collection, cleanup, cooldown, and repeated execution. Support randomized ordering,
preferably randomized/interleaved blocks; avoid grouped fixed technology order.

See [experimental-framework.md](../docs/experimental-framework.md) for CLI, adapters, and
lifecycle, and [methodology.md](../docs/methodology.md) for scientific controls.

## Planned campaign evidence

The [persistence design](../docs/control-plane-persistence.md) defines immutable
run storage, manifests and partial evidence. Existing correctness evidence under
`experiments/local/` retains its current layout. Analysis output stays separate.

## Values still provisional

TRex is a candidate pending hardware/NIC and workload compatibility. Candidate sizes
64/256/512/1024/1500 bytes, possible IMIX-like traffic, exact load levels, timings, and
approximately 30 repetitions are proposals. Multiple repetitions are mandatory; final
count depends on variance and analysis. No final data or runner is introduced yet.
