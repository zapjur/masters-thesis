# Experiments

This directory will contain the benchmark definitions and execution tooling.

Do not place final measurement data here until the experiment format and metadata structure
have been defined.

## Future structure

A likely future structure is:

```text
experiments/
├── scenarios/
├── runner/
├── raw/
├── processed/
└── metadata/
```

Do not create these directories yet.

## Experiment principle

Every experiment should be reproducible from:

- repository state,
- configuration,
- environment metadata,
- experiment parameters.

Manual benchmark procedures should be minimized.

## Raw data rule

Once `raw/` is introduced:

- files inside it should be treated as immutable,
- processing should write to another directory,
- analysis scripts must never overwrite source measurements.
