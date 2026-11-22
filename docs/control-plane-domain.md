# Experiment domain and execution lifecycle

| Entity | Responsibility and relationship |
| --- | --- |
| Scenario | Versioned declarative EDN specification of workload, references, environments, resources and validity requirements |
| Campaign | Immutable resolved plan including ordered entries, seed and planner version; execution status derives from its runs |
| Run | One execution of a plan entry with a unique immutable ID; records provenance, phase, outcome and completeness |
| Job | One typed action within a run, such as a warm-up or measurement generator invocation; identifies adapter and required capability |
| Attempt | One actual dispatch of a job; holds executor identity, state and outcome, with a lease for agent work |
| Agent | Certificate-bound executor registration; persistent enrollment and transient session/health |
| Capability | Versioned typed task contract; dispatch matches reported support and controller authorization |
| Resource reservation | Exclusive claim keyed by resource and owned by run; survives coordinator restart |
| Lease | Attempt-scoped, renewable time-limited process authority; expiry never proves termination |
| Artifact | Immutable named bytes with length, hash and provenance tied to run and producer/attempt |
| Execution event | Typed observation or command with identity, provenance and occurrence/receipt time |
| Effect intent | Durable instruction describing external work; has identity, preconditions, policy and outcome |

Planning does not contact executors. Canonically expand validated scenarios, apply
seeded block randomization with a recorded algorithm/version, resolve ordering and
freeze the plan before execution. Given the same inputs and seed, plan entries and
order must agree. Allocate run IDs when materializing executions. Record the selected
technology, units, workload hash, resources, schedule and repetition. Illustrative
rates, payload sizes and durations remain development parameters.

## State transitions

| Entity | Normal transitions | Exceptional transitions |
| --- | --- | --- |
| Run | queued → preparing → deploying → warming-up → running → collecting → cleaning-up → completed. Explicit scenario kinds may skip phases (e.g. no DUT deployment for direct controls). | queued → cancelled before acquisition. Active phases stop further execution and enter collecting/cleaning-up as applicable, then failed or cancelled. Uncertain effects or cleanup enter recovery-required, retaining reservations. Recovery resumes collection or cleanup, or resolves to a terminal state supported by evidence. |
| Job | queued → dispatched → running → collecting → completed | queued → cancelled; dispatched/running/collecting → cancelling → cancelled once stopped, or → failed with known outcome; uncertainty enters recovery-required. Verified recovery can continue collection or resolve failed/cancelled. A proven no-start eligible action may return to queued for a new attempt. |
| Attempt | assigned → start-authorized → running → exited → reported | Assigned may be closed as unstarted only with proof (e.g. no start authorization committed). Cancellation after authorization → stopping → exited; ambiguous launch/exit → recovery-required → exited or verified no-start. Reported is terminal and records success, failure or cancellation. A pre-start rejection can be reported without an exit. |
| Agent | enrolled/offline → online on authenticated registration/reconciliation; online → suspect on missed heartbeats → offline on timeout | Suspect/offline → online through authenticated contact and reconciliation. Any availability state → deactivated by operator; re-enable only by explicit enrollment action. |

`started` grants start authority before spawn; running means evidence of process
launch or execution, not simply authority. Terminal process outcome and research
validity are separate. Completion requires verified stop/cleanup and required
durable artifacts; failures preserve completeness gaps. Recovery cannot convert an
ambiguous run into a valid measurement on the basis of a later retry.

## Scheduling and invariants

Use a queue ordered by the frozen campaign plan. A blocked entry waits. Independent
work may use disjoint resources when it preserves campaign order and declared
contention constraints. Requirements resolve to concrete exclusive keys in EDN
(e.g. DUT, generator, backend host, shared link or experimental fixture). Reserve
all keys in one transaction before any preparation or dispatch. A DUT key spans
all measured variants, including scaling configurations with multiple instances
inside one run. Hold reservations during collection, confirmed cleanup and configured
cooldown/reset; release atomically with the final disposition. Claims also honor
agent availability, capability and the per-agent concurrency limit.

1. Check prerequisites and stage pinned resources. Start/reset local backends;
   prepare the DUT through existing SSH/runtime adapters; verify collector health
   where required and actively probe backend/VNF behavior with bounded deadlines.
2. Run warm-up and measured traffic at their defined boundaries using typed agent
   jobs. Arm direct startup timing before its start action in startup scenarios.
   Record controller events and agent-local monotonic durations; correlate clocks
   without assuming simultaneous timestamps.
3. Stop traffic, collect results and monitoring exports, verify run-owned backend,
   services and VM cleanup, finalize evidence and apply cooldown. Cleanup only
   touches resources identified as owned by the run.
4. On cancellation or operational failure, stop admitting actions for the run,
   request scoped termination, collect partial outputs and record validity and
   cleanup. If termination is uncertain, block conflicting allocations and reconcile.

Measurement is never automatically rerun after uncertain start or outcome. A new
measurement attempt always gets a fresh run ID linked to the original plan entry
and preceding execution. Preserve failed, cancelled and interrupted runs. Harmless
retryable control effects may replay only with stable identity and proven
idempotence; a job retry requires a new attempt ID and proof that repetition is safe.
