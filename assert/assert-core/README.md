
<!-- title start -->

# assert-core

Core comparison components

[![javadoc](https://javadoc.io/badge2/com.mastercard.test.flow/assert-core/javadoc.svg)](https://javadoc.io/doc/com.mastercard.test.flow/assert-core)

 * [../assert](..) Comparing models against systems

<!-- title end -->

## Overview

This module provides an abstract mechanism for comparing a model against a system, and reporting on the differences found.
It includes:
 * Flow selection
 * Failure avoidance
 * Flow ordering
 * Report generation

It lacks:
 * An integration into a test framework - this is provided in `assert-junit4` and `assert-junit5`.
 * The exact mechanism by which data is supplied to and extracted from the real system.
   This is obviously completely dependent on the system under test, so it must be implemented in the client test suite.

## Usage

It is unlikely that you'll need to depend directly on this module, it will be supplied transitively by `assert-junit4` or `assert-junit5`.

## System properties

Some aspects of assertion behaviour can be controlled by system properties:

<!-- start_property_table -->

| property | description |
| -------- | ----------- |
| `mctf.dir` | The path to the dir where assertion artifacts are saved |
| `mctf.filter.cli.min_width` | The minimum width of the command-line interface |
| `mctf.filter.exclude` | A comma-separated list of tags values that flows must not have |
| `mctf.filter.fails` | Configures filters to repeat flows that did not pass assertion in a previous run. Supply the location of a report from which to extract results, or `latest` to extract from the most recent local report |
| `mctf.filter.include` | A comma-separated list of tags values that flows must have |
| `mctf.filter.indices` | A comma-separated list of indices and index ranges for flows to process |
| `mctf.filter.repeat` | Supply `true` to use the previous filters again |
| `mctf.filter.update` | Supply `true` to update filter values at runtime in the most appropriate interface.Supply `cli` to force use of the command-line interface or `gui` to force use of the graphical interface |
| `mctf.replay` | The location of a report to replay, or `latest` to replay the most recent local report |
| `mctf.report.dir` | The path from the artifact directory to the report destination |
| `mctf.suppress.assertion` | Set to `true` to continue processing a flow in the face of assertion failure |
| `mctf.suppress.basis` | Set to `true` to process flows whose basis flows have suffered assertion failure |
| `mctf.suppress.dependency` | Set to `true` to process flows whose dependency flows have suffered errors |
| `mctf.suppress.filter` | Set to `true` to process flows that would otherwise be rejected by the `Flowcessor.exercising()` filter |
| `mctf.suppress.system` | Set to `true` to process when the system under test lacks declared dependencies |

<!-- end_property_table -->

## Flow Selection

The framework will default to exercising all flows in the model that are relevant to the system under test. It is possible to run a subset of flows by setting the `mctf.filter` system properties described above.

Note that flows will automatically be brought into the execution order as required to satisfy flow dependencies in the system model.

## Failure avoidance

If a parent flow fails, it is likely that the descendants of that flow will fail in the same way.
The assert components will thus skip running them.
This speeds up test execution and avoids spamming the report with duplicates of the same failure.

We'll also skip flows where the dependency flows suffered an error.

## Flow ordering

The assert components will work out a flow execution order that:
 * Guarantees that inter-flow dependencies are honoured.
 * Minimises expensive context changes
 * Attempts to maximise the efficiency of the failure avoidance behaviour, by running parent flows before their children.

## Report generation

The results of flow execution can be (depending on how the Flocessor is configured) collated into a human-readable report that details observed system behaviour and the results of comparing that against the system model.
The location of the report can be controlled with the `mctf.dir` and `mctf.report.name` system properties.

## Report replay

If a generated report indicates that observed system behaviour does not match that described in the model as documented in the flows, there are two possibilities:
 1. The system is correct and the model should be updated
 2. The model is correct and the system should be updated

If the first case is true then we need to update the flows to match the reality of the system.
This will be an iterative exercise of updating the flow construction code and re-running the assertion against the system until documented behaviour matches the observed system.

While we _could_ run these iterative assertions against the same system that provoked the failure report, we don't _have_ to do so as the observed behaviour that we're trying to match is already captured in the report.
Using a report as the source of data for assertion is likely to be more efficient than exercising the actual system, and is obviously far more convenient if the system is difficult to reliably access.

Set the `mctf.replay=path/to/report_directory` system property to activate replay mode - the specified report will be used as the source of observed behaviour rather than the actual system.
Setting `mctf.reply=latest` will cause the most recently-generated report in the `mctf` artifact directory to be replayed.

In your tests you can check the value of `Replay.isActive()` to detect if replay mode is active.
This allows you to avoid doing initialisation and teardown operations that are only relevant if you're testing against the actual system.
