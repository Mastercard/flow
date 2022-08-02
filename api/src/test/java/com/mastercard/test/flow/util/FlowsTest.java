package com.mastercard.test.flow.util;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.FieldAddress;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Metadata;
import com.mastercard.test.flow.Unpredictable;

/**
 * Exercises the flow-traversal methods in {@link Flows}
 */
@SuppressWarnings("static-method")
class FlowsTest {

	private static Flow flow( String desc, String tags, Flow basis, Flow... deps ) {
		Actor AVA = actor( "AVA" );
		Actor BEN = actor( "BEN" );
		Actor CHE = actor( "CHE" );

		Interaction root = interaction( AVA, "abc", BEN, "cba" );
		Interaction child = interaction( BEN, "def", CHE, "fed" );
		Mockito.when( root.children() ).thenAnswer( i -> Stream.of( child ) );

		Flow flw = Mockito.mock( Flow.class );
		Metadata meta = Mockito.mock( Metadata.class );
		Mockito.when( meta.description() ).thenReturn( desc );
		Mockito.when( meta.trace() ).thenReturn( "trace" );
		Mockito.when( meta.tags() )
				.thenReturn( Stream.of( tags.split( "," ) )
						.collect( toSet() ) );
		Mockito.when( meta.id() ).thenCallRealMethod();

		Mockito.when( flw.meta() ).thenReturn( meta );
		Mockito.when( flw.root() ).thenReturn( root );
		Mockito.when( flw.basis() ).thenReturn( basis );

		List<Dependency> dependencies = Stream.of( deps ).map( f -> {
			FieldAddress fa = Mockito.mock( FieldAddress.class );
			Mockito.when( fa.getFlow() ).thenReturn( Optional.of( f ) );
			Dependency dep = Mockito.mock( Dependency.class );
			Mockito.when( dep.source() ).thenReturn( fa );
			return dep;
		} )
				.collect( Collectors.toList() );
		Mockito.when( flw.dependencies() ).then( i -> dependencies.stream() );

		return flw;
	}

	private static final Map<String, Actor> actorCache = new HashMap<>();

	private static final Actor actor( String name ) {
		return actorCache.computeIfAbsent( name, n -> {
			Actor m = Mockito.mock( Actor.class );
			Mockito.when( m.name() ).thenReturn( n );
			return m;
		} );
	}

	private static final Interaction interaction( Actor from, String request,
			Actor to, String response ) {
		Interaction m = Mockito.mock( Interaction.class );
		Mockito.when( m.requester() ).thenReturn( from );
		Message req = message( request );
		Mockito.when( m.request() ).thenReturn( req );
		Mockito.when( m.responder() ).thenReturn( to );
		Message res = message( response );
		Mockito.when( m.response() ).thenReturn( res );
		return m;
	}

	private static final Message message( String name ) {
		return new MockMessage( name );
	}

	private static class MockMessage implements Message {
		private final String assertable;

		MockMessage( String assertable ) {
			this.assertable = assertable;
		}

		@Override
		public Message child() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Message peer( byte[] content ) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String assertable( Unpredictable... masks ) {
			return assertable;
		}

		@Override
		public byte[] content() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Set<String> fields() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Message set( String field, Object value ) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Object get( String field ) {
			throw new UnsupportedOperationException();
		}

	}

	/**
	 * Bumps pitest coverage
	 *
	 * @throws Exception reflection failure
	 */
	@Test
	void constructor() throws Exception {
		// pitest complains that we don't exercise the private constructor, so...
		Constructor<Flows> c = Flows.class.getDeclaredConstructor();
		assertTrue( Modifier.isPrivate( c.getModifiers() ) );
		c.setAccessible( true );
		c.newInstance();
		c.setAccessible( false );
	}

	/**
	 * Exercises {@link Flows#ancestors(Flow)}
	 */
	@Test
	void ancestors() {
		Flow root = flow( "root", "", null );
		Flow grandparent = flow( "grandparent", "", root );
		Flow parent = flow( "parent", "", grandparent );
		Flow child = flow( "child", "", parent );

		Assertions.assertEquals( ""
				+ "parent\n"
				+ "grandparent\n"
				+ "root",
				Flows.ancestors( child )
						.map( f -> f.meta().description() )
						.collect( joining( "\n" ) ) );
	}

	/**
	 * Exercises {@link Flows#interactions(Flow)}
	 */
	@Test
	void interactions() {
		Assertions.assertEquals( ""
				+ "AVA > abc\n"
				+ "BEN < cba\n"
				+ "BEN > def\n"
				+ "CHE < fed",
				Flows.interactions( flow( "flow", "", null ) )
						.map( i -> String.format( "%s > %s\n%s < %s",
								i.requester().name(), i.request().assertable(),
								i.responder().name(), i.response().assertable() ) )
						.collect( joining( "\n" ) ) );
	}

	/**
	 * Exercises {@link Flows#transmissions(Flow)}
	 */
	@Test
	void transmissions() {
		Assertions.assertEquals( ""
				+ "0 AVA>BEN REQUEST abc\n"
				+ "1 BEN>CHE REQUEST def\n"
				+ "1 CHE>BEN RESPONSE fed\n"
				+ "0 BEN>AVA RESPONSE cba",
				Flows.transmissions( flow( "flow", "", null ) ).stream()
						.map( i -> String.format( "%s %s>%s %s %s",
								i.depth(), i.transmitter().name(),
								i.receiver().name(), i.type(), i.message().assertable() ) )
						.collect( joining( "\n" ) ),
				"data content" );

		Assertions.assertEquals( ""
				+ "AVA 🠖 BEN [] MockMessage\n"
				+ "  BEN 🠖 CHE [] MockMessage\n"
				+ "  BEN 🠔 CHE [] MockMessage\n"
				+ "AVA 🠔 BEN [] MockMessage",
				Flows.transmissions( flow( "flow", "", null ) ).stream()
						.map( Transmission::toString )
						.collect( joining( "\n" ) ),
				"default formatting" );
	}

	/**
	 * Exercises {@link Flows#ID_ORDER}
	 */
	@Test
	void order() {
		Flow a = flow( "abc", "", null );
		Flow b = flow( "def", "x,y,z", null );
		Flow c = flow( "def", "i,j", null );

		Assertions.assertEquals( ""
				+ "abc []\n"
				+ "def [i, j]\n"
				+ "def [x, y, z]",
				Stream.of( a, b, c )
						.sorted( Flows.ID_ORDER )
						.map( f -> f.meta().id() )
						.collect( Collectors.joining( "\n" ) ) );
	}

	/**
	 * Exercises {@link Flows#intersects(Flow, java.util.Set)}
	 */
	@Test
	void intersects() {
		Flow flow = flow( "flow", "", null );

		Stream.of( "AVA", "BEN", "CHE" )
				.map( a -> Stream.of( actor( a ) ).collect( toSet() ) )
				.forEach( s -> Assertions.assertTrue(
						Flows.intersects( flow, s ),
						"for " + s ) );

		Stream.of( "DAN" )
				.map( a -> Stream.of( actor( a ) ).collect( toSet() ) )
				.forEach( s -> Assertions.assertFalse(
						Flows.intersects( flow, s ),
						"for " + s ) );
	}

	/**
	 * Exercises {@link Flows#structure(Flow)}
	 */
	@Test
	void structure() {
		Flow f = flow( "desc", "x,y,z", null );
		assertEquals( ""
				+ "desc [x, y, z]\n"
				+ "trace\n"
				+ "AVA 🠖 BEN [] msg_type\n"
				+ "  BEN 🠖 CHE [] msg_type\n"
				+ "  BEN 🠔 CHE [] msg_type\n"
				+ "AVA 🠔 BEN [] msg_type",
				Stream.of( Flows.structure( f ).split( "\n" ) )
						// mask out dynamic mockito class names
						.map( l -> l.replaceAll( "\\] .*$", "] msg_type" ) )
						.collect( joining( "\n" ) ) );

		assertEquals( null, Flows.structure( null ) );
	}

	/**
	 * Exercises {@link Flows#dependencies(Flow, java.util.Collection)}
	 */
	@Test
	void dependencies() {
		Flow a = flow( "a", "", null );
		Flow b = flow( "b", "", null, a );
		Flow c = flow( "c", "", null, a, b );
		Flow d = flow( "d", "", null, c );

		assertEquals( "c,a,b,a",
				Flows.dependencies( d, new ArrayList<Flow>() )
						.stream()
						.map( f -> f.meta().description() )
						.collect( joining( "," ) ) );
	}
}
