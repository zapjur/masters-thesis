# Firewall

## Status

Not implemented.

## Purpose

This workload will represent a network function that evaluates traffic against a filtering
policy.

The implementation should remain intentionally small and measurable.

## Requirements to define

Before implementation, decide:

- network layer,
- packet or request processing model,
- filtering rule representation,
- number and complexity of rules,
- allowed and rejected traffic behavior,
- stateful vs stateless operation,
- whether connection tracking is required.

## Initial preference

Prefer a stateless design unless a stateful firewall provides a clear research benefit.

A simpler function reduces uncontrolled implementation differences between execution
technologies.

## Experimental characteristics

Potential workload dimensions include:

- packets per second,
- packet size,
- number of filtering rules,
- allow/drop ratio.

These are not yet final.
