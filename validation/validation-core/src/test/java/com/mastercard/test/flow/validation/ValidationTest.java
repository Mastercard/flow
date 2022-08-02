package com.mastercard.test.flow.validation;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Metadata;

/**
 * Exercises {@link Validation}
 */
@SuppressWarnings("static-method")
class ValidationTest {

	/**
	 * Exercising offender formatting
	 */
	@Test
	void offenderString() {
		Violation v = new Violation( null, null, null, null );
		Flow left = flw( "left" );
		v.offender( left, ntr( "A", "B", "c,d" ) )
				.offender( left, ntr( "B", "C", "d,e" ) )
				.offender( flw( "right" ),
						ntr( "D", "E", "f" ),
						ntr( "F", "G", "h,i,j" ) );

		Assertions.assertEquals( ""
				+ "left\n"
				+ "trace for left\n"
				+ "  A->B [c, d]\n"
				+ "  B->C [d, e]\n"
				+ "right\n"
				+ "trace for right\n"
				+ "  D->E [f]\n"
				+ "  F->G [h, i, j]",
				v.offenderString() );
	}

	private static Flow flw( String id ) {
		Metadata meta = Mockito.mock( Metadata.class );
		Mockito.when( meta.id() ).thenReturn( id );
		Mockito.when( meta.trace() ).thenReturn( "trace for " + id );

		Flow flw = Mockito.mock( Flow.class );
		Mockito.when( flw.meta() ).thenReturn( meta );

		return flw;
	}

	private static Interaction ntr( String from, String to, String tags ) {
		Interaction ntr = Mockito.mock( Interaction.class );
		Actor req = actr( from );
		Mockito.when( ntr.requester() ).thenReturn( req );
		Actor res = actr( to );
		Mockito.when( ntr.responder() ).thenReturn( res );
		Mockito.when( ntr.tags() ).thenReturn(
				Stream.of( tags.split( "," ) ).collect( Collectors.toSet() ) );

		return ntr;
	}

	private static Actor actr( String name ) {
		Actor actr = Mockito.mock( Actor.class );
		Mockito.when( actr.name() ).thenReturn( name );
		return actr;
	}
}
