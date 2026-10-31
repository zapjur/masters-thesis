# Infrastructure

## Accepted plan; not implemented

The common bare-metal DUT is the i5-12400 x86 desktop (16 GB RAM, SSD), running Linux
with verified AlmaLinux 10.2. It is intended to host Wasmtime, Podman with Quadlet,
and QEMU/KVM/libvirt Linux guests using virtio networking. All three technologies are
measured on this DUT. M4 Max and M1 Pro remain generator/orchestrator and backend roles.

## Responsibility boundaries

Ansible provisions/configures machine and OS prerequisites: packages, users, SSH, runtime
installation, directories, networking, system tuning, and services. `mt deploy` manages
application/artifact builds and deployment; `mt run` controls benchmark lifecycles.
Ansible is not the primary per-benchmark orchestration engine.

Future `infra/ansible/` inventory, roles (`common`, `generator`, `dut`, `backend`), and
`site.yml` are documented in [experimental-framework.md](../docs/experimental-framework.md).
No roles, inventory, dependency installation, host configuration, images, or VMs are
created during this inventory/specification milestone. Read-only homelab inspection
has completed; see [dut-inventory.md](../docs/dut-inventory.md).

## Open configuration and hardware work

Three RTL8125 NICs are verified. One link reported 100 Mb/s and two had no carrier. Verify
links, routing, NIC/traffic-generator compatibility, management access, and host headroom.
Exact versions, Linux guest, CPU affinity/vCPU pinning, memory allocation, power controls,
and network paths remain unresolved. See [architecture.md](../docs/architecture.md).

AWS/EC2 may support development, CI, functional/reproducibility tests, or auxiliary studies;
final primary measurements use the bare-metal DUT. Kubernetes, Terraform, service mesh,
and additional cloud infrastructure are not introduced by these decisions.
