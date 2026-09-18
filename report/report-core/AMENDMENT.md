# Single-active-writer amendment implementation

Ticket 28 implements the approved report-scope amendment of 2026-09-15 before
ticket 23's run-owned reporting integration. This is not acceptance of the whole
parallel-execution branch. Overlapping active report destinations/publication
locations are unsupported, not serialized or deterministically rejected.

## Comparison and implementation boundary

- `main` re-resolved to `483c5428c2f1fb517c12f7f63200b39066059e17`, unchanged from
  the amendment's review baseline.
- Slice starting `HEAD`: `4c800ee3f68b12f30a7d27bb2b6365b862c290d8`.
- Report foundation: `4d1c2286ac984096894af9e7042bfdcab5ee0e19`.
- Original destination-claim implementation: `c9867a0b31e8466441bf60c6b2950dd99ef9f9e7`.
- The commit introducing this record is the implementation revision. Existing
  unstaged Stop/cancellation and packaged-consumer changes are outside this commit.

Individual hunks were compared, not whole commits reverted. Old decision records
and delivery evidence remain historical. Current supported behavior is documented
in [README.md](README.md); the active failures remain in
[implementation status](../../IMPLEMENTATION_STATUS.md).

## Changed, restored and retained

| Files | Decision and rationale |
| --- | --- |
| Deleted claim implementation and dedicated ownership test | Remove sidecar locks, local registry, ancestor/descendant probes, linked-file identity and Windows delete-denial checks, competing-publication claims, release bookkeeping and both process helpers. No replacement coordination mechanism. |
| [Writer](src/main/java/com/mastercard/test/flow/report/Writer.java) | Remove claim acquisition/release scopes; retain shared-writer serialization, detail collision protection, failure latch, synchronous finalization and callback. |
| `ReportFiles` (since removed by ticket 36) | Keep sequential physical-path/latest handling in the existing filesystem boundary. Preserve strict atomic move and operation/fault seam. |
| `WriterReplacementTest` (since removed by ticket 36) | Extract retained sequential reuse, aliases, nested/latest outputs, unrelated entries, initialization failure and completion callback cases before production simplification. No competing-writer oracle remains. |
| [WriterTest](src/test/java/com/mastercard/test/flow/report/WriterTest.java) | Restore immediate-writer scopes to the pre-claim version, retaining same-writer producer/snapshot assertions. |
| [FilterTest](../../assert/assert-filter/src/test/java/com/mastercard/test/flow/assrt/filter/FilterTest.java), [FailuresTest](../../assert/assert-filter/src/test/java/com/mastercard/test/flow/assrt/filter/cli/FailuresTest.java), [FlowPanelTest](../../assert/assert-filter/src/test/java/com/mastercard/test/flow/assrt/filter/gui/FlowPanelTest.java) | Restore claim-only immediate-writer scopes exactly to main. |
| [ReportingTest](../../assert/assert-core/src/test/java/com/mastercard/test/flow/assrt/ReportingTest.java) | Remove competing-publication assertions; retain runtime-options, completion, sequential/latest and alias behavior. |
| [ReportTestUtilTest](src/test/java/com/mastercard/test/flow/report/ReportTestUtilTest.java) | Describe retained sequential serving/population-failure behavior rather than claims. |
| [JUnit4 README](../../assert/assert-junit4/README.md), [JUnit5 README](../../assert/assert-junit5/README.md), [JUnit4 Flocessor](../../assert/assert-junit4/src/main/java/com/mastercard/test/flow/assrt/junit4/Flocessor.java), [JUnit5 Flocessor](../../assert/assert-junit5/src/main/java/com/mastercard/test/flow/assrt/junit5/Flocessor.java) | Replace obsolete ownership-open guidance with actual completion/publication requirements. Retain lifecycle examples. |

Mixed-purpose deltas from main deliberately remain:

- Requested versus physical Writer paths preserve explicit output aliases and
  canonical callback paths. Existing-prefix resolution handles missing components
  and physical parent traversal; lock-specific reserved-name validation is gone.
- Configured latest is known before replacement, including nested report names.
  Only this report's advertisement is withdrawn; unrelated links/ordinary files
  are preserved. An explicit output named latest remains an output access path.
- Original timestamp naming and replay suffix separation remain; no collision-safe
  allocator was present to remove.
- Final-only indexing, same-directory temporary files and strict atomic publication,
  snapshots, final-link corrections, JsApp IO and counting/failure fixtures remain.
  Reader, replay, schema and detail identity algorithms are unchanged.
- Core/legacy/prepared completion paths and caller scopes that execute finalization,
  callbacks or resource cleanup remain. In particular, ReportTestUtil accepts an
  arbitrary writer consumer that can register a callback; completion must precede
  starting its server. Factory return cannot substitute for actual caller drainage.
- Example runner lifetimes and fresh-run fixture initialization remain independently
  necessary. Deferred service suppliers fix construction before fixture setup, not
  report claims. No unrelated source/UI correctness delta was reverted.
- Parallel reporting stays guarded. Exactly-once prepared initialization, run-owned
  final-only activation and integrated stopped-run reporting remain ticket 23 work.

## Source size evidence

Physical source lines, not deletion targets; named classes include nested classes
but exclude anonymous classes. Writer's two enums are not counted as classes.

| Source | Main lines | Before | After | Classes before/after |
| --- | ---: | ---: | ---: | ---: |
| Writer | 312 | 659 | 634 | 2 / 2 |
| Removed claim implementation | 0 | 325 | 0 | 1 / 0 |
| ReportFiles | 0 | 50 | 139 | 1 / 1 |
| Removed ownership tests | 0 | 1008 | 0 | 3 / 0 |
| WriterReplacementTest | 0 | 0 | 341 | 0 / 1 |
| Total | 312 | 2042 | 1114 | 7 / 4 |

Report-core production overall: main 20 Java units / 1852 lines / 18 named
classes; slice start 22 / 2592 / 20; after 21 / 2331 / 19. The residual production
report changes from main are Writer, JsApp and ReportFiles for the retained
contract, not a replacement ownership coordinator.

## Validation

Zulu Java 17.0.19, Maven 3.6.3, JUnit 6.0.3. Commands used the explicit repository
directory and `JAVA_HOME=/c/Program Files/Zulu/zulu-17`, with its bin directory
first on PATH. Command-scoped Windows-ROOT trust used
`-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE` in
MAVEN_OPTS, without bypassing TLS verification.

Scoped formatter:

```bash
mvn -B -nsu -pl report/report-core,assert/assert-core,assert/assert-filter,assert/assert-junit4,assert/assert-junit5 formatter:format '-Dformatter.includes=**/Writer.java,**/ReportFiles.java,**/WriterReplacementTest.java,**/WriterTest.java,**/ReportTestUtilTest.java,**/ReportingTest.java,**/Flocessor.java,**/FilterTest.java,**/FailuresTest.java,**/FlowPanelTest.java'
```

Focused gate, with upstream reactor dependencies and actual test compilation:

```bash
mvn -B -nsu -fae -pl assert/assert-junit5,message/message-json -am test -Dnode=system -DskipTests=false -Dmaven.test.skip=false -Dmaven.test.failure.ignore=false -Dsurefire.failIfNoSpecifiedTests=false '-Dtest=WriterTest,WriterLifecycleTest,WriterPublicationTest,WriterFinalLinksTest,WriterReplacementTest,ReaderTest,ReportTestUtilTest,ReportingTest,ReplayTest,AbstractFlocessorTest,CaptureScopeTest,FlocessorTest,MetaTest,PreparedFlowLifecycleTest,PreparedCaptureTest,SerialCleanupTest,FilterTest,FailuresTest,FlowPanelTest'
```

Final working-tree result: **194 Java cases, 179 passed, 15 skipped, zero failures/errors**.
Report fixtures: 59/13 skipped; filter fixtures: 19/0; shared-core reporting,
replay and capture: 90/2; JUnit5 lifetimes: 26/0. Frontend: **72 passed**, with
the real **39-entry asset manifest**, not a placeholder report. Formatter and
actual javac compilation passed. Windows occupied-index atomic publication failure
and injected detail/temp/write/close/move failure checks passed.

Review caught loss of advertisement-parent creation when removing publication
claims. The three direct-publication cases failed before repair (two failures,
one error). Writer now retains the resolved latest location and creates its parent
only before the completion action, inside the existing failure latch. Missing-parent
publication, setup failure preventing the callback, original-cause retention and no
retry pass. The full replacement class passed 22 cases (12 symlink skips), followed
by the 194-case selector above. Logs: `flow-report28-review-red.log`,
`flow-report28-resume-replacement.log`, `flow-report28-resume-focused.log` and
`flow-report28-resume-format.log` under `C:/Data/Code/`.

Independent current-index reviews found **Standards 0 / Spec 0** scoped defects.
An earlier review used stale source and falsely reported deleted claims as present;
it was rejected, not counted as evidence. Editor recursion/style suggestions are
not javac failures: canonical resolution descends to a strictly shorter parent and
has an explicit root failure case. No unrelated cleanup was made for those hints.

The exact staged source was then exported to
`C:/Data/Code/flow-report28-staged-EPn4jB` (tree
`111eaf007e9052c70737d131b77f5fc884288a90`), excluding unstaged Stop/consumer work.
Fresh Java 17 compilation and focused checks passed **837 cases: 822 passed,
15 skipped, zero failures/errors** across 23 upstream/selected modules. This includes
the same 194 cases, three validation/legacy adapter metadata cases and **640
documentation cases** (50 source links, 50 snippets, 540 console-use checks).
The command above used `-pl doc` instead of `-pl assert/assert-junit5,message/message-json`
and appended `CodeLinkTest,SnippetTest,SysOutTest` to its selector. It omitted
`-Dnode=system`: the export used the same real built 39 frontend assets, while the
72 frontend tests had already passed in the working-tree gate. Both skip flags and
failure-ignore remained false. Output is `C:/Data/Code/flow-report28-staged-validation.log`.
Only final evidence prose was updated after this gate; this is not the full reactor.

The replacement characterization baseline passed 21 cases (12 symlink skips)
before refactoring. Baseline and first focused reactor attempts failed at malformed
JUnit5 test imports; these are not passing reactor gates or behavioral red tests.
The authorized import-only repair restored arrival content, leaving no committed
diff in that file. Failed attempts and final output remain external under
`C:/Data/Code/flow-report28-`, including `baseline.log`, `final-focused.log`,
`final-after-import-repair.log`, `results.log`, `review.md`, arrival/final hashes,
main/feature diffs and detailed count/audit records (each uses that prefix).

The earlier repeated-report failure remains **OPEN**. Passing this selector does
not diagnose or waive it. The separate packaged-consumer failure is also not
resolved by this slice. Twelve symlink cases and three existing platform cases
were skipped on Windows; POSIX/physical-alias runtime proof remains unavailable.
No new packaged-consumer, broad JUnit4 runtime, IDE or workload acceptance is claimed.
The user explicitly reserved the full reactor for final branch integration.
