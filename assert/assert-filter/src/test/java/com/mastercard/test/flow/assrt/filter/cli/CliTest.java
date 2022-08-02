package com.mastercard.test.flow.assrt.filter.cli;

import static com.mastercard.test.flow.assrt.filter.Util.copypasta;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;

import org.jline.utils.AttributedStyle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Explores {@link Cli} behaviour
 */
@Tag("cli")
@SuppressWarnings("static-method")
class CliTest {

	/**
	 * Checks API fluency
	 */
	@Test
	void fluency() {
		Cli cli = new Cli( 40 );
		assertSame( cli, cli.box( "box", b -> {
			assertSame( b, b.box( "child", c -> {
				// nowt
			} ) );
			assertSame( b, b.descriptionList( dl -> {
				assertSame( dl, dl.item( "dt", "dd" ) );
			} ) );
			assertSame( b, b.indexedTaggedList( AttributedStyle::bold, itl -> {
				assertSame( itl, itl.item( 0, "itli", Arrays.asList( "tag" ) ) );
			} ) );
			assertSame( b, b.paragraph( "paragraph" ) );
			assertSame( b, b.section( "section" ) );
			assertSame( b, b.words( AttributedStyle::bold, Arrays.asList( "word" ) ) );
		} ) );
		assertSame( cli, cli.append( "" ) );
		assertSame( cli, cli.fill( ' ', 1 ) );
		assertSame( cli, cli.styled( AttributedStyle::bold, "" ) );
		assertSame( cli, cli.ellipsised( 10, AttributedStyle::bold, "" ) );
		assertSame( cli, cli.endLine( " ", "" ) );

	}

	/**
	 * Explores box layout
	 */
	@Test
	void nesting() {
		assertEquals( copypasta(
				"┌─ root ───────────────────────────────┐",
				"│ short content                        │",
				"├─ section ────────────────────────────┤",
				"│ long content that will be wrapped    │",
				"│ onto multiple lines                  │",
				"│┌─ child ────────────────────────────┐│",
				"││ areallylongsinglewordthatwillbesp- ││",
				"││ -litontomultiplelines              ││",
				"││┌─ grandchild ─────────────────────┐││",
				"││├─ subsection ─────────────────────┤││",
				"│││ anotherlongsinglewordthatwillbe- │││",
				"│││ -splitontomultiplelines          │││",
				"││└──────────────────────────────────┘││",
				"│└────────────────────────────────────┘│",
				"│ well that went well                  │",
				"└──────────────────────────────────────┘",
				"┌─ another box ────────────────────────┐",
				"└──────────────────────────────────────┘" ),
				copypasta( new Cli( 40 )
						.box( "root", b -> b
								.paragraph( "short content" )
								.section( "section" )
								.paragraph( "long content that will be wrapped onto multiple lines" )
								.box( "child", c -> c
										.paragraph( "areallylongsinglewordthatwillbesplitontomultiplelines" )
										.box( "grandchild", g -> g
												.section( "subsection" )
												.paragraph(
														"anotherlongsinglewordthatwillbesplitontomultiplelines" ) ) )
								.paragraph( "well that went well" ) )
						.box( "another box", b -> {
							// no content
						} )
						.content().toString() ) );
	}

	/**
	 * Explores title behaviour
	 */
	@Test
	void title() {
		assertEquals( copypasta(
				"┌──────────────────┐",
				"│ no title         │",
				"└──────────────────┘",
				"┌─ a long title … ─┐",
				"└──────────────────┘" ),
				copypasta( new Cli( 20 )
						.box( "", b -> b
								.paragraph( "no title" ) )
						.box( "a long title that will be cut short", b -> {
							// no content
						} )
						.content().toString() ) );
	}

	/**
	 * Shows text formating
	 */
	@Test
	void paragraph() {
		assertEquals( copypasta(
				"┌─ box ────────────────────────────────┐",
				"│ This content is long enough to be    │",
				"│ wrapped onto multiple lines          │",
				"│ This content                         │",
				"│ has newlines                         │",
				"│ already                              │",
				"│ Carriage returns                     │",
				"│ are never welcome                    │",
				"└──────────────────────────────────────┘" ),
				copypasta( new Cli( 40 )
						.box( "box", b -> b
								.paragraph( "This content is long enough to be wrapped onto multiple lines" )
								.paragraph( "This content\nhas newlines\nalready" )
								.paragraph( "Carriage returns\r\nare never welcome" ) )
						.content().toString() ) );
	}

	/**
	 * Shows text formating
	 */
	@Test
	void lines() {
		assertEquals( copypasta(
				"┌─ box ────────────────────────────────┐",
				"│ This content is long enough to be w… │",
				"│ This content                         │",
				"│ has newlines                         │",
				"│ already                              │",
				"│ Carriage returns                     │",
				"│ are never welcome                    │",
				"└──────────────────────────────────────┘" ),
				copypasta( new Cli( 40 )
						.box( "box", b -> b
								.line( "This content is long enough to be wrapped onto multiple lines" )
								.line( "This content\nhas newlines\nalready" )
								.line( "Carriage returns\r\nare never welcome" ) )
						.content().toString() ) );
	}

	/**
	 * Shows description list formatting
	 */
	@Test
	void descriptionList() {
		assertEquals( copypasta(
				"┌─ box ────────────────────────────────┐",
				"│ This is a description list           │",
				"│   first : description                │",
				"│  second : a description that will be │",
				"│           wrapped onto multiple lines│",
				"│   third : you get the idea           │",
				"└──────────────────────────────────────┘" ),
				copypasta( new Cli( 40 )
						.box( "box", b -> b
								.paragraph( "This is a description list" )
								.descriptionList( dl -> dl
										.item( "first", "description" )
										.item( "second", "a description that will be wrapped onto multiple lines" )
										.item( "third", "you get the idea" ) ) )
						.content().toString() ) );
	}

	/**
	 * Shows index/tagged item list layout
	 */
	@Test
	void indexedTaggedList() {
		assertEquals( copypasta(
				"┌─ box ────────────────────────────────┐",
				"│ This is a list of indexed and tagged │",
				"│ items                                │",
				"│   1 first a b c                      │",
				"│  15 long tag areallylongtagethatwi-  │",
				"│          -llbesplitontomultiplelines │",
				"│ 466 many tags a large set of tags    │",
				"│          that will be wrapped onto   │",
				"│          multiple lines              │",
				"│1000 big index                        │",
				"│   7 a long item that will be ellips… │",
				"└──────────────────────────────────────┘" ),
				copypasta( new Cli( 40 )
						.box( "box", b -> b
								.paragraph( "This is a list of indexed and tagged items" )
								.indexedTaggedList( AttributedStyle::bold, itl -> itl
										.item( 1, "first", Arrays.asList( "a", "b", "c" ) )
										.item( 15, "long tag", Arrays.asList( "areallylong"
												+ "tagethatwillbesplitontomultiplelines" ) )
										.item( 466, "many tags", Arrays.asList( ("a large "
												+ "set of tags that will be wrapped onto multiple lines")
														.split( " " ) ) )
										.item( 1000, "big index", Arrays.asList() )
										.item( 7, "a long item that will be ellipsised, hopefully "
												+ "nudging users into preferring tags",
												Arrays.asList() ) ) )
						.content().toString() ) );
	}
}
