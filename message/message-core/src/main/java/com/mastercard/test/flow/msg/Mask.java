package com.mastercard.test.flow.msg;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.mastercard.test.flow.Message;

/**
 * Field-masking operations
 *
 * @see AbstractMessage#masking(com.mastercard.test.flow.Unpredictable,
 *      java.util.function.UnaryOperator)
 */
public class Mask implements Consumer<Message> {

	private Consumer<Message> operation = m -> {
		// no-op
	};

	/**
	 * Builds a no-op mask that can be built on
	 */
	Mask() {
	}

	/**
	 * Deletes fields from the message
	 *
	 * @param fields The fields to delete
	 * @return <code>this</code>
	 */
	public Mask delete( String... fields ) {
		return delete( Arrays.asList( fields ) );
	}

	/**
	 * Deletes fields from the message
	 *
	 * @param fields The fields to delete
	 * @return <code>this</code>
	 */
	public Mask delete( Stream<String> fields ) {
		return delete( fields.collect( toList() ) );
	}

	/**
	 * Deletes fields from the message
	 *
	 * @param fields The fields to delete
	 * @return <code>this</code>
	 */
	public Mask delete( Collection<String> fields ) {
		return andThen( m -> {
			for( String f : fields ) {
				m.set( f, AbstractMessage.DELETE );
			}
		} );
	}

	/**
	 * Deletes fields from the message
	 *
	 * @param fields The fields to <i>not</i> delete
	 * @return <code>this</code>
	 */
	public Mask retain( String... fields ) {
		return retain( Stream.of( fields ) );
	}

	/**
	 * Deletes fields from the message
	 *
	 * @param fields The fields to <i>not</i> delete
	 * @return <code>this</code>
	 */
	public Mask retain( Stream<String> fields ) {
		return retain( fields.collect( toSet() ) );
	}

	/**
	 * Deletes fields from the message
	 *
	 * @param fields The fields to <i>not</i> delete
	 * @return <code>this</code>
	 */
	public Mask retain( Set<String> fields ) {
		return andThen( m -> {
			for( String f : m.fields() ) {
				if( !fields.contains( f ) ) {
					m.set( f, AbstractMessage.DELETE );
				}
			}
		} );
	}

	/**
	 * Adds a custom masking operation to a single field
	 *
	 * @param field    The field to mask
	 * @param mutation How to mutate that field to make it suitable for assertion
	 * @return <code>this</code>
	 */
	public Mask field( String field, UnaryOperator<Object> mutation ) {
		return andThen( msg -> Optional.ofNullable( msg.get( field ) )
				.map( mutation )
				.ifPresent( value -> msg.set( field, value ) ) );
	}

	/**
	 * Adds a value-replacement operation to a single field
	 *
	 * @param field The field
	 * @param value The value that will be used to replace non-null values in the
	 *              masked field
	 * @return <code>this</code>
	 */
	public Mask replace( String field, Object value ) {
		return field( field, v -> value );
	}

	/**
	 * Adds a custom masking operation to a single string field
	 *
	 * @param field    the field to mask
	 * @param mutation How to mutate that field to make it suitable for assertion
	 * @return <code>this</code>
	 */
	public Mask string( String field, UnaryOperator<String> mutation ) {
		return field( field, o -> mutation.apply( String.valueOf( o ) ) );
	}

	/**
	 * Adds a length-aware masking operation to replace the contents of a single
	 * field
	 *
	 * @param field       The field to mask
	 * @param replacement The characters to use to replace the actual value. These
	 *                    will be repeated/truncated to cover all characters of the
	 *                    actual value
	 * @return <code>this</code>
	 */
	public Mask chars( String field, String replacement ) {
		if( replacement.isEmpty() ) {
			throw new IllegalArgumentException( "Replacement characters are mandatory" );
		}

		return string( field, value -> rightPad(
				new StringBuilder(), value.length(), replacement ) );
	}

	/**
	 * Adds a masking operation to replace the contents of a single field
	 *
	 * @param field The field to mask
	 * @param label The label for this field. This will be truncated/padded with
	 *              underscores to fit the length of the actual value.
	 * @return <code>this</code>
	 */
	public Mask label( String field, String label ) {
		return string( field, value -> {
			int lpad = (value.length() - label.length()) / 2;
			StringBuilder sb = new StringBuilder();
			rightPad( sb, lpad, "_" );
			sb.append( label );
			return rightPad( sb, value.length(), "_" );
		} );
	}

	/**
	 * @param sb     The builder to pad
	 * @param length The minimum length to pad to
	 * @param pad    The padding string
	 * @return The resulting padded string, which will be <i>exactly</i> the
	 *         specified length
	 */
	static String rightPad( StringBuilder sb, int length, String pad ) {
		while( sb.length() < length ) {
			sb.append( pad );
		}
		return sb.substring( 0, length );
	}

	/**
	 * Adds a masking operation that will try to match a single field's value
	 *
	 * @param field The field to mask
	 * @param regex A regular expression. Values that match will be replaced with an
	 *              appropriate message.
	 * @return <code>this</code>
	 */
	public Mask match( String field, Pattern regex ) {
		return string( field, value -> regex.matcher( value ).matches()
				? "Matches '" + regex.pattern() + "'"
				: null );
	}

	/**
	 * Adds a masking operation that will try to match a single field's value
	 *
	 * @param field The field to mask
	 * @param regex A regular expression. Values that match will be replaced with an
	 *              appropriate message.
	 * @return <code>this</code>
	 */
	public Mask match( String field, String regex ) {
		return match( field, Pattern.compile( regex ) );
	}

	/**
	 * Adds a partial field-masking operation
	 *
	 * @param field   the field to mask
	 * @param regex   A regular expression
	 * @param replace How to produce the replacement value from the resulting
	 *                {@link Matcher} (which has been found against the actual
	 *                value). This will be called for each match in the value and
	 *                the results concatenated and then trimmed of whitespace
	 * @return <code>this</code>
	 */
	public Mask captures( String field, Pattern regex, Function<Matcher, String> replace ) {
		return string( field, s -> {
			Matcher m = regex.matcher( s );
			StringBuilder masked = new StringBuilder();
			boolean found = false;
			while( m.find() ) {
				masked.append( replace.apply( m ) );
				found = true;
			}
			return found ? masked.toString().trim() : null;
		} );
	}

	/**
	 * Adds a partial field-masking operation
	 *
	 * @param field The field to mask
	 * @param regex A regular expression. Field values that match this regex will be
	 *              replaced by the concatenation of all capture groups in the
	 *              regex, or the entire match if there are no capture groups
	 * @return <code>this</code>
	 */
	public Mask captures( String field, String regex ) {
		return captures( field,
				Pattern.compile( regex ),
				m -> {
					if( m.groupCount() == 0 ) {
						return m.group( 0 );
					}
					return IntStream.rangeClosed( 1, m.groupCount() )
							.mapToObj( m::group )
							.collect( Collectors.joining() );
				} );
	}

	/**
	 * Adds a partial field-masking operation
	 *
	 * @param field   The field to mask
	 * @param regex   A regular expression
	 * @param replace The replacement value. Field values that match the supplied
	 *                regex will be replaced with this. Include capture group sigils
	 *                here (e.g.: <code>$1</code>) to include portions of the true
	 *                value.
	 * @return <code>this</code>
	 */
	public Mask captures( String field, String regex, String replace ) {
		return captures( field,
				Pattern.compile( regex ),
				m -> m.replaceAll( replace ) );
	}

	@Override
	public void accept( Message msg ) {
		operation.accept( msg );
	}

	@Override
	public Mask andThen( Consumer<? super Message> c ) {
		operation = operation.andThen( c );
		return this;
	}
}
