# Parallel execution implementation status

This branch is a **partial implementation**, not an accepted parallel release.
The original review baseline is `483c5428c2f1fb517c12f7f63200b39066059e17`.
The approved 27-ticket backlog and Q1–Q10/T01–T36 acceptance programme remain
authoritative; no frozen feasibility evidence or design decision was rewritten.

## Delivered code slices

| Tickets | Implementation | Evidence seam |
| --- | --- | --- |
| 01–03 | Serialized direct Writer updates/snapshots; explicit final-only index and atomic close; latched failures; final basis/dependency rename correction using serialized detail evidence | `WriterTest`, `WriterLifecycleTest`, `WriterPublicationTest`, `WriterFinalLinksTest`, `ReaderTest` |
| 06 | Shared configuration and processor with invocation-local message/assertion/failure evidence; legacy fluent behavior retained | `AbstractFlocessorTest`, legacy JUnit 4 and Jupiter `MetaTest` |
| 07 | `FlowTest` / model-free `FlowExecution` / `PreparedFlocessor`; frozen preparation and provider-free native serial consumption | `FlowExecutionTest`, `PreparedFlowLifecycleTest` through real Launcher |
| 12 | Identity-visited prerequisite closure after filtering; retained bindings; hard and contracted-chain cycle rejection; valid in-flow bindings | `PrerequisiteSelectionTest`, `OrderTest`, `PreparedSelectionTest` |
| 20 | Successfully begun capture ends/materializes/closes once within invocation before Writer callbacks; capture-only diagnostics; primary-error preservation; merged-source drainage | `CaptureScopeTest`, `LogCaptureTest`, `MergeTest`, `PreparedCaptureTest` |
| 08 (partial only) | Internal token receipt/native-event attachment seam with latched notification failures; **not wired to an early Launcher decorator or parallel admission** | `FlowNativeCallTest` on real **serial** Flow executions |

The new caller rejects `flow.parallel=true` before factory/SUT execution. There
is no scheduler, resource isolation or parallel speedup claim. Report-only runner
failure classification and run-owned final-only activation are not complete.

## Blocking work and unpassed gates

### 04: destination claims need a genuine legacy lifecycle

Both legacy adapters create immediate Writers without a reliable run-completion
hook. Adding mandatory constructor-level destination locks would leave those
destinations owned after completed legacy runs and reject later same-path runs.
Report-core cannot distinguish completion from a pause between `with()` calls.

No lock exemption, guessed last-test release, stream-return finalization, cleaner
success hook or implicit mode change was introduced. Ticket 04 is **not implemented**.
The next integration must provide a genuine legacy lifetime or obtain an explicit
compatibility decision, then implement canonical cooperating claims, retained
ownership through advertisement, alias/process collisions and owned `latest`
withdrawal. Until then, different Writers require distinct non-overlapping paths.

### Native execution, hosts and workload

- 08 still needs the early public Launcher execution-call association, actual
  A→B binding with controlled independent native overlap, admission grants,
  callback-context restoration, stop gating and owned drainage.
- 09 ownership/selection/pool guards are unfinished. 10's published-consumer and
  intended-IDE Run/Debug/navigation/selection/Stop gate has **not passed**.
- 05 needs the actual 6,000–7,000-flow consuming workload and resource-owner input.
  That environment was not supplied; the reported 52/25-minute observations are
  not new measurements or a diagnosed serial-tail cause.
- 11, 13–19 and 21–27 are not delivered. This includes same-JVM resource ownership,
  dependency-ready parallel admission, uninterrupted chain reservations, fairness,
  bounded cancellation, correlated capture/cutoff, truthful stopped-run final
  artifacts, integrated compatibility, performance assessment and rollout.

The user was unavailable for the workload/host questions and instructed autonomous
work. Missing human evidence is recorded as missing, not substituted with nested
Launcher tests. No workload, publishing or rollout action was performed.

## Validation record

All focused Maven tests used the relevant upstream reactor, Java 17, both
`skipTests=false` and `maven.test.skip=false`, and the existing Java formatter.
Initial data-only report iterations were reduced checks; later Writer/Reader
regressions used the rebuilt real Angular assets. The local installation initially
contained Angular 18 against the committed Angular 14 lockfile and a stale data-only
index. Restoring locked dependencies and rebuilding corrected those environment
failures without dependency or frontend-source changes.

- Direct Writer/Reader focused run: 31 tests, zero failures/errors, one existing
  Windows-only skip. Filesystem tests cover temporary creation/write/close failure,
  unsupported atomic move, and a real occupied-index replacement failure.
- Four-member counting fixture with one repeated update: immediate mode serializes
  14 index entries across five writes; final-only serializes four in one write.
  Detail writes, corrective writes and bytes are counted separately. This is not
  a whole-Writer linearity or elapsed-time claim.
- External-ancestry fixture: 100 ancestors with 20 submitted leaves and one
  submitted ancestor; an external shared node is read once. Final correction
  uses captured ancestry despite later source mutation. Large detail payloads
  retain their captured values without replaying decorators.
- Serial caller/native attachment focused probes passed on coherent JUnit 5.10 /
  Platform 1.10 and 6.0.3 using Java 17. Actual serial outcomes and source positions
  were observed with automatic provider/interceptor channels disabled where
  required. These are not packaged-consumer or IDE support claims.
- Capture focused run: 102 tests, zero failures/errors/skips, including replay,
  merged-source failures, source stream closure and a real serial Launcher case.

### Standards review

The independent staged-WIP review identified two lifecycle defects, a grouped
non-private Javadoc omission and one possible duplication smell:

- The returned factory stream's cleanup ran during validation. It now runs once
  at native consumption close (or failed-validation cleanup), retaining primary
  failures and never interpreting early close as successful finalization.
- Emitted dynamic executables captured the full runner. They now capture only a
  detachable owner/index; owned completion clears selected flows and runner.
- New Jupiter lifecycle and native-observer methods have ownership/failure
  Javadocs. The store owner implements both cleanup interfaces supported by the
  tested JUnit versions.
- The small adapter-local Jupiter outcome classification duplication is retained
  as a disclosed judgement call, not a hard standards violation. Moving it into
  shared core would introduce an unwanted framework dependency; further local
  extraction is optional and does not affect the shared processing algorithm.

### Specification review

The independent review identified incomplete overall delivery (listed above),
missing true-empty selection coverage, and destructive intra-writer collisions:

- Real Launcher tests now cover repeated empty-model and filter-all selection,
  owner disposal and unchanged lazy report initialization.
- Two distinct flows with the same initial description demonstrate both a rename
  overwriting another detail and a first decorator deleting an unowned old path.
  Four regression cases failed before the fix and pass in both indexing modes.
  Writer now checks the final decorated path before destructive IO and deletes
  only its own previously written path. Failures latch, preserving both files;
  no blanket pre-decoration identity rejection or hash change was added.
- The explicit missing parallel/ownership/host/workload requirements remain
  missing. No review outcome changes that status.

Post-review serial checks: 84 focused tests passed with zero failures/skips;
the same 5.10-compiled serial module/test binaries passed 34/34 on both coherent
5.10.0 and 6.0.3 runtimes. Stream cleanup, retained-description detachment and
dual cleanup contracts each had observed red/green regression evidence.

Focused follow-up reviewers found no remaining functional defect in the claimed
slices. The remaining test-fixture Javadoc omissions were corrected without Java
implementation-token changes. The adapter-local duplication heuristic remains
disclosed; specification acceptance remains incomplete as listed above.

### Full-suite finding: initial invisible-edge rendering

The first unrestricted full Maven run stopped at
`ServedIndexTest.filteredInteractions`: a filtered-out edge was invisible but
had Mermaid's initial `thick` class rather than Flow's expected `normal` class.
The focused browser class reproduced it. The unchanged component normalized
incremental updates but omitted normalization after full asynchronous rendering.

A real-Mermaid first-render test reproduced the exact style mismatch; no SVG
stub, sleep or relaxed expectation was used. The component now applies its same
style routine after the render promise completes. The report frontend's complete
72-test suite and production build passed. The full Maven reactor is rerun with
rebuilt assets and without new GUI/browser exclusions; its final result follows.

The next run passed the original diagram browser assertion but exposed a separate
test-isolation issue: `WriterTest.writeDuctIndex` reused a persistent output
directory containing the prior frontend build's main bundle, so its unchanged
strict resource assertion counted two hashes. It now uses a fresh `@TempDir`;
no file-count expectations or production replacement semantics were relaxed.

The subsequent unrestricted run passed every runtime, GUI/browser and example
module, then failed documentation checks: the serial extraction required updating
generated source-line links, the intentional capture diagnostic needed the
existing exact-line console-use allowlist, and local ignored planning/skills/probe
assets contain example links/properties/console usage that are not library
documentation. The first two are corrected in this change. The planning artifacts
and documentation scanner are left unchanged. Final unrestricted verification is
performed from an isolated export of the staged sources (no ignored local assets),
using the real report frontend build from those same sources.

### Final verified results

- The clean staged-source full reactor completed successfully on 2026-09-14:
  **42 modules successful**, with no added test selectors, GUI/browser exclusions
  or skip switches. Invocation: `mvn -B -fae test -DskipTests=false -Dmaven.test.skip=false`.
- Validated source tree: `fb46970f9665238823feeb290b5befc8238e6558`. All 822 exported
  tracked files were checked against that tree; Java/TypeScript/Markdown/XML and
  other pinned-LF files matched byte-for-byte. Only Git's configured line-ending
  conversion was permitted for unpinned text. All 39 real frontend build assets
  matched the source-workspace build byte-for-byte.
- Fresh Surefire XML: **175 suites, 2,034 reported test cases, zero failures/errors,
  eight skips**. The XML contains exactly 2,034 testcase elements. Important
  module counts: report-core 113 (four skips), assert-core 144 (one skip),
  assert-junit4 one, assert-junit5 35, doc 852.
- Fourteen additional factory suites reported zero tests and contained zero
  testcase elements, including the core/queue/other dynamic examples. They are
  **not** evidence that those example behaviors or Deferred/provoked scenarios
  were exercised. This remains an explicit acceptance limitation.
- The separate full frontend run passed **72/72**, and its production build passed.
  POM sorting and the existing Java formatter passed. Focused source diagnostics
  were clear; the editor still reported that the changed Maven project model
  requires refresh, despite successful real Maven compilation.
- The original checkout still contains the intentionally unmodified ignored
  planning/probe assets. Its broad documentation scanner therefore remains
  sensitive to their illustrative links, historic properties and console output;
  the clean-source success must not be described as a green scan of those assets.

Only this validation record was appended after the validated source-tree snapshot;
no implementation or test behavior changed afterward.

No whole cross-cutting scenario is marked accepted solely because one focused
fixture passed. In particular, executed-zero dynamic example runs do not count
as Deferred/provoked behavioral evidence.
