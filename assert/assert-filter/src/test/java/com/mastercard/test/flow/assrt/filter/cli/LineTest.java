package com.mastercard.test.flow.assrt.filter.cli;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Exercises the finctionality in {@link Line}
 */
@Tag("cli")
@SuppressWarnings("static-method")
class LineTest {

	/**
	 * Exercising word wrapping
	 */
	@Test
	void wrap() {
		testWrap( ""
				+ "a sentence that will be wrapped because it "
				+ "is longer than 20 chars in width", 20, 0,
				""
						+ "01234567890123456789\n"
						+ "a sentence that will\n"
						+ "be wrapped because\n"
						+ "it is longer than 20\n"
						+ "chars in width" );

		testWrap( ""
				+ "a sentence that contains a word that will be split", 20, 0,
				""
						+ "01234567890123456789\n"
						+ "a sentence that con-\n"
						+ "-tains a word that\n"
						+ "will be split" );

		testWrap( ""
				+ "a sentence that contains a word that will not be split", 20, 5,
				""
						+ "01234567890123456789\n"
						+ "a sentence that\n"
						+ "contains a word that\n"
						+ "will not be split" );

		testWrap( ""
				+ "a sentence that contains a word that will be split", 20, 2,
				""
						+ "01234567890123456789\n"
						+ "a sentence that con-\n"
						+ "-tains a word that\n"
						+ "will be split" );

		testWrap( ""
				+ "a sentence that conta a word that will not be split", 20, 2,
				""
						+ "01234567890123456789\n"
						+ "a sentence that\n"
						+ "conta a word that\n"
						+ "will not be split" );

		testWrap( ""
				+ "areallylongwordthatwillbesplitmultipletimes", 20, 0,
				""
						+ "01234567890123456789\n"
						+ "areallylongwordthat-\n"
						+ "-willbesplitmultipl-\n"
						+ "-etimes" );

		testWrap( ""
				+ "excessivegracevaluesriskinfinite"
				+ "loopsunlesshandled", 20, 100,
				""
						+ "01234567890123456789\n"
						+ "excessivegracevalue-\n"
						+ "-sriskinfiniteloops-\n"
						+ "-unlesshandled" );

		// Explores the splitting tipping points:
		// The start fragment would be too small
		testWrap( "01234567890123456 12345", 20, 1,
				""
						+ "01234567890123456789\n"
						+ "01234567890123456\n"
						+ "12345" );
		// the word is split
		testWrap( "0123456789012345 12345", 20, 1,
				""
						+ "01234567890123456789\n"
						+ "0123456789012345 12-\n"
						+ "-345" );
		// The end fragment would be too small
		testWrap( "012345678901234 12345", 20, 1,
				""
						+ "01234567890123456789\n"
						+ "012345678901234\n"
						+ "12345" );
	}

	/**
	 * Shows what happens when we try to wrap into very small spaces
	 */
	@Test
	void squeeze() {

		// squeezing the width down increases the number of lines
		testWrap( "wrappable", 9, 0, ""
				+ "01234567890123456789\n"
				+ "wrappable" );
		testWrap( "wrappable", 6, 0, ""
				+ "01234567890123456789\n"
				+ "wrapp-\n"
				+ "-able" );
		testWrap( "wrappable", 5, 0, ""
				+ "01234567890123456789\n"
				+ "wrap-\n"
				+ "-pab-\n"
				+ "-le" );
		testWrap( "wrappable", 4, 0, ""
				+ "01234567890123456789\n"
				+ "wra-\n"
				+ "-pp-\n"
				+ "-ab-\n"
				+ "-le" );

		// 3 is the minimum width in the general case
		testWrap( "wrappable", 3, 0, ""
				+ "01234567890123456789\n"
				+ "wr-\n"
				+ "-a-\n"
				+ "-p-\n"
				+ "-p-\n"
				+ "-a-\n"
				+ "-b-\n"
				+ "-le" );

		// try to go beyond and it'll fail...
		IllegalArgumentException iae = assertThrows( IllegalArgumentException.class,
				() -> testWrap( "wrappable", 2, 0, "" ) );
		assertEquals( "Failed to consume any characters while wrapping [-rappable] into width 2",
				iae.getMessage() );

		// ...unless the input text is also very short
		testWrap( "hi", 2, 0, ""
				+ "01234567890123456789\n"
				+ "hi" );
		testWrap( "!", 1, 0, ""
				+ "01234567890123456789\n"
				+ "!" );
	}

	private static void testWrap( String input, int width, int splitGrace, String expect ) {
		ArrayDeque<String> words = new ArrayDeque<>( Arrays.asList( input.split( " " ) ) );
		assertEquals( expect,
				"01234567890123456789\n" +
						Line.wrap( width, splitGrace, words )
								.stream()
								.map( l -> l.stream().collect( joining( " " ) ) )
								.collect( joining( "\n" ) ),
				"for input:\n" + input + "\nwidth: " + width + " splitGrace: " + splitGrace );
	}

	/**
	 * Exploring the behaviour of {@link Line#ellipsise(int, String)}
	 */
	@Test
	void ellipsise() {
		BiConsumer<String, String> test = ( in, out ) -> assertEquals(
				out, Line.ellipsise( 8, in ), "For " + in );

		test.accept( "123456", "123456" );
		test.accept( "1234567", "1234567" );
		test.accept( "12345678", "12345678" );
		test.accept( "123456789", "1234567…" );
		test.accept( "1234567890", "1234567…" );
	}
}
