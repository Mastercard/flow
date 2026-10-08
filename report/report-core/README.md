
<!-- title start -->

# report-core

Report input/output

[![javadoc](https://javadoc.io/badge2/com.mastercard.test.flow/report-core/javadoc.svg)](https://javadoc.io/doc/com.mastercard.test.flow/report-core)

 * [../report](..) Visualising assertion results

<!-- title end -->

## Usage

It is unlikely that you'll need to depend directly on this module, it will be transitively supplied by [`assert-junit4`](../../assert/assert-junit4) or [`assert-junit5`](../../assert/assert-junit5).

## Functionality

This module provides an object model for the data in an execution report along with facilities for writing and reading that data to and from storage.

### Index publication

The three-argument `Writer` constructor uses `Writer.Indexing.IMMEDIATE`, preserving the compatibility behavior of publishing a complete index after every successful update.

Lifecycle-aware callers can select `Writer.Indexing.FIRST_THEN_EXPLICIT`. The first completely successful update publishes a readable snapshot; later updates write their details but leave that snapshot stale until the caller invokes `Writer.publishIndex()`. Calling `publishIndex()` before an update, or again without another update, is a no-op. JUnit 5 selects this policy internally and publishes from its final completing dynamic-test body, so ordinary `Flocessor.tests()` users do not need to call it.

Deferred index snapshots are replaced atomically, so failed publication preserves any earlier index. If a deferred detail update or publication fails, the original operation fails and later publication remains invalidated rather than advertising a misleading complete report. Detail files retain their existing direct-write failure semantics, so a failed rewrite of an already-indexed detail is not guaranteed to preserve that detail's prior contents.

## Testing

In addition to the unit tests for the report input/output functionality, this module also contains [selenium-powered](https://www.selenium.dev/) tests to exercise the functionality of the [report webapp](../report-ng).

The following system properties offer some control over the behaviour of the selenium-based tests:

| property            | description |
| ------------------- | ------------|
| `browser.skip`  | Set to `true` to skip test execution. This is convenient if you haven't changed the webapp. |
| `browser.show`  | Set to `true` to show test execution on a visible browser instance |
| `browser.share` | Set to `true` to use a single browser instance for all tests. This is faster, but can exhibit stability issues |
