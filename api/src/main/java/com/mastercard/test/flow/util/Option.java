package com.mastercard.test.flow.util;

import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Configuration flag to control some aspect of framework behaviour
 */
public interface Option {
	/**
	 * Defines the name for the option
	 *
	 * @return A short human-readable name for this control
	 */
	String name();

	/**
	 * Defines a descriptiopn for the option's effects
	 *
	 * @return A description of the function of this control
	 */
	String description();

	/**
	 * Defines the system property that controls the option
	 *
	 * @return The system property name associated with this control
	 */
	String property();

	/**
	 * Defines the option value in the absence of user input
	 *
	 * @return The static value to assume in the absence of user input
	 */
	default String defaultValue() {
		return null;
	}

	/**
	 * Value accessor
	 *
	 * @return The value of the system property named as {@link #property()}, or
	 *         {@link #defaultValue()} if no such property is set
	 */
	default String value() {
		return System.getProperty( property(), defaultValue() );
	}

	/**
	 * Value accessor
	 *
	 * @param dflt The default value to return
	 * @return The value of the system property named as {@link #property()}, or
	 *         {@link #defaultValue()} if no such property is set, or the value from
	 *         the argument if no such static default is set
	 */
	default String orElse( Supplier<String> dflt ) {
		return Optional.ofNullable( value() ).orElseGet( dflt );
	}

	/**
	 * Clears the system property named as {@link #property()}
	 *
	 * @return The previously-set value
	 */
	default String clear() {
		return System.clearProperty( property() );
	}

	/**
	 * Clears the system property named as {@link #property()}
	 *
	 * @param value the new value
	 * @return The previously-set value
	 */
	default String set( String value ) {
		if( value == null ) {
			return System.clearProperty( property() );
		}
		return System.setProperty( property(), value );
	}

	/**
	 * Value accessor
	 *
	 * @return <code>true</code> if {@link #value()} return the string
	 *         <code>"true"</code>
	 */
	default boolean isTrue() {
		return "true".equals( value() );
	}

	/**
	 * Value accessor
	 *
	 * @return The property value parsed as an integer. If the user-supplied value
	 *         is not parseable, then we'll silently fall back to the default
	 */
	default int asInt() {
		try {
			return Integer.parseInt( value() );
		}
		catch( @SuppressWarnings("unused") Exception e ) {
			return Integer.parseInt( defaultValue() );
		}
	}

	/**
	 * Value accessor
	 *
	 * @param delimiter What to split the {@link #value()} around
	 * @return {@link #value()}, split around instances of the supplied delimiter
	 */
	default Stream<String> asList( String delimiter ) {
		return Optional.ofNullable( value() )
				.map( v -> Stream.of( v.split( delimiter ) ) )
				.orElse( Stream.empty() );
	}

	/**
	 * Value accessor
	 *
	 * @return {@link #value()}, split around commas
	 */
	default Stream<String> asList() {
		return asList( "," );
	}

	/**
	 * Builds the commandline argument that would set this {@link Option}'s current
	 * value in a new JVM
	 *
	 * @return The commandline argument, or <code>null</code> if this {@link Option}
	 *         has no value
	 */
	default String commandLineArgument() {
		return Optional.ofNullable( value() )
				.map( v -> String.format( "-D%s=%s", property(), v ) )
				.orElse( null );
	}

	/**
	 * Use in try-with-resources blocks where you want an {@link Option} to have a
	 * specific value and then revert to the previous value when the block ends
	 *
	 * @param value The value inside the try block
	 * @return The resource to declare in the try block
	 */
	default Temporary temporarily( String value ) {
		String prior = set( value );
		return () -> set( prior );
	}

	/**
	 * Controls the value of an {@link Option} within a try-with-resources block
	 */
	public interface Temporary extends AutoCloseable {
		@Override
		void close();
	}

	/**
	 * Mutable implementation of {@link Option}
	 */
	public static class Builder implements Option {
		private String name;
		private String description;
		private String property;
		private String defaultValue;

		@Override
		public String name() {
			return name;
		}

		/**
		 * Sets the name value
		 *
		 * @param n The new {@link #name()}
		 * @return <code>this</code>
		 */
		public Builder name( String n ) {
			name = n;
			return this;
		}

		@Override
		public String description() {
			return description;
		}

		/**
		 * Sets the description value
		 *
		 * @param d the new {@link #description()}
		 * @return <code>this</code>
		 */
		public Builder description( String d ) {
			description = d;
			return this;
		}

		@Override
		public String property() {
			return property;
		}

		/**
		 * Sets the property name value
		 *
		 * @param p the new {@link #property()}
		 * @return <code>this</code>
		 */
		public Builder property( String p ) {
			property = p;
			return this;
		}

		@Override
		public String defaultValue() {
			return defaultValue;
		}

		/**
		 * Sets the default value
		 *
		 * @param p the new {@link #defaultValue()}
		 * @return <code>this</code>
		 */
		public Builder defaultValue( String p ) {
			defaultValue = p;
			return this;
		}
	}
}
