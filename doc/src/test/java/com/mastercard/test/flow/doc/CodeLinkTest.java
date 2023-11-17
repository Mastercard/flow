package com.mastercard.test.flow.doc;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import com.mastercard.test.flow.autodoc.CodeLink;
import com.mastercard.test.flow.autodoc.Docs;
import com.mastercard.test.flow.autodoc.Docs.Host;

/**
 * Applies {@link CodeLink} to the documents in this project
 */
@SuppressWarnings("static-method")
class CodeLinkTest {

	// snippet-start:code_link_usage
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
	// snippet-end:code_link_usage
}
