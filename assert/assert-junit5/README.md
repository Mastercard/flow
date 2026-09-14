
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

## Prepared serial caller (implementation preview)

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
or receipt channel. **`flow.parallel=true` currently fails before the factory body;
parallel execution is not delivered or enabled by this preview.**

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
parallel scheduling, native selector provenance, cancellation drainage and
integrated final-only reporting remain separate unfinished delivery work.

Real Launcher regressions have exercised the serial caller on coherent JUnit
5.10/Platform 1.10 and JUnit 6.0.3 stacks with Java 17. These are tested points,
not a published support range or evidence of IDE Run/Debug/Stop acceptance. Keep
consumer JUnit dependencies coherent; no JUnit 6 upgrade is required by this caller.