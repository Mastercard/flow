package com.mastercard.test.flow.assrt;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.util.Flows;

/**
 * Tracks the processing results of {@link Flow}s. This allows us to skip
 * {@link Flow}s where their prerequisites have already failed
 */
public class History {

	/**
	 * The possible {@link Flow} processing states
	 */
	public enum Result {
		/**
		 * The {@link Flow} was processed and all assertions passed
		 */
		SUCCESS(true),
		/**
		 * The {@link Flow} was processed but the results did not match our expectations
		 */
		UNEXPECTED(true),
		/**
		 * We had an opportunity to process the {@link Flow}, but the root {@link Actor}
		 * is autonomous so we could not observe results
		 */
		NOT_OBSERVED(true),
		/**
		 * An error occurred when the {@link Flow} was processed
		 */
		ERROR(false),
		/**
		 * We had an opportunity to process the {@link Flow}, but we didn't do it
		 */
		SKIP(false),
		/**
		 * The {@link Flow} has not been processed yet
		 */
		PENDING(false);

		/**
		 * <code>true</code> if this result indicates that the flow was processed to
		 * completion
		 */
		public final boolean processed;

		Result( boolean processed ) {
			this.processed = processed;
		}
	}

	private final Map<Flow, Result> results = new HashMap<>();

	/**
	 * Records the outcome of {@link Flow} processing. This data will be used to
	 * inform the decision of whether to process later {@link Flow}s
	 *
	 * @param flow   The {@link Flow}
	 * @param result The outcome of processing that {@link Flow}
	 */
	public void recordResult( Flow flow, Result result ) {
		results.putIfAbsent( flow, result );
	}

	/**
	 * Gets the outcome for a {@link Flow}
	 *
	 * @param flow The {@link Flow} to query for
	 * @return The result of that {@link Flow}
	 */
	public Result get( Flow flow ) {
		return results.getOrDefault( flow, Result.PENDING );
	}

	/**
	 * Clears the saved {@link Flow} outcomes
	 */
	public void clear() {
		results.clear();
	}

	/**
	 * Looks for a reason to skip {@link Flow} execution
	 *
	 * @param flow         A {@link Flow} that we're thinking about processing
	 * @param statefulness <code>true</code> if the system is stateful (which means
	 *                     that missing dependencies that hit the system under test
	 *                     spell doom for a flow)
	 * @param system       The actors in the system under test
	 * @return The reason why we shouldn't, or the empty {@link Optional} if we
	 *         should
	 */
	public Optional<String> skipReason( Flow flow, State statefulness, Set<Actor> system ) {

		if( !AssertionOptions.SUPPRESS_BASIS_CHECK.isTrue() ) {
			Optional<String> basisFailure = Flows.ancestors( flow )
					.map( this::get )
					// One of our ancestors failed, so we are likely to fail in the same way. Let's
					// avoid flooding test results with duplicates of the same failure
					.filter( r -> r == Result.UNEXPECTED )
					.map( r -> "Ancestor failed" )
					.findFirst();

			if( basisFailure.isPresent() ) {
				return basisFailure;
			}
		}

		if( statefulness == State.FUL && !AssertionOptions.SUPPRESS_DEPENDENCY_CHECK.isTrue() ) {
			// the system is stateful and the check has not been suppressed...
			Optional<String> depFailure = flow.dependencies()
					.map( d -> d.source().flow() )
					// ... and the flow has a dependency that intersects with the system ...
					.filter( f -> Flows.intersects( f, system ) )
					.map( this::get )
					.filter( r -> !r.processed )
					// ... and that dependency has not been processed ...
					.map( r -> "Missing dependency" )
					// ... so there's no point proceeding
					.findFirst();

			if( depFailure.isPresent() ) {
				return depFailure;
			}
		}

		return Optional.empty();
	}
}
