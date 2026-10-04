# Network Function Implementations

## Accepted baseline; not implemented

Rust is the reference language for stateless Layer 4 vFW and round-robin Layer 4 vLB.
Share function logic across WebAssembly and native x86_64 targets wherever technically
feasible. Wasmtime runs the WebAssembly build; Docker Engine on Linux and a Linux guest
under QEMU/KVM/libvirt with virtio networking run the same native executable or equivalent
build from the same source tree. All variants execute on the common x86 DUT.

Rust is chosen for fairness, portability, low runtime overhead, and mature tooling;
it is not assumed to be universally the fastest WebAssembly language.

## Fairness and remaining design work

Validate equivalent filtering/balancing behavior and apply identical traffic profiles.
Document target-specific networking adapters rather than assuming identical socket APIs.
The initial preferred interface is L4/socket-based; exact WASI target, TCP/UDP semantics,
framing, error/drop behavior, dependencies, and native/Wasm build settings remain open.
Avoid unrelated implementations/algorithms that confound environment comparisons.
If shared code cannot provide equivalence, document the limitation and proposal before
changing the strategy. HTTP frameworks and raw packet-processing stacks are not baseline
requirements.

Read [decisions.md](../docs/decisions.md), [architecture.md](../docs/architecture.md),
[methodology.md](../docs/methodology.md), and each function README before implementation.
Optional Rust/C/C++/TinyGo WebAssembly comparisons must not delay the primary experiment.
No Rust projects or source-code structure are created at this documentation stage.
