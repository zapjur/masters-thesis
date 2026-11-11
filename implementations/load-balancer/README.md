# Virtual Load Balancer (vLB)

Not implemented. Layer 7 balancing follows the firewall milestone (D031). A minimal
HTTP proxy with per-request round-robin backend selection is proposed; exact protocol,
connection reuse, backend count, and failure behavior remain open.

Reuse validated Rust parsing/transport/policy infrastructure where feasible. All variants
run on the same i5-12400 DUT through Wasmtime, Podman with Quadlet, or QEMU/KVM/libvirt.
Keep TLS termination, weighted routing, sticky sessions, and complex health checks out
of the baseline. Synthetic HTTP on M1 is the primary backend workload (D046). D047 proposes Rust
application backends, replacing D021’s language direction only if approved. Social Feed
adds complementary realism; existing Clojure fixtures remain.

Define equivalent request handling and demonstrate generator/backend headroom before
performance campaigns. See [architecture.md](../../docs/architecture.md),
[methodology.md](../../docs/methodology.md), and
[decisions.md](../../docs/decisions.md). Historical L4 decision D018 is superseded.

See [HTTP workload design](../../docs/http-workloads.md) and
[roadmap phase 8](../../docs/implementation-roadmap.md#phase-8--l7-load-balancer).
