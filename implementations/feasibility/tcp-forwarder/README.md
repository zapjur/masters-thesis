# TCP Forwarding Feasibility Probe

This is a dependency-free Rust socket probe, not the L7 firewall. It accepts one
length-prefixed request, forwards it to one backend, returns the framed response,
then closes sockets and exits. Lengths and total exchange time are bounded.

`mt feasibility tcp` compiles identical source for native x86_64 Linux/musl and
`wasm32-wasip2`, runs matching functional fixtures, and retains logs and metadata.
The Clojure/Babashka fixture in `mt/src/mt/tcp_check.clj` is test tooling on DUT
loopback, not the final backend service. The probe and tool bootstrap require no Python.
See [the report](../../../docs/tcp-feasibility.md) and
[mt instructions](../../../mt/README.md) for setup, reproduction, and observed limits.

Command arguments are positional:

```text
probe LISTEN_IP:PORT BACKEND_IP:PORT IO_TIMEOUT_MS ACCEPT_TIMEOUT_MS MAX_BYTES
```

Use numeric addresses. Do not expose this development probe as a public service.
The Cargo manifest describes the crate for editors; the isolated runner compiles
with pinned `rustc` directly, so Cargo and a C compiler are not required.
