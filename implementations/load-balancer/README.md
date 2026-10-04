# Load Balancer

## Status

Not implemented.

## Purpose

This workload will represent a network function that forwards or distributes incoming
traffic between multiple backend destinations.

## Requirements to define

Before implementation, decide:

- network layer,
- connection model,
- balancing algorithm,
- backend count,
- health-check behavior,
- stateful vs stateless operation,
- connection persistence requirements.

## Initial preference

Prefer a simple balancing strategy such as round-robin unless another algorithm provides
specific research value.

The goal is to evaluate execution technology rather than load-balancing algorithm quality.

## Experimental characteristics

Potential workload dimensions include:

- request or connection rate,
- concurrent connections,
- backend count,
- packet or request size.

These are not yet final.
