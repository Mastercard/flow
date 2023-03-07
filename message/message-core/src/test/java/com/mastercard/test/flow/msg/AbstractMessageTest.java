package com.mastercard.test.flow.msg;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

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
	 * The minimal concrete type we'll use to test the functionality of
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
											: valueString( u.value() ) ) )
							.collect( joining( "\n  " ) ))
									.trim();
		}

		private static final String valueString( Object value ) {
			if( value.getClass().isArray() ) {
				StringBuilder sb = new StringBuilder();
				sb.append( "[" );
				for( int i = 0; i < Array.getLength( value ); i++ ) {
					if( i != 0 ) {
						sb.append( "," );
					}
					sb.append( valueString( Array.get( value, i ) ) );
				}
				sb.append( "]" );
				return sb.toString();
			}
			return String.valueOf( value );
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
		protected Object access( String field ) {
			return updates.stream()
					.filter( u -> u.field().equals( field ) )
					.findAny()
					.map( Update::value )
					.orElse( null );
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
				.set( "foo", "bar" )
				.set( "enum", NPrdctbl.FOO );

		Assertions.assertEquals( ""
				+ "msg\n"
				+ "  foo=bar\n"
				+ "  enum=FOO",
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
		// clear the static immutable type set back to default
		Field f = AbstractMessage.class.getDeclaredField( "immutableTypes" );
		f.setAccessible( true );
		((Set<Class<?>>) f.get( null )).remove( ArrayList.class );

		ConcreteMessage msg = new ConcreteMessage( "msg" );

		// by default we only let the basic value types be set
		IllegalArgumentException iae = assertThrows( IllegalArgumentException.class,
				() -> msg.set( "field", Collections.EMPTY_LIST ) );
		assertEquals( ""
				+ "Field 'field' - Possibly-mutable value type class java.util.Collections$EmptyList\n"
				+ "If you're sure that this type is immutable, then you can call\n"
				+ "  AbstractMessage.registerImmutable( EmptyList.class )\n"
				+ "to suppress this error.\n"
				+ "Alternatively you can use\n"
				+ "  AbstractMessage.registerDefensiveCopier( type, function )\n"
				+ "to make defensive copies of you mutable types.\n"
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

	/**
	 * Runs through the default allowed types and shows they pass the mutability
	 * filter
	 */
	@Test
	void validTypes() {
		ConcreteMessage msg = new ConcreteMessage( "msg" );
		msg.set( "boolean", true );
		msg.set( "byte", (byte) 1 );
		msg.set( "short", (short) 2 );
		msg.set( "character", 'a' );
		msg.set( "integer", 4 );
		msg.set( "float", 4.0f );
		msg.set( "long", 8L );
		msg.set( "double", 8.0 );
		msg.set( "string", "bcd" );
		msg.set( "bigdecimal", new BigDecimal( "1.234" ) );
		msg.set( "biginteger", new BigInteger( "5678" ) );
		msg.set( "uuid", new UUID( 12345L, 67890L ) );
		msg.set( "timestamp", new Timestamp( 1234567890L ) );

		assertEquals( true, msg.get( "boolean" ) );
		assertEquals( (byte) 1, msg.get( "byte" ) );
		assertEquals( (short) 2, msg.get( "short" ) );
		assertEquals( 'a', msg.get( "character" ) );
		assertEquals( 4, msg.get( "integer" ) );
		assertEquals( 4.0f, msg.get( "float" ) );
		assertEquals( 8L, msg.get( "long" ) );
		assertEquals( 8.0, msg.get( "double" ) );
		assertEquals( "bcd", msg.get( "string" ) );
		assertEquals( new BigDecimal( "1.234" ), msg.get( "bigdecimal" ) );
		assertEquals( new BigInteger( "5678" ), msg.get( "biginteger" ) );
		assertEquals( new UUID( 12345L, 67890L ), msg.get( "uuid" ) );
		assertEquals( new Timestamp( 1234567890L ), msg.get( "timestamp" ) );
	}

	/**
	 * Demonstrates the mechanism that allows mutable field types to be used via
	 * defensive copies
	 *
	 * @throws Exception if something goes wrong with our reflection
	 */
	@SuppressWarnings("unchecked")
	@Test
	void defensiveCopy() throws Exception {

		// pitest runs the test multiple times in the same classloader, so we have to
		// clear the static copier map set back to default
		Field f = AbstractMessage.class.getDeclaredField( "defensiveCopiers" );
		f.setAccessible( true );
		((Map<Class<?>, UnaryOperator<Object>>) f.get( null )).remove( StringBuilder.class );

		ConcreteMessage msg = new ConcreteMessage( "msg" );

		assertThrows( IllegalArgumentException.class,
				() -> msg.set( "field", new StringBuilder( "buff" ) ) );

		AbstractMessage.registerDefensiveCopier( StringBuilder.class,
				b -> new StringBuilder( "copy of [" ).append( b.toString() ).append( "]" ) );

		msg.set( "field", new StringBuilder( "buff" ) );

		assertEquals( ""
				+ "msg\n"
				+ "  field=copy of [buff]",
				msg.asHuman(),
				"The message holds a defensive copy of the supplied value" );

		assertEquals( "copy of [copy of [buff]]", msg.get( "field" ).toString(),
				"When queried, the message returns a defensive copy of the held value" );
	}

	/**
	 * Demonstrates that we reject ineffective defensive copiers
	 *
	 * @throws Exception if something goes wrong with our reflection
	 */
	@SuppressWarnings("unchecked")
	@Test
	void badDefensiveCopy() throws Exception {

		// pitest runs the test multiple times in the same classloader, so we have to
		// clear the static copier map set back to default
		Field f = AbstractMessage.class.getDeclaredField( "defensiveCopiers" );
		f.setAccessible( true );
		((Map<Class<?>, UnaryOperator<Object>>) f.get( null )).remove( StringBuilder.class );

		ConcreteMessage msg = new ConcreteMessage( "msg" );

		AbstractMessage.registerDefensiveCopier( StringBuilder.class,
				b -> b );

		IllegalStateException ise = assertThrows( IllegalStateException.class,
				() -> msg.set( "field", new StringBuilder( "buff" ) ) );

		assertEquals(
				"Ineffective defensive copy for field 'field', type class java.lang.StringBuilder",
				ise.getMessage() );
	}

	/**
	 * Shows that setting arrays takes a defensive copy
	 */
	@Test
	void arrayDefense() {
		ConcreteMessage msg = new ConcreteMessage( "msg" );

		Object[][] array = { { 1 } };
		msg.set( "field", array );
		array[0][0] = 2;
		assertEquals( "msg\n"
				+ "  field=[[1]]",
				msg.asHuman(),
				"The value set in the message has not changed" );

		array[0][0] = new Object();
		IllegalArgumentException iae = assertThrows( IllegalArgumentException.class,
				() -> msg.set( "field", array ) );
		assertEquals( ""
				+ "Field 'field[0][0]' - Possibly-mutable value type class java.lang.Object\n"
				+ "If you're sure that this type is immutable, then you can call\n"
				+ "  AbstractMessage.registerImmutable( Object.class )\n"
				+ "to suppress this error.\n"
				+ "Alternatively you can use\n"
				+ "  AbstractMessage.registerDefensiveCopier( type, function )\n"
				+ "to make defensive copies of you mutable types.\n"
				+ "You can also override\n"
				+ "  AbstractMessage.validateValueType( field, value )\n"
				+ "in your subclass implementation to do validation more suitable\n"
				+ "for your requirements. Please be fully aware of the implications\n"
				+ "of mutable value types in your message fields: the interface\n"
				+ "contract of child() is put at risk and the resulting failures\n"
				+ "can be painful to debug", iae.getMessage(),
				"Array contents are searched for suspicious types" );
	}
}
