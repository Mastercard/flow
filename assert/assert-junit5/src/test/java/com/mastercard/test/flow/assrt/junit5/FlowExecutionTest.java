package com.mastercard.test.flow.assrt.junit5;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.junit5.mock.Actrs;
import com.mastercard.test.flow.assrt.junit5.mock.Mdl;

/**
 * Real Flow processing through the provider-free caller and native Launcher.
 */
@SuppressWarnings("static-method")
class FlowExecutionTest {
	@Test
	void onePreparedSnapshotPerInvocation() {
		List<Throwable> failures = new ArrayList<>();
		execute( FrozenFactory.class, "false", failures( failures ) );
		assertEquals( List.of(), failures );
		assertThrows( IllegalStateException.class,
				() -> FrozenFactory.handle.flocessor( "reuse", new Mdl() ) );
		assertThrows( IllegalStateException.class, () -> FrozenFactory.runner.behaviour( a -> {
		} ) );
		assertThrows( IllegalStateException.class, () -> FrozenFactory.runner.tests() );
	}

	@FlowTest
	static class FrozenFactory {
		static FlowExecution handle;
		static PreparedFlocessor runner;

		@TestFactory
		Stream<DynamicNode> flows( FlowExecution execution ) {
			handle = execution;
			runner = execution.flocessor( "frozen", new Mdl() )
					.system( State.LESS, Actrs.BEN )
					.behaviour( a -> a.actual().response( a.expected().response().content() ) );
			assertThrows( IllegalStateException.class, () -> execution.flocessor( "second", new Mdl() ) );
			Stream<DynamicNode> tests = runner.tests();
			assertThrows( IllegalStateException.class, () -> runner.tests() );
			List<Runnable> setters = List.of(
					() -> runner.system( State.FUL, Actrs.AVA ), () -> runner.autonomous( Actrs.BEN ),
					() -> runner.masking(), () -> runner.applicators(), () -> runner.checkers(),
					() -> runner.logs( null ), () -> runner.listening( null ),
					() -> runner.filtering( null ), () -> runner.exercising( null, null ),
					() -> runner.behaviour( null ), () -> runner.motivation( null ),
					() -> runner.reporting( null ) );
			setters.forEach( setter -> assertThrows( IllegalStateException.class, setter::run ) );
			return tests;
		}
	}

	/**
	 * Unsupported parallel selection and conflicting serial mode fail before
	 * factory entry.
	 */
	@Test
	void unsupportedModesFailBeforeFactoryOrBody() {
		for( String mode : new String[] { "true", "invalid" } ) {
			SerialFactory.thread = null;
			List<Throwable> failures = new ArrayList<>();
			execute( SerialFactory.class, mode, failures( failures ) );
			assertEquals( null, SerialFactory.thread, "must reject before the original factory" );
			assertTrue( failures.stream().anyMatch( f -> f.toString().contains( "flow.parallel" )
					|| f.toString().contains( "sole top-level" ) ),
					failures.toString() );
		}
		SerialFactory.thread = null;
		List<Throwable> failures = new ArrayList<>();
		execute( ConcurrentFactory.class, "false", failures( failures ) );
		assertEquals( null, SerialFactory.thread );
		assertTrue( failures.stream().anyMatch( f -> f.toString().contains( "SAME_THREAD" ) ),
				failures.toString() );
	}

	private static TestExecutionListener failures( List<Throwable> failures ) {
		return new TestExecutionListener() {
			@Override
			public void executionFinished( TestIdentifier id, TestExecutionResult result ) {
				result.getThrowable().ifPresent( failures::add );
			}
		};
	}

	@Execution(ExecutionMode.CONCURRENT)
	static class ConcurrentFactory extends SerialFactory {
		// Conflicting inherited factory mode must not override Flow opt-out.
	}

	@Test
	void nativeSerialOutcomesAndNavigation() {
		for( String mode : new String[] { "absent", "false", "absent" } ) {
			SerialFactory.events.clear();
			List<String> results = new ArrayList<>();
			List<Integer> lines = new ArrayList<>();
			List<String> diagnostics = new ArrayList<>();
			execute( SerialFactory.class, mode, new TestExecutionListener() {
				@Override
				public void executionStarted( TestIdentifier id ) {
					if( id.isTest() ) {
						SerialFactory.events.add( "start:" + id.getDisplayName() );
						if( SerialFactory.thread != Thread.currentThread() ) {
							diagnostics.add( "Native serial start moved off the factory thread: " + id );
						}
					}
				}

				@Override
				public void executionFinished( TestIdentifier id, TestExecutionResult result ) {
					if( id.isTest() ) {
						results.add( id.getDisplayName() + ":" + result.getStatus() );
						SerialFactory.events.add( "finish:" + id.getDisplayName() );
						if( id.getSource().orElse( null )instanceof ClassSource source
								&& Mdl.class.getName().equals( source.getClassName() )
								&& source.getPosition().isPresent() ) {
							lines.add( source.getPosition().get().getLine() );
						}
						else {
							diagnostics.add( "Missing or incorrect Flow source: " + id.getSource() );
						}
					}
					else if( result.getStatus() != TestExecutionResult.Status.SUCCESSFUL ) {
						diagnostics.add( "Native container failure: " + id + ": " + result );
					}
				}
			} );
			// Launcher listener exceptions are logged, not test vetoes. Evaluate all
			// listener observations here so violations actually fail this regression.
			assertEquals( List.of(), diagnostics, diagnostics::toString );
			assertEquals( List.of( "error []:FAILED", "errorChild []:FAILED", "errorDependent []:ABORTED",
					"failure []:FAILED", "failureChild []:ABORTED", "failureDependent []:FAILED",
					"success []:SUCCESSFUL", "successChild []:SUCCESSFUL", "successDependent []:SUCCESSFUL" ),
					results );
			assertEquals( List.of( 32, 45, 61, 27, 41, 55, 22, 37, 49 ), lines );
			// No leaf start can overtake the previous native finish (including skipped
			// bodies).
			List<String> nativeEvents = SerialFactory.events.stream()
					.filter( e -> !e.startsWith( "body:" ) ).toList();
			for( int i = 0; i < nativeEvents.size(); i += 2 ) {
				assertTrue( nativeEvents.get( i ).startsWith( "start:" ) );
				assertEquals( nativeEvents.get( i ).replace( "start:", "finish:" ),
						nativeEvents.get( i + 1 ) );
			}
			assertEquals( 7,
					SerialFactory.events.stream().filter( e -> e.startsWith( "body:" ) ).count() );
		}
	}

	static void execute( Class<?> factory, String mode, TestExecutionListener listener ) {
		LauncherDiscoveryRequestBuilder request = LauncherDiscoveryRequestBuilder.request()
				.selectors( selectClass( factory ) )
				.configurationParameter( "junit.jupiter.execution.parallel.enabled", "true" )
				.configurationParameter( "junit.jupiter.execution.parallel.mode.default", "concurrent" )
				.configurationParameter( "junit.jupiter.execution.parallel.mode.classes.default",
						"concurrent" )
				.configurationParameter( "junit.jupiter.execution.parallel.config.strategy", "fixed" )
				.configurationParameter( "junit.jupiter.execution.parallel.config.fixed.parallelism", "2" )
				.configurationParameter( "junit.jupiter.execution.parallel.config.fixed.max-pool-size",
						"2" )
				.configurationParameter( "junit.platform.launcher.interceptors.enabled", "false" );
		if( !"absent".equals( mode ) ) {
			request.configurationParameter( "flow.parallel", mode );
		}
		LauncherConfig config = LauncherConfig.builder()
				.enableLauncherSessionListenerAutoRegistration( false )
				.enableTestExecutionListenerAutoRegistration( false )
				.enableLauncherDiscoveryListenerAutoRegistration( false ).build();
		LauncherFactory.create( config ).execute( request.build(), listener );
	}

	@FlowTest
	static class SerialFactory {
		static final List<String> events = new ArrayList<>();
		static Thread thread;

		@TestFactory
		Stream<DynamicNode> flows( FlowExecution execution ) {
			thread = Thread.currentThread();
			return execution.flocessor( "provider-free serial", new Mdl() )
					.system( State.FUL, Actrs.BEN )
					.behaviour( asrt -> {
						assertEquals( thread, Thread.currentThread() );
						events.add( "body:" + asrt.flow().meta().id() );
						if( asrt.flow().meta().id().contains( "success" ) ) {
							asrt.actual().response( asrt.expected().response().content() );
						}
						else if( asrt.flow().meta().id().contains( "failure" ) ) {
							asrt.actual().response( "unexpected content!".getBytes( UTF_8 ) );
						}
						else {
							throw new IllegalArgumentException( "no thanks!" );
						}
					} ).tests();
		}
	}
}
