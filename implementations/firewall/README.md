# Virtual Firewall (vFW)

Not implemented. The accepted direction is a Layer 7 firewall in Rust, developed
before the load balancer (D031). Shared logic targets Wasmtime/WebAssembly and native
Podman/Quadlet and QEMU/KVM/libvirt variants on the same i5-12400 DUT.

The proposed first milestone is a restricted HTTP/1.1 filtering proxy. See
[the firewall contract](../../docs/l7-firewall.md) for method/path rules, default-deny
behavior, framing/size limits, connection handling, correctness cases, and feasibility
gates. These details are proposed in D035, not final benchmark configuration.

Validate Wasmtime sockets and parser compatibility before substantial implementation.
Verify blocked requests never reach the backend and allowed requests behave identically
across all three environments. Do not interpret HTTP requests as raw network packets.
TLS termination, broad body inspection, IDS/IPS, and production WAF features are outside
the initial milestone. See [methodology.md](../../docs/methodology.md) and
[decisions.md](../../docs/decisions.md).
