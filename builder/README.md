
<!-- title start -->

# builder

Implementations of the Flow and Interaction interfaces

[![javadoc](https://javadoc.io/badge2/com.mastercard.test.flow/builder/javadoc.svg)](https://javadoc.io/doc/com.mastercard.test.flow/builder)

 * [../flow](https://github.com/Mastercard/flow) Testing framework

<!-- title end -->

## Overview

This module provides an API by which Interactions can be built and combined into Flow instances.

To create a Flow from scratch, use [Creator][Creator].
In this case we must supply _all_ of the test case's data - define every interaction and every scrap of message content.

To create a Flow that is based on an existing Flow, use [Deriver][Deriver].
In this case we only need to supply the updates that distinguish the derived flow from its basis - define the _diff_ between the two flows.

Example of API usage can be found in the unit tests:
 * [CreatorTest][CreatorTest]
 * [DeriverTest][DeriverTest]

and in the [example system model project](../example/app-model).

<!-- code_link_start -->

[Creator]: src/main/java/com/mastercard/test/flow/builder/Creator.java
[Deriver]: src/main/java/com/mastercard/test/flow/builder/Deriver.java
[CreatorTest]: src/test/java/com/mastercard/test/flow/builder/CreatorTest.java
[DeriverTest]: src/test/java/com/mastercard/test/flow/builder/DeriverTest.java

<!-- code_link_end -->

## Usage

After [importing the `bom`](../bom):

```xml
<dependency>
  <!-- flow construction -->
  <groupId>com.mastercard.test.flow</groupId>
  <artifactId>builder</artifactId>
</dependency>
```

Note that the above assumes that your system model is being defined in a module on its own and the system model is the published artifact of the module.
If the system model is being defined directly in the system's test suite then you should add `<scope>test</scope>` to this dependency.
