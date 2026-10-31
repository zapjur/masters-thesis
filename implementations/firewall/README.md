# Virtual Firewall (vFW)

The first restricted HTTP/1.1 firewall is implemented in shared Rust using httparse
1.10.1. Native Linux/musl and WASIp2/Wasmtime pass 47 wire cases each, with paired
response parity verified on the i5-12400 DUT. Podman/Quadlet and VM parity remain
future work. See [the evidence and reproduction](../../docs/firewall-correctness.md).

The functional contract is accepted under D038, implementing a subset of D035. See
[the firewall contract](../../docs/l7-firewall.md) for method/path rules, default-deny
behavior, framing/size limits, connection handling, correctness cases, and feasibility
gates. Functional limits and sequential operation are not final benchmark configuration.

Wasmtime sockets and parser compatibility have now been validated for this subset.
Verify blocked requests never reach the backend and allowed requests behave identically
across all three environments. Do not interpret HTTP requests as raw network packets.
TLS termination, broad body inspection, IDS/IPS, and production WAF features are outside
the initial milestone. See [methodology.md](../../docs/methodology.md) and
[decisions.md](../../docs/decisions.md).

From `mt/`, after preparing the pinned tools:

```sh
bb mt firewall check --ssh-host DUT_ALIAS --remote-root /tmp/mt-tcp-feasibility-clojure
```

The executable's positional arguments are:

```text
firewall LISTEN_IP:PORT BACKEND_IP:PORT IO_MS ACCEPT_MS MAX_HEAD MAX_HEADERS MAX_BODY MAX_CONNECTIONS [allow|deny:METHOD|*:PATH|* ...]
```

Rules are evaluated in order; omitted matches are denied. `MAX_CONNECTIONS=0`
continues until an idle deadline or external termination. The automated suite uses
three connections per process: two active readiness checks and one case. The
current model is single-threaded and sequential, with one request per connection.
