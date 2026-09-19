package com.mastercard.test.flow.assrt;

import java.io.InterruptedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.logging.Logger;

/**
 * Classification and reporting of report and capture faults that did not fail
 * the test. These go to a logger rather than to the report, so that they cannot
 * feed back into a captured log backend.
 */
final class Faults {

	/** Named for the processor, which is where the documentation points users */
	private static final Logger DIAGNOSTICS = Logger.getLogger( FlowProcessor.class.getName() );

	private Faults() {
		// no instances
	}

	/**
	 * A fault is ordinary when it is a runtime exception whose cause chain holds no
	 * {@link Error}, interruption or cancellation. Ordinary faults become
	 * diagnostics; anything else must fail or abort the test.
	 *
	 * @param failure The fault
	 * @return <code>true</code> if the fault may be reduced to a diagnostic
	 */
	static boolean ordinary( Throwable failure ) {
		if( !(failure instanceof RuntimeException) ) {
			return false;
		}
		Set<Throwable> seen = Collections.newSetFromMap( new IdentityHashMap<>() );
		for( Throwable cause = failure; cause != null && seen.add( cause ); cause = cause.getCause() ) {
			if( cause instanceof Error || cause instanceof InterruptedException
					|| cause instanceof InterruptedIOException || cause instanceof ClosedByInterruptException
					|| cause instanceof CancellationException ) {
				return false;
			}
		}
		return true;
	}

	/**
	 * @param message What went wrong, without stack trace or source message text
	 */
	static void diagnostic( String message ) {
		DIAGNOSTICS.warning( message );
	}
}
