
<!-- title start -->

# validation-junit5

Junit5 validation components

[![javadoc](https://javadoc.io/badge2/com.mastercard.test.flow/validation-junit5/javadoc.svg)](https://javadoc.io/doc/com.mastercard.test.flow/validation-junit5)

 * [../validation](..) Checking model consistency

<!-- title end -->

## Usage

```xml

<dependency>
  <!-- model validation -->
  <groupId>com.mastercard.test.flow</groupId>
  <artifactId>validation-junit5</artifactId>
  <version>${flow.version}</version>
</dependency>
```

The `Validator` implementation provided by this module can used in a [dynamic test](https://junit.org/junit5/docs/current/user-guide/#writing-tests-dynamic-tests):

```java
@TestFactory
Stream<DynamicNode> checks() {
	return new Validator()
			.checking( MY_SYSTEM_MODEL )
			.with( AbstractValidator.defaultChecks() )
			.tests();
}
```
