package com.mastercard.test.flow.msg.bytes;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mastercard.test.flow.msg.AbstractMessage;

/**
 * Raw byte array message.
 * <ul>
 * <li>individual bytes are addressed by zero-based index</li>
 * <li>byte ranges are address by zero-based index ranges, e.g.:
 * <ul>
 * <li><code>x..y</code> addresses the bytes from index <code>x</code>
 * (inclusive) to <code>y</code> (exclusive)</li>
 * <li><code>..y</code> addresses the bytes from index <code>0</code>
 * (inclusive) to <code>y</code> (exclusive)</li>
 * <li><code>x..</code> addresses the bytes from index <code>x</code>
 * (inclusive) to the end of the array</li>
 * <li><code>..</code> addresses the bytes from index <code>0</code> (inclusive)
 * to the end of the array</li>
 * </ul>
 * </li>
 * </ul>
 */
public class Bytes extends AbstractMessage<Bytes> {

	private final Supplier<byte[]> base;

	/**
	 * Builds a new, empty, message
	 */
	public Bytes() {
		base = () -> new byte[0];
	}

	/**
	 * Builds a new message
	 *
	 * @param content The message content
	 */
	public Bytes( byte[] content ) {
		byte[] copy = copy( content );
		base = () -> copy;
	}

	private Bytes( Bytes parent ) {
		base = parent::build;
	}

	@Override
	public Bytes child() {
		return copyMasksTo( new Bytes( this ) );
	}

	@Override
	public Bytes peer( byte[] content ) {
		return copyMasksTo( new Bytes( content ) );
	}

	private byte[] build() {
		byte[] array = base.get();
		for( Update update : updates ) {
			byte[] content;
			if( update.value() == DELETE ) {
				content = new byte[0];
			}
			else if( update.value() instanceof Byte ) {
				content = new byte[] { (byte) update.value() };
			}
			else {
				content = (byte[]) update.value();
			}

			array = new Range( update.field() ).replace( content, array );
		}
		return array;
	}

	@Override
	public byte[] content() {
		return build();
	}

	@Override
	public Set<String> fields() {
		// not supported - how do you enumerate all the ways the bytes could be
		// addressed?
		return Collections.emptySet();
	}

	@Override
	protected String asHuman() {
		StringBuilder sb = new StringBuilder();
		for( byte b : build() ) {
			sb.append( String.format( "0b%s  0d%03d  0x%02X  %s\n",
					Integer.toBinaryString( (b & 0xFF) + 0x100 ).substring( 1 ),
					b & 0xFF,
					b,
					0 <= b // if the high bit isn't set...
							? Character.getName( b ) // ...then it's a valid character
							: "" ) );
		}
		return sb.toString();
	}

	@Override
	protected byte[] access( String field ) {
		return new Range( field ).infix( build() );
	}

	private static final byte[] copy( byte[] array ) {
		byte[] copy = new byte[array.length];
		System.arraycopy( array, 0, copy, 0, copy.length );
		return copy;
	}

	@Override
	public Bytes set( String field, Object value ) {
		// we want this to fail immediately so the exception traces to where the call is
		// being made rather than where the message is being compiled
		@SuppressWarnings("unused")
		Range r = new Range( field );
		return super.set( field, value );
	}

	private static class Range {
		private final int start;
		private final int end;
		private static final Pattern INDEX_PATTERN = Pattern.compile( "(\\d+)" );
		private static final Pattern RANGE_PATTERN = Pattern.compile( "(\\d*)\\.\\.(\\d*)" );

		/**
		 * @param s The range specification
		 */
		Range( String s ) {
			if( s == null ) {
				throw new IllegalArgumentException( "index range must be non-null" );
			}

			Matcher im = INDEX_PATTERN.matcher( s );
			Matcher rm = RANGE_PATTERN.matcher( s );
			if( im.matches() ) {
				start = Integer.parseInt( im.group( 1 ) );
				end = start + 1;
			}
			else if( rm.matches() ) {
				start = Optional.of( rm.group( 1 ) )
						.filter( d -> !d.isEmpty() )
						.map( Integer::parseInt )
						.orElse( 0 );
				end = Optional.of( rm.group( 2 ) )
						.filter( d -> !d.isEmpty() )
						.map( Integer::parseInt )
						.orElse( Integer.MAX_VALUE );
			}
			else {
				throw new IllegalArgumentException( "'" + s + "' is not a valid range" );
			}

			if( start > end ) {
				throw new IllegalArgumentException( "range indices must be in 'a..b' order, where a<=b" );
			}
		}

		/**
		 * Extracts the prefix content of this range
		 *
		 * @param array an array
		 * @return The bytes of the supplied array that come before this range
		 */
		byte[] prefix( byte[] array ) {
			int s = Math.min( start, array.length );
			byte[] prefix = new byte[s];
			System.arraycopy( array, 0, prefix, 0, prefix.length );
			return prefix;
		}

		/**
		 * Extracts the infix content of this range
		 *
		 * @param array an array
		 * @return The bytes of the array that lie in this range
		 */
		byte[] infix( byte[] array ) {
			int s = Math.min( start, array.length );
			int e = Math.min( end, array.length );
			byte[] infix = new byte[e - s];
			System.arraycopy( array, s, infix, 0, infix.length );
			return infix;
		}

		/**
		 * Extracts the suffix content of this range
		 *
		 * @param array an array
		 * @return The bytes of the supplied array that come after this range
		 */
		byte[] suffix( byte[] array ) {
			int e = Math.min( end, array.length );
			byte[] suffix = new byte[array.length - e];
			System.arraycopy( array, e, suffix, 0, suffix.length );
			return suffix;
		}

		/**
		 * Inserts content into this range in an array
		 *
		 * @param content The content to insert
		 * @param array   The array to insert into
		 * @return the new array
		 */
		byte[] replace( byte[] content, byte[] array ) {
			byte[] prefix = prefix( array );
			byte[] suffix = suffix( array );
			byte[] combined = new byte[prefix.length + content.length + suffix.length];
			System.arraycopy( prefix, 0, combined, 0, prefix.length );
			System.arraycopy( content, 0, combined, prefix.length, content.length );
			System.arraycopy( suffix, 0, combined, prefix.length + content.length, suffix.length );
			return combined;
		}
	}
}
