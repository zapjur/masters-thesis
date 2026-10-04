# Data Analysis

This directory will contain code used to analyze benchmark measurements.

## Intended responsibilities

Analysis tooling should eventually:

- load raw benchmark data,
- validate measurement completeness,
- calculate statistical summaries,
- calculate percentiles,
- compare execution technologies,
- generate tables,
- generate figures used in the thesis.

## Reproducibility

Every figure and table based on benchmark results should ideally be generated from code.

Avoid manually copying values into thesis figures.

## Principle

Raw data must remain unchanged.

Any cleaning, filtering, aggregation, or outlier handling must be implemented in scripts and
documented.
