package com.mastercard.test.flow.msg.web;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openqa.selenium.WebDriver;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.mastercard.test.flow.Unpredictable;

/**
 * Exercises {@link WebSequenceTest}
 */
@SuppressWarnings("static-method")
class WebSequenceTest {

	/**
	 * Display of an empty sequence
	 */
	@Test
	void empty() {
		WebSequence ws = new WebSequence();
		Assertions.assertEquals(
				"""
						┌────────────┐
						│ Operations │
						├────────────┤
						│            │
						└────────────┘
						┌─────────────────────┐
						│ Parameters │ Values │
						├─────────────────────┤
						│            │        │
						└─────────────────────┘""",
				ws.assertable() );
	}

	/**
	 * Display of a non-empty sequence that has not been processed - only the input
	 * parameters are shown
	 */
	@Test
	void unprocessed() {
		WebSequence ws = new WebSequence()
				.set( "foo", "bar" )
				.set( "multiline", "so\nmany\nlines with a lot of data" );
		Assertions.assertEquals(
				"""
						┌────────────┐
						│ Operations │
						├────────────┤
						│            │
						└────────────┘
						┌───────────────────────────────────────┐
						│ Parameters │ Values                   │
						├───────────────────────────────────────┤
						│ foo        │ bar                      │
						│ multiline  │ so                       │
						│            │ many                     │ .
						│            │ lines with a lot of data │
						└───────────────────────────────────────┘""",
				ws.assertable() );
	}

	/**
	 * Validate tabs for page source
	 */
	@Test
	void pageSource() {
		WebSequence ws = new WebSequence()
				.set( "foo", "bar" )
				.set( "page_source", "<html><head>\n\t\t<title>Histogram</title>\n\t</head></html>" )
				.set( "multiline", "so\nmany\nlines" );
		Assertions.assertEquals(
				"""
						┌────────────┐
						│ Operations │
						├────────────┤
						│            │
						└────────────┘
						┌────────────────────────────────────────────────┐
						│ Parameters  │ Values                           │
						├────────────────────────────────────────────────┤
						│ foo         │ bar                              │
						│ multiline   │ so                               │
						│             │ many                             │
						│             │ lines                            │
						│ page_source │ <html><head>                     │
						│             │         <title>Histogram</title> │
						│             │     </head></html>               │
						└────────────────────────────────────────────────┘""",
				ws.assertable() );
	}

	/**
	 * Display of a processed sequence - parameters have been updated
	 */
	@Test
	void processed() {
		WebSequence ws = new WebSequence()
				.set( "a", "b" )
				.set( "c", "d" )
				.set( "e", "f" )
				.operation( "param update", ( driver, params ) -> {
					params.remove( "e" );
					params.put( "g", "h" );
					params.put( "c", "i" );
				} );

		WebSequence results = ws.peer( ws.process( null ) );

		Assertions.assertEquals(
				"""
						┌──────────────┐
						│ Operations   │
						├──────────────┤
						│ param update │
						└──────────────┘
						┌─────────────────────┐
						│ Parameters │ Values │
						├─────────────────────┤
						│ a          │ b      │
						│ c          │ i      │
						│ g          │ h      │
						└─────────────────────┘""",
				results.assertable() );
	}

	/**
	 * Field enumeration
	 */
	@Test
	void fields() {
		WebSequence ws = new WebSequence()
				.set( "a", "b" )
				.set( "c", "d" )
				.set( "e", "f" );

		Assertions.assertEquals( "[a, c, e]", ws.fields().toString() );

		WebSequence child = ws.child()
				.set( "g", "h" );

		Assertions.assertEquals( "[a, c, e, g]", child.fields().toString() );
	}

	/**
	 * Field access
	 */
	@Test
	void get() {
		WebSequence ws = new WebSequence()
				.set( "a", "b" );
		Assertions.assertNull( ws.get( "foo" ) );
		Assertions.assertEquals( "b", ws.get( "a" ) );
	}

	/**
	 * Content and operations are copied to peers
	 */
	@Test
	void peer() {

		AtomicReference<String> ref = new AtomicReference<>( "Operation has not been invoked!" );

		WebSequence ws = new WebSequence()
				.set( "a", "b" )
				.operation( "op", ( driver, params ) -> ref.set( "Operation has been invoked!" ) );

		WebSequence peer = ws.peer( ws.content() );

		Assertions.assertEquals(
				"""
						┌────────────┐
						│ Operations │
						├────────────┤
						│ op         │
						└────────────┘
						┌─────────────────────┐
						│ Parameters │ Values │
						├─────────────────────┤
						│ a          │ b      │
						└─────────────────────┘""",
				peer.assertable() );

		Assertions.assertEquals( "Operation has not been invoked!", ref.get() );

		peer.process( null );

		Assertions.assertEquals( "Operation has been invoked!", ref.get() );
	}

	/**
	 * Shows what happens when bad data is supplied to
	 * {@link WebSequence#peer(byte[])}
	 */
	@Test
	void peerFail() {
		WebSequence ws = new WebSequence();
		byte[] badBytes = "{]".getBytes( UTF_8 );
		UncheckedIOException uioe = assertThrows( UncheckedIOException.class,
				() -> ws.peer( badBytes ) );
		assertEquals( "Failed to parse '{]' ([123, 93])", uioe.getMessage() );
		assertEquals( ""
				+ "Unexpected close marker ']': expected '}' (for Object starting at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 1])\n"
				+ " at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 2]",
				uioe.getCause().getMessage() );
	}

	/**
	 * Content masking and inheritance
	 */
	@Test
	void masking() {
		Unpredictable rng = () -> "rng";

		WebSequence ws = new WebSequence()
				.set( "a", "b" )
				.set( "c", "d" )
				.masking( rng, m -> m.delete( "c" ) );

		WebSequence peer = ws.peer( ws.content() );
		Assertions.assertEquals(
				"""
						┌────────────┐
						│ Operations │
						├────────────┤
						│            │
						└────────────┘
						┌─────────────────────┐
						│ Parameters │ Values │
						├─────────────────────┤
						│ a          │ b      │
						└─────────────────────┘""",
				peer.assertable( rng ) );
	}

	/**
	 * Operation order
	 */
	@Test
	void sequence() {
		List<String> operations = new ArrayList<>();
		WebSequence parent = new WebSequence()
				.operation( "second", ( driver, params ) -> {
					operations.add( "second operation invoked with " + params );
					params.put( "c", "d" );
				} );
		WebSequence child = parent.child()
				.operation( "first", ( driver, params ) -> {
					operations.add( "first operation invoked with " + params );
					params.put( "a", "b" );
				} )
				.operation( "third", ( driver, params ) -> {
					operations.add( "third operation invoked with " + params );
					params.put( "e", "f" );
				} );

		WebSequence results = child.peer( child.process( null ) );

		Assertions.assertEquals( "["
				+ "first operation invoked with {}, "
				+ "second operation invoked with {a=b}, "
				+ "third operation invoked with {a=b, c=d}]",
				operations.toString() );

		Assertions.assertEquals(
				"""
						┌────────────┐
						│ Operations │
						├────────────┤
						│ first      │
						│ second     │
						│ third      │
						└────────────┘
						┌─────────────────────┐
						│ Parameters │ Values │
						├─────────────────────┤
						│ a          │ b      │
						│ c          │ d      │
						│ e          │ f      │
						└─────────────────────┘""",
				results.assertable() );
	}

	/**
	 * Operation failures return the current page source
	 */
	@Test
	void failure() {
		WebSequence ws = new WebSequence()
				.operation( "opname", ( driver, params ) -> {
					throw new UnsupportedOperationException( "ruh roh!" );
				} );
		WebDriver driver = Mockito.mock( WebDriver.class );
		when( driver.getCurrentUrl() ).thenReturn( "url" );
		when( driver.getPageSource() ).thenReturn( "source" );

		IllegalStateException ise = assertThrows( IllegalStateException.class,
				() -> ws.process( driver ) );

		assertEquals( ""
				+ "Operation 'opname' failed on page 'url'\n"
				+ "source", ise.getMessage() );
		assertEquals( "ruh roh!", ise.getCause().getMessage() );
	}

	/**
	 * Failure to get page details in the event of failure is coped with
	 */
	@Test
	void metafailure() {
		WebSequence ws = new WebSequence()
				.operation( "opname", ( driver, params ) -> {
					throw new UnsupportedOperationException( "ruh roh!" );
				} );
		WebDriver driver = Mockito.mock( WebDriver.class );
		when( driver.getCurrentUrl() )
				.thenThrow( new UnsupportedOperationException( "URL access failed!" ) );

		IllegalStateException ise = assertThrows( IllegalStateException.class,
				() -> ws.process( driver ) );

		assertEquals( ""
				+ "Operation 'opname' failed on page 'No URL'\n"
				+ "URL access failed!", ise.getMessage() );
		assertEquals( "ruh roh!", ise.getCause().getMessage() );
	}
}
