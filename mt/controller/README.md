# Phase 4A controller

The controller is a foreground Clojure/JVM service for the M1 Pro. The existing
Babashka launcher invokes the JVM for startup and calls its loopback API for
status. The JVM classpath is defined in [deps.edn](../deps.edn). It includes
`controller/src`, `controller/resources`, and, with the test alias,
`controller/test`. Babashka continues to use `mt/src` and the existing fixtures.

## Toolchain

Use a Java 25 LTS JDK and Clojure CLI. The local validation toolchain used Azul
Zulu 25.0.2+10-LTS, Clojure CLI 1.12.5.1654, and Babashka 1.12.218 on Apple
M1 Pro. Set `JAVA_HOME` to the local Java 25 JDK. The test and controller aliases
enable JNI access for the SQLite JDBC driver on the JVM classpath.

| Dependency | Pin | Use |
| --- | --- | --- |
| [Clojure](https://clojure.org/releases/downloads) | 1.12.5 | JVM language and `clojure.test` |
| [Integrant](https://clojars.org/integrant/integrant/versions/1.0.1) | 1.0.1 | Component graph and lifecycle |
| [Malli](https://clojars.org/metosin/malli/versions/0.20.3) | 0.20.3 | Config, domain, and Management response validation |
| [Ring](https://clojars.org/ring/ring-jetty-adapter/versions/1.15.6) | 1.15.6 | HTTP adapter with Jetty 12.1.14 transitively |
| [Reitit Ring](https://clojars.org/metosin/reitit-ring/versions/0.11.0) | 0.11.0 | Routing |
| [next.jdbc](https://clojars.org/com.github.seancorfield/next.jdbc/versions/1.4.72) | 1.4.72 | SQL and transaction boundaries |
| [SQLite JDBC](https://central.sonatype.com/artifact/org.xerial/sqlite-jdbc/3.53.1.0) | 3.53.1.0 | Embedded local SQLite |
| [data.json](https://central.sonatype.com/artifact/org.clojure/data.json/2.5.2) | 2.5.2 | Small Management response codec |

## Provision and run

Use a private directory outside the checkout. Generate a random bearer token as
64 lowercase hex digits and give the token file mode `0600`. Each config uses an
absolute data directory and token path. The database name is a single filename
under the data directory on a local filesystem. The controller creates the data
directory with mode `0700` when absent and checks its owner and permissions.
Run the following from the repository root.

```sh
umask 077
controller_dir="$(mktemp -d)"
openssl rand -hex 32 > "$controller_dir/token"
cp mt/controller/config.example.edn "$controller_dir/controller.edn"
chmod 600 "$controller_dir/controller.edn"
```

Edit the copy and set `:data-dir` to the absolute path `$controller_dir/data`
and `:credential-file` to the absolute path `$controller_dir/token`, substituting
the actual directory printed by `printf '%s\n' "$controller_dir"`. Set
`JAVA_HOME` to your Java 25 installation and start the service.

```sh
mt/bin/mt controller start --config "$controller_dir/controller.edn"
```

From a second terminal, use the same absolute config path.

```sh
mt/bin/mt controller status --config /absolute/path/to/controller.edn
```

Stop the foreground controller with Ctrl-C or send SIGTERM to the foreground
process you launched. Admission closes and outstanding work drains up to the
configured budget. Startup with the same configuration reopens and recovers the
store. Rotate the token by provisioning a replacement file and restarting.

## Management API

The listener binds to IPv4 loopback `127.0.0.1`. Both endpoints require an
`Authorization: Bearer <token>` header. Responses use JSON and disable caching.

| Method and path | Body |
| --- | --- |
| `GET /api/v1/health` | `ready`, `version`, `lifecycle`, `storage_ready` |
| `GET /api/v1/status` | Health fields, `queue` (capacity, occupancy, reserved), `effect_capacity`, `effect_slots_used`, and durable `effects` counts by status |

Ready reads return 200. Unavailable health/status reads return 503 with
readiness fields. Application errors use `{"error":{"code":"..."}}`, including
401 for invalid or absent credentials, 404 for an unknown route, 405 for an
unsupported method, 413 for a request body, and 503 for handler saturation.
Jetty may reject malformed framing or oversized headers before Ring.
Management serves fixed-size response shapes, uses 4 KiB request and response
header limits, a two-second socket idle timeout, a 32-connection limit, a
16-thread Jetty maximum, a 64-entry Jetty task queue, and eight application
handler permits. The handlers perform no database or process I/O.
The API is intended for a trusted local host. Plain loopback HTTP does not
authenticate the server to the CLI. Keep the configured port and credential
under operator control.

An unauthenticated check requires no secret on the command line.

```sh
curl -i http://127.0.0.1:8765/api/v1/health
```

## Lifecycle and work

- `mt.controller.system` wires Integrant configuration, credential loading,
  storage, coordinator, effects, and management. Migrations and stale-active
  recovery finish before dispatch. Startup rolls back initialized components
  when a later component fails.
- `mt.controller.domain` implements a pure fixture transition with explicit
  event IDs and times. One accepted command creates one stable effect ID derived
  from its command ID. The permitted paths are pending to active, then active to
  completed, failed, or uncertain. Tests submit the two fixed fixture operations
  directly to the coordinator.
- `mt.controller.store` holds the database and local ownership lock. Each
  transition commits the state, event, and intent or outcome in one transaction.
  `mt.controller.coordinator` runs the single writer and publishes a read snapshot
  after the transaction returns.
- The event queue has `queue-capacity - reserved-capacity` external permits,
  including any command being processed. Each running effect has one reserved
  outcome slot until its outcome is committed and both virtual threads exit.
  The configured reserved capacity must exceed the effect concurrency limit,
  leaving space for the shutdown signal. Offers are nonblocking. Persisted
  pending effects are bounded by `queue-capacity` and stay in SQLite until a slot
  opens. Terminal history accumulates in SQLite.
- `mt.controller.effects` runs a worker and a timeout supervisor on virtual
  threads per slot. The writer commits active state before launch. On timeout or
  shutdown the supervisor requests interruption and reports uncertainty. A
  timed-out worker can keep its slot while it is still running. On restart,
  active intents become uncertain before work dispatches. Pending fixture intents
  are safe to run and uncertain intents remain durable for later reconciliation.
- Admission replies distinguish accepted, rejected, committed, and uncertain.
  An expired queued command is rejected. A caller whose wait expires before a
  definitive reply receives uncertainty, including when SQLite has begun its
  bounded transaction. The result can still commit. Shutdown stops new admissions
  and dispatch, cancels running fixtures, and drains queued commands and outcomes
  within the configured coordinator budget. SQLite stays owned until any write
  already in progress exits.
- Structured EDN diagnostics go to stderr and correlate event, command and effect
  IDs. They are operational logs.

The fixture store has no historical schema older than migration 001. The
migration checksum and version are verified on reopening. Power-loss behavior,
backup/restore procedures, terminal history retention and operator reconciliation
remain open. Additional execution adapters require verified cancellation and
replay semantics before they can use this controller.

## Verification

Run from `mt/` with Java 25 selected.

```sh
clojure -Srepro -Stree
clojure -Srepro -M:test
bb test
```

Tests create disposable local databases and loopback listeners. They exercise
rollback, durable outcomes, recovery, saturation, deadlines, cancellation,
private authentication, and listener shutdown. They do not require the DUT.
See the [persistence design](../../docs/control-plane-persistence.md) and
[implementation roadmap](../../docs/implementation-roadmap.md).
