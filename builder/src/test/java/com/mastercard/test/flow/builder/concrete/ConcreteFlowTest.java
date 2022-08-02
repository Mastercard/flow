package com.mastercard.test.flow.builder.concrete;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Unpredictable;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.util.Transmission.Type;

/**
 * Exercising {@link ConcreteFlow}
 */
@SuppressWarnings("static-method")
class ConcreteFlowTest {

	/**
	 * Exercising dependency population
	 */
	@Test
	void dependencies() {
		ConcreteFlow flow = new ConcreteFlow( null, null, null, null, null, null );

		ConcreteFlow built = flow.with( new ConcreteDependency( null, null, null ) )
				.complete();

		assertSame( flow, built );
		assertEquals( 1, flow.dependencies().count() );

		ConcreteDependency dep = new ConcreteDependency( null, null, null );
		assertThrows( UnsupportedOperationException.class,
				() -> flow.with( dep ),
				"Dependencies cannot be added after complete() call" );
	}

	/**
	 * Exercises error case of specifying a dependency where the source field does
	 * not exist
	 */
	@Test
	void missingSourceField() {

		Actor AVA = () -> "AVA";
		Actor BEN = () -> "BEN";
		Message noData = new Empty( null );

		Flow source = Creator.build( flow -> flow
				.call( a -> a
						.from( AVA ).to( BEN )
						.request( noData )
						.response( noData ) ) );

		IllegalStateException ise = Assertions.assertThrows( IllegalStateException.class,
				() -> Creator.build( flow -> flow
						.call( a -> a
								.from( AVA ).to( BEN )
								.request( noData )
								.response( noData ) )
						.dependency( source, dep -> dep
								.from( i -> i.responder() == BEN, Type.RESPONSE, "field" )
								.to( i -> i.responder() == BEN, Type.REQUEST, "field" ) ) ) );

		Assertions.assertEquals( ""
				+ "Failed to find dependency source value in:\n"
				+ "flow: []\n"
				+ "com.mastercard.test.flow.builder.concrete.ConcreteFlowTest.missingSourceField(ConcreteFlowTest.java:##)\n"
				+ "AVA 🠖 BEN [] Empty\n"
				+ "AVA 🠔 BEN [] Empty\n"
				+ "   msg:Empty message assertable content",
				ise.getMessage().replaceAll( ":\\d+", ":##" ) );
	}

	/**
	 * Exercises error case of specifying a dependency where the sink message does
	 * not exist
	 */
	@Test
	void missingSinkMessage() {

		Actor AVA = () -> "AVA";
		Actor BEN = () -> "BEN";
		Message noData = new Empty( "value" );

		Flow source = Creator.build( flow -> flow
				.call( a -> a
						.from( AVA ).to( BEN )
						.request( noData )
						.response( noData ) ) );

		IllegalStateException ise = Assertions.assertThrows( IllegalStateException.class,
				() -> Creator.build( flow -> flow
						.call( a -> a
								.from( AVA ).to( BEN )
								.request( null )
								.response( noData ) )
						.dependency( source, dep -> dep
								.from( i -> i.responder() == BEN, Type.RESPONSE, "field" )
								.to( i -> i.responder() == BEN, Type.REQUEST, "field" ) ) ) );

		Assertions.assertEquals( ""
				+ "Failed to find dependency sink message in:\n"
				+ "   []\n"
				+ "com.mastercard.test.flow.builder.concrete.ConcreteFlowTest__(ConcreteFlowTest.java:##)\n"
				+ "AVA 🠖 BEN [] null\n"
				+ "AVA 🠔 BEN [] Empty",
				ise.getMessage()
						.replaceAll( "\\.lambda.*\\(", "__(" )
						.replaceAll( ":\\d+", ":##" ) );
	}

	private static class Empty implements Message {

		private final String value;

		Empty( String value ) {
			this.value = value;
		}

		@Override
		public Message child() {
			return this;
		}

		@Override
		public Message peer( byte[] content ) {
			return this;
		}

		@Override
		public String assertable( Unpredictable... masks ) {
			return "Empty message assertable content";
		}

		@Override
		public byte[] content() {
			return null;
		}

		@Override
		public Set<String> fields() {
			return null;
		}

		@Override
		public Message set( String field, Object value ) {
			return this;
		}

		@Override
		public Object get( String field ) {
			return value;
		}

	}
}
