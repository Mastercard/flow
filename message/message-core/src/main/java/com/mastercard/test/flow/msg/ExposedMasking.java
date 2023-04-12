package com.mastercard.test.flow.msg;

import java.util.Map;

import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Unpredictable;

/**
 * Message specialisation that exposes masking operations.
 * <p>
 * It's common for one message type to be wrapped in another (e.g.: consider a
 * json string passed as the body of an HTTP request). In such cases it's easy
 * to make the mistake of defining masking operations on the wrapped message
 * rather than the wrapper. When it's time to apply the masking operations, that
 * call gets made on the <i>wrapper</i> and the wrapped content masking is never
 * triggered.
 * </p>
 * <p>
 * This is very frustrating to debug.
 * </p>
 * <p>
 * This interface provides access to the types of masking operations that a
 * message has. This allows wrapping message types to deal with, or at least
 * warn about, misplaced masking operations.
 * </p>
 */
public interface ExposedMasking extends Message {

	/**
	 * Accessor for masking operations.
	 *
	 * @return The sources of {@link Unpredictable} data for which this message has
	 *         masking operations
	 */
	Map<Unpredictable, Mask> masks();

	@Override
	ExposedMasking child();

	@Override
	ExposedMasking peer( byte[] content );

	@Override
	ExposedMasking set( String field, Object value );
}
