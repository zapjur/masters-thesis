# KVM firewall correctness

Implemented and verified (D040). Functional checks only; no performance claims.

`mt firewall check --with-vm` adds a transient libvirt `qemu:///session` KVM guest
to the native/WASIp2 checks. Combine it with `--with-podman` for all four controls:

```sh
mt firewall check --ssh-host homelab \
  --remote-root /tmp/mt-tcp-feasibility-clojure --with-podman --with-vm
```

Prepare the temporary Rust/Wasmtime/Babashka tools using the existing bootstrap
instructions in [mt/README.md](../mt/README.md). The optional infrastructure
[playbook](../infra/ansible/playbooks/prepare-vm.yml) installs the pinned distro
packages. It does not create a bridge, enable system services, or configure NICs.
The DUT user must have access to /dev/kvm and unprivileged session libvirt.

## Functional controls

The guest image, SHA-256, package versions, machine type, and functional limits are
pinned in [guest.lock.json](../infra/vm/guest.lock.json). The image checksum was
retrieved from AlmaLinux's HTTPS CHECKSUM listing, not inferred from its filename.
The base image is checksum verified before each use and remains unchanged. Each
attempt creates a separate qcow2 overlay, NVRAM, and transient domain. There is no
TCG fallback: the runner checks QMP query-kvm enabled=true.

Use the exact musl native executable built for the host control. Copy it into the
guest and verify SHA-256 before starting the cases. No guest compiler, custom
Python tooling, or VNF source variant is introduced. Cloud-init is the distribution
image's provisioning component. One vCPU and 1024 MiB are development guards, not
accepted benchmark allocations or equivalent limits across environments.

All 47 cases use the same Clojure client/backend fixtures on the DUT, unchanged
request bytes, policy, response assertions, and timeout limits. Each guest VNF
process gets two active readiness requests (allowed and denied) before its case.
The VNF listens on guest 0.0.0.0:8080. A unique host loopback port forwards to it
through QEMU SLIRP and virtio-net-pci. The guest reaches the host loopback backend
at 10.0.2.2 and its ephemeral port. The rewritten upstream Host header therefore
contains this guest-facing address; its expected value is verified explicitly.
Client response bytes must match every selected variant exactly.

This exercises a virtual network data path, unlike the Podman host-network control.
SLIRP and loopback port forwarding are functional adapters, not the final topology.
They add processing and change connection behavior; do not attribute their overhead
solely to virtualization. No host bridge, routing change, physical NIC assignment,
or externally exposed forwarded listener is added. Guest outbound networking is
available through SLIRP to reach the host backend; this is not a network isolation
experiment. Final measurements need a separately accepted topology.

## Lifecycle and retained evidence

The run records package versions, libvirt capabilities/version, requested/live XML,
KVM state, guest OS/kernel, serial boot logs, executable hash, per-case logs and
responses, parity summary, and cleanup verification under experiments/local/<run>.
Failed attempts remain intact. Disk overlays are retained as debugging artifacts;
these can contain guest session credentials and must be treated as private.
Ephemeral key files and the cloud-init seed live outside collected results in a
0700 directory, removed after domain destruction. SSH host keys are pinned before
boot through cloud-init; StrictHostKeyChecking remains enabled.

Only the exact UUID-derived run domain is destroyed. Domains are transient and
never autostart. Cleanup verifies that this domain is absent; unrelated domains
and host resources are untouched. A shutdown hook covers ordinary process signals;
SIGKILL, power loss, or a lost controller cannot be assumed to run hooks. Inspect
`virsh -c qemu:///session list --all` after such an event and destroy only the exact
`mt-http-<run-id>` recorded in that attempt's domain.xml. Never use global VM cleanup.
The recovery check terminates an SSH supervisor and verifies explicit owned-domain
cleanup; it does not claim host-power-loss recovery.

## Results

Verified 2026-11-11 in attempt
`http-1794414586938-67e32c08-5b5f-4ce6-b12d-7a2e5209fd05`:

- 47 cases each for native, WASIp2, Podman, and VM: **188/188 passed**,
  including active readiness and exact client-response parity.
- Five Rust unit tests passed; controller regression: 19 tests, 64 assertions,
  zero failures/errors.
- QMP reports KVM present and enabled. The guest reports AlmaLinux 10.2,
  kernel `6.12.0-211.64.1.el10_2.x86_64`, and the `virtio_net` driver. The guest
  kernel differs from the DUT's `6.12.0-211.68.1.el10_2.x86_64`; this is recorded,
  not assumed equivalent for performance.
- The native, container, and guest executable SHA-256 is
  `0f01ca5e2fa28a695c004b52346660061d3e5f562b75c5627bd25d2e76d601f2`.
- SSH-supervisor interruption recovery and domain cleanup passed. Follow-up
  inspection found no session domains or containers, a running user-systemd
  manager, and no remaining run secret directory.

The run records base commit `cbe440328a7b0f296b8f3cc9dda304061abdbfeb` with
`git_dirty=true` and retained source snapshots/hashes. Raw output is under
`experiments/local/<attempt>/results`, ignored by Git; it includes private guest
disk evidence and should not be published wholesale. Build/provisioning and suite
wall times are not startup/latency benchmark measurements.

Two earlier attempts remain unchanged: `http-1794402861291-c53a7c0c-1d53-4704-a2b3-c73e3d4d508e`
stopped before cases because the temporary toolchain was missing;
`http-1794414321861-a3794da2-0686-4dc6-8d8f-ad577c9b955e` passed the 141
native/WASIp2/Podman cases but VM creation failed on a PCI address collision.
The final adapter assigns the raw virtio NIC slot 0x10 explicitly. Neither failure
is hidden or promoted to a successful VM result.

Primary references consulted 2026-11-11:
[AlmaLinux image checksums](https://repo.almalinux.org/almalinux/10.2/cloud/x86_64/images/CHECKSUM),
[libvirt domain XML](https://www.libvirt.org/formatdomain.html),
[cloud-init SSH module](https://docs.cloud-init.io/en/latest/reference/modules.html#ssh),
and [QEMU user networking](https://www.qemu.org/docs/master/system/invocation.html).
