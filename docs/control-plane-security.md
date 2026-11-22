# Control plane security

Provision a private CA offline using a documented local operator procedure.
Issue a controller certificate with the management DNS name or IP in its SAN and
server authentication usage, and separate client-auth certificates with unique
assigned agent identities in SANs. The agent trusts the private CA and checks
server identity and validity. The controller verifies mandatory client certificates
and maps the verified SAN and enrolled certificate fingerprint to a registry entry.
Never accept caller text as identity or bypass TLS verification. Keep the CA
signing key offline. Do not build a PKI service.

Check enrollment and revocation/deactivation on every operation, including long-poll
dispatch and upload commit. Reported capabilities are advisory. Intersect them with
the operator's capability grant and allowed revisions; authorize ownership, job
type, and action. Deactivation blocks claims and renewal, closes outstanding
requests where possible and moves affected runs to recovery. Before deactivation
or certificate rollover, drain and reconcile when possible. Replacement enrollment
uses a new key and certificate, retires the old fingerprint and fences old sessions.
If deactivation prevents evidence delivery, hold unresolved reservations for verified
operator recovery. Reconnect must never restore a revoked grant.

Bind the Management HTTP listener explicitly to loopback and authenticate with a
locally provisioned high-entropy bearer credential from a private file. Keep the
Agent HTTPS listener on explicitly configured management addresses; never expose
Management routes there. Run under dedicated least-privilege identities. Restrict
key, token, local configuration and spool permissions to their owners (e.g. 0600
files and 0700 directories or equivalent ACLs), and validate permissions at startup.
Keep keys, secrets, credentials and private endpoint details outside Git and
sanitize logs and public provenance exports.

Use locally configured target allowlists on controller and agent. Resolve typed
target IDs to configured schemes, addresses, ports and permitted paths; check
resolved addresses and prevent redirects or alternate resolution from escaping
the allowlist. Execute only a locally configured, verified k6 binary with trusted,
hash-identified local workloads and typed bounded parameters. Use direct argv,
controlled environment and a private working directory. Exclude generic remote
shell, arbitrary executable dispatch and controller-supplied arbitrary scripts.

Bound request headers/body, response sizes, long polls, per-file and aggregate
artifact bytes, spool usage, streaming duration and simultaneous uploads.
Apply quotas before and during streaming. Artifact names are single ASCII safe
segments with a bounded length, reject traversal/encoded separators and symlinks,
write via trusted directory handles or equivalent no-follow confinement, and use
internal IDs to resolve paths. Keep content opaque and never unpack uploads.
Numeric budgets, concrete certificate lifetime/rotation tooling, listener addresses
and deployment configuration require verification during implementation.
