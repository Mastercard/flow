
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

## Parallel execution

`Flocessor` supports standard Jupiter parallel execution. Annotate the test
class or factory with `@Execution(ExecutionMode.CONCURRENT)` and enable Jupiter
parallel execution in `junit-platform.properties`. `Flocessor` does not create
an executor: Jupiter remains responsible for running each dynamic test body.

The first call to `tests()` prepares one run and freezes the fluent
configuration. Reusing that `Flocessor`, either by calling `tests()` again or by
changing its configuration, throws `IllegalStateException`.

Independent flows can invoke behavior callbacks, per-flow `Listener` callbacks,
`Checker` instances, `MotivationCustomizer`, and other shared user callbacks
concurrently. Implementations of those callbacks must therefore be thread-safe.
Context `Applicator` transitions are serialized, but an applicator must not rely
on different flows using the same Jupiter thread.

When reporting is enabled, each flow still updates the report as it completes.
The configured `LogCapture` is responsible for thread-safe state and attribution
when flows overlap. In particular, an implementation that attributes events only
by a shared time interval may assign events to the wrong flow. The existing
`LogCapture` interface remains supported; parallel execution does not require a
new capture API.

Replay remains supported but is deliberately serialized in canonical flow order,
even when Jupiter parallel execution is enabled.

The dynamic stream waits when emitted flows are still running and none of their
successors are ready. Cancelling already-emitted leaves before their executables
run can therefore leave the producer waiting; bound the overall test run in the
build or test harness when using cooperative cancellation.