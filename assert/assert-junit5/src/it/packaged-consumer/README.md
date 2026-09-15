# Packaged consumer gate

This opt-in, standalone Maven project verifies the public real-model/factory-to-Launcher
seam against locally installed Flow artifacts. It is deliberately **not** a reactor
module and has no Flow parent. Its only direct dependencies are four Flow artifacts;
Jupiter and the compile-scope Launcher must arrive through the published dependency
graph. Nothing copies production or reactor test classes into this consumer or its
artifacts, and there is no replacement service provider or `systemPath` dependency.

The consumer JUnit BOM must precede the Flow BOM in [pom.xml](pom.xml): the latter
inherits Flow's build-time JUnit management. The retained verbose dependency tree and
effective POM show consumer management replacing `6.0.3` with `5.10.0`/`1.10.0` for
the baseline run. Tests also assert the actual loaded JAR versions, not just POM text.

## Run

From the repository root, using Bash (Git Bash on Windows), Maven and Java 17:

```bash
export JAVA_HOME='C:/Program Files/Zulu/zulu-17' # adapt to your Java 17 installation
mvn -B -pl assert/assert-junit5,model,message/message-text,bom -am \
  -DskipTests=false -Dmaven.test.skip=false \
  -Dtest=FlowParallelBindingTest,FlowExecutionTest,FlowLauncherBridgeTest,FlowNativeCallTest,FlowNativeProfileTest,FlowLauncherSixTest \
  -Dsurefire.failIfNoSpecifiedTests=false install
bash assert/assert-junit5/src/it/packaged-consumer/verify.sh
```

This uses normal local `install`, not remote deployment. Keep the real report webapp
assets present (build them using the normal report instructions if necessary); do not
substitute `node=none`. The recorded run reused the existing 39 Angular assets.
No Flow rebuild happens between consumer runtime points. `-nsu` prevents remote
snapshot updates during the consumer runs; all required release dependencies still
resolve normally. The script explicitly selects `PackagedConsumerTest`, disables
test-failure ignoring and both test-skip flags, and removes only that class's known
Surefire XML before each run. It then requires a fresh single-suite XML header with
exactly two tests and zero failures, errors or skips. Failed Maven, XML, hash or
class-load checks stop the script; old reports cannot establish success.

Each runtime/mode gets an isolated Surefire JVM. The script retains Maven commands'
dependency tree, verbose effective POM, native summaries/events, code-source JAR paths,
SHA-256 hashes of every resolved Flow JAR and adjacent POM (plus BOM/parent POMs), and
Surefire XML under the consumer's `target/evidence`. JVM class-load logs are in its
`target` directory. The hash lists must match across all four points. Archive that
directory before another run if the previous evidence must be preserved.

To run one point directly (does not perform the script's cross-run hash/class-load checks):

```bash
mvn -nsu -B -f assert/assert-junit5/src/it/packaged-consumer/pom.xml \
  -Djunit.version=6.0.3 -Dplatform.version=6.0.3 -Dconsumer.parallel=true \
  -DskipTests=false -Dmaven.test.skip=false -Dmaven.test.failure.ignore=false \
  -Dtest=PackagedConsumerTest test
```

## What is asserted

[PackagedConsumerTest.java](src/test/java/consumer/PackagedConsumerTest.java) has two
ordinary Surefire tests, `artifactsAndConsumerSelectedVersions` and
`realCallerOutcomesAndCleanup`. The latter uses real nested Launchers and
`SummaryGeneratingListener`; an outer Surefire success or dynamic-test count of zero
is **not** the real-flow oracle.

* One top-level `@FlowTest` factory receives `FlowExecution`, then constructs an
  `EagerModel` with real `Creator` flows and `Text` messages. A response from A is
  bound into B's request; independent C provides controlled overlapping work.
* Absent, request-false and global-system-property-false `flow.parallel` all retain
  actual native `SAME_THREAD` despite global Jupiter concurrency. Factory/body thread
  identity, native start/finish ordering and intercepted execution mode are checked.
  The Launcher hook property is absent. Whole-JVM class traces reject loading the
  parallel interceptor, call receiver, owner or optional helper in these serial runs.
* Parallel uses the intended JAR `ServiceLoader` entry, early hook enablement and
  explicit independence audit under the sole-factory standard fixed 12/20 profile.
  Latches prove A and C overlap, predecessor native finish precedes B, and B finishes
  while C remains active. An outer public factory interceptor observes closure of
  Flow's live native-consumed stream, distinct from the original description source.
  C waits for that native close, not deferred source cleanup. The test requires
  native close before C ends and original-source cleanup only after C's native
  finish: enumeration closure cannot masquerade as terminal drainage.
* Native names, model class/positive source lines, positive individual durations and
  the actual invocation context are checked. A listener records each started leaf's
  display name and UID. Inside the real Flow callback, `flow.meta().id()` looks up
  that independent event's UID and must equal the intercepted `CURRENT` UID. Names
  are only an oracle join here, not production routing. The observer wraps the
  original native invocation without replacing or offloading it; its context is
  cleared afterwards.
* Normal, processing-error/dependent-abort and empty runs observe factory terminal
  and exactly one close of each stream. Post-run handle close is idempotent; handle
  reuse and retained executable re-entry fail. Thirty-four same-JVM parallel repeats
  provide a short repeated-execution smoke check. Together these prove repeated execution,
  idempotent close and re-entry guards, **not registration disposal or heap
  reachability**. Source review found no production leak: `FlowNativeCall.PENDING`
  is removed at attachment independently of later owner release; the handshake
  `SLOTS` limit cannot establish retained-registration or model-reference disposal.
* Disabling the provider hook in parallel rejects before factory/SUT entry; the
  expected receiver and incomplete-backstop failures remain visible. A method
  selector is rejected before discovery/hidden work. This does not approve arbitrary
  subset selection: the checked parallel selector remains the whole sole class.

## Recorded run: 2026-09-15

Baseline: `c9867a0b31e8466441bf60c6b2950dd99ef9f9e7`. Windows 11 amd64,
Azul Java `17.0.19`, Maven `3.6.3`, Surefire `3.5.3`, compiler plugin `3.15.0`.
Dependency/help plugins `3.8.1`/`3.5.1` actually resolved and executed; no new parent
plugin or Invoker dependency was necessary. The focused 16-project reactor installed
Flow `1.1.8-SNAPSHOT`; all 47 selected launcher regression tests passed, and the
adapter's 13 production sources were compiled by javac with `--release 17` against
the repository's JUnit `6.0.3`. **Those same produced Flow binaries**, not a special
baseline build, passed both consumer-selected stacks below.

| Consumer Jupiter / Platform | Route | Outer tests passed | Real native found / started | Successful / failed / aborted |
| --- | --- | ---: | ---: | ---: |
| 5.10.0 / 1.10.0 | serial: absent, false, global false | 2 | 18 / 18 | 12 / 3 / 3 |
| 5.10.0 / 1.10.0 | parallel 12/20 | 2 | 105 / 105 | 103 / 1 / 1 |
| 6.0.3 / 6.0.3 | serial: absent, false, global false | 2 | 18 / 18 | 12 / 3 / 3 |
| 6.0.3 / 6.0.3 | parallel 12/20 | 2 | 105 / 105 | 103 / 1 / 1 |

Failures/aborts in this table are deliberate A-error/B-dependent-abort controls.
Normal/error/empty runs have zero failed or aborted containers. Each parallel point
additionally has two expected failed containers in the disabled-provider control,
with zero native leaves; selector rejection produces no native plan or summary.
The complete matrix therefore asserts 246 real leaf starts, not merely eight outer
tests. Both baseline class traces contain no `FlowLauncherSix`; both serial traces
contain no parallel provider/receiver/owner/helper. The 6.0.3 parallel trace loads
the helper from the installed adapter JAR. These observations establish only these
exact runtime/host points, not a support range, other versions or split classloaders.

Adapter JAR SHA-256:
`a13ad855128a353f580c44f4a1ce575ddb2e32ba7f5046206d78cbbade18cf55`.
Adapter POM: `0fb6a72bc76707297521838b883fee2f036f198de281b9693d58cd2cfa60e492`.
Flow BOM: `6847063c00bdc0d941079e5d1f6ef0b73649df5ef5f1cf1e80b23c4730c07bd4`.
All resolved Flow files' hashes and GAV paths are retained by the executable gate.
These identify this local build, not reproducible-build guarantees or remote releases.
The final evidence was also archived outside the checkout under
`C:/Data/Code/flow-packaged-gate-20260915/`. A Java 17 `jdeps -verbose:class
-filter:none` inspection of the adapter JAR found typed references to
`CancellationToken`, `LauncherExecutionRequest` and its builder only in
`FlowLauncherSix`. That dependency listing intentionally had no JUnit classpath
(`not found` is not a linkage-test result); the actual four-runtime executions and
class traces above provide the runtime evidence. `javap` reported class major 61.

The pre-change gap was absence of a retained standalone real-artifact consumer;
there was no invented failing production behavior. Initial offline dependency
metadata resolution was an environment failure, not a behavioral red. Two fixture
logging failures exposed the baseline null versus JUnit 6 exception from querying
`getSummary()` before a rejected request reaches a plan; the fixture now avoids that
query. No production fix was required.

### Review follow-up: 2026-09-15

The five-file fixture/documentation follow-up reran all four points against the
same 25 installed Flow JAR/POM files above, without rebuilding Flow. The table's
counts remain unchanged: eight outer passes and 246 native found/started leaves
(230 successful, eight deliberate failures, eight dependent aborts). Each fresh
outer XML reports `tests=2`, `failures=0`, `errors=0`, `skipped=0`. All 238 actual
Flow callbacks checked their invocation UID against the matching listener event;
the eight dependent aborts do not enter the Flow callback. The serial points each
have 15 such comparisons, the parallel points each 104. The parallel outer behavior
test, including all 34 repeats and controls, took 0.777s / 0.803s on the two stacks;
this is smoke-test cost, not a workload performance claim.

Before the green matrix, two temporary `ContextCheck.CURRENT` mutations each ran
the actual script's first point (`5.10.0` serial):

* Supplying the factory's UID failed the A/B/C native-UID comparisons.
* Supplying A's genuine leaf UID for B failed with expected B `dynamic-test:#2`
  versus actual A `dynamic-test:#1`.

Each control ran two outer tests with one failure, zero errors/skips and script
exit 1. Both used external `MAVEN_OPTS=-Dmaven.test.failure.ignore=true` in addition
to the Java trust-store options; the script's explicit command-line `false` won,
as recorded in the fresh XML. These are validation-oracle mutation reds, not a
newly discovered production bug. Both mutations were restored before the matrix;
no permanent mutation switch or additional harness was added.

The original archive was preserved byte-for-byte. Follow-up evidence is under its
new `review-followup-1/` subdirectory: `matrix.log`, `counts.log`, `format.log`,
`validation.log`, `evidence/` (per-point Maven logs, fresh XML and Flow hashes),
class-load logs, and the two `wrong-factory-uid/` and `wrong-leaf-uid/` controls
(mutated source, actual script output, XML and exit status). `fixture.sha256` covers
all five final scope files; `artifacts.sha256` covers the follow-up archive.
The printed provenance is intentional; the parent documentation stdout check's
exact allowlist remains separate follow-up work, not a check passed here.

### Ticket 18 source-cleanup oracle correction

The original 5.10 parallel failure was a fixture circular wait, not evidence that
native enumeration remained open: C waited for the original description source to
close, while safe disposal now correctly waits for C's native terminal. An
observation-only failing run saw live native-stream closure before C timed out and
original-source cleanup afterwards. The correction observes those two streams
separately through an outer public factory interceptor. It retains the real binding,
overlap, outcome and UID oracles, requires original cleanup after C's native finish,
and checks exactly one close of each stream. No timeout or production guard is relaxed.

The red/full-class, red/exact-method and observation-only red runs, then the focused
green and four-point matrix, are archived separately under
`C:/Data/Code/flow-stop18-packaged-diagnosis/`. The archived matrix passed eight outer
tests, 246 native starts and 238 body UID checks against identical installed ticket-18
artifacts across the two JUnit stacks. Those artifacts still contained the report
claims subsequently removed by amendment 28; this historical matrix is not validation
of the current combined source. The original failed evidence remains intact.

### Current combined-source verification: 2026-09-16

The reviewed ticket-18 source plus committed amendment 28 was installed locally
after the 26-class Stop/lifecycle gate passed 522 cases (520 passed, two existing
Windows skips, zero failures/errors). The unchanged verifier then passed all four
points: each fresh XML has two tests and zero failures/errors/skips, with identical
25-file Flow artifact hashes. Serial/provider-free and optional JUnit6 helper
class-load checks passed. The installed report JAR was checked to exclude the
retired claim class, rather than reusing the older diagnosis build.

Java 17.0.19, both test-skip flags and failure-ignore false; real 39 report assets.
Adapter JAR SHA-256: `cb3d49b804232d189bc316e4fc0d023675bf96aa8132dfed4890b206568e5022`.
Fresh logs, XML, hashes, class-load traces and the tested consumer source are under
`C:/Data/Code/flow-stop18-commit-20260916/`. This resolves the specific stream-oracle
failure, not the separate report-reuse failure, complete Stop/cancellation coverage,
IDE/retained-reference acceptance or workload performance.

### Baseline public API references

Engine provenance uses public `ServiceLoader<TestEngine>`, selects the provider by
engine ID `junit-jupiter` and inspects `provider.getClass()` dynamically. There is no
typed reference to the INTERNAL Jupiter engine implementation; the existing STABLE
JUnit Commons `JUnitException` reference remains a version/provenance check.

The exercised bridge uses the public [Launcher request/plan operations and listeners](https://docs.junit.org/5.10.0/api/org.junit.platform.launcher/org/junit/platform/launcher/Launcher.html),
[discovery selectors](https://docs.junit.org/5.10.0/api/org.junit.platform.engine/org/junit/platform/engine/discovery/DiscoverySelectors.html),
[MethodOrderer.getDefaultExecutionMode](https://docs.junit.org/5.10.0/api/org.junit.jupiter.api/org/junit/jupiter/api/MethodOrderer.html#getDefaultExecutionMode()),
[ExtensionContext and ordinary stores](https://docs.junit.org/5.10.0/api/org.junit.jupiter.api/org/junit/jupiter/api/extension/ExtensionContext.html),
and [Store.CloseableResource](https://docs.junit.org/5.10.0/api/org.junit.jupiter.api/org/junit/jupiter/api/extension/ExtensionContext.Store.CloseableResource.html).
The [LauncherInterceptor](https://docs.junit.org/5.10.0/api/org.junit.platform.launcher/org/junit/platform/launcher/LauncherInterceptor.html)
and [three-argument dynamic interception](https://docs.junit.org/5.10.0/api/org.junit.jupiter.api/org/junit/jupiter/api/extension/InvocationInterceptor.html)
are EXPERIMENTAL at this baseline, not an all-STABLE path.
The existing optional helper is selected by presence of `LauncherExecutionRequest`
(present alongside `CancellationToken` on the verified 6.0.3 stack); this gate does
not exercise that token's cancellation semantics or claim a different presence guard.

## Still pending

Ticket 10 is not fully passed. By the user's approved sequencing decision, intended
**IntelliJ IDEA Run/Debug, visible names/durations/source navigation, supported subset
selection, cooperative Stop and an honest uncooperative Stop control**, plus the
manual 6,000-flow workload, remain deferred until the end. No historical IDE fixture
was altered or reused. This automated factory is driven by the enclosing test and
is not itself an independently configured IDE smoke fixture. Native metadata is not
evidence of IDE UI behavior or graceful drainage after process death.

The automated acceptance criterion for **no safely disposable retained
registrations** remains unverified and must be addressed at the later integrated
ticket 25 gate. Repeated execution and post-close rejection do not establish it;
this fixture adds no internal metrics, reflection or GC-dependent reachability test.

General resource scheduling, reporting/capture integration, full cancellation,
unsupported-selector expansion, and the later complete packaged/host gate remain
separate work. This fixture explicitly uses `Reporting.NEVER`, default no-op capture
and the currently admitted independence profile; it does not relax those boundaries.
