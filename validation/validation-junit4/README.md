
<!-- title start -->

# validation-junit4

Junit4 validation components

[![javadoc](https://javadoc.io/badge2/com.mastercard.test.flow/validation-junit4/javadoc.svg)](https://javadoc.io/doc/com.mastercard.test.flow/validation-junit4)

 * [../validation](..) Checking model consistency

<!-- title end -->

## Usage

```xml

<dependency>
  <!-- message implementation utilities -->
  <groupId>com.mastercard.test.flow</groupId>
  <artifactId>validation-junit4</artifactId>
  <version>${flow.version}</version>
</dependency>
```

The `Validator` implementation provided by this module can used in a [parameterised test](https://junit.org/junit4/javadoc/4.12/org/junit/runners/Parameterized.html):

```java
@RunWith(Parameterized.class)
public class MyTest {
	@Parameters(name = "{0}")
	public static Collection<Object[]> flows() {
		return new Validator()
				.checking( MY_SYSTEM_MODEL )
				.with( AbstractValidator.defaultChecks() )
				.parameters();
	}

	@Parameter(0)
	public String name;

	@Parameter(1)
	public Runnable check;

	@Test
	public void test() {
		check.run();
	}
}
```
