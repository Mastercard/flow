
<!-- title start -->

# model

Implementations of the Model interface

[![javadoc](https://javadoc.io/badge2/com.mastercard.test.flow/model/javadoc.svg)](https://javadoc.io/doc/com.mastercard.test.flow/model)

 * [../flow](https://github.com/Mastercard/flow) Testing framework

<!-- title end -->

## Overview

This module provides an API by which Flows can be grouped and collected into a coherent model of system behaviour.

The three concrete classes are:
 * [`EagerModel`][EagerModel] - this will build all of its constituent flows immediately upon model construction
 * [`LazyModel`][LazyModel] - this combines multiple `EagerModel` implementations, and constructs them as required as Flows are requested.
 * [`CombineModel`][CombineModel] - combines multiple child `Model` instances.

The point of structuring Flow construction into separate models is to improve testing performance and iteration time.

For example, let's assume that the system under test has two separate functions, called `foo` and `bar`.
Our system model will thus contains a bunch Flow instances, some tagged with `foo`, and some tagged with `bar`.
Let's also assume that those flow instances are defined in two separate EagerModels, one for `foo` flows and one for `bar` flows, and those two models are combined in a LazyModel. 
Thus when we're iterating on changing the `foo` behaviour we can supply the `foo` tag to the assert component and avoid building all the `bar` flows that we don't even want to run.

<!-- code_link_start -->

[EagerModel]: src/main/java/com/mastercard/test/flow/model/EagerModel.java
[LazyModel]: src/main/java/com/mastercard/test/flow/model/LazyModel.java
[CombineModel]: src/main/java/com/mastercard/test/flow/model/CombineModel.java

<!-- code_link_end -->

## Usage

```xml
<dependency>
  <!-- flow grouping -->
  <groupId>com.mastercard.test.flow</groupId>
  <artifactId>model</artifactId>
  <version>${flow.version}</version>
</dependency>
```

Note that the above assumes that your system model is being defined in a module on its own and the system model is the published artifact of the module.
If the system model is being defined directly in the system's test suite then you should add `<scope>test</scope>` to this dependency.