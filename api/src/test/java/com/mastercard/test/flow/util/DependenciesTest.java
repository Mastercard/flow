
package com.mastercard.test.flow.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.FieldAddress;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;

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

		Mocks() {

			when( snk.dependencies() ).thenReturn( Stream.of( dep ) );

			when( dep.source() ).thenReturn( srcAdr );
			when( dep.sink() ).thenReturn( snkAdr );
			when( dep.mutation() ).thenReturn( o -> String.valueOf( o ).toUpperCase() );

			when( srcNtr.requester() ).thenReturn( () -> "AVA" );
			when( srcNtr.responder() ).thenReturn( () -> "BEN" );
			when( srcNtr.tags() ).thenReturn(
					Stream.of( "a", "b", "c" ).collect( toSet() ) );

			when( srcAdr.isComplete() ).thenReturn( true );
			when( srcAdr.flow() ).thenReturn( src );
			when( srcAdr.getInteraction() ).thenReturn( Optional.of( srcNtr ) );
			when( srcAdr.getMessage() ).thenReturn( Optional.of( srcMsg ) );
			when( srcAdr.field() ).thenReturn( "source field" );

			when( snkAdr.isComplete() ).thenReturn( true );
			when( snkAdr.getMessage() ).thenReturn( Optional.of( snkMsg ) );
			when( snkAdr.field() ).thenReturn( "sink field" );

			when( srcMsg.peer( actual ) ).thenReturn( peer );

			when( peer.get( "source field" ) ).thenReturn( "source value" );
		}

		Stream<Flow> flows() {
			return Stream.of( src, snk );
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
		Message msg = new Dependencies( mocks.flows() )
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
		when( mocks.srcMsg.peer( ArgumentMatchers.any() ) )
				.thenThrow( npe );

		Dependencies d = new Dependencies( mocks.flows() );
		IllegalArgumentException iae = assertThrows( IllegalArgumentException.class,
				() -> d.publish( mocks.src, mocks.srcNtr, mocks.srcMsg, mocks.actual ) );

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
	 * Synchronous failure retains earlier writes and even a setter's own partial
	 * change. Repeated scheduling pairs must not erase any binding operation.
	 * Parsing failure before any binding is covered by {@link #parseFailure()}.
	 *
	 * @param fault The operation that throws
	 */
	@ParameterizedTest
	@ValueSource(strings = { "get", "mutation", "set", "set-after" })
	void publicationFaultsPreservePartialWritesAndOriginalCaller( String fault ) {
		Mocks mocks = new Mocks();
		Thread caller = Thread.currentThread();
		RuntimeException original = new IllegalStateException( fault );
		List<String> operations = new ArrayList<>();
		AtomicInteger gets = new AtomicInteger();
		AtomicInteger mutations = new AtomicInteger();
		AtomicInteger sets = new AtomicInteger();
		AtomicReference<Object> sink = new AtomicReference<>( "initial" );
		when( mocks.snk.dependencies() )
				.thenReturn( Stream.of( mocks.dep, mocks.dep, mocks.dep ) );
		when( mocks.srcMsg.peer( mocks.actual ) ).thenAnswer( invocation -> {
			assertSame( caller, Thread.currentThread() );
			operations.add( "peer" );
			return mocks.peer;
		} );
		when( mocks.peer.get( "source field" ) ).thenAnswer( invocation -> {
			assertSame( caller, Thread.currentThread() );
			int call = gets.incrementAndGet();
			operations.add( "get" + call );
			if( call == 2 && fault.equals( "get" ) )
				throw original;
			return "value" + call;
		} );
		when( mocks.dep.mutation() ).thenReturn( value -> {
			assertSame( caller, Thread.currentThread() );
			int call = mutations.incrementAndGet();
			operations.add( "mutation" + call );
			if( call == 2 && fault.equals( "mutation" ) )
				throw original;
			return value;
		} );
		when( mocks.snkMsg.set( Mockito.eq( "sink field" ), Mockito.any() ) )
				.thenAnswer( invocation -> {
					assertSame( caller, Thread.currentThread() );
					int call = sets.incrementAndGet();
					operations.add( "set" + call );
					if( call == 2 && fault.equals( "set" ) )
						throw original;
					sink.set( invocation.getArgument( 1 ) );
					if( call == 2 && fault.equals( "set-after" ) )
						throw original;
					return mocks.snkMsg;
				} );
		Dependencies publisher = new Dependencies( mocks.flows() );
		var failure = assertThrows( IllegalArgumentException.class,
				() -> publisher.publish( mocks.src, mocks.srcNtr, mocks.srcMsg, mocks.actual ) );
		assertSame( original, failure.getCause() );
		assertEquals( fault.equals( "set-after" ) ? "value2" : "value1", sink.get() );
		List<String> expected = switch( fault ) {
			case "get" -> List.of( "peer", "get1", "mutation1", "set1", "get2" );
			case "mutation" -> List.of( "peer", "get1", "mutation1", "set1", "get2", "mutation2" );
			default -> List.of( "peer", "get1", "mutation1", "set1", "get2", "mutation2", "set2" );
		};
		assertEquals( expected, operations,
				"no retry, rollback, replay or third binding after failure" );
	}

	/**
	 * Nothing should happen unless source and sink addresses are complete
	 */
	@Test
	void incompleteAddress() {
		{
			Mocks mocks = new Mocks();
			when( mocks.srcAdr.isComplete() ).thenReturn( false );
			new Dependencies( mocks.flows() )
					.publish( mocks.src, mocks.srcNtr, mocks.srcMsg, mocks.actual );
			Mockito.verifyNoInteractions( mocks.snkMsg );
		}
		{
			Mocks mocks = new Mocks();
			when( mocks.snkAdr.isComplete() ).thenReturn( false );
			new Dependencies( mocks.flows() )
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
			new Dependencies( mocks.flows() )
					.publish( wrong, mocks.srcNtr, mocks.srcMsg, mocks.actual );
			Mockito.verifyNoInteractions( mocks.snkMsg );
		}
		{
			Mocks mocks = new Mocks();
			Interaction wrong = Mockito.mock( Interaction.class );
			new Dependencies( mocks.flows() )
					.publish( mocks.src, wrong, mocks.srcMsg, mocks.actual );
			Mockito.verifyNoInteractions( mocks.snkMsg );
		}
		{
			Mocks mocks = new Mocks();
			Message wrong = Mockito.mock( Message.class );
			new Dependencies( mocks.flows() )
					.publish( mocks.src, mocks.srcNtr, wrong, mocks.actual );
			Mockito.verifyNoInteractions( mocks.snkMsg );
		}
	}

	/**
	 * Demonstrates the operation of
	 * {@link Dependencies#propagateStaticData(Stream)}
	 */
	@Test
	void propagateStaticData() {
		Mocks mocks = new Mocks();
		when( mocks.srcMsg.content() ).thenReturn( mocks.actual );

		Dependencies.propagateStaticData( mocks.flows() );

		verify( mocks.snkMsg ).set( "sink field", "SOURCE VALUE" );
	}
}
