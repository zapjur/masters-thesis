# Deterministic HTTP backend

This native Rust service supplies fixed HTTP responses for functional checks and
future synthetic L7 firewall and load-balancer experiments. Run it on the
MacBook M1 Pro, outside the DUT. The same executable can run several independent
listeners. Request handling uses Tokio and Axum with no per-request logging,
filesystem access, or artificial delay.

## Build and run on macOS

Build with Rust and Cargo 1.91.1. Direct dependencies are Axum 0.8.6 (HTTP/1
and Tokio features) and Tokio 1.48.0. The [lockfile](Cargo.lock) pins transitive
dependencies. Use the same locked dependency graph for debug and release builds.

```sh
cd backends/synthetic
cargo +1.91.1 build --locked --release
./target/release/mt-synthetic-backend
```

Use `--help` to display the options. The process reports its bound address and
maximum size at startup. Stop it with Ctrl-C or SIGTERM to drain connections.

| Option | Default | Meaning |
| --- | --- | --- |
| `--bind IP` | `127.0.0.1` | Numeric IPv4 or IPv6 address |
| `--port PORT` | `8080` | TCP port. Use 0 to request an OS-assigned port |
| `--max-size BYTES` | `65536` | Maximum echo body. Allowed range is 1–16777216 bytes |
| `--instance-id ID` | Omitted | Adds `X-Backend-Instance` to endpoint responses. Use 1–64 ASCII letters, digits, dots, underscores or hyphens |

For home LAN development, explicitly pass the M1 Pro's existing LAN address with
`--bind` and use that address from the client. Loopback is the default.

```sh
# Run in two terminals.
./target/release/mt-synthetic-backend --port 8080 --instance-id first
./target/release/mt-synthetic-backend --port 8081 --instance-id second

curl --http1.1 -fsS http://127.0.0.1:8080/health
curl --http1.1 -fsS http://127.0.0.1:8080/ready
curl --http1.1 -fsS 'http://127.0.0.1:8081/echo?size=1024' | wc -c
curl --http1.1 -fsS 'http://127.0.0.1:8081/echo?size=1024' | shasum -a 256
```

## API

The endpoints accept bodyless requests and return fixed-length HTTP/1.1
responses to HTTP/1.1 clients. Body literals below use `\n` for a final LF.

| Request | Status | Body |
| --- | --- | --- |
| `GET /health` | 200 | `ok\n` |
| `GET /ready` | 200 | `backend-ok\n` |
| `GET /echo?size=N` | 200 | Exactly N bytes. Byte at offset i is i modulo 256. Content type is `application/octet-stream` |
| `GET /echo` | 200 | Same pattern, with size min(1024, configured maximum) |
| `GET /echo` with invalid query | 400 | `invalid size\n` |

The raw echo query is either empty or a single `size=` followed by one or more
ASCII decimal digits. Zero and leading zeros are valid. Signs, escapes,
additional parameters, and values above the maximum return 400. The body for
each valid size is identical across runs, instances, and build profiles.
HTTP metadata such as the Date header can vary. Each instance builds one
immutable payload at startup, and requests share
prefixes of it. 128, 1024, 4096 and 16384 bytes are development examples.

## Firewall compatibility

Configure the backend address as the firewall upstream. Add exact allow rules
`allow:GET:/ready` and `allow:GET:/echo` and set the firewall response-body
limit at or above the largest requested response. Use bodyless HTTP/1.1 GETs
and valid sizes. The firewall matches `/echo` and forwards its query verbatim.
Both endpoints return HTTP/1.1 200 with a single Content-Length and no redirects
or chunked encoding on this path. The readiness body matches the existing
fixture. Use `/health` directly, or add its own allow rule for proxying it.
An invalid echo query is exposed as 400 directly and becomes a 502 through
the current firewall, which accepts only upstream 200 responses. The existing
47-case correctness suite manages its own fixtures.

## Tests

```sh
cargo +1.91.1 fmt --check
cargo +1.91.1 clippy --locked --all-targets -- -D warnings
cargo +1.91.1 test --locked
```

Tests start real processes on OS-assigned loopback ports and verify HTTP/1.1
status and framing, stable binary bodies, query errors, size bounds, concurrent
requests, instance isolation, and successful SIGINT/SIGTERM shutdown.

On 2026-11-19, an Apple M1 Pro running macOS 26.5.1 (25F80) passed formatting,
Clippy, and all four integration tests in both debug and release profiles.
The unchanged native firewall forwarded the 11-byte readiness body and the
byte-identical 1024-byte echo body, and denied an unmatched path with 403.
The direct and proxied 1024-byte bodies had SHA-256
`785b0751fc2c53dc14a4ce3d800e69ef9ce1009eb327ccf458afe09c242c26c9`.
Explicit binding to the existing LAN IP passed a same-host readiness request.
Cross-host LAN traversal and backend capacity remain unmeasured.

## Limits and planned workload use

The service provides plain HTTP with no database or authentication. Its
native Tokio/Axum build establishes compatibility for the backend only.
The firewall currently closes each connection after one request. Future
k6 pilots will establish backend headroom and distinguish that path from
direct-backend connection reuse. Future `mt` campaign support should pass
the resolved configuration, probe readiness, supervise each instance, and
record compiler and dependency versions, binary and payload hashes, and
instance diagnostics. Payloads and rates for final campaigns remain open.
