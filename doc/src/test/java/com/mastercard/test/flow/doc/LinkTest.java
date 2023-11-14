package com.mastercard.test.flow.doc;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import com.mastercard.test.flow.autodoc.Docs;
import com.mastercard.test.flow.autodoc.RelativeLink;

/**
 * Checks the validity of relative links in markdown files. A link is valid if
 * either:
 * <ul>
 * <li>The destination is a directory that contains a README.md file</li>
 * <li>The destination is a regular file</li>
 * </ul>
 */
@SuppressWarnings("static-method")
class LinkTest {

	// snippet-start:relative_link_usage
	/**
	 * Scans all of the markdown files in this project and checks relative link
	 * validity in each
	 *
	 * @return test instances
	 */
	@TestFactory
	Stream<DynamicTest> markdown() {
		return Docs.markdownFiles()
				.map( mdFile -> dynamicTest( mdFile.toString(),
						() -> RelativeLink.check( mdFile, Assertions::assertEquals ) ) );
	}
	// snippet-end:relative_link_usage
}
