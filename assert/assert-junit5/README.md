
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

The `Flocessor` is `AutoCloseable`, but the factory returns before its dynamic
tests run, so do not close it in the factory or with try-with-resources: close it
from `@AfterAll`, which is what publishes the report. `close()` fails if a flow is
still being processed; afterwards no further flows can be processed. Closing a
second time does nothing, unless the report failed to close, in which case the
failure is thrown again.

## Concurrent flows

`@FlowTest` supplies a `FlowExecution` to a `@TestFactory` parameter. Its
`PreparedFlocessor` is configured like a `Flocessor`, but `tests()` returns a lazy
stream: each flow is emitted only once every flow it must follow has finished.

```java
@FlowTest
class MyTest {
  @TestFactory
  Stream<DynamicNode> flows( FlowExecution execution ) {
    return execution.flocessor( "my test name", mySystemModel )
      .system( /* The actors that are being exercised */ )
      .behaviour( asrt -> {
        // exercise the system
      } ).tests();
  }
}
```

Enable standard Jupiter parallel execution (for example
`junit.jupiter.execution.parallel.enabled=true` and
`junit.jupiter.execution.parallel.mode.default=concurrent`) and independent flows
run at the same time. Nothing Flow-specific is configured; with parallel execution
disabled the same class runs serially with identical results.

Flows are kept apart only by what the model already declares:

 * a flow starts after the flows it has dependency bindings on have finished;
 * a flow starts after its nearest selected basis ancestor, so an ancestor's
   unexpected failure still skips its descendants as "Ancestor failed";
 * chain members run in order, one after another, with no outside flow between them;
 * flows that publish into or read the same destination message run one after another;
 * flows that apply a `Context` to the system run one after another, while flows
   without contexts may overlap them and leave the applied state untouched.

Everything else may overlap. Configuration is frozen at `tests()`; the report is
written once and closed when the test class finishes, including when the factory is
aborted or skipped. Interval-based `LogCapture` and replay are rejected for
concurrent runs; configure `logs( CorrelatedCapture )` to attribute log events by
correlation identifier. `progressTimeout( Duration )` bounds how long the factory
waits for a running flow before failing the run with a diagnostic naming the flows
still running; the default is ten minutes.

How many flows actually run at once is Jupiter's decision. Its `ForkJoinPool`
executor may run an emitted flow on the factory thread itself — when the queue is
already saturated, or once the last flow has been emitted and the factory joins its
children — so do not write flow bodies that wait for a sibling flow to start.
