# Network Function Implementations

This directory will contain the implementations used in the experimental comparison.

Initial functions:

- firewall,
- load balancer.

The exact source-code structure should not be created until the implementation strategy
has been selected.

## Fairness principle

The implementations should be as functionally equivalent as reasonably possible across:

- WebAssembly,
- containers,
- virtual machines.

Avoid implementing fundamentally different algorithms and then attributing the resulting
performance difference to the execution technology.

## Open design decision

Two possible implementation strategies exist.

### Shared-core approach

Implement the function logic once and compile/package it for different environments.

Advantages:

- strong functional equivalence,
- easier maintenance.

Possible problem:

- not every execution environment exposes equivalent networking APIs.

### Environment-specific implementations

Implement equivalent behavior separately for each execution environment.

Advantages:

- allows more idiomatic platform integration.

Possible problem:

- code differences may become a confounding variable.

This decision must be made before implementation begins.
