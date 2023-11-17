
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
[Snippet context](../doc/src/test/java/com/mastercard/test/flow/doc/LinkTest.java#L22-L36)

<!-- snippet end -->

## CodeLink

This check tries to ensure that links to classes and methods are accurate.

Use it by adding [reference style links](https://www.markdownguide.org/basic-syntax/#reference-style-links) in your markdown, where the link ID is the name of the class and method that you're targeting.
Wrap the link destination tags in comments:

<pre><code>... use the &lsqb;`FooBar`&rsqb;&lsqb;FooBar&rsqb; class and its &lsqb;baz()&rsqb;&lsqb;FooBar.baz()&rsqb; method...

&lt;!-- code_link_start --&gt;
&lsqb;FooBar&rsqb;: src/main/java/com/example/FooBar.java
&lsqb;FooBar.baz()&rsqb;: src/main/java/com/example/FooBar.java#11-16
&lt;!-- code_link_end --&gt;</code></pre>

Add a test like so:

<!-- snippet start -->

<!-- CodeLinkTest:code_link_usage -->

```java
/**
 * Checks that code links in markdown documents are up-to-date
 *
 * @return per-file test instances
 */
@TestFactory
Stream<DynamicTest> markdown() {
	Docs docs = new Docs( "..", Host.GITHUB, Assertions::assertEquals );
	CodeLink link = new CodeLink( docs );
	return docs.markdownFiles()
			.map( mdFile -> dynamicTest(
					mdFile.toString(),
					() -> link.check( mdFile ) ) );
}
```
[Snippet context](../doc/src/test/java/com/mastercard/test/flow/doc/CodeLinkTest.java#L22-L35)

<!-- snippet end -->

Running the test will update the link destinations, raising a test failure if that changed the document.

If you're trying to link to a class but other classes that start with the
same name get in the way, end the classname with `!` to signal
that you're looking for an exact match. e.g.: if you're trying to link to
`Foo` but the search also turns up `FooBar` then use
link reference `Foo!`