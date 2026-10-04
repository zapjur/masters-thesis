# Virtual Firewall (vFW)

## Status and baseline

Not implemented. The accepted baseline is a small stateless Layer 4 firewall in Rust,
sharing logic across Wasmtime/WebAssembly and native Docker/VM variants on the same DUT.
Layer 4 / socket-based networking is initially preferred.

Potential policy fields are source address, destination address, source port, destination
port, protocol, and allow/drop decision. Exact field set and rule representation remain
open. The baseline excludes deep packet inspection, IDS/IPS, connection tracking, NAT,
and complex policy engines.

## Requirements still to define

- TCP/UDP interface, WASI compatibility, and forwarding behavior.
- Rule representation, ordering, default policy, and complexity/count matrix.
- Exact allow/drop/reject semantics and verification of permitted/blocked traffic.
- Meaning of packet versus payload/socket operation for measured throughput.
- Equivalent behavior and network paths across native and WebAssembly targets.

Stateless filtering does not resolve whether TCP transport handling itself requires
connection state; document this distinction rather than adding stateful firewall policy.
A simple TCP/UDP receiver on the M1 Pro may verify correct forwarding.

## Candidate experimental dimensions

Rule count, packet/payload size, packets/s or other appropriate L4 throughput unit,
and allow/drop ratio are candidates, not a finalized matrix. Intentional drops must be
distinguished from overload loss. See [methodology.md](../../docs/methodology.md) and
D017 in [decisions.md](../../docs/decisions.md). No application code is introduced yet.
