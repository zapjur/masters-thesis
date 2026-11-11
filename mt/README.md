# mt: Initial Feasibility Command

The Clojure experiment controller currently implements `feasibility tcp` and `firewall check`,
using the existing controller Babashka (tested with 1.12.218). General `deploy`,
`run`, metric collection, and analysis are not implemented yet. The HTTP correctness
command optionally manages rootless Podman/Quadlet and transient KVM VM variants.
See [VM correctness](../docs/vm-correctness.md) for package/image pins, networking,
private artifact handling, and reproduction with `--with-vm`.
No additional Clojure dependencies or JVM installation are required. The DUT uses
checksum-pinned Babashka 1.12.218 for the Clojure fixtures; no Python is required there.

## Install the local command

With Babashka (`bb`) on PATH, link the launcher into a directory on your PATH:

```sh
mkdir -p ~/.local/bin
ln -s /absolute/path/to/masters-thesis/mt/bin/mt ~/.local/bin/mt
```

If `~/.local/bin` is not already on PATH, add `export PATH="$HOME/.local/bin:$PATH"`
to your shell configuration and open a new terminal. The launcher resolves its own
symlink to locate the checkout; it works from any directory and uses the repository's
sources/configuration. Keep the checkout at that location or update the symlink after
moving it. No code is copied and no additional dependencies are installed.

```sh
mt --help
mt firewall check --ssh-host DUT_ALIAS --remote-root /tmp/mt-tcp-feasibility-clojure
mt feasibility tcp --ssh-host DUT_ALIAS --remote-root /tmp/mt-tcp-feasibility-clojure
```

The `bb mt ...` tasks from this directory continue to work too. Help does not contact
the DUT. CLI failures return a nonzero exit code.

## Prepare the DUT

Use the existing homelab Ansible configuration and private inventory. From
`../homelab/infra/ansible`, select this repository's playbook by its absolute path:

```sh
ansible-playbook /absolute/path/to/masters-thesis/infra/ansible/playbooks/prepare-tcp-feasibility.yml
```

The playbook delegates preparation to the controller, where Clojure uses curl, tar,
and OpenSSH. Ansible itself uses Python on the controller; no Ansible Python modules
or custom Python scripts execute on the DUT. To avoid invoking Ansible altogether,
run the same preparation directly from the repository root:

```sh
bb infra/feasibility/bootstrap.clj \
  --ssh-host DUT_ALIAS \
  --root /tmp/mt-tcp-feasibility \
  --lock infra/feasibility/toolchains.lock.json \
  --cache .cache/feasibility
```

The default remote root is `/tmp/mt-tcp-feasibility`. Override it with
`-e mt_probe_root=/tmp/mt-tcp-feasibility-other` if needed. The playbook downloads
checksum-pinned tools into that root with no sudo or system package changes.
It can be rerun; installed component markers are checked against the lock. The
temporary directory may be removed by OS cleanup; rerun preparation if necessary.
Use the same SSH destination for preparation and execution.

## Run and inspect

From this directory, substitute an existing OpenSSH alias:

```sh
bb mt feasibility tcp --ssh-host DUT_ALIAS
```

Use `--remote-root /tmp/mt-tcp-feasibility-other` when preparation used another root.
The command stages source, verifies deployed hashes, compiles both targets, runs
the EDN-defined functional checks, and collects partial/successful evidence under
`experiments/local/<unique-id>/`. SSH host-key checks remain enabled. Private host
configuration stays outside Git. Neither runtime remains running after the tests.

The runner preserves earlier attempts and refuses to reuse a remote results directory.
Do not edit results; correct code/configuration and run again with a new ID.
Retain ignored local artifacts outside the checkout before deleting the repository
or allowing temporary DUT storage to expire. These are development checks, not
final thesis measurement campaigns.

## HTTP firewall correctness

After preparing the same tool directory, run from `mt/`:

```sh
bb mt firewall check --ssh-host DUT_ALIAS --remote-root /tmp/mt-tcp-feasibility-clojure
```

This command checksum-verifies the pinned httparse archive, stages the shared Rust
firewall and Clojure fixtures, builds native/WASIp2 variants, and runs 47 cases per
variant with active readiness and explicit response parity. It retains the dependency
archive and all source/build/test evidence under a new `experiments/local/http-.../`
directory. It requires no extra DUT package or network/service configuration.
See [the report](../docs/firewall-correctness.md) for the contract and limitations.

To include the installed Podman/Quadlet environment and compare all three variants:

```sh
mt firewall check --with-podman --ssh-host DUT_ALIAS \
  --remote-root /tmp/mt-tcp-feasibility-clojure
```

Prepare Podman once using the scoped playbook documented in
[the container report](../docs/podman-correctness.md). The suite builds a scratch
image from the same native executable, manages temporary user Quadlets, runs the
same 47 cases per variant, checks response parity, and verifies interruption cleanup.
It exports the OCI image and retains unit/container metadata and logs. No sudo is
used during cases; no boot autostart, lingering, or network/firewall change is added.

## Controller checks

```sh
bb test
```

The tests cover captured/inherited output, failure propagation, remote argument
validation, binary Clojure fixtures, framing limits, and preservation of earlier
results. Socket tests need local loopback access; they do not contact the DUT. See
[the feasibility report](../docs/tcp-feasibility.md) for actual DUT evidence and limits.

## Editor analysis

Repository `.lsp/config.edn` defines the active Clojure source roots. The standalone
bootstrap uses namespace `bootstrap`, matching `infra/feasibility/bootstrap.clj`;
the tests require it through the declared Babashka classpath.

Clojure LSP and clj-kondo exclude `experiments/local/` and downloaded `.cache/`
artifacts from analysis. Saved run sources may retain historical namespace layouts
and must not be edited to resolve editor diagnostics. Restart the language server
after changing editor configuration to clear findings from previously analyzed files.

## Planned campaign host roles

The M1 Pro is the primary mt controller, local backend host and central immutable artifact
store (D042–D044). Future mt run campaigns will remotely supervise k6 on M4 Max, selected
VNFs on the DUT and retrieve monitoring from the planned Raspberry Pi (model unspecified).
These campaign interfaces are not implemented by the existing correctness commands.
See [framework lifecycle](../docs/experimental-framework.md),
[monitoring plan](../docs/monitoring.md) and [roadmap](../docs/implementation-roadmap.md).
