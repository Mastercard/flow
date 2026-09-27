package com.mastercard.test.flow.assrt.junit5;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.junit5.mock.Actrs;
import com.mastercard.test.flow.assrt.junit5.mock.Msg;
import com.mastercard.test.flow.builder.Creator;

/** Consumer-shaped coverage of standard Jupiter parallel execution. */
@SuppressWarnings("static-method")
class ParallelFlocessorTest {

	/** Independent leaves overlap while a dependent remains unsubmitted. */
	@Test
	void standardJupiterConcurrency() throws Exception {
		Scenario scenario = new Scenario();
		ParallelFactory.current = scenario;
		Thread launcher = new Thread( () -> LauncherFactory.create().execute( request(), scenario ),
				"parallel-launcher" );
		launcher.start();
		try {
			assertTrue( scenario.started( "source" ).await( 10, TimeUnit.SECONDS ),
					"source did not start" );
			assertTrue( scenario.started( "independent" ).await( 10, TimeUnit.SECONDS ),
					"independent flow did not overlap the held source" );
			assertFalse( scenario.hasStarted( "dependent" ),
					"dependent was submitted before source completion" );
		}
		finally {
			scenario.releaseSource.countDown();
			launcher.join( 30_000 );
			ParallelFactory.current = null;
		}
		assertFalse( launcher.isAlive(), "launcher did not finish" );
		assertTrue( scenario.failures.isEmpty(), scenario.failures::toString );
		assertTrue( scenario.hasStarted( "dependent" ), "dependent was never admitted" );
	}

	private static LauncherDiscoveryRequest request() {
		return LauncherDiscoveryRequestBuilder.request()
				.selectors( selectClass( ParallelFactory.class ) )
				.configurationParameter( "junit.jupiter.execution.parallel.enabled", "true" )
				.configurationParameter( "junit.jupiter.execution.parallel.mode.default", "concurrent" )
				.configurationParameter( "junit.jupiter.execution.parallel.config.strategy", "fixed" )
				.configurationParameter( "junit.jupiter.execution.parallel.config.fixed.parallelism", "4" )
				.build();
	}

	/** Consumer-shaped factory selected by the launcher test. */
	@Execution(ExecutionMode.CONCURRENT)
	static class ParallelFactory {
		static volatile Scenario current;

		@TestFactory
		Stream<DynamicNode> flows() {
			Scenario scenario = current;
			return new Flocessor( "parallel", scenario.model )
					.system( State.FUL, Actrs.BEN )
					.behaviour( assertion -> {
						String name = assertion.flow().meta().description();
						scenario.started( name ).countDown();
						if( "source".equals( name ) ) {
							await( scenario.releaseSource );
						}
						assertion.actual().response( assertion.expected().response().content() );
					} )
					.tests();
		}
	}

	private static final class Scenario implements TestExecutionListener {
		private final CountDownLatch releaseSource = new CountDownLatch( 1 );
		private final Map<String, CountDownLatch> starts = new ConcurrentHashMap<>();
		private final List<Throwable> failures = Collections.synchronizedList( new ArrayList<>() );
		private final Flow source = flow( "source" );
		private final Flow dependent = flow( "dependent", source );
		private final Flow independent = flow( "independent" );
		private final Model model = model( source, dependent, independent );

		private CountDownLatch started( String name ) {
			return starts.computeIfAbsent( name, ignored -> new CountDownLatch( 1 ) );
		}

		private boolean hasStarted( String name ) {
			return started( name ).getCount() == 0;
		}

		@Override
		public void executionFinished( TestIdentifier testIdentifier, TestExecutionResult result ) {
			result.getThrowable().ifPresent( failures::add );
		}
	}

	private static void await( CountDownLatch latch ) {
		try {
			if( !latch.await( 10, TimeUnit.SECONDS ) ) {
				throw new IllegalStateException( "Timed out waiting for test coordination" );
			}
		}
		catch( InterruptedException e ) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException( e );
		}
	}

	private static Flow flow( String name, Flow... prerequisites ) {
		return Creator.build( f -> {
			f.meta( m -> m.description( name ) )
					.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
							.request( new Msg( "request" ) ).response( new Msg( "response" ) ) );
			for( Flow prerequisite : prerequisites ) {
				f.prerequisite( prerequisite );
			}
		} );
	}

	private static Model model( Flow... flows ) {
		return new Model() {
			@Override
			public Stream<Flow> flows( java.util.Set<String> include, java.util.Set<String> exclude ) {
				return Stream.of( flows );
			}

			@Override
			public Model listener( Listener listener ) {
				return this;
			}

			@Override
			public String title() {
				return "parallel model";
			}

			@Override
			public com.mastercard.test.flow.util.TaggedGroup tags() {
				return null;
			}

			@Override
			public Stream<Model> subModels() {
				return Stream.empty();
			}
		};
	}
}
