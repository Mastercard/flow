# Parallel execution implementation status

This branch is a **partial implementation**, not an accepted parallel release.
The original review baseline is `483c5428c2f1fb517c12f7f63200b39066059e17`.
The original 27-ticket backlog and Q1–Q10/T01–T36 acceptance programme remain
authoritative subject to explicit approved amendments. Frozen feasibility evidence
and the original decision records remain historical.

## Ticket 25 acceptance refresh — 2026-09-17

At `b7a4f070`, the focused 16-project packaging/install reactor passed, followed by
the standalone consumer matrix against the same installed artifacts:

| Jupiter / Platform | Flow parallel | Outer tests (failures/errors/skips) | Native starts |
| --- | --- | --- | --- |
| 5.10.0 / 1.10.0 | false | 2 (0/0/0) | 18 |
| 5.10.0 / 1.10.0 | true | 2 (0/0/0) | 105 |
| 6.0.3 / 6.0.3 | false | 2 (0/0/0) | 18 |
| 6.0.3 / 6.0.3 | true | 2 (0/0/0) | 105 |

The 246 native starts were independently totaled from the fresh Launcher summaries.
The four sorted 25-file Flow artifact manifests have the identical SHA-256
`edcf145b9df2132f015099c87e67f6f59441e0d4869fff0caf5fa03a1cfa59f1`.
Fresh XML and class-loading checks passed via the existing
[consumer verification script](assert/assert-junit5/src/it/packaged-consumer/verify.sh).
This fixture uses `Reporting.NEVER`: it is packaged binding/runtime evidence, not
packaged reporting, replay or the complete trace-variant acceptance matrix.

Fresh unchanged-gate PIT at that endpoint reproduced the core failure: 2,111/2,367
lines (89%), 1,066/1,219 detected mutations (87%), 118 uncovered and 35 surviving.
Jupiter independently reproduced all 38 unmutated failures before mutation analysis:
auxiliary top-level native factories were selected without their controlling tests'
configuration or registry. These are automated test-discovery failures, not locked
desktop failures.

The discovery correction selects controlling classes using Surefire's four default
naming conventions. The compiled-class inventory confirms all four patterns select
the same 17 controllers as the successful `*Test` command-line probe; no production
mutation targets, mutators, thresholds or existing assertions are reduced. Auxiliary
factories remain exercised through their controlling tests' real nested Launchers.
The probe passed its unmutated coverage phase and entered mutation analysis; this
alone does not establish either Jupiter's 82/97 gate or complete ticket-25 acceptance.
The run was subsequently stopped after capturing a prolonged worker stall. Three
minion stacks show nested native Launcher waits; the oldest captured stack runs
through `JUnit5TestUnitFinder.findTestUnits`, the busy two-worker regression, and a
factory waiting in `FlowAdmission.next()`. No finished Jupiter mutation score exists.
The earlier native-progress correction is therefore not fully accepted under this
path; diagnose the retained stack evidence before claiming that finding closed.
The verified PIT process tree was terminated, not left running or counted as a pass.

Core commit `156b7297` adds three admission-seam regressions: unused handoff proof
retains outstanding operations without inventing results; actual native skip is
idempotent and distinct from processing; throwing/nested/foreign receipt delivery
preserves factory ownership. All 323 core cases passed with the same two existing
skips and no failures/errors. The full unchanged 94/95 mutation gate is being
remeasured in independent slices. The first complete rerun reached 2,148/2,367
lines (91%) and 1,088/1,219 detected mutations (89%), with 96 uncovered and 35
surviving: 37 more covered lines and 22 more detected mutations, but still a
failing gate. A passing unit suite does not waive it.

Commits `615da5fe` and `0556aa18` add native-configuration guard cases and fixture
owner/lifecycle regressions, including applied context surviving runner completion
until explicit reset. The affected 47 cases passed, then the whole core suite passed
339 cases with the same two skips. Its completed PIT result is 2,196/2,367 lines
(93%) and 1,109/1,219 detected mutations (91%: 1,086 killed plus 23 timed out), with
65 uncovered and 45 surviving. This remains below 94/95; no targets or gates changed.

The new [external report command gate](assert/assert-junit5/src/it/packaged-consumer/verify-report-commands.sh)
passed 16 external JVM runs: both JUnit stacks, serial/parallel, healthy/broken report
output and passing/mixed bodies. It observed 48 genuine native starts (32 successful,
eight intentional failures, eight dependent aborts), exact process exits and actual
loaded runtime/JAR versions. Report-only faults leave successful commands successful;
the original SUT error makes the command fail with or without a report fault. Healthy
final reports were checked by Reader. The original four-point packaged binding gate
also passed again after these opt-in additions, with unchanged artifact manifests.

This is a public-Launcher host, not a Surefire waiver. Direct Surefire 3.5.3 execution
loses dynamic leaves whose ClassSource points to a different class, independently
reproduced by a plain Jupiter test without Flow. Surefire 3.6.0 fixes that minimized
source case, but the actual parallel command is then rejected by Flow's existing
no-filters/sole-class request guard. See the [consumer host findings](assert/assert-junit5/src/it/packaged-consumer/README.md#surefire-host-findings-not-waived-by-the-launcher-command).
No metadata rewrite, selector allowance or repository-wide plugin upgrade was made.

The frozen evidence archive is under `C:/Data/Code/flow-ticket25-acceptance-20260917/`:
core reports, consumer logs/class traces/output, source snapshots and the stopped
PIT job's parent/minion stacks. Incomplete Jupiter output is diagnostic material,
not a passing mutation report.

Decisions for later review: retain the discovery correction rather than making
standalone auxiliary fixtures silently succeed; retain all mutation gates; keep
cleanup 29–32 after functional completion and the user's trial/feedback. The direct
Surefire request/profile needs a justified host decision rather than disabling
counts or hiding native source metadata. Core/Jupiter mutation acceptance, native
progress under the stalled PIT path, remaining report/trace acceptance, manual
IntelliJ interaction and the user's workload trial remain open. No speedup, release
acceptance or locked-desktop pass is claimed.

## Native-progress follow-up: reproduced, not fixed — 2026-09-17

The PIT stall also reproduces without PIT or mutation instrumentation. An isolated
run of `busyTargetTwoMakesRealInlineProgressAndRestoresContextOnReuse` passed, but
the fourth subsequent bounded invocation failed its chain terminal-listener order
assertion. That is a separate observation, not a reproduction of the hang and not
yet a justified reason to change the assertion.

A throwaway process-bounded harness then repeatedly invoked the unchanged genuine
Launcher fixture. On `busy-chain` iteration 93 it captured 78 started/completed
leaves, 78 restored contexts and one remaining queued native task. The factory was
waiting in `FlowAdmission.next()` and the other worker was parked in the native
pool. The live pool snapshot was parallelism 2, size 2, active 1, running 0,
tasks 1, submissions 0. Flow 020 had been registered but had not started; its
successor 021 had not been registered. The probe's ten-second per-call deadline
dumped the real events/stacks and terminated its own JVM. This makes an
instrumentation-only explanation untenable; it does not prove a missed notification.

A three-flow regression reduced the insufficient-progress condition: one native
worker is held in an independent body, A is the only queued prerequisite, and B
cannot be admitted until A publishes its real dependency binding. A registration
receipt coordinates the held worker; no fabricated native execution is used.
The original queue-width threshold failed this regression with the bounded fixture
timeout (5.61 seconds). Removing that threshold passed the same test (0.527 seconds),
including native counts, inline execution, context restoration and binding.

**That candidate is rejected, not implemented.** The full adapter suite with it
ran 302 cases and failed 11: five fixture-cancellation cases, three independent
binding/progress cases and three native-resource cases, including combined
resource/chain/context/report acceptance. Unconditionally executing a queued child
on the factory does not preserve the existing cancellation and scheduling contracts.
Both production and test sources were restored to their committed baseline, checked
with an empty Git diff before recompilation. The restored full adapter suite passed
301 cases with zero failures, errors or skips in 1 minute 37 seconds; this confirms
rollback of the candidate's regressions, not absence of the intermittent baseline
stall. No existing assertion or test was disabled, and the new red regression is
retained in the diagnostic archive rather than presented as a completed implementation.

The separate `native-progress-investigation.tar.gz` archive under the evidence
directory above contains the rejected source snapshot, its three-flow regression,
the standalone probe, live stall evidence, behavioral red/green logs, and all 302
candidate-suite XML results. SHA-256:
`58937b6575c6c65549bf14a743657add0956a4fc744998e3ed00f6b7dd867af8`.
Its green focused test is **not** acceptance of the rejected candidate. The original
`evidence.tar.gz` remains unchanged.

Decision for follow-up: do not replace the threshold with an idle-worker heuristic,
drain the pool, weaken cancellation assertions, or introduce an alternate body
executor. A native-wait/progress solution must pass both the bounded small-backlog
reproducer and the existing cancellation/independent-progress contracts before a
fresh Jupiter mutation run. The earlier “resolved” native-progress checkpoint below
is historical and is superseded by this open finding.

## Post-functional cleanup clarification — 2026-09-16

The user clarified that the remaining simplification/refactoring/test-reduction
tickets 29–32 were meant to be deferred, not cancelled. Finish retained functional
work and focused integration first, then the user's trial in their consuming
repository (26), and address its functional feedback before starting cleanup.
These tickets remain planned work, but do not block reporting, ticket 25 or that
initial trial. The unrelated deferred capabilities remain deferred.

Re-evaluate cleanup candidates against the completed implementation; prefer
removing redundant branch-added tests and unnecessary indirection without losing
distinct behavior or rewriting main-existing tests unnecessarily. Preserve ticket
28 and delivered corrections. Fresh before/after regression/mutation evidence and
a combined coverage review belong to the later cleanup phase, with existing gates
unchanged. Refresh affected integration evidence after cleanup rather than treating
the user's earlier trial as validation of later changes. No cleanup, test run,
workload enablement or release is performed by this documentation correction.

## Approved focused delivery — 2026-09-16

The user confirmed the reduced scope after a Q1–Q7 interview. This section
supersedes conflicting pending-work instructions in the historical entries below;
it records a scope decision, not new implementation or passing tests. This
reconciliation changes documentation/status/dependencies only.

**Deliver:** safe opt-in parallel execution within one model/factory and one
automatically finalized, thread-safe report. Preserve serial default, dependency
and chain semantics, canonical bindings, fixture/context/resource safety, genuine
outcomes and the implemented cancellation/unsafe-ownership guarantees. Keep the
current architecture and single-active-writer reporting contract. Prepared serial
and parallel paths now accept `Reporting.QUIETLY` through run-owned final-only
publication; other native reporting modes remain guarded.

| Work | Approved disposition |
| --- | --- |
| 29–32 | Defer simplification/refactoring/test reduction until functional completion and the user's repository trial/feedback. Not an integration or initial-trial prerequisite; retain existing tests and corrections until reviewed cleanup. |
| 21–22 | Defer per-flow parallel capture and correlated collector/file-source work. External logging remains; no unsupported per-flow attribution is claimed. |
| 20 | Preserve existing capture lifecycle correctness and finish only what reporting integration/serial compatibility still needs. |
| 23 | Finish normal-run final-only reporting, including genuine failure/skip outcomes and visible non-fatal report-only errors without changing processing semantics. |
| 24 | Defer useful partial reports after cancellation. Retain safe drainage and visible cancelled/incomplete diagnostics; no invalid final report may be advertised. |
| 05, 25–27 | Narrow to actual resource auditing, focused combined acceptance, comparable real-workload measurement and a short reversible adoption checklist. |
| Remaining 10, 17, 19 acceptance | Retain relevant host/fixture checks, delivered evidence and safety boundaries; do not repeat completed implementation or broaden cancellation machinery. |
| Parallel replay/broader native support | Defer; preserve existing serial behavior and explicit fail-fast unsupported-configuration restrictions. |
| 33 | Before acceptance, remove the temporary class-source prerequisite. Preserve automatic traces, addenda, arbitrary string traces, supported non-class URIs and absent source metadata; compare source only when comparable evidence exists. Standard validation remains recommended and optional. |

The active integration dependency is 16/23/33 → 25, not 24/29–32 → 25. Reporting
retains 03, the automated part of 10, narrowed 20 and completed 28 as prerequisites;
human host acceptance remains at final integration. Then 05/25 → 26 → 27.
Deferred collector and partial-report work must not re-enter this critical path.
After 26 and completion of functional feedback, resume 29–32 as post-functional
cleanup; do not restore the old 29–32 → 25 blocking edges.

Required acceptance still includes combined parallel/reporting and relevant
serial/safety regressions, real command outcomes, existing packaged-runtime checks,
IntelliJ Run/Debug/navigation/selection/Stop and comparable user-workload assessment with
the supplied 12-target/20-cap reference profile plus at least one different valid
application-selected fixed profile. The exact 12/20 values are not a product limit;
retain structural profile validation and derive admission from the actual pool.
Broad benchmark matrices are deferred; existing automated complexity/wake-work
checks remain. No speedup is claimed.

Native compatibility acceptance also covers automatic class traces, optional
addenda, arbitrary `trace(String)` text, supported non-class URIs, absent source
metadata and duplicate traces when optional model validation was not invoked.
Explicit standard validation must continue to report duplicate traces. The
production correction is limited to existing identity validation: no metadata
rewriting, synthetic source, second identity subsystem or validation coupling.
The current consumer-wide addenda conversion is a temporary workaround, not an
adoption requirement. Ticket 33 owns this correction and joins focused ticket-25
acceptance; its publication does not mark any production or test work complete.

**Existing mutation gates remain:** API 100/100, report-core 90/90, core 94/95,
Jupiter 82/97 mutation/line coverage. Current valid results are required; no
threshold reduction or silent exclusion is authorized. Any necessary discovery
correction requires separate justification. Deferral of test reductions postpones
their before/after reduction comparisons to cleanup, not these gates. Reporting
work can proceed independently of mutation repair and the deferred cleanup phase.

The core threshold failure and Jupiter's 38 unmutated failures were observed at
historical endpoint 15177819, not freshly reproduced at the reconciliation endpoint
0c2ba7fe. The retained logs were checked read-only: Jupiter names tracked test
fixtures, not scratch documents. These results are distinct from the 22 ignored/
local-document failures already accepted as non-blocking, and do not by themselves
prove production defects. The four application setup errors and unavailable
host/platform/workload evidence remain recorded, not converted into passes.

Local spec, plan/analysis, design map and affected implementation tickets now carry
this amendment. Original checklists/results remain historical; deferred work is
not marked complete. No production/test changes, test runs or
workload enablement are performed by this update.

## Documentation follow-up — 2026-09-16

The user directed that failures in Git-ignored scratch artifacts are non-blocking
for proceeding when library tests pass. A fresh unchanged-scope Link/Property/SysOut
run reproduced the 22 local-input failures across 963 cases: 13 link failures,
eight probe console-use failures and one historical property reference. No library
documentation repair is needed for these failures; scratch evidence, imported
skills, scanner scope and assertions remain unchanged. The earlier committed status
links were already repaired in `b01a51b6`.

All library modules passed in the full reactor recorded below, and their source
has not changed since that run. The four example-application setup errors remain
separate from library tests. This disposition does not turn the full reactor into
a pass, resolve the core/Jupiter mutation-baseline failures, or waive the remaining
report-reuse, manual-host and workload acceptance findings.
The fresh reproduction log is C:/Data/Code/flow-doc-followup-red.log.

## Ticket 25 native-progress resolution — 2026-09-16

The intermittent two-worker `busy-chain` failure is now diagnosed and resolved.
An amplified unchanged-owner probe stopped with 79 descriptions issued but only 76
terminal: three genuine dynamic children had registered native IDs but had never
started, no Flow body/resource operation was active, the factory worker was waiting
in `FlowAdmission.next()`, and the second native worker was idle. The same failure
remained after externally releasing the test's deliberately held first body, ruling
out fixture-release circularity. Jupiter had queued registered children on the
still-enumerating ForkJoin worker without guaranteeing that another worker would
steal them before the factory stream requested a completion-dependent successor.

`FlowAdmission.next()` now offers adapters one cooperative-progress action only
after ordinary polling finds no admissible Flow and before the factory waits. It
retries readiness only when that action changes admission state, preserving the
existing unchanged-wake/resource-attempt bound. The Jupiter owner uses public JDK
ForkJoin extension hooks to execute one task from the factory worker's local queue
only when that queue has at least one full pool-width of backlog. This keeps genuine
Jupiter execution, IDs, listeners and outcomes; it adds no body pool, synthetic test,
replay or healthy-run timeout. A broader `helpQuiesce()` experiment was rejected
because it could enter deliberately held native fixtures before their controllers
released them.

The original 80-flow independent/whole-chain target-two regression now repeats 20
times in the tracked suite. The isolated diagnostic ran both shapes 100 times
(16,000 genuine children) without a stall. Final affected-module validation passed
all **320 assert-core** cases (two existing skips) and **300 assert-junit5** cases,
with zero failures/errors. The temporary two-second state probe and sandbox-only
fixture changes are not production changes. The report-reuse resolution below and
the remaining packaged-runtime, mutation, host and workload acceptance
stay separate.

## Ticket 19 final reactor — 2026-09-16

Implementation slices are committed on `parallel_execution_controller`:
`84e451ab` (latest-link prose), `319323bc` (native query), `cfa468bc` (cooperative
fixture hooks), and `3eca5e46` (monotonic owner budget and review fixes). The arriving
`.gitignore` and repeated `ServedIndexTest` edits remain unchanged and uncommitted.

The requested whole-repository `mvn -B -nsu -fae ... test` ran once, with both test
skip flags and failure-ignore false. It **FAILED**, exit 1 after 11m56s. All 180
reported suites were cross-checked against fresh XML testcase elements and count
attributes: **3,196 cases, 3,170 passed, 22 failures, four errors, zero test skips**.
All assertion modules and report-core passed. Reactor-level `app-itest` was skipped
because prerequisite application modules failed; it is not included as executed
coverage or confused with a testcase skip.

- Four application setup errors (`app-web-ui`, `app-ui`, `app-core`, `app-queue`)
  follow multicast service-discovery receive timeouts. Mock listeners and Jetty
  started, but required service advertisements were not received. This happens
  before their Flow factories construct a runner, not in stop-budget execution.
  The exact routing/interface/filtering cause is **not diagnosed**; no network
  policy, timeout, dependency or discovery behavior was changed to force a pass.
- Documentation has 22 failures: 15 in local scratch probes/documents, six in local
  agent-skill documents, and one obsolete parallelism-option reference in the local
  analysis document. The filesystem scanner does not consult Git ignore rules;
  these inputs are not tracked by Git. CodeLink/Snippet passed; no module-source
  console-use failure was reported. No scanner/exclusion weakening or deletion of
  local evidence was applied. A clean tracked-source documentation gate is separate,
  not a replacement claim that this working-tree reactor passed.

Full output is retained in C:/Data/Code/flow-cancel19-final-reactor.log. Its reports
were frozen before any rerun in the external flow-amendments-20260916 archive, and
audit-final-reactor.ps1 verified the log/XML totals and no-skip/no-ignore properties.
The current committed artifacts were then installed with **436/436** focused cases
passing. The unchanged packaged-consumer script passed all four JUnit 5.10/Platform
1.10 and JUnit 6.0.3 serial/parallel points: two tests each, zero failures/errors/skips,
identical Flow artifact hashes, effective dependency versions and actual class-load
checks. This refresh includes hooks and budget; it is compatibility evidence, not
new host or exhaustive cancellation-feature coverage. Logs are
flow-cancel19-final-install and flow-cancel19-final-consumer under C:/Data/Code.

A separate tracked-source export exposed historical status links into ignored
scratch documentation. The contract link now targets the tracked reporting README,
and the local ticket remains a plain reference; the scanner itself is unchanged.
The initial clean-source gate recorded 744/745 passed and one broken-link failure.
After correction, **745/745** passed: 50 CodeLink, 50 Link, 50 Property, 50 Snippet
and 545 SysOut cases, zero failures/errors/skips, with upstream compilation. The
export matches `3eca5e46` under Git's line-ending normalization apart from this
status-document update; all 39 copied real report assets were byte-identical.
This tracked-input gate does not repair or pass the earlier working-tree scan.

The current four-point consumer evidence contains 25 matching Flow JAR/POM hashes.
Core JAR SHA-256: `7c9ed5932ceb482dd4dde2e4522afa5a94615481e7a070d411a1f4dce83a91bc`;
Jupiter adapter: `666748b13d67873505269318907ee2d0745ef35fd89553139b743a6b80e543bc`.
At that checkpoint the report-reuse finding, baseline two-worker native stall,
multicast discovery, mutation blockers and manual/workload gates remained OPEN.
Further simplification requires passing fresh mutation baselines; changing native
helper discovery scope requires an explicit decision, not a silent exclusion change.

## Monotonic owner stop/drain budget — 2026-09-16

Ticket 19's remaining budget implementation adds `FlowExecution.stopBudget(Duration)`
before preparation, with a finite positive **30-second default** and nanosecond
precision. Both prepared owners share a passive timing policy but retain their own
existing monitors and lifecycle predicates. The first owner-observed Stop starts
monotonic elapsed timing before cancellation effects; repeats never reset it.
Healthy execution does not read the budget clock or receive a grace/total-run timeout.

Only reachable close/backstop callers wait for independently drainable work, using
the remaining budget and real event wakeups. Native/resource callbacks stay
non-waiting. Expiry preserves unsafe ownership and records bounded immutable
`ExecutionStatus.stopBudgetMiss()` evidence, including original cause and body,
native/handoff, operation, callback, cleanup and owner counts. Evidence describes
the observation, not reconstructed state at an unseen deadline. Serial native
counts remain unavailable. Final grant release notifies its owner outside locks;
original stream cleanup and runner completion precede final parallel admission
release/status sealing. Late proof cannot erase a missed budget; timely completion
prevents a later phantom miss. Interruption is restored and is not called expiry.

Independent review found and corrected two close races: receipt callbacks could
wait for their own pre-handoff return, and concurrent close could bypass registered
cleanup between final capacity proof and owner notification. A narrow receipt guard
and pending-cleanup wait preserve real evidence without a blanket factory-thread
exemption. Review also corrected two test fault paths so a deliberately throwing
clock or failed Launcher drainage cannot strand shared ownership or a closer thread.
Runtime negative controls verified these fixes, including primary/suppressed failure
preservation and fresh resource reuse. Temporary injections were removed.

After fixes, focused upstream validation passed **436/436 cases across 16 classes**,
zero failures/errors/skips, with fresh XML/source matching. This includes 13 core
budget and 18 adapter budget cases, controlled clocks, actual timeout/notification
parking, configuration/default/boundary checks, reentry and late proof. Zulu 17.0.19
actual compilation and the existing formatter passed with skips/failure-ignore
false. Append-only flow-cancel19-budget and flow-cancel19-budget-review logs under
C:/Data/Code retain failures, controls and final evidence. Post-fix independent
Standards and Spec review found no remaining actionable findings in the four fixes.

**Historical native-progress finding:** an intermittent two-worker `busy-chain`
stall was reproduced against the unchanged pre-budget production baseline
`cfa468bc`. The later passing binding gate was nonrecurrence, not diagnosis or
resolution. The ticket-25 section above records the subsequent diagnosis and
correction. The separately recorded report-reuse failure remains OPEN.

The final reactor and refreshed current-JAR packaged gate are recorded above.
CPU/wall measurement,
intended-host IntelliJ Run/Debug/navigation/selection/Stop and the user's 6,000-test
workload remain deferred, not waived. These changes bound reachable Flow waits, not
blocked inline callbacks/cleanup, native joins, Launcher/JVM return or remote use.
The failed simplification mutation baselines below remain blockers; no reductions
or coverage-scope/threshold changes have been accepted.

## Cooperative fixture cancellation — 2026-09-16

Ticket 19's second slice adds one optional `ContextDomain.cancellation(handler)`
before fixture sharing/use. Only actual exact operations bind it; admission does
not manufacture operations. Stop marks all owned grants and claims their supported
live callbacks before outside-lock delivery. Operation completion and claimed
callback return are independent obligations: both must drain before whole-grant
release, unsafe-retention removal, continuation or disposal. Return/throw does not
complete an operation or permanently damage the domain. First Stop cause, all-attempt
delivery, late proof and no-restart behavior are preserved.

The supported fixture protocol uses an inert, pre-start-cancellable client handle
and a short correlation mutex around receipt registration plus exact identity-map
publication; lookup uses the same mutex, with client cancellation outside it.
Starting must honor earlier cancellation atomically. No IO, waits, Stop or proof
belong inside production publication. Claimed late callbacks must be harmless.
This is an optional client capability, not a generic remote-cancellation guarantee.

Two core and ten added Launcher cases cover exact identity, independent-owner
isolation, claim/proof races, repeated Stop, callback retention, throwing callbacks,
uncooperative work and late safe reuse. One fixture helper exercises provider-free
serial explicit Stop and actual parallel JUnit 6 token observation, including
publication racing Stop and cancellation before start. Concurrent hook isolation
is directly tested at the reservation seam; existing native peer-ownership tests
remain, not a new concurrent native-hook isolation matrix.

Runtime negative controls failed with missing domain hookup (four cases), unlocked
publication (two) and false operation completion on cancellation return (four), then
passed after restoration. The initial core RED was compilation failure, not runtime
evidence. Final focused upstream validation: **335 cases, zero failures/errors/skips**
(119 admission, two operation cancellation, 20 resource planning, 101 fixture,
seven native call, 76 native resource and ten serial cleanup). Zulu 17.0.19 actual
compilation, existing formatting and 39 real report assets passed with test skips
and failure-ignore false. Append-only flow-cancel19-hooks red/green/format/focused
logs are retained under C:/Data/Code. Independent Standards review requested the
README update supplied with this slice; Spec review found no scoped mismatch.

**Still pending:** the monotonic configurable owner stop/drain budget and deadline
evidence, performance measurement, final whole-repository regression and intended-
host/workload acceptance. Query-slice packaged compatibility below predates these
hook changes. Mutation baseline blockers and the report-reuse failure remain open.

## Optional native cancellation query — 2026-09-16

Ticket 19's first vertical slice attaches the identical optional JUnit 6 native
token to the exact execution call behind `BooleanSupplier`; JUnit-6-only linkage
stays in `FlowLauncherSix`. Admission and pre-body entry observe it outside
bookkeeping locks. Only an attached channel enables 250 ms rechecks in the existing
readiness waiter. A generation-stable wake performs constant-size checks without
validation, graph/resource scans, resolver reruns or new per-flow tasks. Real
completion/resource/Stop notifications remain immediate. The first Stop cause is
preserved, clearing the token cannot reopen entry, and query references clear on
Stop or safe disposal. Native, body, operation and fixture ownership remain distinct.

Controlled core tests cover selections of one and 1,000, unchanged notifications,
token ticks, resource/completion/Stop/interrupt wakes and admission/pre-body races.
Counters record actual resource attempts, unchanged wakes and event wakes; expected
low overhead is **not measured CPU/wall-time acceptance**. Negative controls removing
the timed wait or retrying readiness on unchanged wakes failed as intended. Review
also exposed an unexpected-admission test teardown that could mask its assertion;
Stop followed by actual pure-core enclosing completion now preserves the primary
failure and suppresses cleanup failures. Temporary fault controls verified both
sizes, thread joining, zero owners and fresh exclusive reuse, and were removed.

Real Launcher tests cancel only the waiting run's native token in direct and preview
requests while an independent holder remains live; no fabricated Stop is used to
satisfy the assertion. The native consumption latch establishes arrival at
consumption, **not exact parking inside native readiness**; controlled core tests
separately prove parked timed rechecks. A held native pre-body interceptor also
demonstrates that the query cannot execute inside that blocked callback/native join:
after release it produces zero Flow bodies and one actual native abort, with original
cleanup and ownership drainage. No host Stop or remote cessation guarantee follows.

Validation after review fixes: **202/202** targeted admission/native-call/native-
resource cases, then an upstream install gate of **384/384** across 12 classes, with
zero failures/errors/skips. Zulu 17.0.19/Maven 3.6.3 compiled current sources, checked
the existing formatter and copied 39 real report assets; test skips and failure-ignore
were false. Append-only flow-cancel19 red/green/focused/review logs and the final
flow-cancel19-install log are retained under C:/Data/Code. The installed current JARs
passed the existing parent-free packaged-consumer matrix on JUnit 5.10/Platform 1.10
and JUnit 6.0.3, provider-free serial and parallel: **four points, two tests each,
zero failures/errors/skips**, identical Flow hashes, effective dependency versions
and actual class loading. The matrix verifies compatibility, not query-specific
cancellation; the real Launcher tests above exercise that feature on JUnit 6.

**Still pending:** cooperative operation cancellation hooks, the configurable
monotonic 30-second-default owner stop/drain budget and its deadline evidence,
query-on/off performance measurement, final whole-repository regression, and
intended-host IntelliJ Run/Debug/navigation/selection/Stop plus the user's 6,000-test
workload. This is not full ticket-19 acceptance. The failed simplification mutation
baselines and unresolved report-reuse failure below remain open.

## Review-driven simplification baseline — 2026-09-16

Tickets 29–32 were inspected in parallel against implementation endpoint
`151778194e9dbe589aa5a30b9e8c4fae6446ebc9` and main
`483c5428c2f1fb517c12f7f63200b39066059e17`. Their existing core, Writer and real
Launcher seams remain the test surfaces; main-existing fixtures and assertions
have not been moved or reduced. The two arriving user edits remain separate.

Fresh unchanged-source focused regression: **574 cases, 559 passed, 15 Windows
skips, zero failures/errors**, across 34 classes with upstream compilation and
39 real report assets. The following fresh, non-incremental PIT baselines used
Zulu 17.0.19, Maven 3.6.3, JUnit 6.0.3, PIT 1.25.8/Jupiter plugin 1.2.3, eight
threads and the unchanged module targets, mutators, exclusions and thresholds.
Both test-skip flags and failure-ignore were false. No history profile was used.

| Module | Fresh result | Gate |
| --- | --- | --- |
| API | 164 mutants: 162 killed, two timed out; 263/263 lines | Pass: 100/100 mutation/line |
| report-core | 227 mutants: 210 killed, one timed out, four survived, 12 uncovered; 563/605 lines | Pass: 93/93 against 90/90 |
| assert-core | 1,044 mutants: 902 killed, 19 timed out, 26 survived, 97 uncovered; 1,860/2,085 lines | **Blocked:** 88/89 against 94/95 |
| assert-junit5 | 38 unmutated failures during coverage discovery, including auxiliary profile/receipt/resource/binding factories | **Blocked before mutation:** no 82/97 score obtained |

The core gaps span admission, processing, fixture context and other classes;
passing nested Launcher tests do not substitute for the module's participating
mutation tests. The Jupiter failure list includes helper factories selected
outside their enclosing test harnesses. No exclusions, thresholds, supported
profiles or production behavior were changed to make either baseline pass.
Required baseline failures block simplification acceptance; no candidate test
deletion/consolidation or production refactoring has been applied. In particular,
the proposed reporting consolidation remains an external, unapplied candidate.
The outstanding native accumulated-reporting obligation and unresolved report-reuse
failure below are not waived by these baselines or their later nonrecurrence.

Evidence is frozen outside the checkout under
C:/Data/Code/flow-amendments-20260916 (baseline-core and baseline-all), with source
hashes, mutation identities/statuses, XML/HTML reports and Surefire outputs.
The separate flow-amendments-baseline-tests-20260916,
flow-amendments-baseline-pit-20260916 and
flow-amendments-baseline-jupiter-pit-20260916 logs retain the successful and failed
invocations. Snapshot source inventories include only the two documentation edits
made while PIT ran in addition to the two arriving user edits; Java source remained
at the pinned endpoint throughout those baseline runs. Later ticket-19 work is a
separate feature, not a passing simplification or a repair of these mutation gates.

Ticket 31's documentation correction distinguishes destination-specific latest-link
withdrawal from successful runner publication: publication may replace an old
symlink but preserves its target and ordinary files/directories at latest. Explicit
latest output retains its separate semantics. This corrects prose only; it adds no
foreign-target protection, cross-run ownership or new runtime acceptance.

## Approved report-scope amendment — 2026-09-15

The user approved [single-active-writer reporting](report/report-core/README.md#sequential-replacement-and-completion-publication)
and requested ticket 28, Simplify single-run report ownership.
Cross-run report locks, rejection registries and competing-publication protection
are removal targets, not requirements to restore during other work. Final-only
indexing, direct immediate compatibility, within-run safety and sequential
replacement/latest behavior remain required. Compare code, tests and documentation
with `main` and restore simpler earlier forms only where the removed requirement
was their sole justification. Preserve active work and independently required fixes.

Ticket 28's simplification is implemented and ticket 23 builds run-owned reporting
on its retained contract. The delivered ticket-04 claims and their
test/platform evidence below describe the original implemented contract, not the
current implementation. All other slice evidence and open failures remain unchanged;
removing a requirement is not a diagnosis or resolution of a recorded test failure.

### Ticket 28 implementation evidence

Cross-run claim files, the local registry, ancestor/descendant probes, lock identity
and Windows delete-denial machinery, competing-publication claims and their release
bookkeeping are removed. No replacement coordinator or duplicate rejection was added.
Supported reporting assumes no competing active writers in the same/overlapping
output namespace or publication location; unsupported overlap may delete, mix or
misleadingly publish output. Shared SUT/resource/fixture/Stop coordination is unchanged.

Sequential path/latest handling remains in the existing filesystem abstraction.
Immediate reads, within-writer synchronization/detail protection, detached snapshots,
canonical final links, strict atomic final-only publication and observable failures
remain. Claim-only test scopes were restored to earlier forms; actual completion,
publication callbacks, capture cleanup and runner lifetimes were retained.

The focused Java gate passed **194 cases: 179 passed, 15 platform skips, zero
failures/errors**, with actual Java 17 compilation, both skip flags and failure-ignore
false. Frontend tests passed **72 cases**, with the real 39-entry report asset manifest.
The known repeated-report failure did not recur and remains **OPEN**; the separate
packaged-consumer failure/evidence is not resolved or waived by this reporting gate.
Twelve sequential symlink cases and three existing platform cases were skipped on
Windows. Actual occupied-index publication failure and injected filesystem failures
passed. No POSIX, new host, packaged-consumer or full-reactor acceptance is claimed;
the user reserved the full reactor for final integration.

The [amendment implementation record](report/report-core/AMENDMENT.md) records exact
comparison revisions, changed/restored/retained files, source counts and commands.
The publication-parent regression is repaired: directory setup occurs before the
completion callback, and failures latch without invoking or retrying that action.
Independent current-index review found Standards 0 / Spec 0 scoped defects.
The exact staged source, excluding unfinished Stop/consumer changes, compiled fresh
and passed **837 focused cases: 822 passed, 15 skipped, zero failures/errors**,
including 640 documentation checks. This remains a selected gate, not the full reactor.
Historical claim-delivery evidence below is intentionally retained.

## Delivered code slices

| Tickets | Implementation | Evidence seam |
| --- | --- | --- |
| 01–03 | Serialized direct Writer updates/snapshots; explicit final-only index and atomic close; latched failures; final basis/dependency rename correction using serialized detail evidence | `WriterTest`, `WriterLifecycleTest`, `WriterPublicationTest`, `WriterFinalLinksTest`, `ReaderTest` |
| 04 | Canonical filesystem destination claims; active ancestor/descendant protection; safe failure disposal/reuse; finalization-owned configured latest publication; explicit direct-caller completion | `WriterOwnershipTest`, `ReportingTest`, `ReportTestUtilTest`, existing caller and replay regressions; platform limits below |
| 06 | Shared configuration and processor with invocation-local message/assertion/failure evidence; legacy fluent behavior retained | `AbstractFlocessorTest`, legacy JUnit 4 and Jupiter `MetaTest` |
| 07 | `FlowTest` / model-free `FlowExecution` / `PreparedFlocessor`; frozen preparation and provider-free native serial consumption | `FlowExecutionTest`, `PreparedFlowLifecycleTest` through real Launcher |
| 12 | Identity-visited prerequisite closure after filtering; retained bindings; hard and contracted-chain cycle rejection; valid in-flow bindings | `PrerequisiteSelectionTest`, `OrderTest`, `PreparedSelectionTest` |
| 20 | Successfully begun capture ends/materializes/closes once within invocation before Writer callbacks; capture-only diagnostics; primary-error preservation; merged-source drainage | `CaptureScopeTest`, `LogCaptureTest`, `MergeTest`, `PreparedCaptureTest` |
| 08 (checked tracer) | Same-caller real parallel processing, A-to-B binding with controlled independent C, one readiness waiter, admission before emission, native terminal plus drainage | `FlowParallelBindingTest` through real Launcher |
| 09 (partial) | Early service-loaded public Launcher decorator; exact call/preview ownership; actual pool and metadata guards; optional isolated JUnit 6 overload | `FlowLauncherBridgeTest`, `FlowLauncherSixTest`, `FlowNativeProfileTest`, `FlowNativeCallTest` |
| 10 (automated portion) | Standalone consumer of locally installed Flow POM/BOM/JARs; real model, binding, native overlap, provider-free serial and exact callback UID checks on consumer-selected JUnit stacks | `assert/assert-junit5/src/it/packaged-consumer`; artifact hashes, JVM class-load traces and native summaries |
| 11 | Named rule union/provenance, UNKNOWN global exclusion, shared same-JVM whole-resource-set and execution-slot reservation before native emission; prepared serial/parallel cooperation | `ResourcePlanningTest`, `NativeResourceAdmissionTest`, serial cleanup and actual packaged consumer regressions |
| 13 | Shared-core dependency-ready admission, coherent processing/native accounting, sparse canonical basis visibility, direct successor release and idempotent evidence | `FlowAdmissionTest`, actual serial/native `FlowParallelBindingTest` oracles, resource/queueing and packaged-consumer regressions |
| 14 | Canonical destination and identical-message participant precedence; retained synchronous binding operations, partial effects and order-only eligibility | `DependenciesTest`, `AbstractFlocessorTest`, `FlowAdmissionTest`, real serial/native publication and fault oracles |
| 15 (supported execution slice) | Selected-only chain planning, whole-interval grants, default global exclusion, explicit whole-chain isolation audits and safe native member advancement | Core capacities 1/2/5; real serial/parallel scope, payload, cleanup, stop-gate and native-inline regressions; context/fixture integration remains 17 |
| 16 | Atomic readiness-cohort publication; oldest-ready conflict protection, disjoint bypass, exclusive drain/resume and cancellation wakeups | `ResourcePlanningTest`, `FlowAdmissionTest`, actual separate native pools and serial/parallel cooperation; narrow race qualifications below |
| 17 (supported partial slice) | Explicit actual-fixture domains, shared applied state, additive whole-grant ownership and receipt-based unsafe-use retention across native outer cleanup | `ContextFixtureTest`, `ResourcePlanningTest`, real serial/parallel Launchers and isolated unsafe controls; consumer audits and broader acceptance remain pending |
| 18 (supported Stop/ownership slice) | Irreversible admission Stop, bounded Writer-independent status, exact remaining-operation receipts and evidence-gated late disposal without repairing unsafe fixture state | `FlowAdmissionTest`, `ResourcePlanningTest`, `ContextFixtureTest`, `NativeResourceAdmissionTest`, `FlowNativeCallTest`; native-token/cooperative channels and owner wait budget remain 19 |
| 23 | Run-owned final-only reporting after valid prepared descriptions and safe drainage; enabled empty reports; genuine outcome tags; bounded same-artifact diagnostics; ordinary report faults remain visible and non-fatal | `FlowParallelBindingTest`, `SerialCleanupTest`, `NativeResourceAdmissionTest`, `WriterLifecycleTest`, `WriterPublicationTest`, parsed `Reader` output |

The new caller permits `flow.parallel=true` only through the checked native owner.
Named bulk `resources()`, `exclusive()` and `independent()` declarations are resolved
once after dependency expansion. Unmatched work remains UNKNOWN/global-exclusive,
including against explicitly known-empty work. Capture/replay and non-quiet reporting
remain guarded; contexts, residue, applicators, checkers and autonomous actors require
explicit actual-fixture ownership. Other unchecked parallel surfaces fail closed
before SUT use. These temporary implementation boundaries are not permanent public
support restrictions. There is
no workload-speedup or wall-clock scheduling guarantee.
Prepared quiet reporting initializes once after returned-description validation,
publishes one final index after safe mode-specific completion, emits a bounded
diagnostics companion, and treats ordinary report-only faults as visible non-fatal
diagnostics. Legacy assertion runners retain immediate reporting.

## Ticket 25 focused combined acceptance — 2026-09-16

`NativeResourceAdmissionTest.combinedResourceChainContextAndReportAcceptance`
now drives one real five-flow model through the sole `NativeResourceFixture`
factory under the supplied fixed 12-target/20-cap Launcher profile. A and disjoint
C enter together. D becomes dependency-ready after A but cannot enter while the
AB chain retains A's shared resource; once B finishes, D progresses while C is
still deliberately held. B observes A's canonically published response. The last
flow has no matching resource declaration, reports the explicit UNKNOWN fallback
before native emission, and waits for its selected prerequisites.

The same run uses the actual Jupiter pool and invocation contexts, checks one
fixture-stream close, rejects an index before drainage, then parses one final
five-entry all-PASS report through `Reader` and verifies the atomic index exists.
The existing profile test separately exercises fixed 2/2 as the different valid
application-selected profile; production admission derives capacity from the
current ForkJoin pool rather than a product constant. The focused combined test
passed, and the full assert-junit5 suite passed **301 cases** with zero failures,
errors or skips. Genuine failure/skip and report-fault command semantics remain
covered by the adjacent mixed-outcome and final-publication regressions rather than
being synthesized into this all-passing combined model.

## Blocking work and unpassed gates

### Resolved report-reuse failure — 2026-09-16

One historical focused run at `02e3bb5c` failed
`scopedCompletionAllowsReportReuseAfterRepeatedExecution`: expected
`abc [] SUCCESS`, observed `abc [] ERROR`. Its archived output did not retain the
underlying exception, so later passing repetitions alone were correctly treated as
nonrecurrence. That revision still opened and locked a persistent sibling
`.flow-writer.lock` for every Writer. A controlled same-revision experiment now
locks the stale sidecar between the test's two sequential runner instances. Built
with the historical report module in the same reactor, it deterministically produces
the same first-execution `abc [] ERROR`; the event has a null message, consistent
with the same-JVM `OverlappingFileLockException` from `FileChannel.tryLock()`.

Ticket 28 commit `4a3e8ca0` removed the cross-run claim subsystem under the approved
single-active-writer contract. The tracked regression now repeats the two complete
runner lifecycles while that exact legacy sidecar remains locked. Current code passes,
because the sidecar is outside the report tree and no longer participates in Writer
construction or publication. The complete assert-core suite passes **320 cases**,
with two existing skips and zero failures/errors. This is an old-code red/current-
code green causal test of the removed failure path, not merely another unlocked
reuse rerun. The original unrecorded throwable cannot be reconstructed, but the
claim path that could turn harmless stale lock interference into the observed Flow
ERROR is structurally absent and guarded against regression. This finding is closed.

### 04: destination claims and explicit completion

Both legacy adapters now implement `AutoCloseable`. Integrations retain their
runner and close it in genuine `@AfterAll` / `@AfterClass` teardown after all
children. Completion rejects active processing without waiting, permanently
prevents further processing, closes an existing Writer without lazy initialization,
and keeps close failures observable. Prepared callers gain no public early-close API.

The lifecycle prerequisite approved on 2026-09-15 now supports mandatory Writer
claims. No last-test guess, factory/stream-return finalization, GC release or mode
change was introduced. Missing completion retains ownership. Existing reporting
test callers now use explicit resource scopes without making the test helper's
`execute()` terminal or changing its repeatable execution semantics.

Claim files live outside each replaced tree. Canonical destination acquisition
precedes ancestor probes and a one-time no-follow descendant scan. Pre-open file
identity and post-probe linked-file validation address a delayed descendant whose
sidecar an earlier ancestor removed; native Windows instead requires the public
JDK 17 `NOSHARE_DELETE` option because its provider returns null file keys. Unknown
identity/unsupported locks fail closed. Channel close owns lock release; uncertain
close keeps its failure/local guard. Stale unlocked claim files permit reuse.

One synchronous `Writer.onClose` action runs after successful finalization while
destination and configured publication ownership remain held. The runner passes
its actual `testDir/latest` before initialization, including nested explicit report
names, and advertises only during completion. Existing immediate indexing and
initial browse behavior remain for legacy callers; prepared presentation waits for
successful final publication.
Owned advertisements are withdrawn before replacement. An explicit output named
`latest` is retained as an output access path, not mistaken for an advertisement.

Final integrated checks: **211 cases, 194 passed, 17 skipped, zero failures/errors**
on Java 17.0.19 / JUnit 6.0.3, with both test-skip flags disabled and the real 39
frontend assets. Real process fixtures exercise same-path, nested, publication-parent
and delayed-claim schedules. Windows delete/rename denial passed. Unlink-capable,
symlink and physical-parent-alias cases remain conditionally skipped here; POSIX,
network filesystems and uncertain-close fault injection are not runtime-verified.
No noncooperating-writer, alias-mutation, crash-durability or recovery guarantee is
made. Standards and specification review defects were corrected; final follow-up
found no residual scoped source defect.

Final clean-export documentation checks passed all **626 cases** (48 source links,
48 snippets, 530 console-use checks); the generated masking-example anchor was
refreshed. These are not a new full-reactor or cross-platform acceptance result.

The original Swing failures also reproduced unchanged on the committed baseline.
Native mouse events were blocked by a locked Windows session. After the user
unlocked it, the unchanged six-case `FlowPanelTest` passed in full. No substitute
button invocation, sleep, relaxed assertion or production GUI fix was introduced.

Legacy-slice validation: 110 focused assertion cases passed; changed execution
sources also compiled against JUnit 5.10 / Platform 1.10 and passed 62 core/legacy
cases. Post-review focused checks passed 71 assertion cases. Actual Core Launcher
execution found and started 15 leaves on each of two same-JVM runs: 14 successes,
one expected out-of-SUT HISTOGRAM abort, no test/container failures; both runners
closed after execution. This exposed and fixed early per-class service capture
and reuse of terminally stopped mock dependencies. JUnit 4 examples likewise
create a fresh runner during parameter discovery. Standards/simplicity and spec
follow-up reviews found no remaining scoped defects. Generated-document and
console-use checks passed all 623 cases in the clean export. Zero-case dynamic
Surefire reports were not counted as runtime acceptance. Historical full-reactor
results below predate this slice; the next full run is reserved for final integration.

### Native execution, hosts and workload

- 08's restricted real-Flow tracer now covers the early call association, actual
  A→B binding with controlled independent native overlap, admission grants,
  callback-context restoration, basic entry-stop gating and normal owned drainage.
  Ticket 18 adds explicit exceptional Stop and exact late-drain proof below;
  bounded cancellation channels remain 19 and nothing is force-finalized.
- 09's principal ownership/selection/pool guards are implemented; its complete
  adversarial acceptance is still partial. 10's packaged-consumer portion is now
  exercised, but its intended-IDE Run/Debug/navigation/selection/Stop gate has
  **not passed**. Ticket 18 exercises safely disposable model/native-table cleanup
  at the supported lifecycle seam; whole-consumer retained-reference acceptance
  remains 25, and the packaged fixture's repeat/re-entry guards do not prove it.
- 05 needs the actual 6,000–7,000-flow consuming workload and resource-owner input.
  That environment was not supplied; the reported 52/25-minute observations are
  not new measurements or a diagnosed serial-tail cause.
- 17 has only the supported actual-fixture slice below, not complete acceptance.
  18 has only the supported Stop/ownership slice below; 19 and 21–27 are not
  delivered. Bounded cancellation, integrated late-safe release,
  correlated capture/cutoff, truthful stopped-run final artifacts, consumer-owner
  audits, integrated compatibility, performance assessment and rollout remain open.

The user was unavailable for the workload/host questions and instructed autonomous
work. Missing human evidence is recorded as missing, not substituted with nested
Launcher tests. No workload, remote publishing or rollout action was performed.

### Packaged consumer evidence

The standalone consumer has no Flow parent or direct JUnit dependencies. Consumer
JUnit BOM management precedes the Flow BOM; actual loaded versions and installed
JAR code sources are checked. The same 25 Flow JAR/POM hashes passed Java 17.0.19
with Jupiter/Platform **5.10.0/1.10.0** and **6.0.3/6.0.3**, serial and parallel:
eight outer tests assert **246 real native starts** and **238 actual callback UID
comparisons**. Deliberate failure/abort controls are counted separately from test
failures. Both serial JVM traces exclude the parallel provider/owner/helper; both
baseline traces exclude the optional JUnit 6 helper. This is not a version range,
split-classloader, remote-release, IDE or general cancellation claim.

Review removed a typed internal engine-class reference in favor of public engine
service discovery and strengthened the actual-body UID oracle. Factory-UID and
wrong-leaf-UID mutations each failed, even with external failure-ignore enabled;
the script disables failure-ignore and requires a fresh two-test, zero-failure XML
report. Thirty-four repeats are only repeatable-execution/idempotent-close/re-entry
evidence, not retained-reference disposal proof. Final follow-up found no residual
scoped source defect. All **629 clean-export documentation checks** passed, and
intentional provenance output uses the existing exact-file/line allowlist.

Commands, artifact provenance, exact outcomes and remaining checks are retained in
[the consumer gate](assert/assert-junit5/src/it/packaged-consumer/README.md). The
original and reviewed evidence are archived separately; no historical IDE fixture
was altered. Human-only IntelliJ and 6,000-flow checks remain deferred until the end.

## Validation record

### Explicit Stop and remaining ownership (18, supported slice)

Public `FlowExecution.stop(cause)` closes admission irreversibly, wakes the existing
factory waiter and withdraws pending requests without cancelling peer runs.
`status()` returns bounded, immutable `ExecutionStatus` evidence independent of
Writer success: **ACTIVE**, **STOPPING**, **QUIESCENT**, the first cause, latched
incompleteness and separate processing/native/remaining-owner counts. At most five
identity prefixes are retained; serial native terminals are `-1`, not a fabricated
listener mirror. QUIESCENT means owned use and required cleanup ended, not success.
Late proof never erases the first cause or incomplete evidence or restarts admission.
These are bounded diagnostics, **not an owner wait budget**.

Queued wrappers take their real native abort path after observed Stop. Actual
registered-child skips and actual enclosing-scope terminal/ancestor evidence retire
only proven-unused ownership; no descendant callback, native outcome or History
result is invented for never-invoked work. Ordinary safely completed errors preserve
existing dependency behavior and unrelated progress rather than blanket fail-fast.
The real cancellation-driven child-skip control has one registered/skipped leaf,
zero native starts/terminals and zero History entries, followed by safe reuse.
Ancestor routing uses actual plan parents and enclosing terminals; there is no
fabricated post-attachment ancestor-skip control. Not every registration/terminal
race is deterministically paused; those narrow guards remain source reasoning.

Before background use escapes, its existing owner must register an exact
`receipt.operation()` or `Grant.operation()` and call that operation's `complete()`
only after actual use and required cleanup end. Future cancellation, timeout,
interruption, request return and native terminal alone are not proof. Multiple
operations retain the whole grant and whole-chain continuation waits for all of
them. After Stop, conflicting users get a visible original-cause pre-use diagnostic.
Safe late proof releases only remaining ownership; permanent
`ContextDomain.uncertain(cause)` is irreparable and cannot be cleared by a receipt.
Unregistered background work is not made safe or audited by this API.

Disposal waits for actual body/native/operation drainage and original cleanup
before safe quiescence drops model, configuration, History and native-table
references. Unsafe owners are retained, never force-released for leak checks.
Genuine incompleteness remains visible through the live factory or real class-store
exceptional backstop. Actual store teardown retires its removal callback, so late
proof does not query a closed store; a closed-store exception is not the backstop.
Stopped reporting is not finalized merely to discard references.

Eight T20 controls cover every serial/parallel owner/competitor pair with prerequisite
and whole-chain work: cancellation of a real future cannot free A for C, B never
enters after Stop, and only controlled operation completion permits disposal and
fresh C use. The fake worker is released and joined in teardown. Permanent unsafe
fixture controls remain isolated child probes, not recovered grants or added native
main-JVM counts. Current Maven core/adapter tests use JUnit 6.0.3; they are not new
JUnit 5.10 core source-compatibility proof.

The supplied independent full reviews found Standards 0 and two Spec P1 findings:
missing final drainage after committed effects and failure to attempt every Stop
notification. Both were fixed with outside-lock all-attempt effects and retained
primary/suppressed failures. A subsequent public premature-release P2 finding was
fixed: no-issued-leaf state is not terminal proof, pending priority must be withdrawn,
and reentrant disposal is blocked while the committed Stop batch remains in flight.
The parent read the actual release source; supplied final targeted follow-ups report
Standards 0 / Spec 0. This validation pass makes no new independent-review claim.

Historical 504-case passes include a repeat after the earlier two-line stop/disposal
guard. The later 513-case broader pass preceded the final nine release-guard cases;
the subsequent 306-case narrow pass included those nine. These are prior-source
gates, not substitutes for the final broader and repeated counts below. Failed and
incomplete attempts remain evidence, not passing counts.

The final 26-class broader gate passed **522 cases: 520 passed, two existing Windows
symlink skips, zero failures/errors** (10 API, 27 message, 226 core, 259 adapter).
Three repeats of the exact nine-class selector each passed **350 cases** (10 API,
125 core, 215 adapter), with zero failures/errors/skips. The install gate passed
the same 350 cases. All five gates recorded exit 0 with Azul Java 17.0.19 / JUnit
6.0.3, both skip flags and failure-ignore false, `surefire.failIfNoSpecifiedTests=false`
and the 39 real frontend assets; no full suite was run.
The known report-reuse failure did not recur; its cause remains **OPEN**, unchanged
and not fixed by these passes.

**Original packaged-consumer failure (retained evidence):** the unchanged consumer
on the then-installed ticket-18 binaries passed the 5.10.0/1.10.0 serial point (two outer tests),
then failed its parallel point (two outer tests, one failure, zero errors/skips).
C failed with `controlled overlap timed out`; the outer success-count assertion
reported `expected: <3> but was: <2>`. The script recorded exit 1 and stopped before
either 6.0.3 point. The cause was undiagnosed at that gate; validation stopped
without a retry, source/fixture change, staging or clean-export docs gate.
Fresh failed XML, logs, class traces and current installed artifact hashes/binaries
are preserved separately from stale matrix leftovers. This is not a passed four-point
matrix or ticket-18 acceptance; the existing fixture is not exhaustive Stop coverage.

Subsequent diagnosis established a fixture circular wait: C awaited the original
description source's close, while ticket 18 correctly defers that cleanup until C's
native terminal. An observation-only failing run recorded the distinct live native
stream closing while C remained active, followed by C's timeout and only then source
cleanup. The corrected public factory interceptor observes live-stream close;
separate assertions still require original-source cleanup after C's native finish
and exactly one close of each stream. No production lifecycle or timeout was weakened.
The archived correction passed all four runtime/mode points against the same installed
ticket-18 binaries. Those artifacts predate amendment 28, so they do not establish
acceptance of the current combined tree. Original reds and the diagnosis remain under
`C:/Data/Code/flow-stop18-packaged-diagnosis/`; the report-reuse failure above remains open.

Current combined-source verification on 2026-09-16 passed the same 26-class gate
and local install: **522 cases, 520 passed, two existing Windows skips, zero
failures/errors**. The unchanged consumer verifier then passed all four points on
the freshly installed artifacts, including amendment 28's claim-free report JAR:
eight outer tests, identical 25-file artifact hashes, and the existing native
count/UID and class-loading checks. Both skip flags and failure-ignore were false
on Java 17.0.19. This diagnoses and resolves the specific consumer-oracle failure,
not the independent report-reuse failure or the wider acceptance programme.
Independent current-source reviews found Standards 0 / Spec 0 scoped defects.
Logs and fresh consumer evidence are archived separately under
`C:/Data/Code/flow-stop18-commit-20260916/`; historical failures remain unchanged.

The exact staged tree `7af13d7f241a4f1d0128549f9ff7f4f0fcd28bad` then compiled fresh
in `C:/Data/Code/flow-stop18-staged-mY5B4l` and passed **1,163 cases: 1,161 passed,
two existing platform skips, zero failures/errors**. This combines the 522-case
selector with 641 documentation checks (50 links, 50 snippets, 541 console-use
checks) through `-pl doc -am test`, with both skip flags and failure-ignore false.
All 813 indexed source/document files matched the export after line-ending
normalization; only final evidence prose changed afterwards. This selected gate
is not the full suite, IDE or workload acceptance.

At this historical ticket-18 checkpoint, ticket 19 still needed native-token query
propagation, cooperative hooks, the accepted
250 ms check cadence and reachable owner drain budget. No uniform Launcher, remote,
IDE or hard-kill cleanup bound is claimed. Capture/report/replay remain guarded;
ticket 23's then-unfinished accumulated native REPORT T30 and final-only obligations
were not waived. Ticket 17's real consumer DB/browser/background-owner audits and broader
artifact/log/retained-reference integration remain 25. Intended IntelliJ checks,
the manual 6,000-flow workload and the full suite remain deferred until the end.

### Actual-fixture context slice (17, partial)

One explicit `ContextDomain` follows the physical fixture lifetime, not a runner,
applicator wrapper, actor label or worker. Its authoritative applied-state map is
shared across runners and fresh wrappers, including transitions to empty context
that remove prior state. State changes only after successful actions. The implicit
domain identity and additional physical-resource keys augment every flow's whole
reservation before chain contraction; UNKNOWN remains conservative. The existing
owner explicitly creates, resets and closes its fixture under ownership. Runner
completion never resets it, including between chain members. Affinity rejects
parallel mode before fixture creation; serial actions verify the actual owner thread.

An exact whole-grant receipt is published before native emission, outside locks.
Outer interceptors can report uncertainty from another diagnostic thread or after
borrowed scope close while that exact grant remains owned. Original-run notification
stops continuation even without a pending competitor; native success does not erase
unsafe outer-cleanup evidence. Stale released receipts cannot poison a newer run.
Callback failure retires only proven-unemitted use, preserves suppressed cleanup
and returns only safely unused ownership. Borrowed `Use` never releases the external
grant and rejects actions after its release. Scope/thread drainage failures retain
ownership without reclassifying a safely completed Flow result as `ERROR`; ordinary
safely completed assertion/outer failures permit reuse. No recovery/reset shortcut,
fixture pool, worker lane or inferred ownership was added.

The 67 context cases exercise 71 main-JVM Launchers: 69 native starts, 57 successes,
four intentional failures, eight aborts and 61 body entries, plus two no-assertion
callbacks. Twenty-six isolated unsafe child probes are counted separately, not added
to those native totals. The inherited 72 resource cases remain 206 Launchers,
577 starts, 547 successes, 20 intentional failures, ten aborts and 557 body entries.
The exact narrow cross-thread admission-to-scope-entry race is source-guarded, not
deterministically paused; actual native scope-failure controls prove retention at
the public seam. Full cancellation/drain/late-safe proof remains tickets 18–19.

Actual legacy examples on the initial ticket-17 binaries found/started Core 15
(14 successes, one expected abort), Queue 15 (six successes, nine expected aborts)
and Store seven (four successes, three expected aborts), with no failures. All five
provoked-chain leaves passed. Later changes did not alter example code; these are
historical example proofs, not new runs or migrations of their fixture owners.
Real DB/browser owners, background quiescence and legacy cooperation still need
consumer audits. At that checkpoint capture/report/replay remained guarded;
distinct real parallel artifact/log attribution and retained-reference checks
remained integration ticket 25. Native accumulated-reporting T30 and final-only
activation were still ticket 23 work.
IntelliJ, the manual 6,000-flow workload and the full suite remain pending.

The parent independently reviewed the source. Final Standards/Spec reviews had
zero hard findings and one low-priority duplicated-helper observation; the parent
deduplicated only that helper, preserving the explicit suppressed-root oracle,
then formatted the one changed file. The earlier formatter-failed broader attempt
is retained, not counted as a pass. The second broader gate completed **472 cases:
470 passed, two existing Windows symlink skips, zero failures/errors** (10 API,
27 message, 201 core, 234 adapter), with `BUILD SUCCESS`. The known report-reuse
failure did not recur; its cause remains **OPEN**, not fixed by these passes.

Three repeats of the exact eight-class selector each passed **294 cases** (10 API,
100 core, 184 adapter), with zero failures/errors/skips and recorded Maven exit 0.
The earlier estimate of 344 was not used: the requested selector was unchanged.
These gates used Azul Java 17.0.19 / JUnit 6.0.3, both skip flags and failure-ignore
disabled, `surefire.failIfNoSpecifiedTests=false`, and the 39 real frontend assets.
The current binaries were then installed with the same **294 passing cases** and
Maven exit 0. The unchanged four-point packaged consumer passed on those same
25 Flow JAR/POM files with 5.10.0/1.10.0 and 6.0.3/6.0.3, serial and parallel:
eight outer passes, 246 native starts and 238 callback-UID comparisons. Its fresh
XML, class-load traces and hash lists are retained separately from older archives.
No artifact dependencies were changed and no additional ticket-17 context scenarios
were added to that compatibility fixture. Scoped Standards/Spec findings are zero
after the parent's helper deduplication; broader acceptance gaps above remain open.
All 639 clean-export checks passed: 49 links, 49 snippets and 541 console-use checks.
Before this three-line note, all 15 staged files and 49 tracked Markdown matched the tested export (normalized EOL).
Evidence is frozen separately in `C:/Data/Code/flow-context17-packaged-20260915/`, including retained failures.

### Fair ready-admission slice

Atomic insertion of a canonical ready cohort into the shared pending set is the
scope-wide readiness-publication point. Roots publish during preparation; native
completion publishes newly ready owners before factory visibility and parent-grant
release. Retries and capacity blockage do not reset age. Every older conflicting
request protects its entire set without partial holdings; disjoint requests may
pass. Ready exclusive/UNKNOWN requests gate newer work, including EMPTY, while
dependency-blocked exclusives have no priority that could block their prerequisites.
Chain continuation keeps existing ownership rather than registering fresh priority.

Pending withdrawal wakes affected coordinators outside both locks. Completion keeps
direct successor counters; pending conflict/retry/notification scans remain separate
resource costs, not a whole-workload linearity claim. Core capacities 1/2/5 and actual
separate native pools cover older-root/new-cheaper-child order, cross-runner conflicts,
disjoint progress, exclusive drainage/resumption and stop withdrawal.

Review corrected test paths that could leak pending priority or unexpected probe
grants when an assertion failed. Sixteen deliberate negative-control failures each
proved same-JVM exclusive reuse before rethrowing the primary failure; these are not
passing tests. Final standards/spec follow-up found zero actionable source findings,
separate from the open report-reuse validation item above.

The broader gate completed **399 cases: 397 passed, two existing Windows symlink
skips, zero failures/errors** (10 API, 27 message, 195 core, 167 adapter). Three
271-test repeats and another 271-test install gate passed without failures/skips.
All four existing packaged-consumer configurations passed on the new same binaries.
Java 17.0.19, both skip flags and failure-ignore disabled, and 39 real frontend assets
were used. Exact stop-between-publication/commit, tentative acquisition/commit and
factory wait-transition pauses remain source-guard reasoning rather than controlled
runtime proof. Broader chain/fairness integration, hosts and full acceptance remain 25.
All 637 clean-export documentation checks passed, with tracked Markdown and staged
files matching after line-ending normalization. The separate fairness16 archive
retains 56 evidence files, including the unresolved report investigation and failures.

### Uninterrupted chain slice

One frozen selected-chain plan supplies both serial and parallel reservations.
Default chains exclude all cooperating outside work, including known-empty flows.
Only a named whole-chain `isolatedChains()` audit permits outside overlap; member
UNKNOWN/exclusive policy still dominates. No extra members are selected, and no
unchained exclusive flow promotes its dependency component. Combined hard, basis,
publication and shared-message constraints survive contraction and cycle validation.
An unchained identity/chain-name collision that concealed a contradiction was fixed.

One grant spans members and native outer cleanup. Parallel continuation requires
processing drainage and actual predecessor native completion; serial return proof
remains the next actual SAME_THREAD advance. Stop returns proven-unused intervals
but retains uncertain ownership. No native join, level barrier or body pool is added.
The member `requirements()` view remains unchanged; `reservation()` exposes effective
whole-chain requirements and immutable, separately classified isolation audit names.
Independent review found and corrected the missing diagnostic provenance; both final
review axes report zero actionable findings.

The broader gate completed **374 cases: 372 passed, two existing Windows symlink
skips, zero failures/errors** (10 API, 27 message, 180 core, 157 adapter). Three
246-test repeats and another 246-test install gate passed without failures or skips.
All four existing packaged-consumer points passed on the new binaries. Tests used
Java 17.0.19, both skip flags and failure-ignore disabled, and 39 real frontend assets.
Actual chain controls include isolated mixed/parallel scopes with B:A and D:C payloads,
independent continuation, 80 busy-target-two leaves and retained History outcomes.
All 637 clean-export documentation checks passed. Every tracked Markdown and staged
file matched the tested export after line-ending normalization; the separate chain15
archive retains 42 evidence files and fingerprints, without changing earlier archives.

This does not discharge context/residue/fixture transitions and no-reset ownership
in 17, fair ready-exclusive draining in 16, full cancellation in 18–19, reporting in
23, or actual IDE display/Stop acceptance. The tentative serial acquire/stop race is
source-guarded, not deterministically paused. No new full-suite or workload claim.

### Canonical publication slice

Preparation adds adjacent canonical precedence within destination-flow and identical
message participant groups. Readers participate as well as writers, preventing a
destination from reading a message while another destination's producer mutates it.
Overlapping groups do not serialize an entire connected component. These edges are
order-only: they do not add dependencies or change History eligibility. The existing
publisher, processor and History are unchanged; every binding still runs synchronously
on its producer thread before masking/comparison, without rollback or replay.
Hidden shared backing state still requires the resource audit.

T29 uses actual serial/native publication for same/different fields, parent-child
paths, different messages, shared-message readers/writers and multiple destinations.
In the latter, A writes X/Y, B writes X and C writes Y: independent D progresses
while A is held, B/C can overlap afterward, all eight bindings run, and final payloads
are X=B/Y=C. Core planning also runs at capacities 1/2/5. Primary-preserving teardown
was verified with a negative control that disabled publication precedence; its
assertion remained primary and later drainage failures were suppressed.

T30 covers peer/get/mutation/set/set-after faults in the API, core immediate and
accumulated modes, and native immediate execution. Literal partial writes, later
message behavior, operation counts, producer-thread/cause identity and unchanged
order-only eligibility are checked. Ticket 23 now covers retained native accumulated
reporting through successful and mixed outcomes, including partial effects and
safely finalized reports through real Launcher execution. The complete stage/mode
mutation matrix, intra-flow/masking behavior and broader native attribution remain
final ticket-25 acceptance; core reporting tests do not replace that obligation.

The broader Java 17 / JUnit 6.0.3 gate completed **312 cases: 310 passed, two existing
Windows symlink skips, zero failures/errors** (10 API, 27 message, 150 core, 125 adapter).
Three 162-test repeats and a further 162-test install gate passed without skips or
failures. Both skip flags and failure-ignore were disabled, with 39 real frontend
assets. Both independent review axes found zero actionable issues after corrections.
All four same-binary packaged-consumer configurations passed on 5.10/1.10 and 6.0.3,
serial and parallel. No new full-suite, manual-host or workload acceptance is claimed.
All 636 clean-export documentation checks passed; every tracked Markdown and staged
source matched that tested export after line-ending normalization. The separate
publication14 archive retains 36 evidence files and their fingerprints.

### Dependency-ready admission slice

`FlowAdmission` now owns ready work, whole grants, direct successor counters and
native/processing records in assertion-core. Actual processing shares its History
monitor; the Jupiter adapter retains native identity/source and actual-pool checks,
not another scheduler. Resource operations, model traversal, callbacks and SUT work
remain outside the short-held bookkeeping monitor. Admission is committed before
emission, including buffered and inline native execution. Only the factory waits.

An independent review exposed two basis-planning defects: later-canonical ancestors
could change an earlier derived flow's eligibility, and retaining all selected
ancestors produced quadratic readiness state. Both were reproduced before correction.
The iterative, identity-cached basis forest now inserts at most two canonical-forward
edges per selected flow; related flows preserve serial visibility in either rank
direction without selecting absent bases or adding sibling edges. Hard and basis
scheduling pairs are deduplicated without dropping bindings. At 7,000 nodes the
tested deep, inverted and shared-absent-path shapes retained 6,999, 13,975 and 6,999
basis edges respectively. Actual admission/completion visits match retained edges.
This measures readiness operations, not History traversal, resource reconsideration,
payload work or whole-workload performance.

Native regressions exercise independent successor progress, actual dependent aborts,
selected/absent/inverted bases, suppression/stateless policies, ordinary timeout,
synchronous timeout-as-assertion, fatal faults and duplicate/conflicting evidence.
The timeout assertion retains the original native failure, permits genuine dependent
SUT entry and suppresses derived work, matching literal serial outcomes. The 12/20
queueing control accepts native inline execution before the 24-grant upper bound;
the bound is not a promised queue depth. Teardown preserves the primary failure and
attaches any cleanup failure rather than replacing it.

Final Java 17 / JUnit 6.0.3 validation passed **219 focused tests** (108 core, 111
adapter) and **three 88-test repeats**, with zero failures/errors/skips. Interrupted
and earlier failing attempts remain recorded and are not counted as passes. Both
test-skip flags and failure-ignore were disabled, with all 39 real frontend assets.
Independent standards and spec follow-ups found no actionable issue. No dedicated
injected dual-cleanup-failure or basis-cycle test is claimed.

An additional 88-test install gate and all four same-binary packaged-consumer points
passed after the extraction: JUnit 5.10/Platform 1.10 and 6.0.3, serial and parallel.
After refreshing generated source anchors, all 636 clean-export documentation
checks passed. Current logs and artifact fingerprints are archived separately at
`C:/Data/Code/flow-admission13-packaged-20260915/`; earlier archives are unchanged.
This does not discharge retained-registration/model-reference, manual IntelliJ,
full Stop or workload acceptance. That slice still guarded native fan-in/shared-message/
intra-flow publication; ticket 14's work is recorded above. Chain/context/capture/report
integration remains later work.

### Resource admission slice

Rules combine all matching capacity-one identities and exclusive policy; an empty
declaration cannot erase another rule. Immutable per-flow diagnostics retain the
matching rule names without changing model tags, identities or report schemas.
The shared core scope reserves an entire set plus one run slot atomically. Parallel
outstanding grants are bounded at twice the native target (24 for 12/20), a logical
emission window, not an estimate of free workers. Serial capacity is one. Only the
actual factory may wait, with counter-guarded notifications; no extra body pool or
per-blocked-flow task was added. Reservation effects run outside both locks.

Serial ownership remains held beyond buffered emission and guarded body return.
The next actual SAME_THREAD factory advance proves native return, including outer
cleanup or pre-body rejection. Both stream close and handle close now share pending
withdrawal, wakeup and unsafe-use retention. A rejected one-shot stream close retains
its original cleanup/backstop. Runner/replay construction also stays outside the
bookkeeping monitor. Review found these lifecycle defects and regression tests
reproduced them before correction; final independent follow-up found no new issue.

The final focused install gate passed **176 tests**. Before the diagnostic follow-up,
a relevant **63-test subset passed three times**; afterward, **92 tests passed three
times**, all without failures/errors/skips on Java 17 with real report assets.
Held live-stream cleanup, rejection/no-op/error, UNKNOWN/empty reuse and disposal
controls use actual native execution. Unsafe buffered ownership is checked in
bounded child JVMs without resetting the shared scope. Exact wait-transition and
post-acquisition/pre-emission pauses remain unproved deterministically; replay's
outside-lock construction is source-verified with nine replay regressions, not a
blocked-IO experiment. No report-hash differential or heap-retention proof is claimed.

The final spec review caught missing emitted UNKNOWN fallback diagnostics. Parallel
factories now publish one native `flow.resources.fallback` entry before native
consumption, outside bookkeeping locks. It includes count, global exclusion policy
and at most five bounded identity previews. Actual Launcher regressions went red
for absence and unbounded output, then green for delivery, bounds, mixed audits and
quiet classified/serial controls. Independent follow-up found no actionable
standards or spec issue.

Newly installed resource-enabled binaries also passed all four standalone consumer
points: both JUnit stacks, serial and parallel, with identical Flow artifacts within
this matrix. Earlier artifact hashes identify earlier binaries and remain historical.
The clean-export documentation gate passed all 634 checks. Matrix outputs and install
logs are archived separately at `C:/Data/Code/flow-resource-packaged-20260915/`, with
the final diagnostic-complete binaries under `diagnostic-followup/`.
Context/fixture lifetime, chains, fairness and full cancellation remain later slices.
Legacy/nonparticipating/background users and other classloader copies/processes are
outside automatic resource cooperation. A blocked ready prefix can still be retried
across disjoint admissions; resource reconsideration is not claimed linear.

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

## Native tracer continuation after `4d1c2286`

The full-suite results above describe the previous committed slice, not this
continuation. The bridge and profile guards were built independently in isolated
directories, then integrated with actual Flow processing and the prepared caller.
No frozen design ledger, ticket status, IDE evidence or workload measurement was
rewritten. On 2026-09-15 the user explicitly changed the gate ordering: continue
implementation using automated checks, and perform manual IntelliJ IDEA
Run/Debug/navigation/selection/Stop acceptance at the end alongside their manual
6,000-test workload run. Those acceptance criteria remain open, not waived.

The new bridge preserves direct request execution rather than substituting a
discover/execute pair. Identity-owned previews are single-use within the same live
explicit session; unknown/imported/consumed and per-operation-session previews are
rejected. The optional JUnit 6 helper preserves listener order and the identical
cancellation token. The early public hook is experimental at Platform 1.10 and
maintained at 6.0.3. It must be enabled before Launcher construction.

The checked tracer uses original native invocation threads and source/name values,
binds each admitted executable to its actual native UID, and publishes History
under individual synchronized operations. It does not turn native FAILED into
History ERROR. Supported fixtures exercise genuine successor aborts, comparison
failures versus processing errors, external native failures after successful Flow
processing, and callback-context restoration on worker reuse. A single readiness
waiter handles native inline completion without idle-worker heuristics. Empty
resource grants follow explicit all-selected-flow audits, not context/state guesses.

### Continuation focused evidence

- Combined Java 17 Maven bridge/profile/tracer/serial/core regression run passed
  on 6.0.3 with both test-skip flags disabled; assert-junit5 reported 67 executed
  cases, zero failures/errors/skips. Another 47 core preparation/order/capture
  cases passed in the same reactor: 114 total.
- Baseline production and tests were compiled against Jupiter 5.10.0 / Platform
  1.10.0 using Java 17; only the isolated optional Six helper/test used 6.0.3 APIs.
  Identical binaries were run in three matrices. Each matrix: bridge 10/13 passes,
  profile 14/15 passes (one expected older-API assumption abort on 5.10), integrated
  real tracer plus provider-free serial 13/13 passes. Total: 234 passes, no failures,
  three expected assumption aborts. Actual 12-target/20-cap and target-two native
  inline execution were included. This is not a published consumer/IDE matrix.
- Evidence retained outside the checkout in the integrated-runtime validation
  directory includes exact compiler/runtime commands and source/binary hashes.
  Source-manifest SHA-256: `d5207f3bfbdd0c6edcf59b94f0e678dfb3aaee18c4cd2f8d2d9ee59925c5e885`.

### Continuation standards review

The independent Standards review prompted an ownership/behavior documentation audit.
A subsequent user-directed simplicity review removed redundant explicit inherited
contract comments, matching existing override style while retaining meaningful
caller contracts and lifecycle reasoning. It also removed a write-only node index
and moved the single 12/20 test into the existing binding suite rather than retain
a separate one-test class. All five assertions and the runtime's 13-case check
were preserved. No generic testing abstraction was introduced.

Possible message-chain and fixture-data-clump smells remain disclosed judgement
calls: the evidence objects intentionally collect one native run's observations,
and the test assertion chains remain local. They are not documented-standard
violations. No generic observation abstraction was added solely to remove them.

### Continuation specification review

The independent Spec review found no concrete implementation defect within the
restricted tracer claim. It confirmed that full adversarial guard coverage,
packaged/intended-host acceptance, resource ownership, bounded cancellation and
the workload gate remain incomplete. In particular, successful normal detachment
does not establish safe cleanup for every stopped/abandoned run; the exceptional
backstop still diagnoses rather than force-releases such ownership.

Standalone compatibility harnesses intentionally print native test summaries and
failures. The existing documentation console-use allowlist now names only these
three harness paths and their two exact summary-output statements; the scanner
and its general rejection behavior are unchanged.

The first continuation full suite caught a blank summary immediately after
filtering. Three isolated repeats and the user's 30-repeat experiment passed
unchanged, but the next full run caught the helper parsing raw Mermaid source
instead of rendered SVG. The helper read asynchronous UI state too early. It now
uses the existing two-second explicit-wait style for the exact expected summary
and exact rendered diagram. No fixed sleep, relaxed expectation, frontend change
or new fixture was added. The served-index class then reported 39 cases, zero
failures/errors, three existing skips, including all 30 stress iterations. The
user's repeat annotation remains unstaged and outside this deliverable.

After simplicity cleanup, 67 focused Jupiter cases passed and freshly rebuilt
baseline binaries passed both coherent runtime points again: 78 successful cases,
zero failures, one expected older-API assumption abort. Binary hashes remained
unchanged between runtime executions and the captured sources matched the checkout.

### Verified continuation result

The unrestricted clean-source Maven run passed on 2026-09-15: **42 successful
modules**, **2,084 reported test cases**, zero failures/errors and eight existing
skips. All 2,084 testcase elements were counted in 179 fresh Surefire suites;
14 suites reported zero cases and remain non-evidence for their dynamic examples.
The exercised modules include report-core 113, assert-core 144, assert-junit5 73
and documentation 864. No GUI/browser exclusions or test selectors were added.

Validated tree: `64228e81a9f2c76a8dfe5680075cdde1693fc79c`. All 835 tracked export
files matched: 814 byte-exact and 21 with only Git's line-ending conversion.
All 39 real frontend assets were byte-exact. The invocation was
`mvn -B -fae test -DskipTests=false -Dmaven.test.skip=false` using Java 17.
The final targeted review also accepted the readiness fix and simpler test layout
without further findings. Only this result record changed after the validated
snapshot; no implementation or test behavior changed afterward.

Manual IntelliJ and 6,000-test workload acceptance remain deferred to the end by
the user's explicit instruction. They are not established by these automated runs.
