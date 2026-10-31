# Experimental Architecture

## Status

**Baseline accepted — networking details and measurement configuration remain open.**

Accepted choices and proposals are recorded in [decisions.md](decisions.md). This document
specifies the physical/logical system; [methodology.md](methodology.md) specifies scientific
controls and [experimental-framework.md](experimental-framework.md) specifies automation.

## Objective and physical roles

Compare both network performance and environment/resource overhead on a common DUT.

| Physical machine | Stable experimental role | Responsibilities |
| --- | --- | --- |
| MacBook M4 Max | Generator and orchestrator | Traffic generation, central `mt` control, final artifact collection |
| x86 desktop: i7-6700K, 16 GB RAM, SSD | Bare-metal DUT | Wasmtime, Docker Engine, QEMU/KVM/libvirt, VNF execution, resource measurements |
| MacBook M1 Pro | Backend and receiver | Controlled backend services and traffic destinations |

The user reports AlmaLinux 10.2 installed on the DUT and three NICs (D032). This is
the current OS baseline; exact kernel/package versions and NIC models/link speeds
remain unverified. Native VNF builds target x86_64.
All three execution technologies use this same DUT. The Apple Silicon machines are
supporting hosts, not DUT variants; their capacity must be verified to avoid bottlenecks.

```text
MacBook M4 Max: traffic generator + mt orchestrator
                         |
                         v
            x86 desktop: common Linux DUT
             +-----------+-----------+
             |           |           |
          Wasmtime     Docker     QEMU/KVM/libvirt
          Rust Wasm  Rust native  Rust native in Linux guest
             |           |           |
             +-----------+-----------+
                         |
                         v
        MacBook M1 Pro: backend / receiver
```

Each selected environment runs vFW or vLB. The diagram shows alternative execution
paths, not a requirement to run all variants concurrently. Experiment ordering and
reset/isolation controls are defined in methodology.

## Execution model

```text
                    Shared Rust source
                           |
                  +--------+--------+
                  |                 |
             WebAssembly       native x86_64
                  |                 |
               Wasmtime       +-----+-----+
                              |           |
                        Docker Engine  Linux guest
                                       QEMU/KVM/libvirt
                                       virtio networking
```

Docker runs the native build. The VM runs the same executable or an equivalent build
from the same source tree. Shared logic is intended where technically feasible; exact
WASI/socket interfaces, target, dependencies, and networking adapters require validation.
Neither source identity nor functional/network-path equivalence is assumed to be proven.

## Network path and hardware proposal

```text
Generator -> ingress -> DUT -> egress -> Backend
```

Physical separation of generator and backend traffic is preferred where practical.
The user reports three DUT NICs. Separate management, ingress, and egress roles are
proposed, not assigned. Evaluate available NICs,
link speeds, cabling/switch layout, compatibility, and routing before selecting the setup.

Container networking, guest virtio networking, and Wasmtime socket access may introduce
different host paths. Record and control these differences rather than attributing all
observed overhead solely to execution technology. L7 is the current workload direction, with firewall first and load balancer second.
A minimal HTTP proxy workload is proposed. Application parsing, firewall rejection,
load-balancer request selection, and transport handling remain unresolved.

## Backends

A single configurable Clojure backend service is planned on the M1 Pro:

| Mode | Behavior | Scope |
| --- | --- | --- |
| `minimal` | Minimal response/acknowledgment processing | Primary vLB benchmark |
| `delayed` | Deterministic configured delay, e.g. 5/20/100 ms | Optional secondary scenario; values illustrative |
| `cpu-light` | Small deterministic computation/serialization | Optional secondary scenario |

Multiple logical backend destinations may run on the same backend machine. Backend count,
protocol, framing, capacity, and port/address layout remain open. The firewall may use an
even simpler TCP/UDP receiver to verify correct forwarding. Backend performance is not
an independent variable, but must not silently limit measured VNF performance.

## Cloud boundary

AWS/EC2 is not the primary environment for final measurements. Underlying virtualization,
noisy neighbours, and weaker CPU/network controls may obscure environment and VM overhead.
Development, CI, functional tests, reproducibility trials, and auxiliary cloud experiments
are permitted. Changing the primary bare-metal baseline requires evidence and a new decision.

## Remaining architecture questions

- Verify NIC inventory, link capacity, topology, and traffic-generator compatibility.
- Select exact Linux guest, runtime/toolchain versions, WASI target and socket integration.
- Define equivalent network paths, TCP/UDP behavior, backend destinations, and management access.
- Define CPU affinity/vCPU pinning, memory limits, and host/guest resource accounting.
- Verify generator/backend headroom and isolate orchestration/collection overhead.
- Specify background activity controls, power/performance settings, and clock handling.

Exact CPU IDs, resource limits, traffic tools, and collectors are not selected here.

## Existing DUT tooling and next planning step

The sibling `../homelab` repository supplies an `hl` power/SSH CLI and a read-only
Ansible inspection playbook; it does not yet provision the host. See D032 for
provenance requirements. No host commands were executed for this documentation update.

Before implementation, specify one minimal L7 firewall behavior, then verify a shared
Rust/Wasmtime/native integration path. HTTP/1.1 without TLS, method/path filtering,
an explicit blocked response, and a minimal forwarding backend are proposals. Define
parser limits, malformed-request behavior, connection reuse, and response framing.
The subsequent L7 load balancer can reuse the validated transport/parser path if feasible;
per-request round-robin is proposed. Keep the same behavior across all three variants.
