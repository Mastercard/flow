package com.mastercard.test.flow.assrt;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.time.Duration;

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
 * @param stopBudgetMiss  First owner-observed expiry, preserved after late
 *                        drainage
 */
public record ExecutionStatus(State state, Throwable cause, boolean incomplete, int selected,
		int admitted, int entered, int completed, int nativeTerminals, int owners,
		List<String> affected, Optional<StopBudgetMiss> stopBudgetMiss) {
	/**
	 * Bounded evidence at observation, not a reconstruction at an unseen deadline.
	 * Serial native evidence stays unavailable; unresolved handoffs are separate.
	 *
	 * @param budget              Configured stop budget
	 * @param elapsed             Monotonic elapsed time when the miss was observed
	 * @param cause               Original Stop cause
	 * @param pendingBodies       Entered bodies without completion
	 * @param pendingNative       Native leaves without finish, skip, coverage or
	 *                            unused proof; -1 in serial
	 * @param pendingHandoffs     Unresolved serial native handoffs
	 * @param cleanup             Required owner cleanup still pending
	 * @param owners              Whole grants retained at observation
	 * @param operations          Actual outstanding operations
	 * @param callbacks           Claimed cancellation callbacks not yet returned
	 * @param cancellationEffects Owner cancellation batch still in flight
	 * @param affected            Bounded identity prefixes, retaining no execution
	 *                            objects
	 */
	public record StopBudgetMiss(Duration budget, Duration elapsed, Throwable cause,
			int pendingBodies, int pendingNative, int pendingHandoffs, int cleanup, int owners,
			int operations, int callbacks, boolean cancellationEffects, List<String> affected) {
		/** Copies the bounded evidence rather than retaining a caller's list. */
		public StopBudgetMiss {
			affected = bounded( affected );
		}

		/** The original cause is a separate channel, not bounded diagnostic text. */
		@Override
		public String toString() {
			return "StopBudgetMiss[budget=" + budget + ", elapsed=" + elapsed
					+ ", pendingBodies=" + pendingBodies + ", pendingNative=" + pendingNative
					+ ", pendingHandoffs=" + pendingHandoffs + ", cleanup=" + cleanup
					+ ", owners=" + owners + ", operations=" + operations + ", callbacks=" + callbacks
					+ ", cancellationEffects=" + cancellationEffects + ", affected=" + affected + "]";
		}
	}

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
		Objects.requireNonNull( stopBudgetMiss );
		affected = bounded( affected );
	}

	private static List<String> bounded( List<String> affected ) {
		return affected.stream().limit( 5 )
				.map( id -> id.length() > 120 ? id.substring( 0, 117 ) + "..." : id ).toList();
	}
}
