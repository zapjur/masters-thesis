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
**Status:** verified / TODO verify

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
