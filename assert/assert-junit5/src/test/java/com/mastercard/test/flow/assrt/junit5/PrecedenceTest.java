package com.mastercard.test.flow.assrt.junit5;

import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Unpredictable;
import com.mastercard.test.flow.assrt.Order;
import com.mastercard.test.flow.assrt.junit5.mock.Actrs;
import com.mastercard.test.flow.assrt.junit5.mock.Msg;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.builder.Deriver;
import com.mastercard.test.flow.util.Transmission.Type;

/**
 * Exercises the admission graph independently of Jupiter's stream consumption.
 */
@SuppressWarnings("static-method")
class PrecedenceTest {

	/** A flow becomes ready only after all of its direct dependencies finish. */
	@Test
	void dependencyReadiness() {
		Flow source = flow( "source" );
		Flow dependent = flow( "dependent", source );
		Flow independent = flow( "independent" );

		Precedence precedence = new Precedence( List.of( source, dependent, independent ) );
		assertEquals( List.of( 0, 2 ), precedence.roots() );
		assertEquals( Set.of( 1 ), precedence.successors( 0 ) );
		assertEquals( List.of( 1 ), precedence.readiness().finished( 0 ) );
	}

	/** The compact graph preserves every canonical model constraint. */
	@Test
	void canonicalConstraints() {
		Flow basis = flow( "basis" );
		Flow child = Deriver.build( basis, f -> f.meta( m -> m.description( "child" ) ) );
		Precedence ancestry = new Precedence( List.of( basis, child ) );
		assertEquals( Set.of( 1 ), ancestry.successors( 0 ) );

		Flow before = flow( "before" );
		Flow first = chained( "first", "serial" );
		Flow second = chained( "second", "serial" );
		Flow after = flow( "after" );
		Precedence chain = new Precedence( List.of( before, first, second, after ) );
		assertEquals( Set.of( 1 ), chain.successors( 0 ) );
		assertEquals( Set.of( 2 ), chain.successors( 1 ) );
		assertEquals( Set.of( 3 ), chain.successors( 2 ) );

		Flow publisherA = publisher( "publisherA" );
		Flow publisherB = publisher( "publisherB" );
		Flow destination = Creator.build( f -> f.meta( m -> m.description( "destination" ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
						.request( new FieldMessage( "request" ) ).response( new FieldMessage( "response" ) ) )
				.dependency( publisherA, d -> d.from( i -> true, Type.RESPONSE, ".+" )
						.to( i -> true, Type.REQUEST, ".+" ) )
				.dependency( publisherB, d -> d.from( i -> true, Type.RESPONSE, ".+" )
						.to( i -> true, Type.RESPONSE, ".+" ) ) );
		Precedence publication = new Precedence( List.of( publisherA, publisherB, destination ) );
		assertEquals( Set.of( 1, 2 ), publication.successors( 0 ) );

		Message shared = new FieldMessage( "shared" );
		Flow messageUserA = messageUser( "messageA", shared );
		Flow messageUserB = messageUser( "messageB", shared );
		assertEquals( Set.of( 1 ),
				new Precedence( List.of( messageUserA, messageUserB ) ).successors( 0 ) );

		Flow earlier = flow( "earlier" );
		Flow contextual = Creator.build( f -> f.meta( m -> m.description( "context" ) )
				.context( mock( Context.class ) ) );
		Flow later = flow( "later" );
		Precedence context = new Precedence( List.of( earlier, contextual, later ) );
		assertEquals( Set.of( 1 ), context.successors( 0 ) );
		assertEquals( Set.of( 2 ), context.successors( 1 ) );

		Precedence replay = new Precedence( List.of( earlier, later, before ), true );
		assertEquals( List.of( 0 ), replay.roots() );
		assertEquals( Set.of( 1 ), replay.successors( 0 ) );
		assertEquals( Set.of( 2 ), replay.successors( 1 ) );
		List<Flow> duplicate = List.of( earlier, earlier );
		assertEquals( "Duplicate selected Flow reference", assertThrows( IllegalArgumentException.class,
				() -> new Precedence( duplicate ) ).getMessage() );
		List<Flow> absent = List.of( flow( "absent", earlier ) );
		assertEquals( "Absent Flow prerequisite", assertThrows( IllegalArgumentException.class,
				() -> new Precedence( absent ) ).getMessage() );

		Flow cyclicChild = mock( Flow.class );
		Flow absentA = mock( Flow.class );
		Flow absentB = mock( Flow.class );
		when( cyclicChild.basis() ).thenReturn( absentA );
		when( absentA.basis() ).thenReturn( absentB );
		when( absentB.basis() ).thenReturn( absentA );
		List<Flow> cyclic = List.of( cyclicChild );
		assertEquals( "Cyclic Flow basis", assertThrows( IllegalArgumentException.class,
				() -> new Precedence( cyclic ) ).getMessage() );

		Flow cycleFirst = chained( "cycleFirst", "cycle" );
		Flow outside = flow( "outside", cycleFirst );
		Flow cycleLast = chained( "cycleLast", "cycle", outside );
		List<Flow> hardCycle = List.of( cycleFirst, cycleLast, outside );
		assertThrows( IllegalArgumentException.class,
				() -> new Precedence( hardCycle ) );
	}

	private static Flow flow( String name, Flow... prerequisites ) {
		return Creator.build( f -> {
			f.meta( m -> m.description( name ) );
			for( Flow prerequisite : prerequisites ) {
				f.prerequisite( prerequisite );
			}
		} );
	}

	private static Flow chained( String name, String chain, Flow... prerequisites ) {
		return Creator.build( f -> {
			f.meta( m -> m.description( name ).tags( t -> t.add( Order.CHAIN_TAG_PREFIX + chain ) ) );
			for( Flow prerequisite : prerequisites ) {
				f.prerequisite( prerequisite );
			}
		} );
	}

	private static Flow publisher( String name ) {
		return Creator.build( f -> f.meta( m -> m.description( name ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
						.request( new FieldMessage( "request" ) ).response( new FieldMessage( name ) ) ) );
	}

	private static Flow messageUser( String name, Message message ) {
		return Creator.build( f -> f.meta( m -> m.description( name ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
						.request( message ).response( new Msg( name ) ) ) );
	}

	private static final class FieldMessage implements Message {
		private String value;

		private FieldMessage( String value ) {
			this.value = value;
		}

		@Override
		public Message child() {
			return this;
		}

		@Override
		public Message peer( byte[] bytes ) {
			return new FieldMessage( new String( bytes, UTF_8 ) );
		}

		@Override
		public String assertable( Unpredictable... masks ) {
			return value;
		}

		@Override
		public byte[] content() {
			return value.getBytes( UTF_8 );
		}

		@Override
		public Set<String> fields() {
			return Set.of( "value" );
		}

		@Override
		public Message set( String field, Object newValue ) {
			value = String.valueOf( newValue );
			return this;
		}

		@Override
		public Object get( String field ) {
			return value;
		}
	}
}
