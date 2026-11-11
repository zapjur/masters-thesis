# Literature Notes

## Purpose

This file is a working index of literature relevant to the thesis.

It is not the final bibliography.

Only verified publications should eventually be included in the thesis bibliography.

Never invent:

- titles,
- authors,
- DOIs,
- publication venues,
- publication years,
- citation counts.

If bibliographic information has not been verified, use:

`TODO: verify source`

## Literature areas

Research should cover at least the following areas.

### WebAssembly fundamentals

Topics:

- WebAssembly specification,
- execution model,
- sandboxing,
- compilation,
- WASI,
- server-side WebAssembly.

### WebAssembly performance

Topics:

- native vs WebAssembly performance,
- startup performance,
- memory overhead,
- runtime differences,
- AOT vs JIT compilation.

### Containers

Topics:

- Linux containers,
- namespaces,
- cgroups,
- container isolation,
- container startup overhead,
- container networking.

### Virtual machines

Topics:

- hardware virtualization,
- hypervisors,
- VM overhead,
- VM networking,
- VM startup time.

### Network Function Virtualization

Topics:

- NFV,
- virtualized network functions,
- software middle boxes,
- performance requirements,
- network-function deployment models.

### WebAssembly for networking

Topics:

- WebAssembly-based network functions,
- programmable proxies,
- WebAssembly in edge networking,
- Wasm-based middleboxes,
- lightweight NFV.

### Benchmark methodology

Topics:

- network benchmarking methodology,
- latency measurement,
- throughput measurement,
- statistical performance evaluation,
- reproducible systems benchmarking.

## Source-quality preference

Prefer:

1. peer-reviewed papers,
2. standards,
3. official specifications,
4. respected conference proceedings,
5. authoritative technical documentation.

Potential publication sources include:

- IEEE Xplore,
- ACM Digital Library,
- USENIX,
- arXiv for recent work when peer-reviewed alternatives are unavailable,
- official WebAssembly / W3C specifications.

## Paper-note template

When adding a paper, use:

```md
### Title

**Authors:**  
**Year:**  
**Venue:**  
**DOI / URL:**  
**Status:** verified / TODO: verify source

#### Relevance

Why the source matters for this thesis.

#### Key findings

- finding
- finding

#### Methodology

Short description of the experimental method.

#### Useful metrics

Metrics or benchmark ideas relevant to our work.

#### Limitations

Limitations relevant when citing or comparing the paper.

#### Thesis usage

Where the source may be used:

- introduction,
- related work,
- methodology,
- discussion,
- other.
```

## Found manually
- WebAssembly at edge: https://ieeexplore-1ieee-1org-1000047ln00b6.wbg2.bg.agh.edu.pl/document/9049531
- WebAssembly vs Docker at the edge: https://ieeexplore-1ieee-1org-1000047ln00b6.wbg2.bg.agh.edu.pl/document/9826054
- WebAssembly in non-Web envs: https://ieeexplore-1ieee-1org-1000047ln00b6.wbg2.bg.agh.edu.pl/document/9640153
- WebAssembly runtimes: https://ieeexplore-1ieee-1org-1000047ln00b6.wbg2.bg.agh.edu.pl/document/9975423
- WebAssembly - serverless at the edge: https://ieeexplore-1ieee-1org-1000047ln00b6.wbg2.bg.agh.edu.pl/document/9283720
- WebAssembly at the edge: https://ieeexplore-1ieee-1org-1000047ln00b6.wbg2.bg.agh.edu.pl/document/10034550

## Research pass — 2026-10-31

This is a focused initial review, not a systematic literature review or final bibliography.
The six manually supplied links above are preserved verbatim. Search used their IEEE
document identifiers and title/topic queries for standalone WebAssembly, containers vs
KVM, NFV, networking interfaces, and benchmarking. Publisher/conference pages, author
copies, standards, and official project documentation support the notes below. Search
snippets and third-party bibliographies were discovery aids, not evidence for findings.

**Verification convention:** `verified` means the linked primary source supports the
recorded identity and notes. The access description distinguishes full text, abstract,
slides, and technical documentation; it does not imply a complete critical reading.
Unresolved records retain `TODO: verify source`. No citation counts are recorded.
All findings are from literature, not measurements from this repository. Suggested
metrics and thesis implications are interpretations, not accepted experimental decisions.
Living documentation must be pinned to the selected software release before implementation.

### Resolution of manually supplied links

| IEEE ID | Resolution and access | Follow-up |
| --- | --- | --- |
| 9049531 | *Challenges and Opportunities for Efficient Serverless Computing at the Edge*; author PDF and SRDS conference programme verified; L03 below. | Extract exact startup boundary and platform configuration before quoting numbers. |
| 9826054 | *Pushing Serverless to the Edge with WebAssembly Runtimes*; conference paper copy inspected; L04. The original label “WebAssembly vs Docker” is informal. | Read evaluation tables before transferring any comparison. |
| 9640153 | Candidate title: *An Evaluation of WebAssembly in Non-Web Environments*. IEEE access blocked; primary full text not obtained. | `TODO: verify source`: authors, year, venue, DOI, methods, and findings against publisher export or paper. No performance claim adopted. |
| 9975423 | *How Far We’ve Come – A Characterization Study of Standalone WebAssembly Runtimes*; official IISWC programme and author slides verified; L05. | Obtain proceedings paper for exact versions and statistical details. |
| 9283720 | IEEE page requires JavaScript/robot verification; identity not established from accessible primary material. | `TODO: verify source`: title, authors, year, venue, DOI, and content. Retain original lead; do not infer identity from its informal label. |
| 10034550 | Candidate title: *WebAssembly for Edge Computing: Potential and Challenges*. IEEE Communications Society [contents digest](https://www.comsoc.org/system/files/2023-03/Publications_Contents_Digest_2023_Feb.pdf) confirms title, 2022, and pages 68–73, but the paper was inaccessible and author spelling requires checking. | `TODO: verify source`: complete metadata and paper content. No experimental finding adopted. |

## Annotated sources

### L01 — Bringing the Web up to Speed with WebAssembly

**Authors:** Andreas Haas, Andreas Rossberg, Derek L. Schuff, Ben L. Titzer, Michael Holman, Dan Gohman, Luke Wagner, Alon Zakai, JF Bastien

**Year:** 2017

**Venue:** PLDI 2017

**DOI / URL:** [10.1145/3062341.3062363](https://doi.org/10.1145/3062341.3062363); [paper copy](https://css.csail.mit.edu/6.5660/2024/readings/wasm.pdf)

**Status:** verified — paper text inspected, especially abstract and design motivation.

#### Relevance

Foundational explanation of WebAssembly's portable binary format and sandboxed execution.

#### Key findings

- Describes validation, compilation, and execution design aimed at safe, efficient code.
- Platform independence is a design objective; performance parity is not a universal guarantee.

#### Methodology

Language/system design with implementation and benchmark evaluation.

#### Useful metrics

Code size, compilation time, execution time relative to native.

#### Limitations

Early browser implementations and computational workloads do not establish present-day
Wasmtime socket performance. Distinguish this PLDI paper from the later CACM article.

#### Thesis usage

Introduction and WebAssembly fundamentals; context for RQ1–RQ4.

### L02 — WebAssembly Core Specification, version 1.0

**Authors:** W3C WebAssembly Working Group; editor Andreas Rossberg

**Year:** 2019

**Venue:** W3C Recommendation, 5 December 2019

**DOI / URL:** [dated specification](https://www.w3.org/TR/2019/REC-wasm-core-1-20191205/); [publication record](https://www.w3.org/standards/history/wasm-core-1/)

**Status:** verified — official specification and publication record.

#### Relevance

Normative foundation for modules, instructions, validation, and execution semantics.

#### Key findings

- Defines the core execution model; host facilities require interfaces beyond the core.
- The specification is a semantic reference, not evidence of runtime performance.

#### Methodology

Normative specification rather than an experiment.

#### Useful metrics

None directly; use to define the execution abstraction accurately.

#### Limitations

Historical version 1.0, not a claim about the latest version or selected runtime features.
It does not establish Rust/WASI socket compatibility.

#### Thesis usage

Technical background; distinguish WebAssembly technology from its runtime and WASI.

### L03 — Challenges and Opportunities for Efficient Serverless Computing at the Edge

**Authors:** Phani Kishore Gadepalli, Gregor Peach, Ludmila Cherkasova, Rob Aitken, Gabriel Parmer

**Year:** 2019

**Venue:** 38th Symposium on Reliable Distributed Systems (SRDS)

**DOI / URL:** [author paper](https://www2.seas.gwu.edu/~gparmer/publications/srds19awsm.pdf); [official accepted-paper record](https://srds2019.projet.liris.cnrs.fr/program/acceptedpapers/)

**Status:** verified — author PDF and conference record; abstract inspected.

#### Relevance

Resolves manual lead 9049531 and motivates resource/startup hypotheses at the edge.

#### Key findings

- Introduces aWsm and reports an initial startup and memory assessment using MiBench functions.
- Its reported benefits concern that framework and experiment, not all WebAssembly runtimes.

#### Methodology

Framework design and initial evaluation of a subset of MiBench microbenchmarks.

#### Useful metrics

Function startup time and memory footprint; identify what initialization work is timed.

#### Limitations

Specialized serverless design rather than Wasmtime L4 forwarding; no direct validation of H1–H3.

#### Thesis usage

Related work and motivation for RQ4–RQ5; avoid transplanting startup numbers.

### L04 — Pushing Serverless to the Edge with WebAssembly Runtimes

**Authors:** Philipp Gackstatter, Pantelis A. Frangoudis, Schahram Dustdar

**Year:** 2022

**Venue:** 22nd IEEE International Symposium on Cluster, Cloud and Internet Computing (CCGrid)

**DOI / URL:** [10.1109/CCGrid54584.2022.00023](https://doi.org/10.1109/CCGrid54584.2022.00023); [paper copy](https://faculty.washington.edu/wlloyd/courses/tcss562_f2023/papers/PushingServerlesstotheEdgewithWebAssemblyRuntimes.pdf)

**Status:** verified — paper copy, title page and abstract inspected.

#### Relevance

Resolves manual lead 9826054; directly relevant to server-side runtime and cold-start discussion.

#### Key findings

- Designs a WebAssembly execution environment and WOW integration with Apache OpenWhisk.
- Separates the runtime opportunity from its integration into a serverless framework.

#### Methodology

Prototype design and experimental evaluation of serverless edge execution.

#### Useful metrics

Cold-start and invocation latency; runtime versus framework initialization costs.

#### Limitations

OpenWhisk overhead and serverless invocation semantics differ from persistent vFW/vLB sockets.
No quantitative comparison is extracted in this pass.

#### Thesis usage

Related work and startup-boundary discussion, RQ5; OpenWhisk is not proposed for implementation.

### L05 — How Far We’ve Come – A Characterization Study of Standalone WebAssembly Runtimes

**Authors:** Wenwen Wang

**Year:** 2022

**Venue:** IEEE International Symposium on Workload Characterization (IISWC)

**DOI / URL:** [official programme](https://iiswc.org/iiswc2022/program2022.html); [author slides](https://iiswc.org/iiswc2022/IISWC2022_76.pdf)

**Status:** verified — identity and findings below verified from official conference slides; proceedings full text not reviewed.

#### Relevance

Resolves manual lead 9975423 and supplies evidence against assuming native parity.

#### Key findings

- Slides report execution and memory overhead relative to native across five standalone runtimes.
- Compiler choice, optimization level, and AOT effects depend on the tested runtime.

#### Methodology

WABench: 50 benchmarks, including existing suites and applications; native baseline,
maximum RSS, and hardware counters. See slides 7–17.

#### Useful metrics

Normalized execution time, maximum RSS, and configuration-dependent compilation effects.

#### Limitations

Slides omit details required to reproduce the study; results describe historical runtime
versions and primarily computational workloads. Multiple runtimes remain optional here.

#### Thesis usage

Related work, RQ3–RQ4 and RQ7; justification for recording compiler/runtime versions.

### L06 — Not So Fast: Analyzing the Performance of WebAssembly vs. Native Code

**Authors:** Abhinav Jangda, Bobby Powers, Emery D. Berger, Arjun Guha

**Year:** 2019

**Venue:** USENIX Annual Technical Conference (ATC 19)

**DOI / URL:** [publisher record and paper](https://www.usenix.org/conference/atc19/presentation/jangda)

**Status:** verified — publisher metadata and abstract inspected.

#### Relevance

Counterevidence to general claims that WebAssembly always runs at native speed.

#### Key findings

- Reports substantial execution overhead for SPEC workloads in the tested browser engines.
- Attributes gaps to code generation, optimizations, and platform constraints.

#### Methodology

Browsix-Wasm enables Unix applications in browsers; compares SPEC execution with native.

#### Useful metrics

Execution-time ratios and workload-specific variation.

#### Limitations

Browser-based historical evidence; cannot predict modern Wasmtime networking results.

#### Thesis usage

Related work and cautious hypothesis formulation, especially RQ7.

### L07 — An Updated Performance Comparison of Virtual Machines and Linux Containers

**Authors:** Wesley Felter, Alexandre Ferreira, Ram Rajamony, Juan Rubio

**Year:** 2015

**Venue:** IEEE International Symposium on Performance Analysis of Systems and Software (ISPASS)

**DOI / URL:** [IBM Research publication record](https://research.ibm.com/publications/an-updated-performance-comparison-of-virtual-machines-and-linux-containers)

**Status:** verified — institutional metadata and abstract inspected.

#### Relevance

Background for container/KVM comparison on a common Linux host. This source studies
Docker; the current thesis container baseline is Podman with Quadlet (D033), so do not
transfer Docker-specific behavior or results without checking applicability.

#### Key findings

- Reports containers performing at least as well as VMs in almost all tested cases.
- Explicitly notes that both environments need tuning for I/O-intensive applications.

#### Methodology

Comparative workload evaluation of Docker and KVM; details require full-paper extraction.

#### Useful metrics

Workload throughput and resource overhead, with networking configuration controlled.

#### Limitations

Historical stack, no WebAssembly arm, and no evidence about this thesis hardware/software.
Do not conflate the earlier technical-report date with this 2015 publication.

#### Thesis usage

Container/VM related work and fairness discussion, RQ1–RQ4.

### L08 — ClickOS and the Art of Network Function Virtualization

**Authors:** Joao Martins, Mohamed Ahmed, Costin Raiciu, Vladimir Olteanu, Michio Honda, Roberto Bifulco, Felipe Huici

**Year:** 2014

**Venue:** USENIX Symposium on Networked Systems Design and Implementation (NSDI 14)

**DOI / URL:** [publisher record and paper](https://www.usenix.org/conference/nsdi14/technical-sessions/presentation/martins)

**Status:** verified — publisher metadata and abstract inspected.

#### Relevance

Direct NFV precedent including firewall and load-balancer workloads.

#### Key findings

- Demonstrates a specialized virtualized middlebox platform with low startup/delay and high packet rates.
- Shows why “VM overhead” depends on the VM design and datapath.

#### Methodology

Evaluates small ClickOS instances, multiple middleboxes, and concurrent-instance packet processing.

#### Useful metrics

Boot time, forwarding delay, packet throughput, and instance density.

#### Limitations

Specialized platform differs from a general Linux guest under QEMU/KVM; its values are
not expected results or targets for this thesis. Does not justify adding a unikernel arm.

#### Thesis usage

NFV background and discussion of external validity, RQ1–RQ2 and RQ5–RQ6.

### L09 — Considerations for Benchmarking Virtual Network Functions and Their Infrastructure

**Authors:** Al Morton

**Year:** 2017

**Venue:** IETF RFC 8172, Informational

**DOI / URL:** [RFC 8172](https://www.rfc-editor.org/rfc/rfc8172.txt)

**Status:** verified — official RFC text inspected.

#### Relevance

The closest methodological source here for VNFs on general-purpose hardware.

#### Key findings

- Hardware, configuration, and shared resources complicate VNF comparisons (§3).
- Discusses black-box evaluation, additional metrics, and benchmark coverage (§4).

#### Methodology

Methodological guidance; not a measured technology ranking or Standards Track specification.

#### Useful metrics

Throughput, latency, resource-related and lifecycle measures with explicit infrastructure context.

#### Limitations

Does not prescribe a WebAssembly comparison, final repetition count, or statistical test.

#### Thesis usage

Methodology and threats to validity across RQ1–RQ6; read before finalizing measurement controls.

### L10 — Benchmarking Methodology for Network Interconnect Devices

**Authors:** Scott Bradner, Jim McQuaid

**Year:** 1999

**Venue:** IETF RFC 2544, Informational

**DOI / URL:** [RFC 2544](https://www.rfc-editor.org/rfc/rfc2544.txt)

**Status:** verified — official RFC text inspected.

#### Relevance

Established vocabulary and procedures for network-device performance testing.

#### Key findings

- Defines throughput, latency, frame-loss, and other test procedures and reporting conventions.
- Device, traffic, and test conditions determine which procedures apply.

#### Methodology

Controlled network-device benchmarking guidance, including throughput search and latency tests (§26).

#### Useful metrics

Loss-free forwarding rate, latency, and frame loss, with frame sizes and offered load stated.

#### Limitations

Ethernet frame sizes are not application payload sizes. Socket-proxy RTT is not automatically
RFC 2544 device latency. Document adaptations; do not claim compliance without all applicable procedures.

#### Thesis usage

Metric definitions for RQ1–RQ2; leaves current traffic sizes and saturation criteria proposed.

### L11 — Benchmarking Methodology for Network Security Device Performance

**Authors:** Balamuhunthan Balarajah, Carsten Rossenhoevel, Brian Monkman

**Year:** 2023

**Venue:** IETF RFC 9411, Informational

**DOI / URL:** [RFC 9411](https://www.rfc-editor.org/rfc/rfc9411.txt)

**Status:** verified — official RFC text inspected; supersedes RFC 3511.

#### Relevance

Firewall benchmarking context and transparent testbed/traffic reporting.

#### Key findings

- Specifies test setup, traffic profiles, reporting, and performance tests for NGFW/NGIPS.
- Makes security functionality and traffic semantics part of benchmark interpretation.

#### Methodology

Network-security-device test procedures, including application traffic and TCP/HTTP tests.

#### Useful metrics

Throughput, connection rate, concurrent connection capacity, transaction latency.

#### Limitations

Primarily richer L7 security workloads; the current L7 vFW direction (D031) still
requires a narrowly defined policy. HTTP is proposed on workload grounds, not to
satisfy this RFC. Do not add broad body inspection or TLS or claim full compliance.

#### Thesis usage

Methodology and scope limitations for firewall comparisons, RQ1–RQ2.

### L12 — Statistically Rigorous Java Performance Evaluation

**Authors:** Andy Georges, Dries Buytaert, Lieven Eeckhout

**Year:** 2007

**Venue:** OOPSLA 2007

**DOI / URL:** [author-hosted paper](https://dri.es/files/oopsla07-georges.pdf)

**Status:** verified — paper title page and abstract inspected.

#### Relevance

Separates startup and steady-state behavior and addresses variability in managed runtimes.

#### Key findings

- Benchmark summaries without rigorous treatment of nondeterminism can mislead.
- Distinguishes repeated execution within an invocation from repeated runtime invocations.

#### Methodology

Reviews evaluation practices and develops statistical approaches for startup and steady state.

#### Useful metrics

Across-run variability, uncertainty intervals, and separate startup/steady-state summaries.

#### Limitations

Java-specific runtime behavior differs from Rust/Wasmtime; the paper does not justify a
universal 30-run rule or a particular test for network latency samples.

#### Thesis usage

Statistical-methodology rationale for all RQs; final analysis remains D029's open proposal.

### L13 — Hierarchical Integration of WebAssembly in Serverless for Efficiency and Interoperability

**Authors:** Mohammadamin Baqershahi, Changyuan Lin, Visal Saosuo, Paul Chen, Mohammad Shahrad

**Year:** 2026

**Venue:** USENIX NSDI 26

**DOI / URL:** [publisher record and paper](https://www.usenix.org/conference/nsdi26/presentation/baqershahi)

**Status:** verified — publisher metadata and paper abstract inspected.

#### Relevance

Recent peer-reviewed context for WebAssembly instance density and resource efficiency.

#### Key findings

- Wasabi co-locates WebAssembly applications inside container sandboxes.
- Reports density benefits from that architecture together with scaling, queuing, and overbooking.

#### Methodology

Serverless system design and comparative evaluation; quantitative results not extracted here.

#### Useful metrics

Instance density and resource use subject to performance constraints.

#### Limitations

Hybrid architecture and multiple scheduling changes prevent treating improvements as isolated
WebAssembly-versus-container effects. Serverless density is not L4 forwarding throughput.

#### Thesis usage

Recent related work and interpretation of RQ4–RQ6; no orchestration scope expansion.

## Official technical references

These complement the paper notes. They describe mechanisms and APIs, not independent
benchmark evidence. All were accessed on 2026-10-31; URLs may track later releases.

| ID | Source and verification | Relevance / useful controls | Limitations and thesis usage |
| --- | --- | --- | --- |
| T01 | [WASI sockets](https://github.com/WebAssembly/wasi-sockets), WebAssembly project; verified official repository, living/historical API material. | TCP/UDP host interfaces and capability access; resolve the Rust target and host integration before claiming equivalent socket behavior. | Repository is archived; pin the actual interface version and runtime support. Background and networking feasibility, not raw Ethernet support or proof of current compatibility. |
| T02 | [Pre-Compiling Wasm](https://docs.wasmtime.dev/examples-pre-compiling-wasm.html), Wasmtime project; verified official documentation. | Precompilation removes compilation from the startup path; record artifact preparation, caches, compiler configuration, and measured boundary. | Documentation explains mechanisms, not a measured speedup for this VNF. RQ4–RQ5; AOT/JIT choice remains open. |
| T03 | [Control Group v2](https://www.kernel.org/doc/html/latest/admin-guide/cgroup-v2.html), Linux kernel documentation, Tejun Heo; verified official reference. | CPU, cpuset, and memory controls/accounting; distinguish configured limits, charged memory, and process RSS. | Pin DUT kernel semantics; cgroup accounting alone does not establish complete VM cost. Resource controls and RQ3–RQ4. |
| T04 | [Bridge network driver](https://docs.docker.com/engine/network/drivers/bridge/), Docker; verified official documentation. | Bridge networking, masquerading, port publishing, and isolation rules identify possible datapath differences. | Not a benchmark or an accepted network-mode choice. Record actual mode/rules; RQ1–RQ3 and fairness. |
| T05 | [KVM API](https://docs.kernel.org/virt/kvm/api.html), Linux kernel documentation; verified official reference. | Separates VM, vCPU, and device mechanisms and capability checks. | Not complete QEMU/libvirt/virtio guidance or evidence of VM startup performance. VM background; deployment details still need version-specific references. |
| T06 | [WebAssembly for Proxies ABI](https://github.com/proxy-wasm/spec), Proxy-Wasm project; verified official specification repository. | Concrete example of WebAssembly hosted inside a programmable proxy; consider host/guest boundaries when interpreting networking claims. | A proxy extension is not a standalone WASI socket VNF. No Envoy/service-mesh experiment is introduced. Networking related work and RQ7. |

## Synthesis for this thesis

The following are interpretations of this review, not experimental results or decisions.

| Research questions | Relevant sources | Implication to investigate |
| --- | --- | --- |
| RQ1–RQ2: latency and throughput | L07–L11, T01, T04, T06 | Measure equivalent forwarding behavior and describe the complete socket/network path; CPU microbenchmarks cannot predict VNF performance. |
| RQ3–RQ4: CPU and memory | L05, L07, L09, L13, T03, T05 | Separate application/runtime costs and total environment costs; density benefits in a shared sandbox need different accounting from isolated instances. |
| RQ5: startup | L03–L04, L08, L12, T02 | Separate compilation, environment creation/boot, application initialization, and readiness. Published startup figures are not comparable without matching boundaries. |
| RQ6: scalability | L08–L09, L13 | Distinguish load scaling from instance-density/resource scaling and identify generator/backend bottlenecks. |
| RQ7: workload dependence | L01, L05–L06, T06 | Keep vFW/vLB separate; document runtime/API differences and limit generalization to tested workloads. |

**Working gap:** the reviewed evidence does not establish the outcome of the accepted
same-source Rust vFW/vLB comparison under Wasmtime, Podman, and QEMU/KVM on the same
x86 Linux DUT with common traffic and resource accounting. This motivates the experiment;
it is not a claim that no prior equivalent study exists. H1–H3 remain falsifiable.

## Remaining literature work

1. Resolve manual leads 9640153, 9283720, and 10034550 using publisher exports or full text;
   obtain L05's proceedings paper. Preserve the original links even if a label proves inaccurate.
2. Extract exact hardware, versions, build flags, startup definitions, workloads, repetitions,
   and uncertainty treatment from L03–L08 and L13 before quoting quantitative results.
3. Search more narrowly for standalone WebAssembly L7 firewall/load-balancer evaluations,
   WASI socket host-call/copy overhead, and fair three-environment NFV comparisons.
   Proxy-Wasm is currently a technical example, not sufficient empirical coverage of this area.
4. Add authoritative namespace/isolation and virtio/QEMU/libvirt references for the selected
   versions; KVM API documentation alone does not cover the full VM datapath.
5. Extend statistical reading to tail-latency sampling, offered-load models/coordinated omission,
   independent experimental units, practical effect sizes, and reproducible systems artifacts.
   Select analysis procedures only through the existing methodology/decision process.
6. Before claiming systematic coverage or novelty, record search databases, query strings,
   date bounds, inclusion/exclusion criteria, and citation snowballing in a dedicated review protocol.
