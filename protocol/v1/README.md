# Agent API contract v1

[OpenAPI](openapi.json) specifies HTTP and mTLS. [JSON Schema](schemas.json)
uses Draft 2020-12 and provides named request and response definitions. Resolve
external references from the OpenAPI document's directory. [Protocol semantics](../../docs/agent-protocol.md)
govern cross-message checks such as identity, authority, ordering, deadline and
artifact closure. The schema bounds are wire ceilings. Deployment limits can
be stricter and values in the contract do not select benchmark settings.
Renew directives require a grant; cancel and reconcile cannot grant authority.
An artifact inventory is unique by name and must match stored length and digest.
Incomplete evidence lists missing names separately. A process state reported by
an agent is evidence to reconcile alongside controller history.

The registration fixture contains a valid shape and an invalid identity override.
Use a matching test clock for its fixed UUIDv7 timestamp in endpoint tests.
Before implementing each endpoint or protocol behavior in Phases 4B–4D, add
shared positive and negative fixtures. Both Clojure and Rust tests validate them against
the named schemas and compare strict decoding and serialization.
Extend fixtures for every endpoint, boundary, unknown field and protocol version.
Loopback integration tests should also exchange actual requests under mTLS and
replay duplicate requests, stale sessions, reordered grants, cancellation and
artifact acknowledgement exchanges. The test-only bounded
executor uses a test configuration and does not extend the production v1 capabilities.
