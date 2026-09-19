
<!-- title start -->

# assert-junit4

JUnit4 comparison components

[![javadoc](https://javadoc.io/badge2/com.mastercard.test.flow/assert-junit4/javadoc.svg)](https://javadoc.io/doc/com.mastercard.test.flow/assert-junit4)

 * [../assert](..) Comparing models against systems

<!-- title end -->

## Overview

This module integrates the capabilities of `assert-core` into the junit4 testing framework.
It provides the `Flocessor` the `FlowRule` classes, which should be combined in a [Parameterised test](https://github.com/junit-team/junit4/wiki/parameterized-tests) that will exercise your system.

## Usage

After [importing the `bom`](../../bom):

```xml
<dependency>
  <!-- system assertion -->
  <groupId>com.mastercard.test.flow</groupId>
  <artifactId>assert-junit4</artifactId>
  <scope>test</scope>
</dependency>
```

There is a certain amount of unavoidable boilerplate required to hook the `Flocessor` and `FlowRule` into junit 4:

```java
@RunWith(Parameterized.class)
public class MyTest {

  private static final Flocessor flows = new Flocessor( "my flow test", mySystemModel )
    .system( /* The actors that are being exercised */ )
    .behaviour( asrt -> {
      // implement this to push data from asrt into your system 
      // and then put the system outputs back into asrt
    } );

  // Boilerplate from here on

  /** @return The {@link Flow} parameters */
  @Parameters(name = "{0}")
  public static Collection<Object[]> flows() {
    return flows.parameters();
  }

  /** Human-readable name for the current test case */
  @Parameter(0)
  public String name;

  /** The current {@link Flow} */
  @Parameter(1)
  public Flow flow;

  /** Captures test outcome */
  @Rule
  public FlowRule flowRule = flows.rule( () -> flow );

  /** Exercises the current {@link Flow} */
  @Test
  public void test() {
    flows.process( flow );
  }
}
```

The report is written as each flow is processed, so nothing needs to happen after
the parameterized cases finish. The one exception is `logs( CorrelatedCapture )`:
that log source is opened once for the run and must be closed with
`Flocessor.close()` from `@AfterClass`, after all cases have finished. A closed
`Flocessor` processes no further flows, so a class that is closed must create a
fresh `Flocessor` in `@Parameters` rather than in a static field.
