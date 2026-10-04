# Infrastructure

This directory will contain infrastructure configuration required to run the experiments.

The concrete infrastructure is intentionally not selected yet.

Possible future contents include configuration for:

- WebAssembly runtime,
- container runtime,
- virtual machines,
- virtual networking,
- benchmark hosts,
- system tuning.

## Design objective

Infrastructure should make the three execution environments as comparable as possible.

## Important

Do not introduce Kubernetes, Terraform, Ansible, or cloud-specific infrastructure solely
for convenience.

Use them only if they solve an identified reproducibility or experimental-control problem.
