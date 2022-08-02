package com.mastercard.test.flow.assrt;

import static com.mastercard.test.flow.assrt.TestModel.Actors.B;
import static com.mastercard.test.flow.assrt.TestModel.Actors.D;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.TestModel.Actors;
import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.report.data.Entry;
import com.mastercard.test.flow.report.data.FlowData;
import com.mastercard.test.flow.report.data.Index;

/**
 * Exercises the generic functionality of {@link AbstractFlocessor} via the
 * trivial {@link TestFlocessor} subclass
 */
@SuppressWarnings("static-method")
class AbstractFlocessorTest {

	/**
	 * Default test behaviour is to fail noisily
	 */
	@Test
	void noBehaviour() {
		TestFlocessor tf = new TestFlocessor( "noBehaviour", TestModel.abc() )
				.reporting( Reporting.QUIETLY )
				.system( State.LESS, Actors.B );

		tf.execute();

		assertEquals( "abc [] error No test behaviour specified", tf.events() );

		// This is also recorded to the report
		Reader r = new Reader( tf.report() );
		Index index = r.read();
		Entry ie = index.entries.get( 0 );
		FlowData fd = r.detail( ie );
		String msg = fd.logs.get( 0 ).message;

		assertTrue( ie.tags.contains( "ERROR" ), ie.tags.toString() );
		assertTrue( fd.tags.contains( "ERROR" ), fd.tags.toString() );
		assertEquals( "Encountered error: java.lang.IllegalStateException: No test behaviour specified",
				msg.replaceAll( "\tat .*", "" ).trim() );
	}

	/**
	 * Construction methods are fluent
	 */
	@Test
	void fluency() {
		TestFlocessor tf = new TestFlocessor( "fluency", TestModel.abc() );
		assertSame( tf, tf.behaviour( null ) );
		assertSame( tf, tf.system( null ) );
		assertSame( tf, tf.masking() );
		assertSame( tf, tf.applicators() );
		assertSame( tf, tf.checkers() );
		assertSame( tf, tf.logs( null ) );
	}

	/**
	 * Illustrates how defining the system under test influences the entrypoint
	 * interaction
	 */
	@Test
	void entryPoint() {
		TestFlocessor tf = new TestFlocessor( "entryPoint", TestModel.abc() )
				.behaviour( assrt -> {
					assrt.actual().response( assrt.expected().response().content() );
				} );

		BiConsumer<Actor, String> test = ( in, out ) -> {
			tf.system( State.LESS, in );
			tf.execute();
			assertEquals( out,
					copypasta(
							"events", tf.events(),
							"results", tf.results() ),
					"for " + in );
		};

		test.accept( Actors.A, copypasta(
				"events",
				"SKIP No interactions with system [A]",
				"results",
				"abc [] SKIP" ) );

		test.accept( Actors.B, copypasta(
				"events",
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abc(TestModel.java:_) A->B [] response",
				" | B response to A | B response to A |",
				"results",
				"abc [] SUCCESS" ) );

		test.accept( Actors.C, copypasta(
				"events",
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abc(TestModel.java:_) B->C [] response",
				" | C response to B | C response to B |",
				"results",
				"abc [] SUCCESS" ) );
	}

	/**
	 * Shows that test behaviours that make no assertions result in the flow being
	 * marked as skipped
	 */
	@Test
	void emptyTest() {
		TestFlocessor tf = new TestFlocessor( "emptyTest", TestModel.abc() )
				.system( State.FUL, B )
				.reporting( Reporting.QUIETLY )
				.behaviour( assrt -> {
					// no assertions!
				} );

		tf.execute();

		assertEquals( "SKIP No assertions made", tf.events() );

		// This is also recorded to the report
		Reader r = new Reader( tf.report() );
		Index index = r.read();
		Entry ie = index.entries.get( 0 );
		FlowData fd = r.detail( ie );
		String msg = fd.logs.get( 0 ).message;

		assertTrue( ie.tags.contains( "SKIP" ), ie.tags.toString() );
		assertTrue( fd.tags.contains( "SKIP" ), fd.tags.toString() );
		assertEquals( "No assertions made", msg );
	}

	/**
	 * Shows that the child is skipped if the basis flow fails. We do this to cut
	 * down on failure spam - the child is very likely to fail in exactly the same
	 * way as the basis.
	 */
	@Test
	void failedBasis() {
		TestFlocessor tf = new TestFlocessor( "failedBasis", TestModel.abcWithChild() )
				.system( State.FUL, B )
				.reporting( Reporting.QUIETLY )
				.behaviour( assrt -> {
					assrt.actual().response( "fail!".getBytes( UTF_8 ) );
				} );
		tf.execute();

		assertEquals( copypasta(
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abcWithChild(TestModel.java:_) A->B [] response",
				" | B response to A | fail! |",
				"",
				"SKIP Ancestor failed" ),
				copypasta( tf.events() ) );

		// This is also recorded to the report
		Reader r = new Reader( tf.report() );
		Index index = r.read();
		Entry ie = index.entries.get( 1 );
		FlowData fd = r.detail( ie );
		String msg = fd.logs.get( 0 ).message;

		assertTrue( ie.tags.contains( "SKIP" ), ie.tags.toString() );
		assertTrue( fd.tags.contains( "SKIP" ), fd.tags.toString() );
		assertEquals( "Skipping transaction: Ancestor failed", msg );
	}

	/**
	 * Shows that the child is skipped if a dependency flow suffers an error
	 */
	@Test
	void failedDependency() {
		TestFlocessor tf = new TestFlocessor( "failedDependency", TestModel.abcWithDependency() )
				.system( State.FUL, B )
				.reporting( Reporting.QUIETLY )
				.behaviour( assrt -> {
					throw new RuntimeException( "kaboom!" );
				} );
		tf.execute();

		assertEquals( copypasta(
				"dependency [] error kaboom!",
				"SKIP Missing dependency" ),
				copypasta( tf.events() ) );

		// This is also recorded to the report
		Reader r = new Reader( tf.report() );
		Index index = r.read();
		Entry ie = index.entries.get( 1 );
		FlowData fd = r.detail( ie );
		String msg = fd.logs.get( 0 ).message;

		assertTrue( ie.tags.contains( "SKIP" ), ie.tags.toString() );
		assertTrue( fd.tags.contains( "SKIP" ), fd.tags.toString() );
		assertEquals( "Skipping transaction: Missing dependency", msg );
	}

	/**
	 * Shows that flows are processed when they have implicit dependencies that are
	 * included in the system under test
	 */
	@Test
	void includedImplicit() {
		TestFlocessor tf = new TestFlocessor( "includedImplicit", TestModel.abcWithImplicit() )
				.system( State.FUL, B, D )
				.behaviour( assrt -> {
					assrt.actual().response( assrt.expected().response().content() );
				} );
		tf.execute();

		assertEquals( copypasta(
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abcWithImplicit(TestModel.java:_) A->B [] response",
				" | B response to A | B response to A |" ),
				copypasta( tf.events() ) );
	}

	/**
	 * Shows that flows are skipped if they have implicit dependencies that are not
	 * included in the system under test
	 */
	@Test
	void missingImplicit() {
		TestFlocessor tf = new TestFlocessor( "missingImplicit", TestModel.abcWithImplicit() )
				.system( State.FUL, B )
				.reporting( Reporting.QUIETLY )
				.behaviour( assrt -> {
					assrt.actual().response( assrt.expected().response().content() );
				} );
		tf.execute();

		assertEquals( copypasta(
				"SKIP Implicitly depends on D, which is not part of the system under test" ),
				copypasta( tf.events() ) );

		// This is also recorded to the report
		Reader r = new Reader( tf.report() );
		Index index = r.read();
		Entry ie = index.entries.get( 0 );
		FlowData fd = r.detail( ie );
		String msg = fd.logs.get( 0 ).message;

		assertTrue( ie.tags.contains( "SKIP" ), ie.tags.toString() );
		assertTrue( fd.tags.contains( "SKIP" ), fd.tags.toString() );
		assertEquals( "Skipping transaction: "
				+ "Implicitly depends on D, which is not part of the system under test",
				msg );
	}

	/**
	 * Shows that we assert on request messages <i>before</i> responses. This is a
	 * sneaky wee usability tweak that is enormously helpful when you're chasing a
	 * message change through a system
	 */
	@Test
	void messageTypeOrder() {
		TestFlocessor tf = new TestFlocessor( "", TestModel.abc() )
				.system( State.FUL, B )
				.behaviour( assrt -> {
					assrt.assertChildren( i -> true ).findFirst()
							.ifPresent( a -> a.actual().request( a.expected().request().content() ) );
					assrt.actual().response( assrt.expected().response().content() );
				} );
		tf.execute();

		assertEquals( copypasta(
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abc(TestModel.java:_) B->C [] request",
				" | B request to C | B request to C |",
				"",
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abc(TestModel.java:_) A->B [] response",
				" | B response to A | B response to A |" ),
				copypasta( tf.events() ) );
	}

	/**
	 * @param content Some strings
	 * @return A string that can be trivially copy/pasted into java source
	 */
	static String copypasta( String... content ) {
		return copypasta( Stream.of( content ) );
	}

	/**
	 * @param content Some strings
	 * @return A string that can be trivially copy/pasted into java source
	 */
	static String copypasta( Collection<String> content ) {
		return copypasta( content.stream() );
	}

	/**
	 * @param content Some strings
	 * @return A string that can be trivially copy/pasted into java source
	 */
	static String copypasta( Stream<String> content ) {
		return content
				.map( s -> s.replaceAll( "\r", "" ) )
				.flatMap( s -> Stream.of( s.split( "\n" ) ) )
				.map( s -> s.replaceAll( "\"", "'" ) )
				.collect( Collectors.joining( "\",\n\"", "\"", "\"" ) );
	}
}
