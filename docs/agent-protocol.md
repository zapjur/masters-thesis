# Agent HTTP protocol v1

[OpenAPI and JSON Schema](../protocol/v1/README.md) define the wire surface.
Use an explicitly configured HTTPS Agent listener with mandatory mTLS, separate
from the loopback authenticated Management listener. The agent initiates all
requests. The path selects the major version. Registration negotiates an
overlapping v1 minor and typed capability revisions; v1 initially supports 1.0.
Reject unsupported versions or capabilities before dispatch. New incompatible
wire shapes require negotiation or a new major version.

| Request | Committed outcome |
| --- | --- |
| Register | Resolve certificate identity, intersect reported/authorized capabilities, establish a session and return active reconciliation coordinates. Fresh sessions fence old sessions without authorizing a second launch. |
| Heartbeat | Report current owned attempt and process evidence; receive lease authority and cancellation directive independently of claims and uploads. |
| Claim | Wait for a typed job up to the poll limit; return an existing assignment on replay or 204 when idle. Permit one outstanding claim per session. |
| `POST /api/v1/jobs/{id}/authorize-start` | Commit start authorization before the agent spawns the process. |
| Complete / fail | Record an immutable terminal report, process termination evidence and artifact inventory. Complete requires its artifacts already acknowledged. Fail can declare gaps. |
| Artifact PUT | Stream one immutable file; return the stored length/hash only after durable commit. |
| Reconcile | For an owned historical attempt, confirm stop/no-start/unknown and transfer partial evidence without renewal or launch authority. |

Proposed development defaults are 5-second heartbeats, 30-second renewable
leases, 20-second maximum long polls and one concurrent measurement job per agent.
Validate timeouts, storage and capacity under interruption and upload load.
Use separate heartbeat/control tasks and connection capacity throughout transfers.

## Identity, start and lease rules

Every job mutation carries session, attempt and lease IDs; bind them and the path
job ID to the certificate identity. The controller writes assignment/lease state
before replying. The agent journals the assignment and a stable start request ID
before requesting authorization. Start authority is absent until this request
commits. Assignment expiry has its own bounded controller deadline. The agent
spawns only after receiving matching live authority and recording durable launch
intent. A duplicate authorization never
causes a second spawn. Startup with a journaled launch intent checks process
ownership and reconciles instead of spawning. Heartbeats report observed process
state; a cancellation directive latches locally for that attempt, disables
renewal and requests process-group stop. Later or reordered grants cannot clear
that latch. The controller persists cancellation before delivery.

Use unique heartbeat requests for renewals. The controller computes expiry from
its grant time, bounded by job deadline. The agent derives a conservative
monotonic deadline from the request's first send time plus the granted validity
interval and clamps it to its own job limit. A replay retains the original first-send
time and grant. Subtract a safety margin for clock drift and scheduling pauses,
and verify clocks and suspension handling in Phase 4C. Ignore out-of-order grants using the persisted grant sequence.
A stale or already expired response confers no authority. If that bound expires
or the controller rejects the current authority, stop and verify owned processes,
preserve outputs and reconcile. Controller restart fences outstanding execution
authority and requires re-registration and stop reconciliation. Agent restart starts
in reconciliation mode; a replacement session does not adopt a live measurement.
A partition stops renewal. Expiry alone never frees reserved resources.

## k6 task limits

`max_vus` is an optional integer ceiling for the arrival-rate executor.
The controller validates resolved tasks before dispatch and the agent validates
them before authorizing start. When supplied, `max_vus` must be greater than
or equal to `preallocated_vus`. When omitted, use `preallocated_vus` as the
effective maximum and pass that explicit value to k6, so the VU pool cannot
grow beyond preallocation. Record the effective value in run provenance.
Null is invalid. The portable JSON Schema checks each field independently;
the controller and agent enforce the relationship and configured capacity limits.

## Requests, results and reconciliation

Use a fresh client-generated UUIDv7 for each new JSON POST. Its embedded
creation time defines the retry window and is fixed across retries. After
authentication, verify request age and current session when applicable before
replay or mutation. For a current session within that window, an exact retry
returns the original committed response. Reusing the key for a different
operation, path or payload returns 409. Registration has no incoming session.
Its exact retry can return the original registration only while that returned
session remains current. A fenced session or an expired request ID returns
409 and cannot recreate a session, extend a lease or rerun an effect.
A replayed claim still requires live start authorization. Retry an idle poll
with a fresh key. Serialize outcome processing for each attempt. See
[receipt retention](control-plane-persistence.md) for admission and pruning rules.
Errors use a structured code, safe message and retryable flag. Validate bodies,
headers and version; distinguish invalid input (400), unauthorized capability (403),
unknown owned object (404), conflict/stale (409), oversized (413), wrong media
type (415), unsupported version (422), and retryable overload (429/503).
TLS rejection can occur before an HTTP response.

Execution and evidence have separate admission windows. When an authorized
agent reports verified exit while authority is live, stop renewals and record a
bounded collection window in which it can upload and submit its terminal report.
Collection confers no launch authority. Fresh operations against attempts stale
for their requested phase are rejected. Reconciliation authenticates the owner on its new session and checks
the original job, attempt and lease against durable state. A reconcile report can
resolve termination uncertainty and attach incomplete evidence without reversing
a terminal disposition or resuming execution. Artifact PUT accepts historical
ownership only while recovery explicitly permits evidence collection; exact
durable receipt replay remains possible after collection closes. Use bounded whole
file retries after interrupted uploads. A late conflicting report is retained as an
event when relevant and cannot replace a committed result. No exactly-once
execution guarantee. See [recovery](control-plane-persistence.md).
