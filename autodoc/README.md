
<!-- title start -->

# autodoc

Documentation automation

[![javadoc](https://javadoc.io/badge2/com.mastercard.test.flow/autodoc/javadoc.svg)](https://javadoc.io/doc/com.mastercard.test.flow/autodoc)

 * [../flow](https://github.com/Mastercard/flow) Testing framework

<!-- title end -->

This project provides some utilities that help to ensure the accuracy of markdown documentation.

## RelativeLink

This checks the validity of relative links in your documentation. A link is valid if it points to a regular file, or to a directory that contains a `README.md` file.

Use it in a test like so:


<!-- snippet start -->

<!-- LinkTest!:relative_link_usage -->

```java
/**
 * Scans all of the markdown files in this project and checks relative link
 * validity in each
 *
 * @return test instances
 */
@TestFactory
Stream<DynamicTest> markdown() {
	Docs docs = new Docs( "..", Host.GITHUB, Assertions::assertEquals );
	RelativeLink link = new RelativeLink( docs );
	return docs.markdownFiles()
			.map( mdFile -> dynamicTest(
					mdFile.toString(),
					() -> link.check( mdFile ) ) );
}
```
[Snippet context](../doc/src/test/java/com/mastercard/test/flow/doc/LinkTest.java#L27-L41)

<!-- snippet end -->
