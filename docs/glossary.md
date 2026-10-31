# Glossary

## WebAssembly

A portable binary instruction format and execution technology designed around a compact,
structured, sandbox-friendly code format.

In this thesis, "WebAssembly" refers to the technology being evaluated.

A concrete execution engine should be referred to as a WebAssembly runtime.

## WebAssembly runtime

Software responsible for loading and executing WebAssembly modules.

Wasmtime is the primary runtime; its version and WASI/socket configuration remain open.

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

Docker Engine on Linux is the baseline container environment.

## Virtual machine

An isolated guest computing environment running through hardware virtualization or
emulation and typically containing its own operating-system kernel.

## Hypervisor

Software or system component responsible for providing and managing virtual machines.
The baseline VM stack is QEMU/KVM/libvirt with a Linux guest and virtio networking.

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
- connections per second,
- requests per second where appropriate,
- bits per second.

## Startup time

Time between initiating creation/startup of the network-function environment and the
function becoming ready to process traffic correctly.

The final measurement boundary must be precisely defined.

## vFW / vLB

Virtual firewall / virtual load balancer. The current thesis direction is Layer 7,
with the firewall implemented first and the load balancer second. HTTP filtering and
per-request round-robin balancing remain proposals.

## Layer 7 / application-layer networking

The current workload direction inspects application messages. HTTP is proposed; exact
protocol semantics remain open. Underlying sockets still handle transport. Exact TCP/UDP,
forwarding, and framing semantics remain to be defined; socket processing does not
necessarily mean processing raw Ethernet frames. Stateless firewall policy is distinct
from any transport connection state required by the implementation.

## DUT

Device Under Test. The common bare-metal x86 i7-6700K Linux desktop runs all tested
execution environments. Supporting MacBooks are not primary DUTs.

## mt

Master-thesis: the planned Clojure experiment orchestrator/CLI on the M4 Max. It manages
application deployment and experiment lifecycles outside the measured VNF datapath.

## EDN

Extensible Data Notation. The accepted format for experiment/scenario definitions;
illustrative values are not final methodology. Run metadata may also use EDN.

## Ansible

The selected host provisioning/configuration tool. It manages machine/OS readiness,
while `mt deploy` manages artifacts and `mt run` manages experiments.

## Traffic profile

Reusable workload definition independent of execution environment, applied identically
to Wasmtime, Docker, and VM runs. Protocol, size, rate, and connections require units.

## Active readiness

A probe demonstrating the VNF can correctly process traffic. Startup time is conceptually
the first successful probe timestamp minus the start-command timestamp; exact boundaries
and probe intervals remain open.

## Campaign and repetition

A campaign groups scenario runs; a repetition repeats a configured measurement. Exact
ID/storage conventions are open. Multiple repetitions are mandatory; approximately 30
per scenario is a proposal.

## Total environment cost

Resource consumption including relevant runtime/container/guest and host virtualization
overhead. It must be distinguished from VNF process cost and allocated memory, particularly
when comparing VM memory with native or WebAssembly process measurements.
