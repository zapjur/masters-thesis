# Virtual Load Balancer (vLB)

## Status and baseline

Not implemented. The accepted baseline is a simple Layer 4 round-robin load balancer
in Rust, sharing logic across Wasmtime/WebAssembly and native Docker/VM variants on the
same DUT. The initial preferred abstraction is socket-based networking.

Exclude TLS termination, weighted routing, sticky sessions, complex health checks, and
application-aware routing. HTTP parsing/frameworks are not the primary experiment.

## Requirements still to define

- TCP/UDP model, socket/WASI interface, forwarding/framing behavior, and error handling.
- Unit of round-robin selection: connection, datagram, or another defined unit.
- Connection lifecycle, persistence, and transport state needed for correct operation.
- Backend count and destination layout on the separate M1 Pro backend machine.
- Functional equivalence and networking-path differences between environments.

Use a single configurable Clojure backend in `minimal` mode for the primary benchmark.
`delayed` (e.g. deterministic 5/20/100 ms) and `cpu-light` modes are optional secondary
scenarios; example delays are not accepted final values. Backend headroom must be verified.

## Candidate experimental dimensions

Connection/request rate, concurrent connections, packet/payload size, and backend count
remain candidate dimensions. Define request framing and units if reporting requests/s
without HTTP. See [architecture.md](../../docs/architecture.md),
[methodology.md](../../docs/methodology.md), and D018/D021 in
[decisions.md](../../docs/decisions.md). No application code is introduced yet.
