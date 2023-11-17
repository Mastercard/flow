package com.mastercard.test.flow.doc;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import com.mastercard.test.flow.autodoc.Docs;
import com.mastercard.test.flow.autodoc.Docs.Host;
import com.mastercard.test.flow.autodoc.Snippets;

/**
 * Applies {@link Snippets} to the documents in this project
 */
@SuppressWarnings("static-method")
class SnippetTest {

	// snippet-start:snippets-usage
	/**
	 * Checks that code snippets in readmes are up-to-date
	 *
	 * @return per-file test instances
	 */
	@TestFactory
	Stream<DynamicTest> markdown() {
		Docs docs = new Docs( "..", Host.GITHUB, Assertions::assertEquals );
		Snippets snippets = new Snippets( docs );
		return docs.markdownFiles()
				.map( mdFile -> dynamicTest(
						mdFile.toString(),
						() -> snippets.check( mdFile ) ) );
	}
	// snippet-end:snippets-usage
}
