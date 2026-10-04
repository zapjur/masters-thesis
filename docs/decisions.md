# Research and Engineering Decision Log

This file records significant decisions that affect:

- thesis scope,
- experimental methodology,
- architecture,
- implementation,
- interpretation of results.

Use sequential identifiers:

`D001`, `D002`, `D003`, ...

Possible statuses:

- proposed,
- accepted,
- rejected,
- superseded.

---

## D001 — Thesis topic

**Status:** accepted

### Decision

The thesis topic is:

**Analiza porównawcza wydajności technologii WebAssembly, konteneryzacji i wirtualizacji w realizacji funkcji sieciowych**

English:

**Comparative analysis of the performance of WebAssembly, containerization, and virtualization technologies in implementing network functions**

### Rationale

The topic provides a clear experimental comparison between modern lightweight execution
technology and established virtualization approaches.

---

## D002 — Compared execution technologies

**Status:** accepted

### Decision

The primary comparison will cover:

1. WebAssembly,
2. containers,
3. virtual machines.

### Rationale

These technologies represent three different approaches to workload isolation and execution
with different expected resource and startup overhead.

---

## D003 — Initial network functions

**Status:** accepted

### Decision

The primary network functions will be:

- firewall,
- load balancer.

### Rationale

They provide two useful and understandable network-processing workloads while remaining
small enough to implement experimentally.

The exact behavior of each function is not yet defined.

---

## D004 — Primary performance dimensions

**Status:** accepted

### Decision

The initial evaluation will include:

- latency,
- throughput,
- CPU utilization,
- memory utilization,
- startup time,
- scalability.

### Rationale

Together these metrics cover both network performance and execution-environment overhead.

---

## D005 — WebAssembly source-language comparison

**Status:** proposed

### Decision

A secondary experiment may compare WebAssembly modules compiled from different source
languages.

Possible candidates:

- Rust,
- C/C++,
- Go or TinyGo.

### Rationale

The source-language toolchain may affect generated WebAssembly code and therefore influence
runtime performance.

### Constraint

This comparison is optional and must not delay the primary comparison between WebAssembly,
containers, and virtual machines.

---

## Open decisions

The next decisions should include:

- D006 — benchmark host architecture,
- D007 — target CPU architecture,
- D008 — operating system,
- D009 — WebAssembly runtime,
- D010 — container runtime,
- D011 — virtualization technology,
- D012 — reference implementation language,
- D013 — network-function processing model,
- D014 — traffic-generation tooling,
- D015 — measurement tooling,
- D016 — benchmark repetition strategy.

Do not mark any of these as accepted until they have been explicitly evaluated.
