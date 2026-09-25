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

	private static final Logger DIAGNOSTICS = Logger.getLogger( Faults.class.getName() );

	/**
	 * Test abort and skip signals are runtime exceptions; both JUnit 4 and Jupiter
	 * use this neutral vocabulary. The package is matched by name as the framework
	 * is not a compile-time dependency of this module.
	 */
	private static final String TEST_CONTROL_PACKAGE = "org.opentest4j.";

	private Faults() {
		// no instances
	}

	/**
	 * A fault is ordinary when it is a runtime exception whose cause chain holds no
	 * {@link Error}, interruption, cancellation or test-control signal. Ordinary
	 * faults become diagnostics; anything else must fail or abort the test.
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
					|| cause instanceof CancellationException || testControl( cause ) ) {
				return false;
			}
		}
		return true;
	}

	private static boolean testControl( Throwable failure ) {
		for( Class<?> type = failure.getClass(); type != null; type = type.getSuperclass() ) {
			if( type.getName().startsWith( TEST_CONTROL_PACKAGE ) ) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @param message What went wrong, without stack trace or source message text
	 */
	static void diagnostic( String message ) {
		DIAGNOSTICS.warning( message );
	}
}
