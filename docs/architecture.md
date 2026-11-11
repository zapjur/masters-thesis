# Experimental Architecture

## Status and authority

D042–D050 record the revised design. Host responsibilities and the two-level HTTP
strategy are accepted; backend stack, monitoring stack, and concurrency design remain
proposals. Deployment is not implied. See [roadmap](implementation-roadmap.md),
[methodology](methodology.md), and [automation](experimental-framework.md).

## Physical hosts

| Host | Role | Responsibilities and controls |
| --- | --- | --- |
| A: MacBook Pro M1 Pro, 32 GB RAM, macOS | Primary controller, backends, central storage | Clojure/Babashka `mt`, EDN configuration/metadata, Ansible, appropriate builds/deployment, local synthetic and Social Feed backends, multiple backend instances, immutable raw artifacts, remote generator supervision, Prometheus retrieval |
| B: MacBook Pro M4 Max, macOS | Dedicated traffic generator | Remote Grafana k6 scenarios, request metrics, diagnostics and export to A; no main orchestrator, backend, or central storage during campaigns |
| C: Intel Core i5-12400, 16 GB RAM, SSD, AlmaLinux 10.2 | Common bare-metal DUT | Selected Rust vFW/vLB, Wasmtime/WASIp2 or Podman/Quadlet or QEMU/KVM/libvirt; controlled CPU/memory, exporters, reproducible deployment and scoped cleanup |
| D: Raspberry Pi, model and purchase undecided | Planned monitoring/infrastructure | Preferred Prometheus/Grafana, time-series storage and run exports; ARM64 Linux compatibility to verify on chosen hardware; portable to another Linux host |

The controller and backend are distinct logical roles on physical host A. All central
management originates there. Finish builds, preparation and deployment before measured
traffic; defer expensive retrieval/compression until afterward. Measure backend CPU,
memory, storage and network headroom, including contention with controller activity.
B must retain generator headroom; its execution interface must allow another compatible
host. Neither Mac is a primary DUT.

D connects by Ethernet to the home LAN and scrapes C over that LAN; no dedicated
Pi–DUT cable is required. D is not known to be purchased or operational. Model, RAM,
storage and deployment remain unresolved. Evaluate Prometheus/Grafana under a constrained
memory budget (including a possible 2 GB configuration), bounded cardinality/retention,
and external SSD versus microSD. Optional Wake-on-LAN, Tailscale, Pi-hole or similar
services must remain lightweight and stable; suspend intensive homelab work during
campaigns. Monitoring resource consumption stays outside the VNF host except exporters.

## Logical paths

```text
M1 Pro: mt controller --management--> M4 Max, DUT, monitoring host
         | local supervision
         v
M1 Pro: backend processes and immutable artifact storage

Measured application path:
M4 Max / k6 --> DUT / selected vFW or vLB --> M1 Pro / HTTP backend(s)

Monitoring path (planned):
Raspberry Pi / Prometheus --scrape--> DUT exporters
M1 Pro / mt --post-run query/export--> Prometheus
Grafana reads Prometheus for inspection; dashboards are not raw evidence.
```

Only one selected environment is measured at a time unless an explicit scaling scenario
specifies otherwise. Native Rust outside a container/VM is a reference and correctness
control, not a fourth primary technology.

## Execution and evidence

Share Rust function logic wherever feasible. Compile WASIp2 for Wasmtime and native
x86_64 for Podman/Quadlet and the Linux KVM guest. Prefer the identical native executable,
otherwise retain justified build differences and hashes. Ansible prepares hosts;
`mt deploy` handles artifacts; `mt run` owns campaign lifecycle.

The D038–D040 restricted firewall is functionally validated: 47 wire cases each for
native, WASIp2, Podman and KVM, 188 total with exact client-response parity. See
[original HTTP](firewall-correctness.md), [Podman](podman-correctness.md) and
[VM evidence](vm-correctness.md). This proves neither concurrent-load feasibility nor
performance. vLB, remote k6 campaigns, external backends and monitoring remain unimplemented.

## Network topology validation gate

[dut-inventory.md](dut-inventory.md) records three RTL8125 controllers; the initial
active link was 100 Mb/s, the other two had no carrier. Later software preparation
does not establish data-link capacity. Confirm negotiated speed, cable/switch/adapter
capacity, MTU, routing, addressing and link stability before selecting NIC roles.

| Candidate | Advantages | Required checks / fairness costs |
| --- | --- | --- |
| Separate ingress/egress, third NIC for management | Clear path and traffic separation using existing NICs | Verify Mac adapters/cabling first; explicit routes and backend access rules; avoid unintended IP forwarding/bypass |
| Existing switched LAN, explicit VNF endpoint and restricted backend access | May avoid purchases | Shared capacity and management contention; prove k6 cannot access backend through an unintended target/path |
| Explicit routed subnets with TAP/virtio guest | Reproducible guest path without SLIRP | Privilege/configuration review, host forwarding/bridge CPU cost and route parity |

A user-space L7 proxy terminates and creates TCP connections; physical traversal alone
does not prove application inspection. Validate endpoint addresses, request markers,
backend observation and deny/non-observation tests. Record routes, bridge/TAP setup,
interface counters and traffic captures where justified. No network changes are made here.

For final VM measurements evaluate Linux bridge + TAP + virtio, or explicitly routed
TAP/virtio, against identical ingress/egress segments. Current SLIRP host-forwarding is
only a functional adapter. Avoid unnecessary NAT and user-space forwarding. Compare
Podman host networking (less network isolation), host Wasmtime/WASI socket handling,
and guest virtio/host bridge processing explicitly: equivalent HTTP behavior does not
make kernel paths identical. Document those costs rather than subtracting them blindly.

## Backend and monitoring designs

[HTTP workloads](http-workloads.md) owns the synthetic and Social Feed design.
[Monitoring](monitoring.md) owns collector semantics and export reliability. Exact
versions, allocations, affinity, concurrency, topology and measurement settings require
pilot evidence and recorded decisions. AWS/EC2 remains auxiliary; final comparisons use C.
