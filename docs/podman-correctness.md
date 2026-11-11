# Podman / Quadlet Firewall Correctness

## Implementation and scope

D039 adds an opt-in rootless Podman variant to the shared HTTP firewall suite:

```sh
mt firewall check --with-podman --ssh-host DUT_ALIAS \
  --remote-root /tmp/mt-tcp-feasibility-clojure
```

The same 47 wire cases, Clojure client/backend, method/path policy, deadlines, and
allowed/denied readiness probes run against native Rust, WASIp2/Wasmtime, and the
container. All three must produce identical client response bytes. These remain
sequential functional checks on DUT loopback, not performance measurements or the
final three-machine data path. VM integration is still future work.

## Installed environment and chosen controls

The user installed the selected AlmaLinux package after inspection established that
sudo required an interactive password. Verified runtime inventory:

| Component | Observed version/control |
| --- | --- |
| Podman | 5.8.2, podman-5.8.2-11.el10_2.alma.1.x86_64 |
| OCI runtime | crun 1.30.1, crun-1.30.1-2.el10_2.x86_64 |
| Monitor | conmon 2.2.1, conmon-2.2.1-2.el10.x86_64 |
| Cgroups | v2, systemd manager, CPU/memory/PID controllers available |
| Execution | Rootless Podman and user-systemd units; subordinate IDs verified |
| Networking | Host network namespace, no port publishing, bridge, or pasta datapath |
| Security | SELinux enabled, container user 65532:65532, read-only rootfs, capabilities dropped, no-new-privileges |
| Functional resource guards | 256 MiB memory, 64 PIDs; no CPU quota/affinity selected |

The exact Podman package and controls live in
[podman.lock.json](../infra/containers/podman.lock.json). Other installed runtime
packages are recorded per run. These are not fully frozen final campaign package
versions. Memory/PID guards are functional limits, not accepted equal-resource
benchmark allocations. No performance/resource-utilization claim follows from them.

Host networking lets the unchanged loopback fixture reach the firewall and lets
the container reach the same host backend. It shares the host network namespace
and reduces network isolation. Rootless Podman's installed default network helper
is not evidence that it participates in this chosen path. Final external traffic
and VM networking must be defined separately and their differences documented.

## Image and binary identity

The [Containerfile](../infra/containers/firewall/Containerfile) uses `FROM scratch`
and copies the exact native executable built earlier in the same attempt. There
is no registry base image, runtime package installation, or compilation inside the
container. The executable remains the same Linux/musl artifact as the native case.

Preparation uses network-disabled, pull-disabled Podman builds with a fixed image
creation timestamp. `mt` creates a non-running inspection container, extracts
`/firewall`, and requires its SHA-256 to equal the native binary's hash. It records
the content-addressed image configuration ID, manifest digest when available, tag,
and exported OCI archive SHA-256. Definitions refer to the exact image ID with
`Pull=never`. The OCI archive remains in the collected evidence after the run tag
is removed; it can be restored with `podman load` for artifact reproduction.

## Lifecycle and cleanup

For each case, `mt` renders a unique user Quadlet from
[firewall.container.in](../infra/quadlet/firewall.container.in) into
`$XDG_RUNTIME_DIR/containers/systemd/`. It generates the service, starts it, and
inspects the live container before releasing the client fixture. This inspection
barrier is separate from VNF readiness, which still requires correct allowed and
denied HTTP traffic and backend observations.

The definition has `Restart=no`, no `[Install]` section, no auto-update, and bounded
start/stop timeouts. It sets systemd accounting and separate actual container
memory/PID limits. Inspect verifies the image, user, host networking, resource guards,
read-only rootfs, non-privileged execution, and no-new-privileges. Unit/container
cgroup paths, generated service, inspect data, journal, and lifecycle actions are
retained. Unit accounting is not assumed to equal the container payload's cost.

The child controller waits for normal service completion and cleans up its owned
container/definition. The parent repeats recovery cleanup when the child finishes
or fails. Cleanup stops/removes only the exact run-owned names, captures logs, clears
that unit's failed state, removes its definition, reloads user-systemd, and verifies
that neither an active/failed unit nor a container remains. It never performs global
container pruning or a global `reset-failed`.

An additional fault check deliberately terminates the child while its service is
active. Parent recovery must remove the owned resources and restore the initially
running user-systemd manager. This is a lifecycle test, separate from the 47 HTTP cases.

No lingering or boot autostart is enabled. The active orchestration SSH session
provides the existing user-systemd session. System package installation is outside
case execution; no firewalld, SELinux, NIC, or VM configuration is changed by the suite.

## Reproduction and prerequisites

Prepare the pinned Rust/Wasmtime/Babashka directory using
[the existing instructions](../mt/README.md). Install the container package once.
The optional scoped Ansible playbook reuses the private homelab inventory:

```sh
# From ../homelab/infra/ansible, substitute the checkout's absolute path:
ansible-playbook /absolute/path/to/masters-thesis/infra/ansible/playbooks/prepare-podman.yml \
  --ask-become-pass
```

This is normal host preparation, not a per-case engine. It installs the selected
package and distribution dependencies and checks rootless Podman/user-systemd.
It does not configure passwordless sudo or enable lingering. No custom Python is
added; application orchestration and fixtures remain Clojure/Babashka.

Then run the opt-in command above. Without `--with-podman`, the original two-variant
suite remains available and does not require Podman. New attempts get unique IDs;
raw evidence remains under ignored `experiments/local/http-.../`, including failures.
Container execution fails fast after a failed case and retains the expected count,
completed cases, and error so an incomplete run cannot be mistaken for a passing suite.

## Evidence and remaining work

The DUT completed three recorded container attempts on 2026-10-31:

| Attempt | Observed outcome |
| --- | --- |
| http-1793482900696-55f39cdd-1b98-4cf4-be03-5f1e9f34cf70 | 141/141 HTTP cases passed with response parity and binary identity; no forced-interruption check yet |
| http-1793483205662-8b3ac61f-5e54-4349-92fc-f7e8f9da2d69 | 141/141 passed; interruption removed the container/definition, but follow-up audit found a cached failed-unit entry |
| http-1793483559550-d944999b-1220-4cbc-8b13-f526427092b2 | 141/141 passed with response parity, verified runtime guards, interruption recovery, and user-systemd restored to running |

The second attempt's original passing summary remains unchanged. It checked active
service/container removal but did not reject a leftover failed-unit entry. The audit
exposed that gap; cleanup now resets only its owned unit and explicitly rejects a
failed state. The earlier owned entry was cleared, and the final attempt verified
the stronger condition. No unsuccessful evidence was removed or retroactively edited.

The final command ran from 21:52:43 to 21:53:24 UTC with source commit
`823870399dc777066291f79f3c62992d0933d49f` and a dirty tree. Per-file source hashes
and snapshots identify the actual implementation. Its summary SHA-256 is
`7ef1b37524cd012fd986d4028da963c0f4a2d23f63857ed9926313e65c2209c6`.
Five native Rust unit tests passed during the run. The local Clojure regression suite
passed 16 tests with 55 assertions. The Podman Ansible playbook passed an offline
syntax check; the initial package installation was performed by the user, not claimed
as an executed privileged Ansible run.

Final artifact identities:

| Artifact | SHA-256 |
| --- | --- |
| Native executable and executable extracted from image | 0e37998ac70d9d58f4de63e2713fc249792f5027311381f6e4a7f378140366dd |
| Image configuration ID | f99466d6d02e751998fea002264f7b1dc8c6f6607a4f38374b75e4fe6e92af5f |
| Image manifest | 4d92cfe2a4b85aad3c8904bb4407cd6f6c0d8dfe44099a2171ddd51fb6a8e688 |
| Exported OCI archive | b9c7018a237805f81c8eec86f15816f69631d6a4ab8c1effb8f4d8c2dfadb66a |

The post-run audit reported user-systemd `running`, no loaded `mt-fw-*` units, no
matching test containers, and no `localhost/mt-firewall` image tags. OCI archives,
source snapshots, successful/failed lifecycle records, and logs remain retained.
Timestamps and runtime accounting configuration are provenance, not measured startup,
CPU, or memory results.

Final concurrency, equal resources, external NIC/backend traffic, VM parity, and
benchmark campaigns remain open. Scratch/musl and host-network choices are not
silently promoted into a complete final methodology.

Primary sources consulted 2026-10-31:
[Quadlet documentation](https://docs.podman.io/en/stable/markdown/podman-systemd.unit.5.html),
[Podman build](https://docs.podman.io/en/stable/markdown/podman-build.1.html).
The installed generator and actual DUT runs verify version-specific behavior.
