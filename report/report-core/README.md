
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

### Direct writers and final-only indexing

The three-argument `Writer` constructor retains immediate indexing: each successful
`with()` writes its detail, index and incremental basis links before returning.
Updates and accessor snapshots are serialized on the writer; callbacks run once,
synchronously on the calling thread. Callers still order repeated updates to the
same flow and must not wait inside a callback for another thread to update that
writer. `missingBases()` returns detached, immutable maps and lists, retaining the
original Flow references.

For a report that only needs a final index, select `Writer.Indexing.FINAL_ONLY`:

```java
try (Writer writer = new Writer("model", "test", destination,
		Writer.Indexing.FINAL_ONLY)) {
	flows.forEach(writer::with);
}
```

Details are written synchronously. Close resolves links against final membership
and supported renames, using previously serialized detail snapshots rather than
rerunning callbacks or reading mutable execution data. Final entries are sorted
by their existing detail identities. The index is written and closed in a temporary
file in the report directory, then atomically moved into place. Unsupported or
failed atomic moves fail reporting; there is no non-atomic fallback.

Successful repeated close does no work, and subsequent updates are rejected.
Within one writer, a decorated detail identity already owned by another flow is
rejected before destructive IO. A first decorator may still move a shared initial
identity to a free path. This narrow check does not change detail hashes or add
model-wide identity validation.
Update/publication failures are latched: subsequent update/close calls throw an
`IllegalStateException` whose cause is the original failure, without retrying IO.
Closing is not proof that the surrounding test run has completed; the caller must
stop submissions and drain its work first. Direct Writer failures remain visible.

### Sequential replacement and completion publication

Supported reporting assumes a single active writer per actual output namespace
and publication location: no competing writer may replace the same destination,
an overlapping parent/child destination, or the publication location. Overlap is
unsupported and may delete, mix or misleadingly publish output. Neither safe
last-writer-wins nor deterministic collision failure is promised. There are no
cross-run locks, rejection registries or automatic serialization. This does not
relax shared SUT resource, chain, context, fixture or cancellation coordination
across cooperating runners; multiple producers may still share one writer.

Construction resolves existing filesystem aliases before replacing output.
Sequential runs replace existing output at the requested destination without
nonempty-directory rejection, relocation, backup or recovery. Readers/replay
inputs remain read-only and must not be chosen as replacement output. Hard
termination may leave useful details but does not guarantee a browsable final-only
index or crash recovery. Failed initialization/finalization preserves surviving
partial files; it does not retry into apparent success.

`writer.onClose(Consumer<Path>)` registers one synchronous completion action. The
existing `close()` finalizes the report, then invokes the action with its canonical
path, creating the configured advertisement parent first if needed. No action runs
after failed detail/index finalization or publication setup.
The action must finish all publication-related use before returning; asynchronous
work must not escape that scope. Repeated successful close does not invoke it again;
an action failure is latched, not retried or rolled back. Immediate reads do not
require close, but completion actions do; final-only indexes also require close.

Before replacing a destination, Writer withdraws its configured `latest` symlink
only if it resolves to that canonical destination; unrelated links and ordinary
files are left alone. An explicitly configured output path named `latest`, including
an alias to an old report, is an output access path rather than an advertisement to
withdraw: it remains readable through `writer.path()` during immediate writes and
after close. Ordinary files/directories chosen as explicit output remain replaceable.
The five-argument constructor
`Writer(model, test, root, indexing, latest)` supplies that location before clearing;
existing constructors default to a sibling. Only the advertisement's parent is
canonicalized, not its possibly foreign link target.

Writer does not create an advertisement itself. Completion actions advertising
`latest` must use the configured location and preserve unrelated references/ordinary
files. Automatic run naming and replay-source separation remain unchanged. Caller
drainage, exactly-once initialization before concurrent bodies, final-only activation
and latest/browse integration remain runner work; legacy assertion adapters still
use immediate reporting. Parallel reporting remains guarded pending ticket 23's
run-owned integration and the real caller/host acceptance gates.

## Testing

In addition to the unit tests for the report input/output functionality, this module also contains [selenium-powered](https://www.selenium.dev/) tests to exercise the functionality of the [report webapp](../report-ng).

The following system properties offer some control over the behaviour of the selenium-based tests:

| property            | description |
| ------------------- | ------------|
| `browser.skip`  | Set to `true` to skip test execution. This is convenient if you haven't changed the webapp. |
| `browser.show`  | Set to `true` to show test execution on a visible browser instance |
| `browser.share` | Set to `true` to use a single browser instance for all tests. This is faster, but can exhibit stability issues |
