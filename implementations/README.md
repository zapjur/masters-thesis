# Network Function Implementations

## Accepted baseline; first HTTP firewall implemented

The restricted HTTP/1.1 firewall passes native/WASIp2 correctness checks; see
[the evidence](../docs/firewall-correctness.md). The load balancer and container/VM
parity remain future work. The bounded TCP probe under
`feasibility/tcp-forwarder/` passes native/WASIp2 functional checks; see
[the report](../docs/tcp-feasibility.md). It is not the final VNF or concurrency model.

Rust is the reference language for Layer 7 vFW followed by Layer 7 vLB.
Share function logic across WebAssembly and native x86_64 targets wherever technically
feasible. Wasmtime runs the WebAssembly build; Podman on Linux and a Linux guest
under QEMU/KVM/libvirt with virtio networking run the same native executable or equivalent
build from the same source tree. All variants execute on the common x86 DUT.

Rust is chosen for fairness, portability, low runtime overhead, and mature tooling;
it is not assumed to be universally the fastest WebAssembly language.

## Fairness and remaining design work

Validate equivalent filtering/balancing behavior and apply identical traffic profiles.
Document target-specific networking adapters rather than assuming identical socket APIs.
The proposed first interface is a restricted HTTP/1.1 proxy; exact WASI target,
framing, rejection behavior, dependencies, and native/Wasm build settings require validation.
Avoid unrelated implementations/algorithms that confound environment comparisons.
If shared code cannot provide equivalence, document the limitation and proposal before
changing the strategy. Raw packet-processing stacks are not baseline requirements.

Read [decisions.md](../docs/decisions.md), [architecture.md](../docs/architecture.md),
[methodology.md](../docs/methodology.md), and each function README before implementation.
Optional Rust/C/C++/TinyGo WebAssembly comparisons must not delay the primary experiment.
See [the firewall milestone](../docs/l7-firewall.md) for the first contract and gates.
