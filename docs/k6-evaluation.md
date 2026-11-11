# k6 traffic-generator proposal

Status: proposed (D041). No k6 installation, load pilot, or performance result is
introduced by this document. TRex remains a historical proposal, not an accepted
requirement. k6 is the proposed primary HTTP generator (D049), executing remotely on the M4 Max under M1 Pro control; Grafana Cloud is
not required. JavaScript traffic definitions would not add a VNF language variant.

k6 is a suitable candidate for the HTTP/1.1 firewall and later load balancer: it
provides request-level metrics, response checks, scenario scheduling, and machine
readable outputs. Keep the Clojure raw-wire correctness fixtures: an HTTP client
API is not a substitute for constructing malformed requests and ambiguous framing.

Before acceptance, automate a pilot through mt and record:

- The pinned k6 binary/version/hash and traffic script/configuration hashes.
- One HTTP request per iteration, explicit connection reuse, redirects, timeout,
  payload sizes, allow/deny mix, and expected response checks. The current firewall
  closes every connection; explicitly disable connection reuse for that subset.
  Check allowed requests for 200 and denied requests for 403 independently, rather
  than treating every 403 as success. Verify the backend body for forwarded traffic.
- An arrival-rate scenario with adequate preallocated virtual users, recording
  configured arrival rate, completed requests, errors, and dropped iterations.
  A closed virtual-user loop alone lets offered load fall as response times rise.
- M4 generator and M1 backend headroom, including a direct-backend control and
  output/collector overhead. Do not infer generator capacity from specifications.
- Defined latency boundaries. k6 http_req_duration covers sending, waiting, and
  receiving; connection establishment is separate. This is client-observed HTTP
  time, not isolated VNF processing time. Report connection metrics separately.
- Identical traffic across technologies, fixed run controls/order, warm-up,
  cooldown, repetition, resource allocation, and immutable raw outputs through mt.
  DUT/guest CPU and total environment memory need separate collectors.

The pilot must precede final load-level selection. The sequential functional VNF
is not yet an accepted concurrency model for the benchmark. Physical links and
backend capacity must also be resolved before interpreting saturation results.

Primary sources consulted 2026-11-11:
[k6 options](https://grafana.com/docs/k6/latest/using-k6/k6-options/reference/),
[open and closed models](https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/open-vs-closed/),
[dropped iterations](https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/dropped-iterations/),
[metrics](https://grafana.com/docs/k6/latest/using-k6/metrics/reference/), and
[expected response callbacks](https://grafana.com/docs/k6/latest/javascript-api/k6-http/set-response-callback/).

## Planned profile matrix and metrics

Version scripts for constant arrival rate, increasing rate and bursts, with synthetic
small/larger responses, Social Feed read-heavy and mixed read/write schedules, and
firewall allow/deny mixes. Fix request selection seeds, methods, sizes and response
checks; do not combine all dimensions before capacity pilots. Open arrival-rate models
are the default for controlled offered load. A closed VU scenario is optional only for
a question about fixed clients, with its feedback on offered load stated explicitly.

Record offered schedule, actual starts/completions, achieved HTTP RPS, http_req_duration
distribution and p50/p95/p99, request failures, expected/unexpected statuses, dropped
iterations, bytes sent/received and connection timings. Iterations/s equal requests/s
only for a verified one-request iteration without redirects or hidden extra calls.
Keep successful business requests, intentional policy denials and overload failures
separate. Pin expected statuses per request class; correctness checks are independent
of performance acceptance thresholds, and failed checks must affect run validity.

Pin binary version/hash, scripts and resolved configuration hashes, HTTP/1.1 behavior,
reuse on both sides, request timeout, redirects disabled or explicitly counted,
response-body checks and output mode. Verify the actual negotiated protocol; no silent
HTTP/2 change. Budget preallocated VUs and monitor M4 CPU/memory/network headroom.
Account for response-body validation and raw-output overhead when sizing the generator.
Current single-request/close firewall pilots must disable reuse explicitly and report
new-connection cost; they do not model a persistent-connection proxy.

## Remote execution and artifacts

The [framework](experimental-framework.md) owns the planned SSH/supervisor contract.
M1 initiates and supervises M4 execution and retrieves exact commands/parameters, process
exit status, stdout/stderr, k6 summary, appropriate raw outputs, start/end times and
scenario versions. Preserve all under run_id on M1, including failed or interrupted runs.
Run correctness fixtures separately: k6 does not replace Clojure malformed-wire tests.
Final adoption still depends on direct-backend and through-VNF pilots, physical network
validation, achieved-load fidelity and generator/backend headroom. No k6 pilot has been
performed by this documentation task.
