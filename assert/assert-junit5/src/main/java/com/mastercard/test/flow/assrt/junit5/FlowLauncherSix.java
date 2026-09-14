package com.mastercard.test.flow.assrt.junit5;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherExecutionRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.core.LauncherExecutionRequestBuilder;

/**
 * Optional JUnit 6 public-overload adapter. Compiled by the normal JUnit 6
 * build; loaded only after public class-presence detection, never on the 5.10
 * path. The identical experimental CancellationToken is passed to native
 * execution.
 */
public final class FlowLauncherSix extends FlowLauncherBridge implements Launcher {

	/**
	 * Adds JUnit 6 execution-request support to the session-local bridge.
	 *
	 * @param delegate The public launcher being decorated
	 */
	public FlowLauncherSix( Launcher delegate ) {
		super( delegate );
	}

	@Override
	public void execute( LauncherExecutionRequest original ) {
		try( FlowNativeCall call = original.getTestPlan().isPresent()
				? planCall( original.getTestPlan().orElseThrow() )
				: requestCall( original.getDiscoveryRequest().orElseThrow() ) ) {
			if( call == null ) {
				delegate.execute( original );
				return;
			}
			var builder = original.getTestPlan().isPresent()
					? LauncherExecutionRequestBuilder.request( original.getTestPlan().orElseThrow() )
					: LauncherExecutionRequestBuilder.request( original.getDiscoveryRequest().orElseThrow() );
			delegate.execute( builder.listeners( listeners( call, original
					.getAdditionalTestExecutionListeners().toArray( TestExecutionListener[]::new ) ) )
					.cancellationToken( original.getCancellationToken() ).build() );
		}
	}
}
