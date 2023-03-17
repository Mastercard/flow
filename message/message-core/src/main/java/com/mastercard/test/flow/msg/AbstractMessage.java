package com.mastercard.test.flow.msg;

import static java.util.stream.Collectors.toCollection;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Unpredictable;

/**
 * Convenience superclass for {@link Message} implementations
 *
 * @param <T> Self type
 */
public abstract class AbstractMessage<T extends AbstractMessage<T>>
		implements ExposedMasking {

	/**
	 * The value to pass when a field should be deleted
	 */
	public static final Object DELETE = new Object();

	/**
	 * Masking operations
	 */
	protected final Map<Unpredictable, Mask> masks = new HashMap<>();

	/**
	 * Message content updates
	 */
	protected final List<Update> updates = new ArrayList<>();

	/**
	 * Adds a masking operation
	 *
	 * @param source    The source of unpredictable data
	 * @param operation How to mask out the unpredictable fields from that source
	 * @return <code>this</code>
	 */
	public T masking( Unpredictable source, UnaryOperator<Mask> operation ) {
		masks().compute( source,
				( k, v ) -> operation.apply( Optional.ofNullable( v ).orElseGet( Mask::new ) ) );
		return self();
	}

	/**
	 * Copies masking operations to another instance
	 *
	 * @param msg The instance to copy to
	 * @return The supplied instance
	 */
	protected T copyMasksTo( T msg ) {
		msg.masks().putAll( masks );
		return msg;
	}

	@Override
	public Map<Unpredictable, Mask> masks() {
		return masks;
	}

	@Override
	public String assertable( Unpredictable... sources ) {

		List<Mask> toApply = Stream.of( sources )
				.map( masks::get )
				.filter( Objects::nonNull )
				.collect( Collectors.toList() );

		if( toApply.isEmpty() ) {
			return asHuman();
		}

		T child = child();
		for( Mask mask : toApply ) {
			mask.accept( child );
		}

		return child.asHuman();
	}

	/**
	 * @return A human-readable version of the message content, suitable for
	 *         comparison in assertions
	 */
	protected abstract String asHuman();

	@Override
	public abstract T child();

	@Override
	public abstract T peer( byte[] content );

	@Override
	public T set( String field, Object value ) {
		Object validValue = validateValueType( field, value );
		updates.add( new Update( field, validValue ) );
		return self();
	}

	@Override
	public final Object get( String field ) {
		Object value = access( field );
		if( value == null ) {
			return null;
		}
		return defensiveCopiers.getOrDefault(
				value.getClass(),
				o -> o )
				.apply( value );
	}

	/**
	 * Field accessor
	 *
	 * @param field The field address
	 * @return The field value
	 */
	protected abstract Object access( String field );

	private static final Set<Class<?>> immutableTypes = Stream.of(
			Boolean.class, Byte.class, Short.class, Character.class,
			Integer.class, Float.class,
			Long.class, Double.class,
			String.class,
			BigDecimal.class, BigInteger.class,
			UUID.class )
			.collect( toCollection( HashSet::new ) );

	/**
	 * This method is called for every field update. This gives implementors the
	 * opportunity to validate and alter values (e.g.: taking a defensive copy of
	 * mutable types)
	 *
	 * @param field The field address that we're trying to populate
	 * @param value The value that we've been asked to populate into the message
	 * @return A validated value for that field
	 */
	protected Object validateValueType( String field, Object value ) {
		if( value != null ) {
			// we can take a defensive copy
			if( value.getClass().isArray() ) {
				Object copy = Array.newInstance(
						value.getClass().getComponentType(),
						Array.getLength( value ) );
				for( int i = 0; i < Array.getLength( copy ); i++ ) {
					Array.set( copy, i,
							validateValueType( field + "[" + i + "]", Array.get( value, i ) ) );
				}
				return copy;
			}
			if( defensiveCopiers.containsKey( value.getClass() ) ) {
				Object copy = defensiveCopiers.get( value.getClass() ).apply( value );
				if( copy == value ) {
					throw new IllegalStateException( String.format(
							"Ineffective defensive copy for field '%s', type %s",
							field, value.getClass() ) );
				}
				return copy;
			}
			if( value != DELETE
					&& !value.getClass().isPrimitive()
					&& !value.getClass().isEnum()
					&& !immutableTypes.contains( value.getClass() ) ) {
				throw new IllegalArgumentException( ""
						+ "Field '" + field + "' - Possibly-mutable value type " + value.getClass() + "\n"
						+ "If you're sure that this type is immutable, then you can call\n"
						+ "  AbstractMessage.registerImmutable( " + value.getClass().getSimpleName()
						+ ".class )\n"
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
						+ "can be painful to debug" );
			}
		}
		return value;
	}

	/**
	 * Adds a type to the set considered to be immutable. This will allows values of
	 * that type to be populated into {@link AbstractMessage} implementations
	 *
	 * @param type the immutable type
	 */
	public static void registerImmutable( Class<?> type ) {
		immutableTypes.add( type );
	}

	private static final Map<Class<?>, UnaryOperator<Object>> defensiveCopiers = new HashMap<>();

	/**
	 * @param <T>    value type
	 * @param type   value type
	 * @param copier function to make a defensive copy of a value
	 */
	@SuppressWarnings("unchecked")
	public static <T> void registerDefensiveCopier( Class<T> type, UnaryOperator<T> copier ) {
		defensiveCopiers.put( type, (UnaryOperator<Object>) copier );
	}

	static {
		registerDefensiveCopier( Timestamp.class, t -> new Timestamp( t.getTime() ) );
	}

	/**
	 * @return <code>this</code>
	 */
	@SuppressWarnings("unchecked")
	protected T self() {
		return (T) this;
	}

	/**
	 * An encapsulation of a single field update
	 */
	public static class Update {
		private final String field;
		private final Object value;

		/**
		 * @param field The field address
		 * @param value The new field value
		 */
		public Update( String field, Object value ) {
			this.field = field;
			this.value = value;
		}

		/**
		 * @return The field address
		 */
		public String field() {
			return field;
		}

		/**
		 * @return The new field value
		 */
		public Object value() {
			return value;
		}
	}
}
