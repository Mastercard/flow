
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
@TestFactory
Stream<DynamicNode> myTest() {
  return new Flocessor( "my test name", mySystemModel )
    .system( /* The actors that are being exercised */ )
    .behaviour( asrt -> {
      // implement this to push data from asrt into your system 
      // and then put the system outputs back into asrt
    } ).tests();
}
```

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
Reporting retains its current immediate behavior. Unclassified resource ownership,
general resource scheduling, cancellation drainage and integrated final-only
reporting remain separate unfinished delivery work.

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

Before `tests()`, use `independent("named resource audit", predicate)` only for
flows actually assessed as having no shared resources, hidden state, surviving
asynchronous use or physical-worker affinity requirements. Audit the SUT, messages,
listeners and callbacks, not just model contexts. Every selected flow must match
at least one named rule; all predicates run during preparation. Missing coverage
is UNKNOWN and fails before any SUT use. `State.LESS` and empty contexts do not
establish independence.

Temporary tracer limits also reject reporting other than `Reporting.NEVER`, capture
other than `LogCapture.NO_OP`, replay, contexts/applicators, residue/checkers,
autonomous actors, chains, basis, shared message instances, fan-in, noncanonical
prerequisites and non-class source URIs. These are fail-closed implementation
boundaries, **not new permanent support restrictions**. General resource rules,
reservations, chains and cancellation are not implemented by `independent()`.

Direct execution preserves the original Launcher request and listeners. An
identity-owned discovery preview may be consumed once in the same live explicit
Launcher session. Imported/consumed previews and previews from the per-operation
sessions of `LauncherFactory.create()` are rejected. The isolated JUnit 6 helper
preserves the native execution overload and identical cancellation token; it does
not implement Flow cancellation polling or bounded stop/drain.

One factory readiness waiter releases successors after predecessor native terminal
evidence and drained processing. Native inline completion is supported; there is
no second body pool or idle-worker estimate. Flow History remains distinct from
native status: an external native failure does not automatically become a Flow
processing error. Successful disposal requires actual factory terminal and owned
drainage, never stream close alone. Exceptional incompleteness is diagnosed without
forced release or successful report finalization.

Real Launcher tests cover A-to-B message binding while independent C remains active,
real dependent aborts, callback context/restoration, native inline execution and
provider-free serial behavior. Baseline-compiled binaries have been run on coherent
5.10/1.10 and 6.0.3 stacks. Published-consumer and intended-IDE Run/Debug/selection/
navigation/Stop acceptance remain open; do not infer them from nested Launcher runs.