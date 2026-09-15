package com.mastercard.test.flow.assrt;

import java.util.List;
import java.util.Objects;

/**
 * Immutable, bounded execution evidence independent of Writer success. Counts
 * describe observations, not manufactured outcomes for selected but unentered
 * work. The original Throwable identity is retained, not rendered or rewritten.
 *
 * @param state           Logical admission/ownership state
 * @param cause           First observed stop, or null for normal execution
 * @param incomplete      Latched missing completion/drain evidence
 * @param selected        Prepared selection size
 * @param admitted        Native handoffs committed
 * @param entered         Actual Flow processing entries
 * @param completed       Actual Flow processing exits, including fatal exits
 * @param nativeTerminals Supplied native leaf terminals; -1 for provider-free
 *                        serial
 * @param owners          Whole grants still owned, including outstanding
 *                        operations
 * @param affected        At most five affected identity prefixes, each at most
 *                        120 chars
 */
public record ExecutionStatus(State state, Throwable cause, boolean incomplete, int selected,
		int admitted, int entered, int completed, int nativeTerminals, int owners,
		List<String> affected) {
	/** Ownership chronology, not test success. */
	public enum State {
		/** Admission may still proceed. */
		ACTIVE,
		/**
		 * Admission is closed; remaining native/use/cleanup evidence may still arrive.
		 */
		STOPPING,
		/** Owned work and required cleanup ended; a prior stop remains a stop. */
		QUIESCENT
	}

	/** Copies and bounds the supplied identity sample; it cannot retain a model. */
	public ExecutionStatus {
		Objects.requireNonNull( state );
		affected = affected.stream().limit( 5 )
				.map( id -> id.length() > 120 ? id.substring( 0, 117 ) + "..." : id ).toList();
	}
}
