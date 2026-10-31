# DUT Inventory and Verification

## Evidence status

This inventory distinguishes user reports from remotely verified facts. Private
addresses, host aliases, MAC addresses, disk identifiers, and inventory files must
remain outside committed documents.

| Item | Current evidence |
| --- | --- |
| DUT CPU | Verified Intel Core i5-12400, x86_64, 6 cores/12 logical CPUs; user accepted as DUT (D034) |
| RAM | Verified 15415 MiB OS-visible; nominal 16 GB baseline |
| Storage | Observed 931.5 GiB NVMe device and 465.8 GiB SATA device; NVMe PCI model WD Black SN850X |
| OS | Verified AlmaLinux 10.2 |
| Physical NIC count | Three PCI Ethernet controllers verified |
| NIC models/drivers | All three Realtek RTL8125 2.5GbE, driver r8169 |
| NIC links | eno1: carrier, reported 100 Mb/s; enp3s0/enp4s0: no carrier, speed unknown |
| NIC roles/cabling/routes | Management/ingress/egress proposed; unverified |
| Kernel and cgroup mode | Running 6.12.0-211.68.1.el10_2.x86_64; cgroup2 mounted at /sys/fs/cgroup |
| systemd | RPM systemd-257-23.el10_2.2.alma.1.x86_64 |
| Security services | SELinux enforcing/targeted; firewalld running/enabled |
| Podman/Quadlet/OCI runtime | Accepted stack (D033); queried Podman/conmon/crun/runc RPMs absent |
| Wasmtime/Rust toolchain | Initial RPM query absent; subsequently prepared isolated Rust 1.99.1 and Wasmtime 50.0.1 under /tmp/mt-tcp-feasibility/tools (D036); Cargo not installed |
| KVM/libvirt/QEMU and guest | CPU reports VT-x; qemu-kvm/libvirt RPMs absent; /dev/kvm access and guest unverified |

Thesis source commit at inspection: `0a0d29844de5cf95056af9bbc98019cedf408138`.
The working tree includes this documentation update; this is development inspection
provenance, not a clean final measurement campaign.

## Existing inspection workflow

Use the sibling homelab repository, inspected at commit
`3eb45c46615485b39d6bb0cad2749b8fd0309b0d`. Its read-only playbook collects
system facts, services, interfaces, routes, storage, and active NetworkManager
connections. It does not install dependencies or configure networking.

From `../homelab/infra/ansible`:

```sh
ansible-playbook playbooks/inspect.yml -e inspect_lvm=false
```

The configured private inventory selects the host. Keep host key checks and SSH
pipelining enabled. LVM is omitted because it is unnecessary for this milestone
and would request sudo. Diagnostic output is private inventory evidence, not benchmark
data; retain any saved output outside Git with timestamp and source commit.

The initial attempt on 2026-10-31 failed before collecting facts because the configured
Tailscale destination did not resolve. The user enabled Tailscale and authorized a retry.
The retry completed with `ok=6 changed=0 unreachable=0 failed=0`; verified values
above came from that attempt and follow-up read-only queries on the same date.

Follow-up queries used `lscpu`, `lspci -nn`, Ansible network facts, and
`rpm -q podman containers-common conmon crun runc passt netavark aardvark-dns
wasmtime rust cargo qemu-kvm libvirt systemd kernel-core`. All queried runtime/network
helper packages were absent; RPM returned nonzero because of those missing names.
This does not rule out binaries installed outside RPM. Ad-hoc command-module output
labels read-only commands as CHANGED by default; that label is not evidence of mutation.

One `nmcli` query failed because this version does not expose WIRED-PROPERTIES.SPEED.
Network facts and PCI inspection provided the NIC evidence instead. No network settings
were changed. No raw inspection output or private inventory is committed.

## Interpretation and remaining network work

The prior i7-6700K inventory was incorrect for this host; the user explicitly confirmed
the inspected i5-12400 as the DUT. Preserve the old decision as history (D006), not as
a competing current platform.

The RTL8125 model is a controller capability, not an observed 2.5 Gb/s connection.
The 100 Mb/s active link may be adequate for management but has not been accepted for
measurement traffic. Check cabling, switch/adapter capacity, negotiation, and both Macs'
links before selecting data paths. Do not infer the cause of the current speed or
change link negotiation remotely during inspection. Proposed roles are eno1 for
management and enp3s0/enp4s0 for data; physical port mapping remains unverified.

## Follow-up checks

After basic inspection, use read-only queries for missing evidence: NIC bus/model,
driver and negotiated speed, CPU topology and virtualization availability, cgroup
mode, installed package versions, SELinux/firewalld state, and available disk space.
Do not install missing diagnostic tools or alter links/services to collect facts.
Distinguish absent packages from commands unavailable on PATH. `podman info` can
initialize container storage; prefer package metadata for the initial inventory.

The three-NIC role proposal requires physical cabling and both Macs' adapter/link
verification. Tailscale may carry management traffic; final experiment traffic must
use the explicitly documented data network rather than accidentally traversing it.

## Subsequent feasibility preparation

After the read-only inventory, the user authorized the TCP spike. The scoped Ansible
playbook prepared pinned portable Rust/Wasmtime tools in `/tmp/mt-tcp-feasibility`
without changing system packages. Both native and WASIp2 probes then passed functional
checks. See [tcp-feasibility.md](tcp-feasibility.md) for observed versions and limits.
The earlier RPM query remains an accurate record of its inspection time.

The subsequent Clojure rewrite (D037) additionally prepared static Babashka 1.12.218
in `/tmp/mt-tcp-feasibility-clojure/tools`. Its bootstrap and test fixtures require
no Python on the DUT; original temporary tools and historical attempts remain intact.
