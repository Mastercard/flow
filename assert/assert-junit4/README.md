
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

```xml
<dependency>
  <!-- system assertion -->
  <groupId>com.mastercard.test.flow</groupId>
  <artifactId>assert-junit4</artifactId>
  <version>${flow.version}</version>
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
