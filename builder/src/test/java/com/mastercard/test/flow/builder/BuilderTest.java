package com.mastercard.test.flow.builder;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.util.Flows;

/**
 * Superclass for tests that exercise {@link Flow} construction
 */
public abstract class BuilderTest {

	/**
	 * Checks the state of a {@link Dependency}
	 *
	 * @param name        An ID for assertion messages
	 * @param dep         The object to test
	 * @param source      The expected source
	 * @param sourceMsg   The expected source message
	 * @param sourceField The expected source field
	 * @param sink        The expected sink
	 * @param sinkMsg     The expected sink message
	 * @param sinkField   The expected sink field
	 */
	protected static void assertDependency( String name, Dependency dep,
			Flow source, String sourceMsg, String sourceField,
			Flow sink, String sinkMsg, String sinkField ) {
		assertEquals( source, dep.source().flow(), name + " source flow" );
		assertEquals( sourceMsg, dep.source().getMessage()
				.map( Message::assertable )
				.orElse( null ),
				name + " source msg" );
		assertEquals( sourceField, dep.source().field(), name + " source field" );

		assertEquals( sink, dep.sink().flow(), name + " sink flow" );
		assertEquals( sinkMsg, dep.sink().getMessage()
				.map( Message::assertable )
				.orElse( null ),
				name + " sink msg" );
		assertEquals( sinkField, dep.sink().field(), name + " sink field" );

	}

	/**
	 * Checks the state of a {@link Dependency}
	 *
	 * @param name        An ID for assertion messages
	 * @param dep         The object to test
	 * @param source      The expected source
	 * @param sourceMsg   The expected source message
	 * @param sourceField The expected source field
	 * @param sink        The expected sink
	 * @param sinkMsg     The expected sink message
	 * @param sinkField   The expected sink field
	 * @param pulled      The value to apply dependency mutation to
	 * @param mutated     The mutated output
	 */
	protected static void assertMutatingDependency( String name, Dependency dep,
			Flow source, String sourceMsg, String sourceField,
			Flow sink, String sinkMsg, String sinkField,
			Object pulled, Object mutated ) {
		assertDependency( name, dep, source, sourceMsg, sourceField, sink, sinkMsg, sinkField );
		assertEquals( mutated, dep.mutation().apply( pulled ) );
	}

	/**
	 * @param flow A {@link Flow}
	 * @return A textual representation of the call structure
	 */
	protected static String structure( Flow flow ) {
		StringBuilder dump = new StringBuilder();
		Flows.transmissions( flow ).forEach( t -> dump
				.append( String.format( "%s%s->%s %s %s\n",
						indent( t.depth() ),
						t.transmitter(), t.receiver(),
						t.message().assertable(), t.source().tags() ) ) );
		return dump.toString().trim();
	}

	/**
	 * @param flow       A flow
	 * @param motivation An explanation for why we are making the assertion
	 * @param lines      The expected flow structure
	 */
	protected static void assertStructure( Flow flow, String motivation, String... lines ) {
		checkParentLinks( flow.root() );
		assertEquals(
				copypasta( lines ),
				copypasta( structure( flow ) ),
				motivation );
	}

	private static void checkParentLinks( Interaction ntr ) {
		ntr.children().forEach( c -> {
			assertSame( ntr, c.parent() );
			checkParentLinks( c );
		} );
	}

	/**
	 * @param content Some strings
	 * @return A string that can be trivially copy/pasted into java source
	 */
	private static String copypasta( String... content ) {
		return copypasta( Stream.of( content ) );
	}

	/**
	 * @param content Some strings
	 * @return A string that can be trivially copy/pasted into java source
	 */
	private static String copypasta( Stream<String> content ) {
		return content
				.map( s -> s.replaceAll( "\r", "" ) )
				.flatMap( s -> Stream.of( s.split( "\n" ) ) )
				.map( s -> s.replaceAll( "\"", "'" ) )
				.collect( Collectors.joining( "\",\n\"", "\"", "\"" ) );
	}

	private static String indent( int d ) {
		return IntStream.range( 0, d ).mapToObj( i -> "  " ).collect( joining() );
	}

}
