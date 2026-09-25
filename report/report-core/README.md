
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

### Writing reports

`Writer` may be shared by several threads: each `with()` call renders its flow's
detail under the writer's lock, then writes the file outside it, so detail IO
from different threads overlaps and `close()` waits for any writes still in
flight. By default the index is
rewritten after every flow; `Writer.Indexing.FINAL_ONLY` writes it once, on close,
which is cheaper for large reports:

```java
try (Writer writer = new Writer("model", "test", destination,
		Writer.Indexing.FINAL_ONLY)) {
	flows.forEach(writer::with);
}
```

The index is written to a temporary file in the report directory and then moved
atomically into place. A failed final-only update latches the writer — the index
has not been published yet, and must not list a detail that was never written — so
subsequent calls throw an
`IllegalStateException` carrying the original failure; a failed default-mode
update costs only that update. Once the writer is closed, subsequent updates are
rejected. Construction clears whatever exists at the destination, and a single
writer per destination is assumed.

In addition to the unit tests for the report input/output functionality, this module also contains [selenium-powered](https://www.selenium.dev/) tests to exercise the functionality of the [report webapp](../report-ng).

The following system properties offer some control over the behaviour of the selenium-based tests:

| property            | description |
| ------------------- | ------------|
| `browser.skip`  | Set to `true` to skip test execution. This is convenient if you haven't changed the webapp. |
| `browser.show`  | Set to `true` to show test execution on a visible browser instance |
| `browser.share` | Set to `true` to use a single browser instance for all tests. This is faster, but can exhibit stability issues |
