package com.mastercard.test.flow.validation.check;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Metadata;
import com.mastercard.test.flow.Model;

/**
 * Exercises {@link InteractionIdentityCheck}
 */
class InteractionIdentityCheckTest extends AbstractValidationTest {

	/***/
	InteractionIdentityCheckTest() {
		super( new InteractionIdentityCheck(),
				"Interaction Identity",
				"All interactions in a flow must have a unique identity" );
	}

	/**
	 * Null case - a single interaction
	 */
	@Test
	void single() {
		test( mdl( flw( "flow", ntr( "A", "B", "" ) ) ),
				"flow : pass" );
	}

	/**
	 * Repeated interactions between the same actors, but disambiguated with tags
	 */
	@Test
	void tagUnique() {
		test( mdl( flw( "flow", ntr( "A", "B", "",
				ntr( "B", "C", "first" ),
				ntr( "B", "C", "second" ) ) ) ),
				"flow : pass" );
	}

	/**
	 * Repeated interactions between the same actors
	 */
	@Test
	void violation() {
		test( mdl( flw( "flow", ntr( "A", "B", "",
				ntr( "B", "C", "tag" ),
				ntr( "B", "C", "tag" ) ) ) ),
				"  details: Shared interaction ID\n"
						+ " expected: null\n"
						+ "   actual: null\n"
						+ "offenders: flow\n"
						+ "trace for flow\n"
						+ "  B->C [tag]" );
	}

	private static Model mdl( Flow... flows ) {
		Model mdl = Mockito.mock( Model.class );
		Mockito.when( mdl.flows() ).thenReturn( Stream.of( flows ) );
		return mdl;
	}

	private static Flow flw( String id, Interaction root ) {
		Metadata meta = Mockito.mock( Metadata.class );
		Mockito.when( meta.id() ).thenReturn( id );
		Mockito.when( meta.trace() ).thenReturn( "trace for " + id );

		Flow flw = Mockito.mock( Flow.class );
		Mockito.when( flw.meta() ).thenReturn( meta );

		Mockito.when( flw.root() ).thenReturn( root );

		return flw;
	}

	private static Interaction ntr( String from, String to, String tags,
			Interaction... children ) {
		Interaction ntr = Mockito.mock( Interaction.class );
		Actor req = actr( from );
		Mockito.when( ntr.requester() ).thenReturn( req );
		Actor res = actr( to );
		Mockito.when( ntr.responder() ).thenReturn( res );
		Mockito.when( ntr.tags() ).thenReturn(
				Stream.of( tags.split( "," ) ).collect( Collectors.toSet() ) );

		Mockito.when( ntr.children() ).thenReturn( Stream.of( children ) );

		return ntr;
	}

	private static Actor actr( String name ) {
		Actor actr = Mockito.mock( Actor.class );
		Mockito.when( actr.name() ).thenReturn( name );
		return actr;
	}

}
