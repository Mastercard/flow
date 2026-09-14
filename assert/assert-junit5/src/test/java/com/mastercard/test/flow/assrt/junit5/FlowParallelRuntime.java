package com.mastercard.test.flow.assrt.junit5;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.io.PrintWriter;

import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/**
 * Runs the same baseline-compiled real binding, serial, inline and 12/20 tests.
 */
public final class FlowParallelRuntime {
	private FlowParallelRuntime() {
		// No instances.
	}

	/**
	 * Runs the real-flow regression suites and rejects incomplete or failing
	 * observations.
	 *
	 * @param args Unused
	 */
	public static void main( String[] args ) {
		var request = LauncherDiscoveryRequestBuilder.request()
				.selectors( selectClass( FlowParallelBindingTest.class ),
						selectClass( FlowExecutionTest.class ) )
				.configurationParameter( "junit.jupiter.execution.parallel.enabled", "false" ).build();
		var listener = new SummaryGeneratingListener();
		LauncherFactory.create( FlowLauncherBridgeTest.config() ).execute( request, listener );
		listener.getSummary().printTo( new PrintWriter( System.out, true ) );
		listener.getSummary().printFailuresTo( new PrintWriter( System.out, true ) );
		if( listener.getSummary().getTestsFoundCount() != 13
				|| listener.getSummary().getTestsSucceededCount() != 13
				|| listener.getSummary().getTotalFailureCount() != 0 ) {
			throw new AssertionError( "Real Flow parallel runtime observations failed" );
		}
	}
}
