
<!-- title start -->

# assert-junit5

JUnit5 comparison components

[![javadoc](https://javadoc.io/badge2/com.mastercard.test.flow/assert-junit5/javadoc.svg)](https://javadoc.io/doc/com.mastercard.test.flow/assert-junit5)

 * [../assert](..) Comparing models against systems

<!-- title end -->

## Overview

This module integrates the capabilities of `assert-core` into the junit5 testing framework.
It provides the `Flocessor` class, which should be used as a generator for a [dynamic test](https://junit.org/junit5/docs/current/user-guide/#writing-tests-dynamic-tests)

## Usage

After [importing the `bom`](../../bom):

```xml
<dependency>
  <!-- system assertion -->
  <groupId>com.mastercard.test.flow</groupId>
  <artifactId>assert-junit5</artifactId>
  <scope>test</scope>
</dependency>
```

The flocessor should be used to provide the output of a [TestFactory](https://junit.org/junit5/docs/current/api/org.junit.jupiter.api/org/junit/jupiter/api/TestFactory.html) method:

```java
@TestInstance(Lifecycle.PER_CLASS)
class MyTest {
  private final Flocessor flows = new Flocessor( "my test name", mySystemModel )
    .system( /* The actors that are being exercised */ )
    .behaviour( asrt -> {
      // implement this to push data from asrt into your system 
      // and then put the system outputs back into asrt
    } );

  @TestFactory
  Stream<DynamicNode> myTest() {
    return flows.tests();
  }

  // Factory return can precede dynamic children; close only after they finish.
  @AfterAll
  void complete() {
    flows.close();
  }
}
```

The legacy runner implements `AutoCloseable`. Retain it until `@AfterAll`:
factory return, description enumeration and stream closure can all precede actual
test execution. Do not wrap a factory's returned stream in try-with-resources to
close the runner. Configuration remains live until explicit completion.

`close()` rejects active processing rather than waiting or cancelling it. Otherwise
it permanently prevents further SUT calls, even with `Reporting.NEVER`, and closes
only an already-created report. Successful close is idempotent; failed reporting
remains observable on repeated close. Omitting completion prevents completion-time
publication. This does not add a public close method to `PreparedFlocessor`.

## Prepared caller (implementation preview)

The existing `Flocessor` remains available. A new self-typed sibling can be
configured through one composed class annotation and one factory-local handle:

```java
@FlowTest
class SystemTest {
  @TestFactory
  Stream<DynamicNode> flows(FlowExecution execution) {
    return execution.flocessor("my test name", mySystemModel)
      .system(State.LESS, actorsUnderTest)
      .behaviour(assertion -> {
        // Exercise the system and populate assertion.actual().
      })
      .tests();
  }
}
```

`FlowTest`, `FlowExecution` and `PreparedFlocessor` are in
`com.mastercard.test.flow.assrt.junit5`; `State` is the existing
`AbstractFlocessor.State`. The handle receives the model only inside the factory.

With the discovery configuration parameter `flow.parallel` absent or `false`,
this caller uses genuine Jupiter `SAME_THREAD` execution even when global Jupiter
concurrency is enabled. It needs no parallel listener/provider, Launcher interceptor
or receipt channel. Parallel execution is restricted to the checked tracer below;
this preview is not a general-purpose parallel release.

Prepare once with `tests()`, then return exactly the owned descriptions. They can
be enumerated and inspected without executing the SUT. Configuration containers
and arrays are copied at preparation while registered callback/domain identities
are retained. A second preparation, second runner, late fluent mutation or reuse
outside the owning factory is rejected. These restrictions do not apply
retroactively to the legacy caller.

Actual serial exhaustion and drained invocations, followed by the observed stream
close, complete the prepared run. Early close or abandonment is diagnosed by its
class-local lifecycle backstop; neither is converted into successful finalization.
Reporting retains its current immediate behavior for the original serial caller.
Resource declarations and explicit fixture domains below add cooperating serial
ownership; full cancellation drainage and integrated final-only reporting remain unfinished.

Real Launcher regressions have exercised the serial caller on coherent JUnit
5.10/Platform 1.10 and JUnit 6.0.3 stacks with Java 17. These are tested points,
not a published support range or evidence of IDE Run/Debug/Stop acceptance. Keep
consumer JUnit dependencies coherent; no JUnit 6 upgrade is required by this caller.

### Checked native parallel tracer

The same caller can execute an explicitly audited, restricted nonempty model on
genuine Jupiter invocation threads. Enable the public construction hook with
`junit.platform.launcher.interceptors.enabled=true` **before Launcher construction**,
then set discovery parameter `flow.parallel=true`. Request-level hook configuration
is too late. The service provider is included in this module. The construction hook
is EXPERIMENTAL at Platform 1.10 and MAINTAINED at 6.0.3, not an all-STABLE API path.

The checked request is one full, top-level `@FlowTest` class with one factory and
no filters or other native workloads. It requires Jupiter's standard fixed pool,
enabled parallel execution, target parallelism at least two, and a valid maximum
at least the target. The 12-target/20-maximum profile is exercised. Conflicting
orderers/modes, resource locks/isolation (including inherited and meta annotations),
nested workloads and separate-thread timeouts are rejected before original factory
execution. Public checks do not prove every live pool constructor setting or the
behavior of arbitrary third-party extensions.

Before `tests()`, configure named bulk declarations using existing flow metadata,
IDs or interactions:

- `resources("queue audit", predicate, "actual-queue-identity", "actual-account-identity")`
  declares the complete set of capacity-one shared-state identities.
- `exclusive("fixture reset", predicate)` conflicts with all cooperating work.
- `independent("empty resource audit", predicate)` remains source-compatible and
  declares a known empty set, equivalent to `resources` with no keys.
- `isolatedChains("whole-scenario audit", "checkout", "refund")` permits outside
  overlap for those entire chains, identified by existing `chain:` tag suffixes.
  It does not classify member resources or select additional flows.

All matching rules combine by union; exclusive dominates, and an empty declaration
cannot erase another restriction. No matching rule means **UNKNOWN global-exclusive**,
not rejection and not independence. UNKNOWN also conflicts with known-empty work.
Parallel requests publish one `flow.resources.fallback` factory report entry before
native emission, with the unclassified count, up to five bounded identity previews
and the exclusion policy; all-unclassified selections are identified as serial.
Classified and provider-free serial runs emit no fallback entry.
`State.LESS`, absent contexts, different actor names and different wrapper objects
do not establish isolation. Equal string keys must describe the same actual state;
different strings are justified only by a real isolation audit. Audit messages,
listeners, callbacks and cleanup as well as the SUT. No declaration authorizes
surviving asynchronous use or required physical-worker affinity.

Predicates run once per selected or dependency-expanded flow, before chain
contraction and before any SUT use. Nothing adds Flow fields or identity-bearing
tags. After `tests()`, `requirements(flow)` returns the stored immutable
`ResourceRequirements`: `keys()`, `rules()`, `unknown()` and `exclusive()` expose
the union and every matching rule name without rerunning predicates. Retain the
value if diagnostics are needed after the run detaches. These are the individual
member requirements. Use `reservation(flow)` for the effective selected-chain union
and global-exclusion policy used by admission; `reservation(flow).isolationRules()`
lists the matching whole-chain audit names separately from resource classification.
Both queries use frozen preparation results and reject detached or unselected flows.

Temporary tracer limits reject reporting other than `Reporting.NEVER`, capture
other than `LogCapture.NO_OP`, replay, foreign binding destinations, noncanonical
prerequisites and non-class source URIs. Contexts/applicators, residue/checkers and
autonomous actors require an explicit actual fixture domain, not just named resource
rules. These are fail-closed implementation boundaries, **not new permanent support
restrictions**. The same guards apply to explicit prepared serial cooperation.
Existing serial
configuration without new declarations retains its earlier processing behavior;
cross-runner fixture/context ownership is not retroactively inferred for that route.

### Actual fixture domains

The existing fixture owner creates one `ContextDomain` for the actual shared SUT
state and keeps it for that physical lifetime. Every cooperating runner uses
`contextDomain(domain)` **before fixture actions and `tests()`**. New applicator
objects, tabs, runner lifetimes and different resource labels must not create fresh
handles for the same state. `Context.domain()` identifies actors, not this physical
domain. One handle conservatively covers every selected flow in its runner.

The handle owns the authoritative applied-context map. Its implicit unique key and
additional constructor keys are added to every selected flow's reservation,
including empty-context flows that may remove earlier state. This preserves UNKNOWN
classification and whole-chain exclusion. Shared-account/configuration keys can
coordinate genuinely separate domains without merging their applied contexts.
Successful transitions update the map; successful removal deletes it. Runner
completion never clears the fixture state or resets between chain members.

Existing owners use nonblocking `domain.tryAcquire()` for creation, reset and
teardown: null means busy and **no fixture action is allowed**. Within a Flow
invocation it borrows the current complete grant; closing that scope does not
release native outer cleanup or chain ownership. `Use.change(action)` runs an
existing lifecycle action; `Use.reset(action)` additionally records a clean context
baseline only after the explicit action succeeds. Neither invents fixture actions
or transfers lifecycle responsibility to Flow. Creation belongs before preparation;
teardown belongs after the owner's actual last use, not in a description-stream
close handler. A class finishing cannot acquire lifecycle ownership while another
class still uses the domain.

An owner may declare an actual required `Thread` in the domain constructor.
Binding rejects initial parallel mode before subsequent fixture creation; serial
binding and every use/lifecycle action check that exact thread. Declaring the
handle after an arbitrary constructor or `@BeforeAll` already touched the fixture
cannot retroactively establish ownership. No lanes, offloading or silent serial
fallback are provided.

Failed transitions and unhandled lifecycle-action failures conservatively retain
the whole grant and diagnose later conflicts. Owner-supplied `uncertain(cause)`
also records continued/uncertain use; a safely completed assertion failure does
not. If a lifecycle failure is known safe, its existing owner must handle that
inside the action rather than relying on a throwable class. There is no retry,
repair, recreation or forced release of this irreversible uncertainty. Exact
operation receipts below distinguish remaining use from damaged fixture state.

The borrowed domain scope surrounds core processing, not arbitrary outer JUnit
interceptors. Owners with external pre-body work or cleanup use
`contextDomain(domain, (flow, receipt) -> ...)` to receive an exact whole-grant
`ContextDomain.Receipt` **before native emission**. This short, nonthrowing callback
runs outside bookkeeping locks; it publishes evidence ownership, not fixture actions.
Correlate each receipt explicitly with the owner's work, and discard it after the
supported invocation interval. Do not infer ownership from a thread, display name or
the domain's currently active user.

`receipt.uncertain(cause)` may deliver diagnostic evidence from another thread or
after a borrowed `Use` closes, while that exact grant is still owned. `Use.receipt()`
also captures evidence ownership during actual synchronous use. Evidence must arrive
before native completion: it retains the whole grant and stops its owning run before
chain continuation or finalization, even with no competing request pending. A released
receipt rejects late evidence without poisoning a newer user; no recovery is implied.
Receipt callback failure before native handoff retires only the proven-unemitted
use; it cannot release explicit uncertainty or another live chain use. No native
terminal or processing result is fabricated for that rejected publication.
Actual fixture actions and applied-state access require a still-live complete grant,
the owning thread and a current LIFO `Use` scope. A scope-close invariant failure
retains the exact grant because drainage is unproved; entry/close failures do not
reclassify genuine processing results as History ERROR. Native
FAILED alone is not uncertainty, and ordinary safely completed outer cleanup failures
still permit reuse. Nonthrowing evidence can stop a run even when its native leaf succeeds.

The fake-fixture tests cover context removal across runners, residue and callbacks,
native outer cleanup, default/isolated chains, cross-class teardown, shared tabs,
separate sessions/shared accounts, actual serial affinity and zero-creation parallel
rejection. They do not certify the consuming suite's resource audit or background
quiescence. Existing service/mock/queue, DB and browser examples remain legacy
serialized owners: their timers, handlers, whole-table observations and root-store
cleanup have not been made automatically cooperating. Replay and report/capture
integration remain guarded, including distinct artifact/log attribution for parallel
browser sessions. No real browser, IDE or full-workload acceptance is claimed.

### Explicit Stop and remaining ownership

`FlowExecution.stop(cause)` requires a non-null cause, irreversibly closes admission,
wakes the existing factory waiter and withdraws pending reservations. The first
cause wins. Already-admitted wrappers that have not entered Flow processing take
their real native abort path. Stop does not cancel peer runs, interrupt pooled
threads, manufacture History results or turn stream close into native completion.
Actual child skip and enclosing-scope terminal evidence can retire unused native
ownership without inventing descendant outcomes.

Before background use escapes, its existing owner registers `receipt.operation()`
(or `Grant.operation()` at the reservation seam). Call that exact operation's
`complete()` only after its actual use and required cleanup have ended. It is
deliberately not `AutoCloseable`: a cancelled future, timeout, request return or
native terminal is not such proof. Multiple operations retain their whole grant
until all finish, and chain continuation waits on the same factory waiter. These
receipts do not authorize fixture access on another thread or establish an audit
for unregistered asynchronous work.

After Stop, outstanding operations retain ownership with a visible pre-use
diagnostic for conflicting cooperating runs. Late exact proof can release that
remaining ownership and safely dispose original stream/model tables; it cannot
restart admission, repair `uncertain(cause)`, erase Stop or rewrite earlier native
results. Original cleanup waits for actual native/body/operation drainage. Genuine
incompleteness and cleanup failures use the live factory or class-local exceptional
backstop where reachable, not just listener exceptions.

`execution.status()` supplies an immutable, bounded `ExecutionStatus` independently
of Writer success: ACTIVE, STOPPING or QUIESCENT, the first cause, latched
incompleteness, processing/native/remaining-owner counts and at most five shortened
identities. Serial native-terminal count is `-1`, not an invented listener mirror.
QUIESCENT means owned work and required cleanup ended, not that the selection
passed. Stop after quiescence is a no-op. Retained unsafe owners are not forcibly
released to make disposal appear successful; stopped reporting is not finalized as
successful merely to discard references.

Ticket 19 still owns native-token query propagation, cooperative cancellation
callbacks, check cadence and the reachable owner drain budget. There is no new
watchdog/executor, uniform Launcher/remote/IDE shutdown guarantee or hard-kill
cleanup promise here. Report/capture/replay guards and consumer resource-audit
limits remain unchanged.

### Uninterrupted selected chains

Both prepared modes reserve a default chain globally against all cooperating work,
including EMPTY and disjoint-key flows in other runners. Only selected and required
dependency-expanded members participate, in canonical order. Chain membership and
basis alone never expand selection. Hard, basis-visibility, producer and identical-
message participant constraints compose with the whole units; contradictory
contraction is rejected before SUT use, not repaired by outside interleaving.

The selected members' entire resource union is acquired before the first member.
One grant survives between members and through synchronous processing and each
member's outer native cleanup, with no partial acquisition or intervening reset.
Any UNKNOWN or exclusive member promotes the whole chain, even when other members
have named rules. An unchained exclusive flow does not promote its dependency
component.

Only `isolatedChains` relaxes default outside exclusion. Audit the whole scenario,
not individual matching members; `resources`, `independent`, `State.LESS` and
distinct keys do not imply that audit. The declaration freezes at `tests()` and
cannot erase actual resource conflicts or turn UNKNOWN into EMPTY. Isolated chains
may overlap across cooperating scopes, but their own members remain sequential.

Parallel leaves remain flat with individual native outcomes and durations. The
next member needs both actual predecessor native terminal evidence and processing
drainage; it does not join unrelated chains. Serial retains genuine SAME_THREAD
execution and uses the next actual native factory advance as return proof. Stop
prevents unused members from entering and retains uncertain use; safe late drainage
never resumes admission. This is not the complete cancellation policy.

Explicit domains cover shared context transitions, residue and owner lifecycle
actions. Capture/report integration remains guarded. In particular,
parallel description-stream close can precede active native cleanup: it is not a
safe fixture-teardown hook. Physical affinity is supported only on a verified actual
serial owner, never by parallel worker assignment.

### Shared reservation scope and lifetime

Prepared runners use the same assertion-core `ResourceReservations.shared()` scope,
including provider-free serial execution. Creating a second runner or Launcher does
not create a separate resource scope. Cross-runner tests use separate supported
native pools, not multiple factories competing in one pool.

The entire named set and one run-owned execution slot are acquired atomically before
native emission. A blocked `{A, B}` request holds neither free A nor a slot while B
is busy. Known-empty work also consumes a slot. The parallel emission window is
twice the checked native target (24 outstanding grants at target 12), allowing the
native queued/inline saturation path; this is a logical bound, not an idle-worker
estimate or a change to the configured 12-target/20-maximum pool. Serial capacity
is one. Only the actual factory consumer may wait, using change notifications with
an event counter to prevent lost wakeups; no body executor or blocked-flow tasks
are created. Reservation operations and notifications run outside run-state locks,
and notifications also run outside reservation locks.

Parallel release requires native terminal evidence and drained synchronous
processing/cleanup. Native rejection before guarded Flow entry may return a proven
unused grant, but still diagnoses the incomplete run. Serial release uses the next
actual SAME_THREAD factory advance, after the previous native invocation (including
outer interceptor cleanup) has returned. This can retire unused ownership after
pre-body rejection without treating omitted Flow processing as successful drainage.
Jupiter can buffer a description before invoking it: `action.accept()` return,
body return, arbitrary stream close, stop and future cancellation are not proof.
An abandoned buffered/emitted description without native return evidence retains
uncertain ownership; retained UNKNOWN ownership can block even known-empty work.
The unsafe serial cleanup regressions assert that retention in isolated JVMs rather
than resetting the shared scope or weakening the ownership rule.

Live-stream close and the public serial `close()` backstop share disposal bookkeeping:
both stop admission and withdraw a pending request, waking the factory without waiting
for the resource holder. A tentative grant racing disposal is returned only if it was
never emitted. Neither route cleans the original fixture or detaches the runner while
an issued grant remains unsafe. A rejected live close retains original cleanup for the
backstop, because JDK stream close handlers run only once even when they throw. The
next actual native advance can retire the grant after a safe handoff return, but stop
remains incomplete; it cannot finalize a report or hide original-stream cleanup
failures. This is the existing lifecycle backstop, not general cancellation.

The native close regressions retain the outer factory interceptor's live stream and
hold post-body cleanup. Predispatch disposal uses a test-local native stream gate;
an additional close/admission race must finish while another real run still holds
the resource. This replaces the timed join/private-stack check, but does not prove
that every race reaches the exact wait transition. Public core request-cancellation
tests cover withdrawal separately. Runner attachment claims once, constructs replay
outside the owner monitor, then publishes only if still live. That lock boundary is
source-verified with existing replay regressions, not a blocked-filesystem race test:
the current replay path has no injectable blocking-read seam.

The public core interface also permits an explicit cooperating fixture owner to
create a `Capacity`, `register` a dependency-ready whole-set request, `tryAcquire`
a `Grant`, cancel an ungranted request, and close a proven unused or safely finished
grant. Wakeups must be short and nonthrowing. This does **not** move fixture creation,
reset or teardown responsibility into Flow.

Conflicting requests retain scope-wide oldest-dependency-ready priority across
resource/capacity waits and retries. An older `{A, B}` blocked on B protects free A
from newer conflicts without holding it; genuinely disjoint C may pass. Ready
exclusive/UNKNOWN work gates all newer requests, including EMPTY, even while its
own capacity is busy. Older pending work keeps precedence; active work drains,
the exclusive runs alone, and admission resumes after safe release. Dependency-
blocked work has no age or gate. Context/canonical preference cannot override an
older conflicting request. This is not a wall-clock termination guarantee.

The readiness linearization point is atomic insertion into the shared pending set,
not a timestamp on invisible work. Parallel preparation publishes all root owners
as one canonical cohort; native completion publishes only newly ready owners as a
canonical cohort before releasing the parent grant. Publication is outside History's
lock and precedes factory visibility; a concurrent stop withdraws the cohort rather
than reopening admission. Serial publishes its next canonical unit on actual
SAME_THREAD advance after prior native release, not early during preparation.
Both modes retain one request across retries and keep the existing grant across
chain continuation. Withdrawal itself wakes other factories outside both locks,
even when no grant is released; completing workers never wait for admission.

Admission is not claimed to be linear: a blocked prefix of B ready nodes can be
retried for each of D disjoint admissions (B×D attempts). Each attempt can compare
older pending requirements; release and withdrawal scan pending requests to
deduplicate coordinator wakeups. These resource reconsideration costs are separate.
Dependency readiness is measured separately: each prepared node is admitted once
and each deduplicated successor edge is visited once, with O(V+E) bookkeeping/storage
plus O(V log V) ordered-ready-set work. Basis planning visits each distinct reachable
identity once and adds at most 2V canonical-forward edges, using O(V log V) rank-set
work without retaining the ancestor closure. Resource reconsideration, History
traversal, callbacks and SUT work are not included in the readiness bound. Core
measurements cover 100, 1,000 and 7,000 nodes in fork, chain and two-predecessor graphs,
plus deep/inverted basis paths and siblings sharing absent bases, counting basis
lookups and retained-edge visits; these are operation-count evidence, not a
whole-run speedup or a workload benchmark.

Legacy `Flocessor` users do not automatically participate. Use the prepared serial
caller with the same declarations for explicit serial cooperation, or explicitly
integrate ownership through the core interface. Unrelated Jupiter tests, background
users, separate classloader copies and external processes are outside this guarantee.
For example, an undeclared background queue consumer can still invalidate a correctly
reserved flow. Matching a rule is not proof that the resource audit is complete.

Direct execution preserves the original Launcher request and listeners. An
identity-owned discovery preview may be consumed once in the same live explicit
Launcher session. Imported/consumed previews and previews from the per-operation
sessions of `LauncherFactory.create()` are rejected. The isolated JUnit 6 helper
preserves the native execution overload and identical cancellation token; it does
not implement Flow cancellation polling or bounded stop/drain.

Shared-core `FlowAdmission` owns direct predecessor counters, ordered ready work,
grants and native/processing records under the same short-held monitor as History.
The Jupiter adapter retains native identity/source and actual-pool validation, not
a second admission state machine. Model traversal, resource effects and callbacks
stay outside bookkeeping. One factory readiness waiter releases each successor
after its own predecessors' safe native completion and synchronous publication;
unrelated slow branches impose no level barrier. Identical evidence is idempotent;
conflicting evidence latches a stop checked before further Flow use.

Selected comparable basis ancestors and descendants retain canonical order in both
directions, including across absent intermediate bases: a later ancestor cannot
publish failure before an earlier derived flow checks eligibility. This neither
selects extra flows nor overrides hard order, and adds no edges between siblings.
Readiness does not require predecessors to pass: unexpected comparisons suppress
derived flows but permit data dependents, while ordinary errors permit derived retries
and abort stateful dependents. Existing suppression/stateless policies still apply.
Those ineligible selected flows take their real native abort path without SUT calls.
Native failure (or an extension suppressing an exception) never rewrites Flow History;
missing processing is not success. Fatal/protocol faults stop admission rather than
pretend normal completion.

Audited fan-in, identical-message aliases and valid intra-flow bindings are now
enabled. Canonical order-only constraints group producers by destination **Flow**,
including different fields/messages and every destination of a multi-destination
producer. Identical **Message** participants include readers as well as writers,
so a destination cannot consume an alias while another producer mutates it.
Only scheduling pairs are deduplicated: every binding still performs synchronous
peer/get/mutation/set on its original producer thread, with no replay or rollback.
Order-only constraints do not change `History` eligibility; a failed A alone does
not suppress otherwise eligible B. Hidden shared backing and callback state still
require resource auditing, not just distinct message identities.

T29 tests hold A's own resource while independent D progresses, then check literal
serial/native payloads and operation counts. With A writing X/Y, B writing X and C
writing Y, B/C can overlap after A; the connected component is not serialized.
Publication planning is exercised through core admission at capacities 1, 2 and 5.

Native **reporting-enabled T30 remains blocked by ticket 23**. After run-owned
reporting is enabled, the real factory/Launcher must repeat peer/get/mutation/set/
set-after faults under accumulation against the serial/core oracle: partial writes,
later-message publication/comparisons, callback counts and producer threads, original
primary cause, order-only versus genuine dependency outcomes, and safely finalized
report payloads/cleanup. Include valid intra-flow updates and masks. Current API and
native immediate-failure checks, plus core `NEVER`/`QUIETLY` coverage of all five
stages, do not satisfy that remaining native integration gate.

Native inline completion is supported; there is no second body pool or idle-worker
estimate. At 12/20 Jupiter may execute inline before filling the 24-grant bound; the
queueing control accepts either supported path and separately checks the core bound.
Successful disposal requires actual factory terminal and owned drainage, never stream
close alone. Exceptional incompleteness is diagnosed without forced release or
successful report finalization.

Real Launcher tests cover A-to-B message binding while independent C remains active,
real dependent aborts, callback context/restoration, native inline execution and
provider-free serial behavior. Resource regressions additionally exercise equal-key
exclusion, disjoint overlap, atomic multi-key admission, UNKNOWN/empty/exclusive
precedence, once-only dependency-expanded resolution, native cleanup and serial/
parallel cooperation through separate real 12/20 pools. Admission regressions also
compare the actual serial MetaTest outcome oracle, basis gaps, suppression, fatal
versus ordinary failures, duplicate/conflicting evidence and core capacities 1/2/5.
Native target one remains a rejected-profile control. The final admission changes
passed 219 focused tests and three 88-test repeats on Java 17/JUnit 6.0.3, including
the separate synchronous timeout-as-assertion oracle. Newly installed binaries also
passed the four-point consumer matrix below. IDE acceptance remains unverified.

### Automated packaged consumer gate

The [standalone consumer](src/it/packaged-consumer/README.md) installs and consumes
normal Flow `1.1.8-SNAPSHOT` JARs/POMs/BOM, without a Flow parent, `systemPath` or
reactor test helpers. Its repeatable commands verify the same repository-built Flow
binaries on Java 17 with consumer-selected JUnit **5.10.0/Platform 1.10.0** and
**6.0.3**, in separate provider-free serial and checked-parallel JVMs. Import the
consumer's JUnit BOM **before** the Flow BOM so Flow's inherited build-time management
does not override that selection.

Native summaries assert nonempty real models, binding/overlap, outcomes, factory
terminal, repeated execution, idempotent close and re-entry guards. Each real Flow
callback's invocation UID must match the listener's started-leaf UID for that flow.
The consumer distinguishes live native-stream closure while C is active from
original-source cleanup after C's native finish; neither is substituted for the other.
Fresh Surefire XML, resolved dependency provenance, JAR/POM hashes and actual JVM
class-loading checks are retained. Repetition does not prove registration or model
disposal: the automated **no safely disposable retained registrations** criterion
remains unverified for the later integrated ticket 25 gate. The resource-enabled and
shared-core admission binaries each passed this four-point matrix after local
installation; earlier artifact hashes remain historical. Ticket 10 is not fully
passed. These are exact tested points, not a support range or a full parallel
release. Intended IntelliJ Run/Debug/selection/navigation/Stop and the manual
6,000-flow workload remain pending, deferred until the end by user decision; nested
Launcher evidence does not complete those manual gates.