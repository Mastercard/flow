package com.mastercard.test.flow.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.FieldAddress;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Model;

/**
 * Exercises the dependency fulfilment behaviour
 */
@SuppressWarnings("static-method")
class DependenciesTest {

	private static class Mocks {

		byte[] actual = "bytes".getBytes( UTF_8 );
		Dependency dep = Mockito.mock( Dependency.class );
		FieldAddress snkAdr = Mockito.mock( FieldAddress.class );
		FieldAddress srcAdr = Mockito.mock( FieldAddress.class );
		Flow snk = Mockito.mock( Flow.class );
		Flow src = Mockito.mock( Flow.class );
		Interaction srcNtr = Mockito.mock( Interaction.class );
		Message peer = Mockito.mock( Message.class );
		Message snkMsg = Mockito.mock( Message.class );
		Message srcMsg = Mockito.mock( Message.class );
		Model model = Mockito.mock( Model.class );

		Mocks() {
			Mockito.when( model.flows() ).thenCallRealMethod();
			Mockito.when( model.flows( Tags.empty(), Tags.empty() ) )
					.thenReturn( Stream.of( src, snk ) );

			Mockito.when( snk.dependencies() ).thenReturn( Stream.of( dep ) );

			Mockito.when( dep.source() ).thenReturn( srcAdr );
			Mockito.when( dep.sink() ).thenReturn( snkAdr );
			Mockito.when( dep.mutation() ).thenReturn( o -> String.valueOf( o ).toUpperCase() );

			Mockito.when( srcNtr.requester() ).thenReturn( () -> "AVA" );
			Mockito.when( srcNtr.responder() ).thenReturn( () -> "BEN" );
			Mockito.when( srcNtr.tags() ).thenReturn(
					Stream.of( "a", "b", "c" ).collect( toSet() ) );

			Mockito.when( srcAdr.isComplete() ).thenReturn( true );
			Mockito.when( srcAdr.flow() ).thenReturn( src );
			Mockito.when( srcAdr.getInteraction() ).thenReturn( Optional.of( srcNtr ) );
			Mockito.when( srcAdr.getMessage() ).thenReturn( Optional.of( srcMsg ) );
			Mockito.when( srcAdr.field() ).thenReturn( "source field" );

			Mockito.when( snkAdr.isComplete() ).thenReturn( true );
			Mockito.when( snkAdr.getMessage() ).thenReturn( Optional.of( snkMsg ) );
			Mockito.when( snkAdr.field() ).thenReturn( "sink field" );

			Mockito.when( srcMsg.peer( actual ) ).thenReturn( peer );

			Mockito.when( peer.get( "source field" ) ).thenReturn( "source value" );
		}
	}

	/**
	 * Happy path in a simple two-flow model
	 */
	@Test
	void publish() {

		// GIVEN a load of mocking
		Mocks mocks = new Mocks();

		// WHEN dependencies are processed
		Message msg = new Dependencies( mocks.model )
				.publish( mocks.src, mocks.srcNtr, mocks.srcMsg, mocks.actual );

		// THEN the returned message is the parsed peer
		Assertions.assertSame( mocks.peer, msg );
		// AND The sink message gets populated with the mutation of the value pulled
		// from the source message
		verify( mocks.snkMsg ).set( "sink field", "SOURCE VALUE" );
	}

	/**
	 * Shows what happens when message parsing fails
	 */
	@Test
	void parseFailure() {
		Mocks mocks = new Mocks();
		NullPointerException npe = new NullPointerException( "oh no!" );
		Mockito.when( mocks.srcMsg.peer( ArgumentMatchers.any() ) )
				.thenThrow( npe );

		IllegalArgumentException iae = assertThrows( IllegalArgumentException.class,
				() -> new Dependencies( mocks.model )
						.publish( mocks.src, mocks.srcNtr, mocks.srcMsg, mocks.actual ) );

		assertEquals( ""
				+ "Failed to parse AVA->BEN [a, b, c] msg_type from\n"
				+ "UTF8:[bytes]\n"
				+ " hex:[6279746573]",
				iae.getMessage()
						// mask out the dynamic class name from mockito
						.replaceAll( "(\\] ).*?( from)", "$1msg_type$2" ) );
		assertSame( npe, iae.getCause() );
	}

	/**
	 * Nothing should happen unless source and sink addresses are complete
	 */
	@Test
	void incompleteAddress() {
		{
			Mocks mocks = new Mocks();
			Mockito.when( mocks.srcAdr.isComplete() ).thenReturn( false );
			new Dependencies( mocks.model )
					.publish( mocks.src, mocks.srcNtr, mocks.srcMsg, mocks.actual );
			Mockito.verifyNoInteractions( mocks.snkMsg );
		}
		{
			Mocks mocks = new Mocks();
			Mockito.when( mocks.snkAdr.isComplete() ).thenReturn( false );
			new Dependencies( mocks.model )
					.publish( mocks.src, mocks.srcNtr, mocks.srcMsg, mocks.actual );
			Mockito.verifyNoInteractions( mocks.snkMsg );
		}
	}

	/**
	 * Nothing should happen if the published results don't match the dependency
	 */
	@Test
	void inapplicableResults() {
		{
			Mocks mocks = new Mocks();
			Flow wrong = Mockito.mock( Flow.class );
			new Dependencies( mocks.model )
					.publish( wrong, mocks.srcNtr, mocks.srcMsg, mocks.actual );
			Mockito.verifyNoInteractions( mocks.snkMsg );
		}
		{
			Mocks mocks = new Mocks();
			Interaction wrong = Mockito.mock( Interaction.class );
			new Dependencies( mocks.model )
					.publish( mocks.src, wrong, mocks.srcMsg, mocks.actual );
			Mockito.verifyNoInteractions( mocks.snkMsg );
		}
		{
			Mocks mocks = new Mocks();
			Message wrong = Mockito.mock( Message.class );
			new Dependencies( mocks.model )
					.publish( mocks.src, mocks.srcNtr, wrong, mocks.actual );
			Mockito.verifyNoInteractions( mocks.snkMsg );
		}
	}
}
