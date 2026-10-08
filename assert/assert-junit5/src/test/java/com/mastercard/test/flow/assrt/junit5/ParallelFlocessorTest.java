package com.mastercard.test.flow.assrt.junit5;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.platform.engine.TestExecutionResult;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.AssertionOptions;
import com.mastercard.test.flow.assrt.Listener;
import com.mastercard.test.flow.assrt.Reporting;
import com.mastercard.test.flow.assrt.junit5.mock.Actrs;
import com.mastercard.test.flow.assrt.junit5.mock.Msg;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.util.Option.Temporary;

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

	/** The visible first snapshot is completed synchronously by the final body. */
	@Test
	void finalBodyPublishesCompleteIndex() throws Exception {
		PublicationScenario scenario = new PublicationScenario();
		PublicationFactory.current = scenario;
		Thread launcher = new Thread(
				() -> LauncherFactory.create().execute( request( PublicationFactory.class ), scenario ),
				"publication-launcher" );
		try( Temporary reportName = AssertionOptions.REPORT_NAME.temporarily( "final-body" ) ) {
			launcher.start();
			assertTrue( scenario.lastDetailWritten.await( 10, TimeUnit.SECONDS ),
					"last detail was not written" );
			assertEquals( List.of( "first" ), descriptions( scenario.flocessor.report() ),
					"the first snapshot remains visible before final body completion" );
		}
		finally {
			scenario.releaseLast.countDown();
			launcher.join( 30_000 );
			PublicationFactory.current = null;
		}

		assertFalse( launcher.isAlive(), "launcher did not finish" );
		assertTrue( scenario.failures.isEmpty(), scenario.failures::toString );
		assertEquals( List.of( "first", "last" ), descriptions( scenario.flocessor.report() ) );
	}

	/** Publication failure remains owned by the final dynamic body. */
	@Test
	void publicationFailureOwnership() {
		RuntimeException body = new RuntimeException( "body" );
		RuntimeException publication = new RuntimeException( "publication" );
		FailureScenario combined = executeFailureScenario( body, publication );
		assertTrue( combined.failures.contains( body ), combined.failures::toString );
		assertEquals( List.of( publication ), List.of( body.getSuppressed() ) );

		RuntimeException causedBody = new RuntimeException( "caused body" );
		RuntimeException causedPublication = new RuntimeException( "caused publication", causedBody );
		FailureScenario caused = executeFailureScenario( causedBody, causedPublication );
		assertTrue( caused.failures.contains( causedBody ), caused.failures::toString );
		assertEquals( 0, causedBody.getSuppressed().length, "direct cause is not suppressed again" );

		RuntimeException onlyPublication = new RuntimeException( "only publication" );
		FailureScenario successfulBody = executeFailureScenario( null, onlyPublication );
		assertTrue( successfulBody.failures.contains( onlyPublication ),
				successfulBody.failures::toString );

		AssertionError fatalBody = new AssertionError( "fatal body" );
		AssertionError fatalPublication = new AssertionError( "fatal publication" );
		FailureScenario fatal = executeFailureScenario( fatalBody, fatalPublication );
		assertTrue( fatal.failures.contains( fatalBody ), fatal.failures::toString );
		assertEquals( List.of( fatalPublication ), List.of( fatalBody.getSuppressed() ) );
	}

	private static FailureScenario executeFailureScenario( Throwable body,
			Throwable publication ) {
		FailureScenario scenario = new FailureScenario( body, publication );
		FailureFactory.current = scenario;
		try {
			LauncherFactory.create().execute( request( FailureFactory.class ), scenario );
			return scenario;
		}
		finally {
			FailureFactory.current = null;
		}
	}

	private static LauncherDiscoveryRequest request() {
		return request( ParallelFactory.class );
	}

	private static LauncherDiscoveryRequest request( Class<?> factory ) {
		return LauncherDiscoveryRequestBuilder.request()
				.selectors( selectClass( factory ) )
				.configurationParameter( "junit.jupiter.execution.parallel.enabled", "true" )
				.configurationParameter( "junit.jupiter.execution.parallel.mode.default", "concurrent" )
				.configurationParameter( "junit.jupiter.execution.parallel.config.strategy", "fixed" )
				.configurationParameter( "junit.jupiter.execution.parallel.config.fixed.parallelism", "4" )
				.build();
	}

	private static List<String> descriptions( java.nio.file.Path report ) {
		return new Reader( report ).read().entries.stream().map( entry -> entry.description ).toList();
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

	/** Consumer-shaped reporting factory selected by the launcher test. */
	@Execution(ExecutionMode.CONCURRENT)
	static class PublicationFactory {
		static volatile PublicationScenario current;

		@TestFactory
		Stream<DynamicNode> flows() {
			PublicationScenario scenario = current;
			scenario.flocessor = new Flocessor( "publication", scenario.model )
					.system( State.FUL, Actrs.BEN )
					.reporting( Reporting.QUIETLY, "report-index-publication" )
					.listening( scenario.progress )
					.behaviour( assertion -> {
						if( "last".equals( assertion.flow().meta().description() ) ) {
							await( scenario.firstCompleted );
						}
						assertion.actual().response( assertion.expected().response().content() );
					} );
			return scenario.flocessor.tests();
		}
	}

	/**
	 * Consumer-shaped publication-failure factory selected by the launcher test.
	 */
	@Execution(ExecutionMode.CONCURRENT)
	static class FailureFactory {
		static volatile FailureScenario current;

		@TestFactory
		Stream<DynamicNode> flows() {
			FailureScenario scenario = current;
			return new FailingPublicationFlocessor( scenario )
					.system( State.FUL, Actrs.BEN )
					.reporting( Reporting.QUIETLY )
					.behaviour( assertion -> {
						assertion.actual().response( assertion.expected().response().content() );
						if( scenario.bodyFailure != null ) {
							throwUnchecked( scenario.bodyFailure );
						}
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

	private static final class PublicationScenario implements TestExecutionListener {
		private final CountDownLatch firstCompleted = new CountDownLatch( 1 );
		private final CountDownLatch lastDetailWritten = new CountDownLatch( 1 );
		private final CountDownLatch releaseLast = new CountDownLatch( 1 );
		private final List<Throwable> failures = Collections.synchronizedList( new ArrayList<>() );
		private final Flow first = flow( "first" );
		private final Flow last = flow( "last" );
		private final Model model = model( first, last );
		private volatile Flocessor flocessor;
		private final Listener progress = new Listener() {
			@Override
			public void flowComplete( Flow flow ) {
				if( flow == first ) {
					firstCompleted.countDown();
				}
				else if( flow == last ) {
					lastDetailWritten.countDown();
					await( releaseLast );
				}
			}
		};

		@Override
		public void executionFinished( TestIdentifier testIdentifier, TestExecutionResult result ) {
			result.getThrowable().ifPresent( failures::add );
		}
	}

	private static final class FailureScenario implements TestExecutionListener {
		private final Throwable bodyFailure;
		private final Throwable publicationFailure;
		private final List<Throwable> failures = Collections.synchronizedList( new ArrayList<>() );
		private final Model model = model( flow( "failure" ) );

		private FailureScenario( Throwable bodyFailure, Throwable publicationFailure ) {
			this.bodyFailure = bodyFailure;
			this.publicationFailure = publicationFailure;
		}

		@Override
		public void executionFinished( TestIdentifier testIdentifier, TestExecutionResult result ) {
			result.getThrowable().ifPresent( failures::add );
		}
	}

	private static final class FailingPublicationFlocessor extends Flocessor {
		private final FailureScenario scenario;

		private FailingPublicationFlocessor( FailureScenario scenario ) {
			super( "publication failure", scenario.model );
			this.scenario = scenario;
		}

		@Override
		protected void publishReportIndex() {
			throwUnchecked( scenario.publicationFailure );
		}
	}

	private static void throwUnchecked( Throwable failure ) {
		if( failure instanceof RuntimeException runtime ) {
			throw runtime;
		}
		throw (Error) failure;
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
