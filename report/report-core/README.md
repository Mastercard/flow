
<!-- title start -->

# report-core

Report input/output
---
[![javadoc](https://javadoc.io/badge2/com.mastercard.test.flow/report-core/javadoc.svg)](https://javadoc.io/doc/com.mastercard.test.flow/report-core)

 * [../report](..) Visualising assertion results

<!-- title end -->

## Functionality

This module provides an object model for the data in an execution report along with facilities for writing and reading that data to and from storage.

## Testing

In addition to the unit tests for the report input/output functionality, this module also contains [selenium-powered](https://www.selenium.dev/) tests to exercise the functionality of the [report webapp](../report-ng).

The following system properties offer some control over the behaviour of the selenium-based tests:

| property            | description |
| ------------------- | ------------|
| `browser.skip`  | Set to `true` to skip test execution. This is convenient if you haven't changed the webapp. |
| `browser.show`  | Set to `true` to show test execution on a visible browser instance |
| `browser.share` | Set to `true` to use a single browser instance for all tests. This is faster, but can exhibit stability issues |
