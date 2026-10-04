# Glossary

## WebAssembly

A portable binary instruction format and execution technology designed around a compact,
structured, sandbox-friendly code format.

In this thesis, "WebAssembly" refers to the technology being evaluated.

A concrete execution engine should be referred to as a WebAssembly runtime.

## WebAssembly runtime

Software responsible for loading and executing WebAssembly modules.

The concrete runtime used in the experiments has not yet been selected.

## WASI

WebAssembly System Interface.

A family of interfaces designed to allow WebAssembly modules to interact with system
capabilities outside the browser.

Its role in the experimental environment remains to be determined.

## Container

An operating-system-level isolation mechanism in which processes typically share the host
kernel while executing in isolated namespaces with controlled resources.

## Container runtime

Software responsible for creating and managing containers.

The runtime used in the experiments has not yet been selected.

## Virtual machine

An isolated guest computing environment running through hardware virtualization or
emulation and typically containing its own operating-system kernel.

## Hypervisor

Software or system component responsible for providing and managing virtual machines.

## Network function

Software that performs a networking-related operation on network traffic.

Examples in this thesis include:

- firewall,
- load balancer.

## Firewall

A network function that allows, rejects, or otherwise processes traffic according to
defined filtering rules.

## Load balancer

A network function that distributes traffic between multiple backend destinations.

## NFV

Network Function Virtualization.

An architectural approach in which network functions traditionally implemented using
dedicated appliances are implemented as software workloads on general-purpose
infrastructure.

## VNF

Virtualized Network Function.

A software implementation of a network function executed within a virtualized environment.

The term should be used carefully because WebAssembly, containers, and VMs provide
different execution models.

## Latency

Time associated with processing or transferring a unit of traffic.

The exact latency definition used in the experiments must be specified in
`methodology.md`.

## Throughput

Amount of successfully processed traffic per unit of time.

Depending on the experiment, this may be represented as:

- packets per second,
- requests per second,
- bits per second.

## Startup time

Time between initiating creation/startup of the network-function environment and the
function becoming ready to process traffic correctly.

The final measurement boundary must be precisely defined.
