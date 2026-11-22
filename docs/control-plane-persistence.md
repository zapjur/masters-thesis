# Operational persistence and research evidence

SQLite WAL holds mutable operational state on the controller's local disk.
The controller service account owns the database, migration metadata and central
artifact root. Run one controller per store; use a local exclusivity guard.
Agents own separate bounded local journals/spools. Keep live SQLite off network
filesystems. Back up operational state and referenced evidence under a documented
consistent procedure. Configure durable commit and verify platform guarantees.

## Conceptual schema and commit boundaries

| Records | Relationship and constraints |
| --- | --- |
| campaigns, plan entries | Resolved scenario, source revision, seed, ordered index |
| runs | Unique immutable ID and plan entry FK, outcome/phase, prior run link, provenance |
| jobs, attempts | Run/job FKs, adapter and capability, assigned identity, intent and outcome |
| agents, capability grants, sessions | Certificate identity and enrollment, claimed/granted revisions, session fencing and availability |
| leases | FK to attempt and owner, authority state, sequence, expiry and hard deadline |
| reservations | Unique active resource key, FK to run; durable quarantine while cleanup uncertain |
| execution events, effect intents | Correlation IDs, order, versions, replay policy and effect outcomes |
| request receipts | Operation scope, key, fingerprint and committed response |
| artifact metadata | Unique immutable attempt/name and run path, SHA-256, length, staged/finalized state and acknowledgements |

Enable foreign keys and enforce unique ownership and outcome constraints in the
database as well as the domain. Apply ordered migrations before accepting work,
using transactions where supported, pre-migration backups and forward compatibility
checks; fail startup on mismatched/newer schema. Persist each state update, events,
new/revised intents and receipts in one coordinator transaction. Claim effect
execution with durable intent state, then perform SSH/process/filesystem work outside
SQL. Commit effect outcomes through the same coordinator. Pending safe idempotent
effects can be redispatched. In-flight side effects are uncertain across crashes
until checked against run-owned evidence. Never blindly replay process starts.

## Filesystem evidence

Use unique run directories under a configurable central root. Include resolved EDN
scenarios, run manifest, revision and dirty-tree status, tool/compiler and binary
identities, workload checksums, traffic/ordering/timing/resource metadata, k6
outputs, monitoring exports, environment snapshots, logs, validation and cleanup
outcomes. Include failed/incomplete runs and indicate missing evidence. Processed
data and figures live separately.

Reserve a bounded staging slot via the coordinator; stream bytes to a unique private
temp file on the same filesystem, checking length and SHA-256. Flush file contents,
publish with atomic no-replace semantics and flush the parent directory using the
verified host durability mechanism. Commit artifact metadata and receipt after
publication. Acknowledge only after both are durable. Serialize concurrent
publication to the same name; identical content reuses the receipt, mismatches
conflict. A crash between publication and SQL commit is reconciled against the
durable intent and verified bytes before acknowledgment. Orphan/inconsistent files
are quarantined for inspection; interrupted temp files are unacknowledged and
retryable. Finalize a manifest with completeness/gaps and verified cleanup; once
published it is immutable. Late evidence after manifest closure requires a linked
append-only recovery record via an operator-controlled path. Never overwrite bytes
or reuse a run directory.

## Restart and failure recovery

After controller restart, disable dispatch until migrations, authority fencing
and targeted reconciliation of unresolved intents/reservations complete or
quarantine affected resources. Fence agent sessions; compare durable assignment,
agent journal, uploaded receipts and process evidence. An agent returning after
restart or partition stops any provably owned surviving process (validate identity
beyond PID), reports verified no-start or exit, or reports uncertainty. If safe
ownership or termination cannot be proven automatically, retain reservations until
the operator verifies scoped termination and records evidence. Apply equivalent
checks to SSH-managed and local resources. Keep immutable partial files and spool
unacknowledged outputs; capacity exhaustion stops admitting new jobs and records
explicit gaps. Lost acknowledgements trigger exact receipt replay, never reexecution.
An operator may schedule a fresh linked run after recovery.
