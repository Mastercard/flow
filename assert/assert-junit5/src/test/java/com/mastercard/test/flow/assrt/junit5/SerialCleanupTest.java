package com.mastercard.test.flow.assrt.junit5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.Reporting;
import com.mastercard.test.flow.assrt.junit5.mock.Actrs;
import com.mastercard.test.flow.assrt.junit5.mock.Mdl;
import com.mastercard.test.flow.assrt.resource.ResourceReservations;
import com.mastercard.test.flow.assrt.resource.ResourceRules;

/**
 * Real Launcher cleanup failures and genuinely empty, repeatable serial runs.
 */
@SuppressWarnings("static-method")
class SerialCleanupTest {
	/**
	 * Preserves primary failures and invokes cleanup only after safe disposal.
	 *
	 * @param mode Completion, validation rejection, early closure, buffering or
	 *             consumer failure
	 */
	@ParameterizedTest
	@ValueSource(strings = { "complete", "reentrant", "validation", "early", "consumer", "buffered" })
	void cleanupFailurePreservesPrimaryAndSafeOwnership( String mode ) {
		if( mode.equals( "consumer" ) || mode.equals( "buffered" ) ) {
			// This path deliberately abandons an emitted UNKNOWN grant without any
			// native return proof. Verify retention in another JVM, not by resetting
			// the shared scope or making subsequent tests inherit unsafe ownership.
			try {
				Path output = Files.createTempFile( "flow-unsafe-cleanup-", ".log" );
				Process process = new ProcessBuilder(
						Path.of( System.getProperty( "java.home" ), "bin", "java" ).toString(),
						"-cp",
						System.getProperty( "surefire.test.class.path",
								System.getProperty( "java.class.path" ) ),
						SerialCleanupTest.class.getName(), mode ).redirectErrorStream( true )
								.redirectOutput( output.toFile() ).start();
				try {
					assertTrue( process.waitFor( 20, TimeUnit.SECONDS ), "unsafe cleanup child timed out" );
					String evidence = Files.readString( output );
					assertEquals( 0, process.exitValue(), evidence );
					System.out.print( evidence );
				}
				finally {
					if( process.isAlive() )
						process.destroyForcibly();
					Files.deleteIfExists( output );
				}
			}
			catch( Exception failure ) {
				throw new AssertionError( failure );
			}
			return;
		}
		verifyCleanup( mode );
	}

	/**
	 * Isolates deliberately unsafe ownership, including retained original cleanup.
	 *
	 * @param args The unsafe consumption mode
	 */
	public static void main( String[] args ) {
		verifyCleanup( args[0] );
		assertUnknownRetained();
		System.out.println( "Unsafe serial " + args[0]
				+ ": cleanup deferred, zero bodies, UNKNOWN retained against EMPTY" );
	}

	private static void assertUnknownRetained() {
		ResourceReservations scope = ResourceReservations.shared();
		var request = scope.register( scope.capacity( 1 ),
				new ResourceRules().resources( "known empty", f -> true ).resolve( null ), () -> {
				} );
		try( var grant = request.tryAcquire() ) {
			assertNull( grant, "abandoned UNKNOWN must conservatively block even known-empty work" );
		}
		finally {
			request.cancel();
		}
	}

	private static void verifyCleanup( String mode ) {
		FailingCleanupFactory.mode = mode;
		FailingCleanupFactory.closes = 0;
		FailingCleanupFactory.bodies = 0;
		List<Throwable> failures = PreparedFlowLifecycleTest.launch( FailingCleanupFactory.class );
		boolean unsafe = mode.equals( "consumer" ) || mode.equals( "buffered" );
		assertEquals( unsafe ? 2 : 1, failures.size(), failures::toString );
		Throwable primary = failures.get( 0 );
		if( mode.equals( "complete" ) || mode.equals( "reentrant" ) ) {
			assertSame( FailingCleanupFactory.cleanup, primary );
			assertEquals( 2, FailingCleanupFactory.bodies );
		}
		else {
			assertEquals( 0, FailingCleanupFactory.bodies );
			assertTrue( primary.getMessage().contains( mode.equals( "validation" )
					? "Return exactly"
					: mode.equals( "consumer" ) ? "consumer failure" : "Incomplete Flow" ),
					primary::toString );
			Throwable closing = mode.equals( "consumer" ) ? primary.getSuppressed()[0] : primary;
			if( unsafe ) {
				assertEquals( 0, closing.getSuppressed().length, "original cleanup is not yet safe" );
				Throwable backstop = failures.get( 1 );
				while( backstop.getCause() != null )
					backstop = backstop.getCause();
				assertTrue( backstop.getMessage().contains( "Incomplete Flow" ),
						failures::toString );
			}
			else {
				assertSame( FailingCleanupFactory.cleanup, closing.getSuppressed()[0] );
			}
			assertNull( FailingCleanupFactory.runner.report(),
					"rejection must not initialize reporting" );
		}
		assertEquals( unsafe ? 0 : 1, FailingCleanupFactory.closes );
		if( unsafe ) {
			assertThrows( IllegalStateException.class, FailingCleanupFactory.handle::close );
			assertUnknownRetained();
		}
		else {
			FailingCleanupFactory.handle.close();
		}
		assertEquals( unsafe ? 0 : 1, FailingCleanupFactory.closes );
	}

	/** Supplies a serial fixture whose cleanup always throws the same failure. */
	@ExtendWith(StopConsumption.class)
	@FlowTest
	static class FailingCleanupFactory {
		/** Exit path exercised by the current invocation. */
		static String mode;
		/** Number of fixture-close callbacks invoked. */
		static int closes;
		/** Number of flow bodies executed. */
		static int bodies;
		/** Execution owner retained to verify repeated cleanup. */
		static FlowExecution handle;
		/** Runner retained to inspect lazy report initialization. */
		static PreparedFlocessor runner;
		/** Stable cleanup failure used to assert throwable identity and suppression. */
		static final IllegalStateException cleanup = new IllegalStateException(
				"fixture close failure" );

		/**
		 * Creates a serial description stream with deliberately failing cleanup.
		 *
		 * @param execution Injected owner of the serial run
		 * @return Descriptions, optionally filtered to trigger validation rejection
		 */
		@TestFactory
		Stream<DynamicNode> flows( FlowExecution execution ) {
			handle = execution;
			runner = execution.flocessor( "failed fixture cleanup", PreparedFlowLifecycleTest.model(
					new Mdl().flows().limit( 2 ).toArray( Flow[]::new ) ) )
					.system( State.LESS, Actrs.BEN ).reporting( Reporting.QUIETLY )
					.behaviour( a -> {
						bodies++;
						a.actual().response( a.expected().response().content() );
					} );
			Stream<DynamicNode> tests = runner.tests();
			return (mode.equals( "validation" ) ? tests.filter( n -> false ) : tests)
					.onClose( () -> {
						closes++;
						if( mode.equals( "reentrant" ) )
							handle.close();
						throw cleanup;
					} );
		}
	}

	/** Simulates early stream closure or a failure while consuming descriptions. */
	static class StopConsumption implements InvocationInterceptor {
		@Override
		public <T> T interceptTestFactoryMethod( Invocation<T> invocation,
				ReflectiveInvocationContext<Method> method, ExtensionContext context ) throws Throwable {
			T returned = invocation.proceed();
			if( FailingCleanupFactory.mode.equals( "early" ) ) {
				((Stream<?>) returned).close();
			}
			if( FailingCleanupFactory.mode.equals( "consumer" ) ) {
				try( Stream<?> live = (Stream<?>) returned ) {
					live.forEach( n -> {
						throw new IllegalArgumentException( "consumer failure" );
					} );
				}
			}
			if( FailingCleanupFactory.mode.equals( "buffered" ) ) {
				try( Stream<?> live = (Stream<?>) returned ) {
					assertTrue( live.iterator().hasNext() );
					assertEquals( 0, FailingCleanupFactory.bodies );
					assertUnknownRetained();
				}
			}
			return returned;
		}
	}

	/**
	 * Verifies repeatable cleanup of an empty model without creating a report.
	 *
	 * @param reporting Whether reporting is enabled for the empty run
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void emptyModelSuccessfullyDisposesAndRepeatsWithoutInitializingReport( boolean reporting ) {
		checkEmpty( false, reporting );
	}

	/**
	 * Verifies repeatable cleanup when filtering excludes every flow.
	 *
	 * @param reporting Whether reporting is enabled for the empty selection
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void filterAllSuccessfullyDisposesAndRepeatsWithoutInitializingReport( boolean reporting ) {
		checkEmpty( true, reporting );
	}

	private static void checkEmpty( boolean filtered, boolean reporting ) {
		EmptyFactory.filtered = filtered;
		EmptyFactory.reporting = reporting;
		for( int run = 0; run < 2; run++ ) {
			EmptyFactory.closes = 0;
			assertEquals( List.of(), PreparedFlowLifecycleTest.launch( EmptyFactory.class ) );
			assertEquals( 1, EmptyFactory.closes );
			assertNull( EmptyFactory.runner.report(), "preserve lazy serial report initialization" );
			EmptyFactory.handle.close();
			assertThrows( IllegalStateException.class, () -> EmptyFactory.runner.tests() );
		}
	}

	/**
	 * Produces empty serial runs from an empty model or an all-excluding filter.
	 */
	@FlowTest
	static class EmptyFactory {
		/** Whether to exclude a populated model rather than supply an empty model. */
		static boolean filtered;
		/** Whether the runner permits report creation. */
		static boolean reporting;
		/** Number of description-stream close callbacks invoked. */
		static int closes;
		/** Execution owner retained for explicit cleanup after the run. */
		static FlowExecution handle;
		/** Runner retained to inspect reporting and reject repeated preparation. */
		static PreparedFlocessor runner;

		/**
		 * Creates an empty serial selection with a counted cleanup callback.
		 *
		 * @param execution Injected owner of the serial run
		 * @return Empty description stream for the configured model and filter
		 */
		@TestFactory
		Stream<DynamicNode> flows( FlowExecution execution ) {
			handle = execution;
			runner = execution.flocessor( "empty serial", filtered ? new Mdl()
					: PreparedFlowLifecycleTest.model() ).system( State.LESS, Actrs.BEN )
					.reporting( reporting ? Reporting.QUIETLY : Reporting.NEVER )
					.behaviour( a -> {
						throw new AssertionError( "empty selection must have no bodies" );
					} );
			if( filtered ) {
				runner.filtering( f -> f.includedTags( java.util.Set.of( "not-present" ) ) );
			}
			return runner.tests().onClose( () -> closes++ );
		}
	}
}
