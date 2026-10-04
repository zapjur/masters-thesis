1. Grafana K6 vs Cisco TRex as a traffic generator?
2. Docker vs Podman for containerization?
3. Rust vs Go for implementing network functions?
4. Clojure for backends?

## Current decision status

The questions above are retained as historical planning notes. The authoritative status
is in [docs/decisions.md](../docs/decisions.md):

- Traffic generator selection is open; TRex is a candidate (D014), not accepted.
- Docker Engine on Linux is the accepted container baseline (D010).
- Rust is the accepted VNF reference language (D012); optional source-language work is D005.
- Clojure backends are planned on the M1 Pro (D021); the primary vLB mode is `minimal`.
