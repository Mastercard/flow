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
 * [aggregator](aggregator) Aggregates build artifacts
 * [example](example) Service constellation to exercise the flow framework
 * [doc](doc) Documentation resources

<!-- title end -->

[![](actions/workflows/maven.yml/badge.svg)](actions/workflows/maven.yml)
[![](actions/workflows/codeql-analysis.yml/badge.svg)](actions/workflows/codeql-analysis.yml)
![GitHub](https://img.shields.io/github/license/Mastercard/flow)

## Overview

This project provides a framework in which the flow of data in a system can be modelled.
This model can then be:
 * Visualised to provide documentation of system behaviour
 * Used to drive testing of the system 

[This document describes the motivations for this approach](doc/src/main/markdown/motivation/index.md).

## Usage

 * [Quickstart guide](doc/src/main/markdown/quickstart.md): Illustrates the construction of a simple system model and its usage.
 * [Further reading](doc/src/main/markdown/further.md): Covers more advanced usage.
 * The submodules under [example](example) illustrate a complete service constellation with flow-based testing

## Links

 * This project is copyright © 2022 Mastercard, and is released under the [Apache version 2.0 licence](LICENCE).
 * [Contribution guidance](CONTRIBUTING.md).
 * [Changelog](CHANGELOG.md)
