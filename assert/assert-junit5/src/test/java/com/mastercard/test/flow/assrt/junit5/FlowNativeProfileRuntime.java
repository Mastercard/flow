package com.mastercard.test.flow.assrt.junit5;

import java.io.PrintWriter;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Standalone runner for the same profile tests on coherent cached runtimes. */
public final class FlowNativeProfileRuntime {
	private FlowNativeProfileRuntime() {
		// No instances.
	}

	/**
	 * Runs native profile checks with the expected optional-provider skip on older
	 * runtimes.
	 *
	 * @param args Unused
	 */
	public static void main( String[] args ) {
		SummaryGeneratingListener listener = new SummaryGeneratingListener();
		LauncherFactory.create( LauncherConfig.builder()
				.enableLauncherSessionListenerAutoRegistration( false )
				.enableTestExecutionListenerAutoRegistration( false ).build() )
				.execute( LauncherDiscoveryRequestBuilder.request()
						.selectors( DiscoverySelectors.selectClass( FlowNativeProfileTest.class ) )
						.configurationParameter( "junit.jupiter.execution.parallel.enabled", "false" ).build(),
						listener );
		listener.getSummary().printTo( new PrintWriter( System.out, true ) );
		listener.getSummary().printFailuresTo( new PrintWriter( System.out, true ) );
		boolean providers = java.util.Arrays.stream(
				org.junit.jupiter.api.parallel.ResourceLock.class.getMethods() )
				.anyMatch( m -> "providers".equals( m.getName() ) );
		if( listener.getSummary().getTestsFoundCount() != 15
				|| listener.getSummary().getTestsSucceededCount() != (providers ? 15 : 14)
				|| listener.getSummary().getTestsAbortedCount() != (providers ? 0 : 1)
				|| listener.getSummary().getTotalFailureCount() != 0 ) {
			throw new AssertionError( "Native profile runtime checks failed" );
		}
	}
}
