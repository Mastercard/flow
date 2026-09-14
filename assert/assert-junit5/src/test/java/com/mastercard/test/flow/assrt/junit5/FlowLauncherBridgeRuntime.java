package com.mastercard.test.flow.assrt.junit5;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.io.PrintWriter;

import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/**
 * Standalone baseline-compiled entry point for cached coherent runtime checks.
 */
public final class FlowLauncherBridgeRuntime {
	private FlowLauncherBridgeRuntime() {
		// Utility class.
	}

	/**
	 * Runs bridge observations and optional suites against the selected runtime
	 * classpath.
	 *
	 * @param args Optional additional suite class names
	 */
	public static void main( String[] args ) {
		var request = LauncherDiscoveryRequestBuilder.request()
				.selectors( selectClass( FlowLauncherBridgeTest.class ) )
				.configurationParameter( "junit.jupiter.execution.parallel.enabled", "false" );
		for( String name : args ) {
			request.selectors( selectClass( name ) );
		}
		var listener = new SummaryGeneratingListener();
		LauncherFactory.create( FlowLauncherBridgeTest.config() ).execute( request.build(), listener );
		listener.getSummary().printTo( new PrintWriter( System.out, true ) );
		listener.getSummary().printFailuresTo( new PrintWriter( System.out, true ) );
		if( listener.getSummary().getTestsSucceededCount() != 10 + (args.length == 0 ? 0 : 3)
				|| listener.getSummary().getTotalFailureCount() != 0 ) {
			throw new AssertionError( "Runtime bridge observations failed" );
		}
	}
}
