package com.mastercard.test.flow.assrt.junit5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.CancellationToken;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryListener;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.LauncherExecutionRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherExecutionRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * Optional 6-only tests of the real execution overload and cancellation token.
 */
@SuppressWarnings("static-method")
class FlowLauncherSixTest {
	/**
	 * Decoration preserves request, plan, token and listener identities for every
	 * execution mode.
	 */
	@Test
	void exactNativeInputsAndListenerOrderSurviveDirectAndPreviewDelegation() {
		String old = System.getProperty( FlowLauncherBridgeTest.HOOK );
		System.setProperty( FlowLauncherBridgeTest.HOOK, "false" );
		try( var session = LauncherFactory.openSession( FlowLauncherBridgeTest.config() ) ) {
			for( String mode : new String[] { null, "false", "true" } ) {
				for( boolean preview : new boolean[] { false, true } ) {
					var evidence = new FlowLauncherBridgeTest.Evidence();
					FlowLauncherBridgeTest.CURRENT.set( evidence );
					var request = FlowLauncherBridgeTest.request( mode );
					var token = CancellationToken.create();
					var executions = new java.util.ArrayList<LauncherExecutionRequest>();
					var discoveries = new java.util.ArrayList<LauncherDiscoveryRequest>();
					Launcher recording = (Launcher) Proxy.newProxyInstance( Launcher.class.getClassLoader(),
							new Class<?>[] { Launcher.class }, ( proxy, method, args ) -> {
								if( method.getName().equals( "execute" ) ) {
									assertEquals( LauncherExecutionRequest.class, method.getParameterTypes()[0] );
									executions.add( (LauncherExecutionRequest) args[0] );
								}
								try {
									return method.invoke( session.getLauncher(), args );
								}
								catch( InvocationTargetException failure ) {
									throw failure.getCause();
								}
							} );
					try( var wrapper = new FlowLauncherSix( recording ) ) {
						wrapper.registerLauncherDiscoveryListeners( new LauncherDiscoveryListener() {
							@Override
							public void launcherDiscoveryStarted( LauncherDiscoveryRequest actual ) {
								discoveries.add( actual );
							}
						} );
						var builder = preview
								? LauncherExecutionRequestBuilder.request( wrapper.discover( request ) )
								: LauncherExecutionRequestBuilder.request( request );
						TestExecutionListener second = new TestExecutionListener() {
							// Identity and relative listener order must be retained.
						};
						var original = builder.listeners( evidence, second ).cancellationToken( token ).build();
						wrapper.execute( original );
						assertEquals( 1, executions.size() );
						var actual = executions.get( 0 );
						assertSame( token, actual.getCancellationToken() );
						assertEquals( List.of( request ), discoveries );
						assertSame( request, discoveries.get( 0 ) );
						assertSame( original.getTestPlan().orElse( null ),
								actual.getTestPlan().orElse( null ) );
						assertSame( original.getDiscoveryRequest().orElse( null ),
								actual.getDiscoveryRequest().orElse( null ) );
						assertEquals( List.of( evidence, second ),
								List.copyOf( original.getAdditionalTestExecutionListeners() ) );
						var listeners = List.copyOf( actual.getAdditionalTestExecutionListeners() );
						assertSame( evidence, listeners.get( 0 ) );
						assertSame( second, listeners.get( 1 ) );
						if( "true".equals( mode ) ) {
							assertEquals( 3, listeners.size() );
							assertTrue( listeners.get( 2 ) instanceof FlowNativeCall );
							FlowLauncherBridgeTest.completed( evidence );
							if( preview ) {
								assertThrows( IllegalArgumentException.class, () -> wrapper.execute( original ) );
								assertEquals( 1, executions.size(), "consumed plans never reach the delegate" );
							}
						}
						else {
							assertSame( original, actual, "serial overload is passed through, not rebuilt" );
							assertEquals( 0, evidence.receipts );
							assertEquals( List.of(), evidence.failures );
							assertEquals( 3, evidence.bodies.get() );
						}
					}
				}
			}
		}
		finally {
			FlowLauncherBridgeTest.CURRENT.remove();
			FlowLauncherBridgeTest.CALLS.clear();
			if( old == null ) {
				System.clearProperty( FlowLauncherBridgeTest.HOOK );
			}
			else {
				System.setProperty( FlowLauncherBridgeTest.HOOK, old );
			}
		}
	}

	/**
	 * Service-loaded JUnit 6 delegation completes actual work and consumes previews
	 * once.
	 */
	@Test
	void nativeOverloadPreservesTokenChoiceAndListenersAndConsumesPlans() {
		String old = System.getProperty( FlowLauncherBridgeTest.HOOK );
		System.setProperty( FlowLauncherBridgeTest.HOOK, "true" );
		try( var session = LauncherFactory.openSession( FlowLauncherBridgeTest.config() ) ) {
			var launcher = session.getLauncher();
			for( boolean preview : new boolean[] { false, true } ) {
				var evidence = new FlowLauncherBridgeTest.Evidence();
				FlowLauncherBridgeTest.CURRENT.set( evidence );
				var request = FlowLauncherBridgeTest.request( "true" );
				var builder = preview
						? LauncherExecutionRequestBuilder.request( launcher.discover( request ) )
						: LauncherExecutionRequestBuilder.request( request );
				LauncherExecutionRequest original = builder.listeners( evidence ).build();
				launcher.execute( original );
				FlowLauncherBridgeTest.completed( evidence );
				assertEquals( 1, evidence.plans );
				assertEquals( 1, evidence.terminals );
				assertEquals( 1, original.getAdditionalTestExecutionListeners().size() );
				if( preview ) {
					assertThrows( IllegalArgumentException.class, () -> launcher.execute( original ) );
				}
			}
		}
		finally {
			FlowLauncherBridgeTest.CURRENT.remove();
			FlowLauncherBridgeTest.CALLS.clear();
			if( old == null ) {
				System.clearProperty( FlowLauncherBridgeTest.HOOK );
			}
			else {
				System.setProperty( FlowLauncherBridgeTest.HOOK, old );
			}
		}
	}

	/**
	 * Cancelling the unchanged native token at plan start prevents factory
	 * admission.
	 */
	@Test
	void identicalCancellationTokenReachesTheActualNativeOverload() {
		var request = FlowLauncherBridgeTest.request( "true" );
		var evidence = new FlowLauncherBridgeTest.Evidence();
		var token = CancellationToken.create();
		AtomicInteger delegated = new AtomicInteger();
		Launcher real = LauncherFactory.create( FlowLauncherBridgeTest.config() );
		Launcher recording = (Launcher) Proxy.newProxyInstance( Launcher.class.getClassLoader(),
				new Class<?>[] { Launcher.class }, ( proxy, method, args ) -> {
					if( method.getName().equals( "execute" ) ) {
						assertEquals( LauncherExecutionRequest.class, method.getParameterTypes()[0] );
						var actual = (LauncherExecutionRequest) args[0];
						assertSame( token, actual.getCancellationToken() );
						assertSame( request, actual.getDiscoveryRequest().orElseThrow() );
						delegated.incrementAndGet();
					}
					try {
						return method.invoke( real, args );
					}
					catch( InvocationTargetException failure ) {
						throw failure.getCause();
					}
				} );
		try( var wrapper = new FlowLauncherSix( recording ) ) {
			wrapper.execute( LauncherExecutionRequestBuilder.request( request ).cancellationToken( token )
					.listeners( evidence, new TestExecutionListener() {
						@Override
						public void testPlanExecutionStarted( TestPlan plan ) {
							token.cancel();
						}
					} ).build() );
		}
		assertTrue( token.isCancellationRequested() );
		assertEquals( 1, delegated.get() );
		assertEquals( 1, evidence.plans );
		assertEquals( 0, evidence.factories );
		assertEquals( 0, evidence.receipts );
		assertEquals( List.of(), evidence.failures, "cancellation must not hide a fixture failure" );
		assertEquals( List.of(), evidence.results );
		assertEquals( 0, evidence.terminals );
	}
}
