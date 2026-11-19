# Phase 2 network topology feasibility

Read-only inventory completed on 2026-11-19. Physical data links, end-to-end HTTP
traversal, and load headroom remain unverified. The topology below is proposed
under D051. See [DUT inventory](dut-inventory.md) for the live snapshot and
inspection provenance.

## Endpoint observations

- M1 Pro (`MacBookPro18,3`) reached the DUT's existing LAN SSH endpoint via
  `en0`. Its Dell Universal Dock D6000 Ethernet interface (`en8`, Apple
  CDC-NCM driver) was present with MTU 1500 and inactive media. The profiler's
  USB bus speed does not establish Ethernet negotiation.
- M4 Max was outside this inspection. Its Ethernet adapter, interface, media,
  negotiated rate and management reachability remain unknown.
- DUT `eno1` is the verified LAN SSH path. Both candidate DUT data ports have
  no carrier. The switch/peer and the physical mapping of the three jacks
  remain to be identified.

## Proposed physical and logical layout

```text
M4 Ethernet ── ingress segment ── enp3s0   DUT   enp4s0 ── egress segment ── M1 Ethernet
                                      └── L7 VNF ──┘
                              client TCP → VNF → backend TCP

M1 controller ── management LAN ── DUT eno1 / SSH
             └── management path to M4 / SSH (verify)
Planned Pi ───── management LAN ── DUT exporters (verify when available)
```

Start with two independent direct Ethernet links, or two isolated switch
segments if cabling requires a switch. Leave the active management link in
place. The experiment uses two distinct static, non-overlapping IPv4 subnets,
selected after checking both Macs' LAN, VPN, and overlay routes. Assign one
address per endpoint per segment with MTU 1500 as the starting configuration.
Data interfaces need only their connected routes and have no default gateway.
Use explicit numeric endpoint addresses and verify the actual source address
and interface for each socket. Verify that IPv6 and management interfaces provide
no alternate path to the test service.

| Segment | Endpoints | Traffic contract |
| --- | --- | --- |
| Ingress | M4 data address, VNF ingress address | M4 targets the VNF listener only |
| Egress | VNF egress address, M1 data address | VNF opens backend connections using the egress route. M1 binds the test backend to its egress address and restricts admission to the VNF egress source |
| Management | Existing DUT LAN, separate Mac management interfaces | M1 controls DUT and M4. Future monitoring scrapes the DUT here |

Keep IP forwarding disabled between data segments on DUT and guest. Keep the
two Ethernet segments separate at Layer 2. Restrict the backend service from
management and overlay routes, and check for host forwarding, proxies and
alternate routes on the Macs. The firewall must terminate inbound TCP and
open a separate outbound TCP connection after applying policy. A physical
path through the DUT alone cannot establish inspection.

For Wasmtime/WASIp2 and Podman/Quadlet host networking, the DUT host owns the
data addresses and the VNF binds to the ingress address. Connected routing
selects the egress source, which must be confirmed on the wire. For KVM, prefer
two independent Linux bridges (`br-ingress` and `br-egress`), each with one
physical port and one guest TAP/virtio interface. An approved setup can use the
same bridges for all variants, with host data addresses on bridges for host
variants and those same addresses on guest interfaces for VM runs. Remove the
host data addresses before assigning them to the guest, verify neighbor
resolution after each handoff, and keep the guest off the management LAN.
Guest supervision can use the libvirt console via DUT management SSH.
Verify bridge/TAP privileges and firewalld/SELinux behavior first. D040's
loopback SLIRP path supplies functional evidence only. See
[libvirt domain interfaces](https://libvirt.org/formatdomain.html#network-interfaces)
and [existing bridge networks](https://www.libvirt.org/formatnetwork.html).

## Arrangement comparison

| Arrangement | Benefit | Constraint |
| --- | --- | --- |
| Two independent wired data segments and existing management | Clear link attribution and simple isolation. Reuses both spare DUT NICs | One wired data interface per Mac plus independent management. Verify physical placement, cable reach, adapters and carrier on both segments |
| One data NIC with two isolated VLANs and separate management | Preserves logical separation with one DUT data cable | Requires compatible managed switching and verified VLAN membership. Both segments share a trunk and its directional capacity. Record tag handling and trunk counters |
| Shared existing switched LAN | Potential low-load functional fallback | Both legs and management compete on the DUT link. Current 100 Mb/s link can dominate results. Same-LAN traffic can bypass the DUT unless backend admission and direct-negative checks establish isolation |
| Separate data segments with routed guest TAPs | Alternative if L2 guest attachment is infeasible | Needs explicit host and endpoint routes and tightly scoped forwarding to/from TAPs. Deny physical ingress-to-egress forwarding, verify return paths and account for the extra host routing work. Requires its own approval |

Bridged guest attachment keeps the external segments and endpoint addresses
common while adding guest TCP/IP, virtio, TAP, and host bridge processing.
Podman host mode uses the host network namespace, and Wasmtime adds the WASI
socket/runtime path. Record the selected paths, queues, offloads, MTU, driver
versions, NIC error/drop counters and guest/host CPU costs. Keep cabling,
switch ports, negotiated rates, application payloads, policy, connection
behavior and backend identical across variants. Include DUT kernel and
virtualization costs in environment accounting. Keep captures outside timed
runs unless capture overhead is an explicit controlled variable. Link rate
is a ceiling. Later capacity pilots must establish generator, backend and
network headroom before attributing saturation to a VNF.

## Minimal validation after approval

1. On both Macs record adapter model and Ethernet rating, port and driver,
   `networksetup -listallhardwareports`, `system_profiler SPEthernetDataType`,
   and `ifconfig IFACE` media/status/MTU. Map the spare DUT NICs to physical
   jacks with one cable at a time and confirm matching negotiated rate and
   duplex at both ends. Record the cable, any switch port and error/drop
   counter deltas. Recheck management SSH and future monitoring independently.
2. Record addresses, route lookups and counters. Use `route -n get -inet DEST`
   on macOS and `ip route get DEST from SOURCE` on Linux including the guest.
   Check M4-to-ingress, VNF-to-M1 and both return legs. Once an approved fixture
   is available, check neighbor resolution and MTU on each segment. Verify
   the M1 backend address has no intended route from the M4 data interface
   and that any management or overlay path cannot reach the listener.
3. With one VNF variant active, send one allowed request to the numeric
   ingress address with a unique request identifier. Confirm expected
   response bytes and exactly one matching backend receipt from the
   prescribed egress source. Correlate DUT ingress/egress observations
   and guest TAPs where applicable using scoped counters or captures.
4. Send a marked `GET /blocked` with the D038 deny policy. Expect HTTP 403
   and zero matching backend receipts during a bounded observation window.
   Attempt direct backend access from M4 using applicable data and management
   paths and verify failure with zero backend receipts. Repeat the allowed
   request with the VNF stopped and expect endpoint failure and zero receipts.
   Repeat these controls for each variant. Save timestamps, source revision,
   routes, resolved settings and observations with private endpoint details
   in local evidence.

## Open requirements

- Identify the `eno1` link peer and its switch port capability/configuration.
  Check patch leads, intermediate wall jacks and negotiated rates. Schedule
  any test involving the active SSH link with local console access and an
  approved maintenance window. Use the spare DUT ports to test known-good
  cables and peers first.
- Confirm M4 adapter presence/speed and M1 dock Ethernet negotiation with a
  known-good peer. Confirm independent management connectivity on both Macs
  and whether direct cable lengths are practical. Prefer verified 2.5 Gb/s
  on both data segments where available. Set a minimum acceptable rate from
  planned workload and later capacity evidence.
- Confirm non-overlapping private address allocations, interface and bridge
  ownership, endpoint access policy, KVM privileges, and network rollback
  before applying configuration. The existing `mt` commands stage and test
  loopback fixtures. External topology validation has no runner yet.
- Confirm monitoring hardware and management reachability when the Pi is
  available. Phase 2 acceptance still requires approved NIC roles and
  topology, verified link capacity, and positive and negative path evidence.
