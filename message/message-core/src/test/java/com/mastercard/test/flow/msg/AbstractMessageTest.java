package com.mastercard.test.flow.msg;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Unpredictable;

/**
 * Exercises {@link AbstractMessage}
 */
@SuppressWarnings("static-method")
class AbstractMessageTest {

	private enum NPrdctbl implements Unpredictable {
		FOO, BAR
	}

	/**
	 * The minimal concrete type we'll use to test the functiuonality of
	 * {@link AbstractMessage}
	 */
	private static class ConcreteMessage extends AbstractMessage<ConcreteMessage> {

		private final String name;

		public ConcreteMessage( String name ) {
			this.name = name;
		}

		@Override
		protected String asHuman() {

			return (name
					+ "\n  "
					+ updates.stream()
							.map( u -> String.format( "%s=%s",
									u.field(),
									u.value() == AbstractMessage.DELETE
											? "The special deletion value"
											: u.value() ) )
							.collect( joining( "\n  " ) ))
									.trim();
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
		public Object get( String field ) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ConcreteMessage child() {
			return new ConcreteMessage( "child of " + name );
		}

		@Override
		public ConcreteMessage peer( byte[] content ) {
			throw new UnsupportedOperationException();
		}
	}

	/**
	 * Exercising field update addition
	 */
	@Test
	void set() {
		ConcreteMessage msg = new ConcreteMessage( "msg" )
				.set( "foo", "bar" );

		Assertions.assertEquals( ""
				+ "msg\n"
				+ "  foo=bar",
				msg.asHuman() );
	}

	/**
	 * Shows that masking operations are applied to a child of the message when they
	 * actually happen
	 */
	@Test
	void masking() {
		ConcreteMessage msg = new ConcreteMessage( "msg" )
				.masking( NPrdctbl.FOO, m -> m.delete( "foo" ) );

		Assertions.assertEquals( "msg", msg.assertable(),
				"no sources of unpredictablity" );

		Assertions.assertEquals( "msg", msg.assertable( NPrdctbl.BAR ),
				"A source, but no associated masks" );

		Assertions.assertEquals( ""
				+ "child of msg\n"
				+ "  foo=The special deletion value",
				msg.assertable( NPrdctbl.FOO ),
				"A source with an associated mask" );
	}

	/**
	 * Exercises copying masks from one message to another
	 */
	@Test
	void copyMasksTo() {
		ConcreteMessage a = new ConcreteMessage( "a" )
				.masking( NPrdctbl.FOO, m -> m.delete( "name" ) );
		ConcreteMessage b = new ConcreteMessage( "b" );

		Assertions.assertEquals( "b", b.assertable( NPrdctbl.FOO ) );

		ConcreteMessage ret = a.copyMasksTo( b );

		Assertions.assertSame( b, ret );
		Assertions.assertEquals( ""
				+ "child of b\n"
				+ "  name=The special deletion value",
				b.assertable( NPrdctbl.FOO ) );
	}

	/**
	 * Demonstrates the guards in place that prevent mutable fields in messages
	 *
	 * @throws Exception if something goes wrong with our reflection
	 */
	@SuppressWarnings("unchecked")
	@Test
	void mutableValues() throws Exception {

		// pitest runs the test multiple times in the same classloader, so we have to
		// clear the static immuatble type set back to default
		Field f = AbstractMessage.class.getDeclaredField( "immutableTypes" );
		f.setAccessible( true );
		((Set<Class<?>>) f.get( null )).remove( ArrayList.class );

		ConcreteMessage msg = new ConcreteMessage( "msg" );

		// by default we only let the basic value types be set
		IllegalArgumentException iae = assertThrows( IllegalArgumentException.class,
				() -> msg.set( "field", new ArrayList<>() ) );
		assertEquals( ""
				+ "Field 'field' - Possibly-mutable value type class java.util.ArrayList\n"
				+ "If you're sure that this type is immutable, then you can call\n"
				+ "  AbstractMessage.registerImmutable( ArrayList.class )\n"
				+ "to suppress this error.\n"
				+ "You can also override\n"
				+ "  AbstractMessage.validateValueType( field, value )\n"
				+ "in your subclass implementation to do validation more suitable\n"
				+ "for your requirements. Please be fully aware of the implications\n"
				+ "of mutable value types in your message fields: the interface\n"
				+ "contract of child() is put at risk and the resulting failures\n"
				+ "can be painful to debug", iae.getMessage() );

		// but you can disable that if you're really, really sure about what you're
		// doing
		AbstractMessage.registerImmutable( ArrayList.class );
		msg.set( "field", new ArrayList<>() );
	}
}
