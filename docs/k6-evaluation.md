# k6 traffic-generator proposal

Status: proposed (D041). No k6 installation, load pilot, or performance result is
introduced by this document. TRex remains a historical proposal, not an accepted
requirement. k6 would supply local HTTP traffic from the M4 Max; Grafana Cloud is
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
