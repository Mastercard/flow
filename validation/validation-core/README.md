
<!-- title start -->

# validation-core

Model validation components

[![javadoc](https://javadoc.io/badge2/com.mastercard.test.flow/validation-core/javadoc.svg)](https://javadoc.io/doc/com.mastercard.test.flow/validation-core)

 * [../validation](..) Checking model consistency

<!-- title end -->

## Overview

This module provides:
 * An API for defining validation checks to apply to system models
 * A suggested range of validation checks
 * An abstract mechanism for performing such checks

The `validation-junit4` and `validation-junit5` projects provide concrete implementations of the check mechanism.
One or other of those should be applied to your system model.

## Usage

It is unlikely that you'll need to depend directly on this module, it will be supplied transitively by `validation-junit4` or `validation-junit5`.
