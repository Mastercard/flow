
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

### Destination ownership and completion publication

Every writer, including an immediate writer, must be closed. Construction resolves
existing filesystem aliases and normalized paths, then acquires a mandatory,
nonblocking file lock before clearing or initializing output. A live competitor
fails reporting without deleting the owner's report. Closed, inactive reports
remain replaceable at the configured destination; output is not archived or moved.
The lock lives beside, not inside, the replaced report tree. Lock files remain on
disk after release: their existence is not ownership. A small JVM-local guard
prevents opening/closing a second descriptor that could invalidate a process's
file lock on some platforms; it is not a substitute for the filesystem lock.
Unsupported or failed filesystem claims fail closed, without a temporary-directory
registry or unlocked fallback.

Ancestor/descendant destinations also conflict. With its own sidecar held, a writer
probes existing ancestor claims and scans its existing output tree for descendant
claims before clearing it. A late descendant sees the ancestor's held claim; an
earlier descendant's held sidecar is found by the ancestor's scan. A descendant
paused while a finishing ancestor removes its newly opened sidecar must fail
linked-file validation before writing or publishing. Probes use the
same atomic local guard and filesystem lock, do not create missing ancestor claims,
and ignore unlocked stale claims. The one-time scan does not follow directory
symlinks; there is no per-flow scan or shared ancestor lock registry.

Claims capture `BasicFileAttributes.fileKey()` before opening the existing sidecar
(created without truncation when needed), then validate the linked key after all
ownership checks, including probes and short publication claims. Key-based providers
must retain a file's identity while its inode is open, as on POSIX filesystems;
the JDK's [file-key contract](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/BasicFileAttributes.html#fileKey()) alone does not guarantee this for every provider.
Null keys fail closed except on native Windows: when `os.name` starts with `Windows`
and the path uses `FileSystems.getDefault()`, claims instead require the public JDK
17 [`ExtendedOpenOption.NOSHARE_DELETE`](https://github.com/openjdk/jdk17u/blob/master/src/jdk.unsupported/share/classes/com/sun/nio/file/ExtendedOpenOption.java)
to prevent deletion/replacement throughout the open handle's lifetime. This assumes
an accurate OS property and the unmodified native default provider; other providers
require usable keys. Unsupported options fail closed without retrying an unlocked
open. Channel close releases its lock; an uncertain close retains the local guard
and failure rather than treating a later no-op close as successful disposal.

Cooperating processes must address the same canonical filesystem namespace and
honor its locks; shared-filesystem support depends on the provider's cross-process
lock semantics. Do not remove or replace sidecars, whose reserved names are
`.<destination>.flow-writer.lock` and `.latest.flow-publication.lock`, except as part
of replacing an inactive ancestor after its ownership checks.
This does not promise protection from older/noncooperating writers, external tree
deletion, mount/alias changes, crash durability, recovery or immediate lock release
after forced process termination. Readers/replay inputs remain read-only and must
not be chosen as replacement output by their callers.

Initialization failure releases its claim and preserves surviving partial files.
Update failure retains ownership until the caller closes; failed close safely
releases it but continues to report the original failure. Finalization failure also
releases safely disposable ownership, without retrying or deleting useful details.

`writer.onClose(Consumer<Path>)` registers one synchronous completion action. The
existing `close()` finalizes the report, invokes the action with its canonical path,
then releases ownership. No action runs after failed detail/index finalization.
The action must finish all publication-related use before returning; asynchronous
work must not escape that scope. Repeated successful close does not invoke it again;
an action failure is latched, not retried or rolled back. Configured publication actions
and owned-link withdrawal share a short-lived nonblocking filesystem claim, so
competing publication fails observably rather than racing link replacement.

Before replacing a destination, Writer withdraws its configured `latest` symlink
only if it resolves to that canonical destination; unrelated links and ordinary
files are left alone. An explicitly configured output path named `latest`, including
an alias to an old report, is an output access path rather than an advertisement to
withdraw: it remains readable through `writer.path()` during immediate writes and
after close. Ordinary files/directories chosen as explicit output remain replaceable.
The five-argument constructor
`Writer(model, test, root, indexing, latest)` supplies that location before clearing;
existing constructors default to a sibling. Only the advertisement's parent is
canonicalized, not its possibly foreign link target. Withdrawal and publication
lock that parent's sidecar and check ancestor ownership; a competing writer cannot
replace the publication parent while the action is active.

Writer does not create an advertisement itself. Completion actions advertising
`latest` must use the configured location and preserve unrelated references/ordinary
files. Automatic run naming, caller drainage,
final-only activation and latest/browse integration remain runner work; legacy
assertion adapters still use immediate reporting.

## Testing

In addition to the unit tests for the report input/output functionality, this module also contains [selenium-powered](https://www.selenium.dev/) tests to exercise the functionality of the [report webapp](../report-ng).

The following system properties offer some control over the behaviour of the selenium-based tests:

| property            | description |
| ------------------- | ------------|
| `browser.skip`  | Set to `true` to skip test execution. This is convenient if you haven't changed the webapp. |
| `browser.show`  | Set to `true` to show test execution on a visible browser instance |
| `browser.share` | Set to `true` to use a single browser instance for all tests. This is faster, but can exhibit stability issues |
