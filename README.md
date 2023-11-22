
[![](https://github.com/Mastercard/flow/actions/workflows/maven.yml/badge.svg)](https://github.com/Mastercard/flow/actions/workflows/maven.yml)
[![](https://github.com/Mastercard/flow/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/Mastercard/flow/actions/workflows/codeql-analysis.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=Mastercard_flow&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=Mastercard_flow)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=Mastercard_flow&metric=coverage)](https://sonarcloud.io/summary/new_code?id=Mastercard_flow)
[![](https://img.shields.io/github/license/Mastercard/flow)](LICENCE)
[![Maven Central](https://img.shields.io/maven-central/v/com.mastercard.test.flow/parent)](https://search.maven.org/search?q=com.mastercard.test.flow)

<!-- title start -->

# flow

Testing framework



 * [api](api) Core type declarations
 * [message](message) Implementations of the Message interface
 * [builder](builder) Implementations of the Flow and Interaction interfaces
 * [model](model) Implementations of the Model interface
 * [validation](validation) Checking model consistency
 * [assert](assert) Comparing models against systems
 * [report](report) Visualising assertion results
 * [bom](bom) Bill of materials
 * [aggregator](aggregator) Aggregates build artifacts
 * [example](example) Service constellation to exercise the flow framework
 * [doc](doc) Documentation resources

<!-- title end -->

## Overview

This project provides a framework in which the flow of data in a system can be modelled.
This model can then be used to drive testing, both of the complete system and of subsystems in isolation.
These tests produce a rich execution report, [for example](https://mastercard.github.io/flow/execution/latest/app-itest/target/mctf/latest/index.html).

[This document describes the motivations for this approach](doc/src/main/markdown/motivation/index.md).

## Usage

 * [Quickstart guide](doc/src/main/markdown/quickstart.md): Illustrates the construction of a simple system model and its usage.
 * [Further reading](doc/src/main/markdown/further.md): Covers more advanced usage.
 * The submodules under [example](example) illustrate a complete service constellation with flow-based testing

<details>
<summary>Artifact dependency structure</summary>

<!-- start_module_diagram:framework -->

```mermaid
graph TB
  subgraph com.mastercard.test.flow
    api
    assert-core
    assert-filter
    assert-junit4
    assert-junit5
    builder
    coppice
    duct
    message-core
    message-http
    message-json
    message-sql
    message-text
    message-web
    message-xml
    model
    report-core
    report-ng
    validation-core
    validation-junit4
    validation-junit5
  end
  api --> message-core
  api --> builder
  api --> model
  api --> validation-core
  api --> report-core
  assert-core --> assert-junit4
  assert-core --> assert-junit5
  assert-filter --> assert-core
  message-core --> message-http
  message-core --> message-json
  message-core --> message-sql
  message-core --> message-text
  message-core --> message-web
  message-core --> message-xml
  report-core --> assert-filter
  report-core --> duct
  report-ng --> report-core
  validation-core --> validation-junit4
  validation-core --> validation-junit5
  validation-core --> coppice
```

<!-- end_module_diagram -->
</details>

## Links

 * This project is copyright © 2022 Mastercard, and is released under the [Apache version 2.0 licence](LICENCE).
 * [Contribution guidance](CONTRIBUTING.md).
 * [Changelog](CHANGELOG.md)
 * [Build artifacts](https://mastercard.github.io/flow/)
