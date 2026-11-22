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
In Phase 4 both Clojure and Rust tests should load the same fixtures, validate them
against their named schema definitions, and compare strict decoding and serialization.
Extend fixtures for every endpoint, boundary, unknown field and protocol version.
Loopback integration tests should also exchange actual requests under mTLS and
replay stale, duplicate, interrupted and reordered messages. The test-only bounded
executor uses a test configuration and does not extend the production v1 capabilities.
