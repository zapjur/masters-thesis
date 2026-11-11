# Native Rust / WASIp2 TCP Feasibility

## Outcome and boundaries

On 2026-10-31, identical Rust source compiled for native x86_64 Linux and
`wasm32-wasip2` passed twelve functional cases per variant on the AlmaLinux DUT.
The complete `mt feasibility tcp` command exited successfully and collected its
artifacts on the controller. This establishes the tested serial socket path, not
HTTP correctness, container/VM equivalence, concurrency, or performance.

The current backend and client are Clojure/Babashka fixtures on DUT loopback (D037). This
development arrangement deliberately avoids changing firewalld, SELinux, cabling,
or the final three-machine topology. The original Python test scripts were replaced
at the user's request; their earlier raw evidence remains preserved. The fixtures
are not a VNF language variant or the final controlled backend service. No throughput/latency benchmark,
warm-up, resource allocation, or final experimental methodology is implied.

## Pinned tools and source

| Component | Observed version or target |
| --- | --- |
| Rust compiler | 1.99.1 (58cf8f9c3 2026-10-13), LLVM 23.1.1 |
| Wasmtime | 50.0.1 (c307ec81d 2026-10-29) |
| Native probe | x86_64-unknown-linux-musl, opt-level=2, panic=abort |
| WebAssembly probe | wasm32-wasip2 command component, opt-level=2, panic=abort |
| Controller | Babashka 1.12.218 running Clojure `mt` |
| Current DUT fixture runner | Checksum-pinned static Babashka 1.12.218 |
| DUT | Intel Core i5-12400, AlmaLinux 10.2, kernel 6.12.0-211.68.1.el10_2.x86_64 |

The native probe uses the compiler's `rust-lld` and self-contained musl linking
because the DUT has no C compiler/development packages. This does not select musl
as the final VNF libc/build baseline. Neither build has third-party Rust dependencies.
Wasmtime uses `run -S inherit-network=y`; the probe uses only numeric loopback
addresses. That flag grants wider host network access than the fixture exercises;
network permission policy remains a deployment question. No WASI HTTP host parses
or forwards the application payload on behalf of the guest.

Tools live under the selected `/tmp/mt-tcp-feasibility.../tools` root, outside system package paths. The
[checksum lock](../infra/feasibility/toolchains.lock.json) records exact release
URLs and SHA-256 values. No shell profile or system package, service, firewall,
SELinux, NIC, or VM configuration was changed. Ansible stages and prepares the
tools through a controller-side Clojure bootstrap; `mt` stages source, builds,
supervises tests, and collects evidence. The bootstrap also runs directly without
Ansible. Archive download, checksum validation, and extraction happen on the controller;
SSH/SCP stage and install the tools on the DUT. Neither Python nor tar is needed on
the DUT. The optional Ansible wrapper still uses its normal Python controller.

## Probe protocol and limits

Each connection carries one frame: a four-byte unsigned big-endian payload length,
followed by exactly that many bytes. The probe accepts one client, reads a bounded
request, connects to one configured backend, forwards the frame, reads a bounded
response, relays it, closes sockets, and exits. The fixture backend XORs each byte
with `0xa5`, proving that responses came through the backend rather than a local echo.
This framing is solely for the socket feasibility check and is not the HTTP contract.

The EDN scenario specifies a 65536-byte frame limit, a 400 ms exchange deadline,
a 1000 ms accept deadline, and a 20-second supervisor bound per case. Native runs
before WebAssembly, once per case; warm-up and cooldown are zero. These are functional
test settings, not accepted benchmark values or a comparative experimental order.
Read/write operations use the remaining exchange budget rather than resetting the
budget per chunk. Timeouts are reported as errors and never successful forwarding.

## Observed correctness checks

| Case | Native | WASIp2 / Wasmtime |
| --- | --- | --- |
| Binary request/response through backend | passed | passed |
| Frame split across multiple client writes | passed | passed |
| Empty frame | passed | passed |
| Exactly 65536 payload bytes | passed | passed |
| Incomplete client input times out | passed | passed |
| Backend response times out | passed | passed |
| Backend connection refused | passed | passed |
| Oversized request rejected before backend contact | passed | passed |
| Oversized backend response rejected | passed | passed |
| Client EOF during incomplete frame | passed | passed |
| No client: accept deadline terminates probe | passed | passed |
| Supervisor terminates probe and listener closes | passed | passed |

For successes, tests compare exact transformed bytes and backend request count.
Failure cases assert error phase/kind, nonzero exit, no returned response, and the
expected backend observation. Every case verifies listener closure after exit.
Forced termination is a supervisor-cleanup check; graceful exit is demonstrated
by the successful exchanges, not by signal-handling equivalence.

Read-deadline expiration is exercised on both sides. Write-timeout options are used
by successful writes, but actual send-buffer backpressure and blackholed outbound
connection timeout are not exercised. IPv6, DNS, sustained concurrency, large
response streaming, and external NIC paths remain untested.

## Original error-path finding and retained Python-runner attempts

In the first attempt, WASIp2 `TcpStream::connect_timeout` returned a stream for a
refused loopback connection, and the failure surfaced at write as “Socket not
connected.” Native reported refusal at connect. This is an observation with this
toolchain/runtime combination, not a proven upstream root cause.

The shared source now checks `take_error()` and `peer_addr()` after connecting,
before forwarding. Both builds then reported refusal at the connect phase. There
is no target-specific errno remapping and the original failed check was retained.

| Attempt ID | TCP cases | Controller result |
| --- | --- | --- |
| tcp-1793467698082-89095f21-fac3-4525-9060-da1b7c60b1a8 | 23/24; WASIp2 refusal diagnostic failed | failure; evidence collected |
| tcp-1793467812429-d2f6e76b-dd99-4b7e-b268-4737e3b2c2cf | 24/24 | failure after tests: inherited output treated as a string; evidence collected |
| tcp-1793467876237-806df9e0-f441-49b8-aa7d-17a632fd3302 | 24/24 | success |

These three attempts used the original Python fixtures. They remain valid historical
evidence; they are not presented as executions of the rewritten Clojure runner.
The controller output handling was corrected and regression-tested. The successful
attempt ran from 17:31:17 to 17:31:22 UTC; these are provenance timestamps, not a
startup-time or performance result. Its source commit is
`501550884e7c1de032920d2314399ba1ef943f47` with a dirty working tree. A commit alone
does not identify this implementation: retained source snapshots and SHA-256 values
identify the code actually used. The final summary JSON SHA-256 is
`e23e53d67467276b1cffc3fa29f59562d1579bb2cb4e8e70813d5afbc19f5cc5`.

Artifacts remain under ignored `experiments/local/<attempt-id>/` and matching
temporary DUT directories. Each attempt has a unique ID; the remote runner refuses
to overwrite an existing results directory. Preserve these records, including
unsuccessful attempts. This checked-in report is a sanitized description, not a
substitute for raw evidence. No raw measurement was manually edited.

## Clojure rewrite verification

At the user's request, the custom Python scripts were replaced under D037. The
Rust source and twelve-case functional contract remain unchanged. A fresh
`/tmp/mt-tcp-feasibility-clojure` directory was prepared using only the controller's
Clojure bootstrap, curl/tar, and OpenSSH. The DUT fixtures run on a checksum-pinned
static Babashka 1.12.218 executable; they require no Python installation.

| Clojure-runner attempt ID | Result |
| --- | --- |
| tcp-1793475965358-b4f732d8-97e0-4960-9c40-1e18d631708e | No cases ran: direct procfs CPU metadata read failed before a summary could be written; source/partial directory retained |
| tcp-1793476004719-76938eac-e08e-47aa-8c08-9bbf820451ca | No cases ran: streaming procfs CPU read also failed; failure summary retained |
| tcp-1793476054090-42388e90-d8a7-4469-be31-ad3ce4799a79 | 24/24 passed; complete controller command succeeded |

The DUT's native-image runtime returned `Invalid argument` while reading procfs
through Java file streams. CPU inventory now uses a coreutils `cat` subprocess;
the collected CPU model is still recorded, and collection failure remains an error.
Controller errors are journaled for later attempts. Earlier source snapshots and
results, including the Python-based runs, remain unmodified.

The successful Clojure attempt ran from 19:47:34 to 19:47:40 UTC on 2026-10-31. Its
commit was `501550884e7c1de032920d2314399ba1ef943f47` with a dirty tree; retained
source snapshots/hashes identify the tested implementation. Its summary SHA-256 is
`d71023a8542da3af1c7f197a8ab60b1be46e5385fb305113ddda59549f5aada8`.
These timestamps and case results are development provenance, not performance data.

Nine local Clojure tests with 24 assertions passed, covering fixtures, framing limits,
cached-archive checksum rejection, process handling, CPU inventory, safe remote
arguments, and refusing to overwrite earlier results. The optional delegated Ansible
wrapper was exercised against the prepared directory and reported `changed=0`.

## Reproduction

Prerequisites: an existing OpenSSH alias with trusted host key, controller Babashka,
curl/tar/OpenSSH, and an x86_64 Linux DUT with bash/core utilities. Tool preparation
stages its own pinned Babashka, compiler and runtime; no Python is required on the DUT.
Tool preparation needs outbound HTTPS on the controller; execution
uses SSH and loopback. No sudo is needed. See [mt/README.md](../mt/README.md) for exact
commands using the existing homelab inventory/configuration.

`mt` captures the resolved EDN configuration, compiler/runtime versions and build
commands, artifact hashes, kernel/CPU/Babashka details, source hashes/snapshots, Git
commit and dirty status, case order, exit codes, backend observations, and logs.
It attempts collection even when compilation or tests fail. A transport failure
can still prevent collection; the uniquely named remote directory remains available
for recovery. General campaign recovery is not implemented by this milestone.

## Next implementation gate

Select and validate an HTTP parser and concurrency/I/O model that work with the
same native/WASIp2 code. Resolve the proposed HTTP contract in D035 before building
the firewall. This probe is intentionally serial and processes a single exchange;
do not promote it into the final throughput/scalability design by default. Podman
and VM functional parity remain later work.

## Primary references

Consulted 2026-10-31: [Rust WASIp2 target](https://doc.rust-lang.org/rustc/platform-support/wasm32-wasip2.html),
[dated Rust distribution manifest](https://static.rust-lang.org/dist/2026-10-15/channel-rust-stable.toml),
[Wasmtime 50.0.1 release](https://github.com/bytecodealliance/wasmtime/releases/tag/v50.0.1),
and [Wasmtime CLI](https://docs.wasmtime.dev/cli-options.html). The pinned executable's
`run -S help` was checked before using its network flag. These sources describe tools;
the correctness observations above come from the retained DUT attempts.
The Clojure rewrite additionally pins the static executable from the official
[Babashka 1.12.218 release](https://github.com/babashka/babashka/releases/tag/v1.12.218),
using the SHA-256 digest from its release asset metadata.
