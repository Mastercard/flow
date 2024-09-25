
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
These tests produce a rich execution report, [for example](https://mastercard.github.io/flow/static/app-itest/target/mctf/latest/index.html).

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
    api[<a href='https://github.com/Mastercard/flow/tree/main/api'>api</a>]
    assert-core[<a href='https://github.com/Mastercard/flow/tree/main/assert/assert-core'>assert-core</a>]
    assert-filter[<a href='https://github.com/Mastercard/flow/tree/main/assert/assert-filter'>assert-filter</a>]
    assert-junit4[<a href='https://github.com/Mastercard/flow/tree/main/assert/assert-junit4'>assert-junit4</a>]
    assert-junit5[<a href='https://github.com/Mastercard/flow/tree/main/assert/assert-junit5'>assert-junit5</a>]
    builder[<a href='https://github.com/Mastercard/flow/tree/main/builder'>builder</a>]
    coppice[<a href='https://github.com/Mastercard/flow/tree/main/validation/coppice'>coppice</a>]
    duct[<a href='https://github.com/Mastercard/flow/tree/main/report/duct'>duct</a>]
    message-bytes[<a href='https://github.com/Mastercard/flow/tree/main/message/message-bytes'>message-bytes</a>]
    message-core[<a href='https://github.com/Mastercard/flow/tree/main/message/message-core'>message-core</a>]
    message-http[<a href='https://github.com/Mastercard/flow/tree/main/message/message-http'>message-http</a>]
    message-json[<a href='https://github.com/Mastercard/flow/tree/main/message/message-json'>message-json</a>]
    message-sql[<a href='https://github.com/Mastercard/flow/tree/main/message/message-sql'>message-sql</a>]
    message-text[<a href='https://github.com/Mastercard/flow/tree/main/message/message-text'>message-text</a>]
    message-web[<a href='https://github.com/Mastercard/flow/tree/main/message/message-web'>message-web</a>]
    message-xml[<a href='https://github.com/Mastercard/flow/tree/main/message/message-xml'>message-xml</a>]
    model[<a href='https://github.com/Mastercard/flow/tree/main/model'>model</a>]
    report-core[<a href='https://github.com/Mastercard/flow/tree/main/report/report-core'>report-core</a>]
    report-ng[<a href='https://github.com/Mastercard/flow/tree/main/report/report-ng'>report-ng</a>]
    validation-core[<a href='https://github.com/Mastercard/flow/tree/main/validation/validation-core'>validation-core</a>]
    validation-junit4[<a href='https://github.com/Mastercard/flow/tree/main/validation/validation-junit4'>validation-junit4</a>]
    validation-junit5[<a href='https://github.com/Mastercard/flow/tree/main/validation/validation-junit5'>validation-junit5</a>]
  end
  api --> message-core
  api --> builder
  api --> model
  api --> validation-core
  api --> report-core
  assert-core --> assert-junit4
  assert-core --> assert-junit5
  assert-filter --> assert-core
  message-core --> message-bytes
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
