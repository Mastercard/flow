package com.mastercard.test.flow.assrt.junit5;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.builder.Chain;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.util.Tags;

/**
 * Validates {@link Flocessor} dynamic-leaf presentation.
 */
@SuppressWarnings("static-method")
class FlocessorTest {

	/**
	 * A flocessor represents one prepared run, so preparation freezes its
	 * configuration and cannot be repeated.
	 */
	@Test
	void oneShotPreparation() {
		Model model = model( (String) null );
		Flocessor flocessor = new Flocessor( "", model );

		flocessor.tests();

		assertThrows( IllegalStateException.class, flocessor::tests );
		assertThrows( IllegalStateException.class, () -> flocessor.behaviour( a -> {
			// no-op
		} ) );
		verify( model, times( 1 ) ).flows( anySet(), anySet() );
	}

	/**
	 * A simple sequence of flows with no chains
	 */
	@Test
	void simple() {
		expectNodes( model( null, null, null, null, null ),
				"test : 0 []",
				"test : 1 []",
				"test : 2 []",
				"test : 3 []",
				"test : 4 []" );
	}

	/**
	 * A single chain of a single flow
	 */
	@Test
	void link() {
		expectNodes( model( null, "a", null ),
				"test : 0 []",
				"test : 1 [chain:a]",
				"test : 2 []" );
	}

	/**
	 * Consecutive single-flow chains
	 */
	@Test
	void links() {
		expectNodes( model( "a", "b", "c" ),
				"test : 0 [chain:a]",
				"test : 1 [chain:b]",
				"test : 2 [chain:c]" );
	}

	/**
	 * A single multi-flow chain
	 */
	@Test
	void chain() {
		// in the middle
		expectNodes( model( null, "a", "a", "a", null ),
				"test : 0 []",
				"test : 1 [chain:a]",
				"test : 2 [chain:a]",
				"test : 3 [chain:a]",
				"test : 4 []" );

		// at the start
		expectNodes( model( "a", "a", "a", null ),
				"test : 0 [chain:a]",
				"test : 1 [chain:a]",
				"test : 2 [chain:a]",
				"test : 3 []" );

		// at the end
		expectNodes( model( null, "a", "a", "a" ),
				"test : 0 []",
				"test : 1 [chain:a]",
				"test : 2 [chain:a]",
				"test : 3 [chain:a]" );
	}

	/**
	 * Multiple multi-flow chains
	 */
	@Test
	void chains() {
		expectNodes( model( "a", "a", null, "b", "b", "c" ),
				"test : 0 [chain:a]",
				"test : 1 [chain:a]",
				"test : 2 []",
				"test : 3 [chain:b]",
				"test : 4 [chain:b]",
				"test : 5 [chain:c]" );
	}

	private static Model model( String... chains ) {
		List<Flow> flows = new ArrayList<>();
		for( int i = 0; i < chains.length; i++ ) {
			int idx = i;
			Flow flow = Creator
					.build( f -> f.meta( data -> data
							.description( String.valueOf( idx ) )
							.tags( Tags.add( Optional.ofNullable( chains[idx] )
									.map( v -> Chain.PREFIX + v )
									.orElse( "" ) ) ) ) );
			flows.add( flow );
		}
		Model model = mock( Model.class );
		when( model.flows( anySet(), anySet() ) )
				.thenReturn( flows.stream() );

		return model;
	}

	private static void expectNodes( Model model, String... expected ) {
		Flocessor flocessor = new Flocessor( "", model );
		List<String> actual = new ArrayList<>();
		flocessor.tests().forEach( node -> {
			stringify( node, "", actual );
			try {
				((DynamicTest) node).getExecutable().execute();
			}
			catch( Throwable ignored ) {
				// The fixture has no executable interactions; only presentation matters here.
			}
		} );
		assertEquals(
				copypasta( Stream.of( expected ) ),
				copypasta( actual.stream() ) );
	}

	private static void stringify( DynamicNode node, String prefix, List<String> lines ) {
		if( node instanceof DynamicTest ) {
			DynamicTest test = (DynamicTest) node;
			lines.add( prefix + "test : " + test.getDisplayName() );
		}
		else {
			throw new IllegalStateException( "unexpected node " + node.getClass() );
		}
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
}
