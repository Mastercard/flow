package com.mastercard.test.flow.assrt.junit5;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Metadata;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.Residue;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.Applicator;
import com.mastercard.test.flow.assrt.AssertionOptions;
import com.mastercard.test.flow.assrt.CorrelatedCapture;
import com.mastercard.test.flow.assrt.LogCapture;
import com.mastercard.test.flow.assrt.Reporting;
import com.mastercard.test.flow.assrt.junit5.mock.Actrs;
import com.mastercard.test.flow.assrt.junit5.mock.Mdl;
import com.mastercard.test.flow.assrt.junit5.mock.Msg;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.util.Option.Temporary;
import com.mastercard.test.flow.util.Transmission.Type;

/**
 * Real Launcher runs of prepared {@link FlowTest} classes, with standard
 * Jupiter parallel execution enabled or disabled. Ordering is asserted with
 * latches released by the flows themselves, never with sleeps.
 */
@SuppressWarnings("static-method")
class FlowExecutionTest {

	/** Configuration is frozen at preparation and the handle is single-use. */
	@Test
	void onePreparedSnapshotPerInvocation() {
		Run run = execute( FrozenFactory.class, false );
		assertEquals( List.of(), run.failures );
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
					() -> runner.logs( (LogCapture) null ), () -> runner.logs( (CorrelatedCapture) null ),
					() -> runner.correlation( null ), () -> runner.captureBudget( null ),
					() -> runner.listening( null ),
					() -> runner.filtering( null ), () -> runner.exercising( null, null ),
					() -> runner.behaviour( null ), () -> runner.motivation( null ),
					() -> runner.reporting( null ), () -> runner.progressTimeout( Duration.ofSeconds( 1 ) ) );
			setters.forEach( setter -> assertThrows( IllegalStateException.class, setter::run ) );
			return tests;
		}
	}

	/**
	 * The same model yields identical per-flow outcomes, skip reasons and source
	 * navigation whether Jupiter parallel execution is on or off.
	 *
	 * @param parallel Whether Jupiter parallel execution is enabled
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void serialAndParallelOutcomesAreIdentical( boolean parallel ) {
		OutcomeFactory.threads.clear();
		Run run = execute( OutcomeFactory.class, parallel );
		assertEquals( List.of(), run.diagnostics, run.diagnostics::toString );
		List<String> results = new ArrayList<>( run.results );
		Collections.sort( results );
		assertEquals( List.of( "error []:FAILED", "errorChild []:FAILED", "errorDependent []:ABORTED",
				"failure []:FAILED", "failureChild []:ABORTED", "failureDependent []:FAILED",
				"success []:SUCCESSFUL", "successChild []:SUCCESSFUL", "successDependent []:SUCCESSFUL" ),
				results );
		assertEquals( "Ancestor failed", run.reasons.get( "failureChild []" ) );
		assertEquals( "Missing dependency", run.reasons.get( "errorDependent []" ) );
		assertEquals( Set.of( 22, 27, 32, 37, 41, 45, 49, 55, 61 ), Set.copyOf( run.lines ) );
		if( !parallel ) {
			assertEquals( Set.of( OutcomeFactory.factoryThread ), OutcomeFactory.threads );
		}
	}

	@FlowTest
	static class OutcomeFactory {
		static final Set<Thread> threads = ConcurrentHashMap.newKeySet();
		static Thread factoryThread;

		@TestFactory
		Stream<DynamicNode> flows( FlowExecution execution ) {
			factoryThread = Thread.currentThread();
			return execution.flocessor( "outcomes", new Mdl() )
					.system( State.FUL, Actrs.BEN )
					.behaviour( asrt -> {
						threads.add( Thread.currentThread() );
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

	/** Independent flows run at the same time on different threads. */
	@Test
	void independentFlowsOverlap() throws Exception {
		Gated gated = Gated.model( flow( "a" ), flow( "x" ) );
		gated.hold( "a" );
		Thread launcher = gated.launch( true );
		gated.awaitStart( "x" );
		assertTrue( gated.started( "a" ) );
		gated.release( "a" );
		Run run = gated.join( launcher );
		assertEquals( List.of(), run.failures, run.failures::toString );
		assertNotEquals( gated.thread( "a" ), gated.thread( "x" ) );
	}

	/** A flow waits for the flows it binds values from, and nothing else. */
	@Test
	void dependentStartsAfterItsSourceFinishes() throws Exception {
		Flow a = flow( "a" );
		Gated gated = Gated.model( a, flow( "b", a ), flow( "x" ) );
		gated.hold( "a" );
		Thread launcher = gated.launch( true );
		gated.awaitStart( "x" );
		assertFalse( gated.started( "b" ) );
		gated.release( "a" );
		Run run = gated.join( launcher );
		assertEquals( List.of(), run.failures, run.failures::toString );
		gated.assertBefore( "finish:a", "start:b" );
	}

	/**
	 * Chain members run in order, one after another, and the chain's outside edges
	 * attach to its first and last members.
	 */
	@Test
	void chainMembersAreContiguousAndOrdered() throws Exception {
		Flow c1 = flow( "c1", "chain:C" );
		Flow c2 = flow( "c2", "chain:C" );
		Gated gated = Gated.model( c1, c2, flow( "d", c1 ), flow( "x" ) );
		gated.hold( "c1" );
		Thread launcher = gated.launch( true );
		gated.awaitStart( "x" );
		assertFalse( gated.started( "c2" ) );
		assertFalse( gated.started( "d" ) );
		gated.release( "c1" );
		Run run = gated.join( launcher );
		assertEquals( List.of(), run.failures, run.failures::toString );
		gated.assertBefore( "finish:c1", "start:c2" );
		gated.assertBefore( "finish:c2", "start:d" );
	}

	/** Flows that publish into the same destination never overlap. */
	@Test
	void sameDestinationPublishersAreSerialised() throws Exception {
		Flow a = flow( "a" );
		Flow b = flow( "b" );
		Flow c = Creator.build( f -> f.meta( m -> m.description( "c" ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN ).request( new Fields( "left:right" ) )
						.response( new Msg( "response" ) ) )
				.dependency( a, d -> d.from( i -> true, Type.REQUEST, "left" ).to( i -> true, Type.REQUEST,
						"left" ) )
				.dependency( b, d -> d.from( i -> true, Type.REQUEST, "right" ).to( i -> true,
						Type.REQUEST, "right" ) ) );
		Gated gated = Gated.model( a, b, c, flow( "x" ) );
		gated.hold( "a" );
		Thread launcher = gated.launch( true );
		gated.awaitStart( "x" );
		assertFalse( gated.started( "b" ) );
		assertFalse( gated.started( "c" ) );
		gated.release( "a" );
		Run run = gated.join( launcher );
		assertEquals( List.of(), run.failures, run.failures::toString );
		gated.assertBefore( "finish:a", "start:b" );
		gated.assertBefore( "finish:b", "start:c" );
	}

	/**
	 * Context-applying flows never overlap each other, but do overlap flows that
	 * apply no context.
	 */
	@Test
	void contextApplyingFlowsAreSerialisedAmongThemselves() throws Exception {
		Gated gated = Gated.model( contextual( "k1" ), flow( "f" ), contextual( "k2" ) );
		gated.configure = r -> r.applicators( new Applicator<>( Setting.class, 1 ) {
			@Override
			public Comparator<Setting> order() {
				return Comparator.comparing( Setting::name );
			}

			@Override
			public void transition( Setting from, Setting to ) {
				gated.events.add( "context:" + (to == null ? "none" : to.name()) );
			}
		} );
		gated.hold( "k1" );
		Thread launcher = gated.launch( true );
		gated.awaitStart( "f" );
		assertFalse( gated.started( "k2" ) );
		gated.release( "k1" );
		Run run = gated.join( launcher );
		assertEquals( List.of(), run.failures, run.failures::toString );
		gated.assertBefore( "finish:k1", "start:k2" );
	}

	/** Model errors surface before any flow body runs. */
	@Test
	void cyclicBasisFailsPreparation() {
		Gated gated = Gated.model();
		Cyclic a = new Cyclic( flow( "a" ) );
		Cyclic b = new Cyclic( flow( "b" ) );
		a.basis = b;
		b.basis = a;
		gated.flows = List.of( a, b );
		Run run = gated.join( gated.launch( true ) );
		assertEquals( List.of(), gated.events );
		assertEquals( List.of(), run.results );
		assertTrue( run.failures.stream().anyMatch( f -> f instanceof IllegalArgumentException
				&& f.getMessage().contains( "Cyclic Flow basis" ) ), run.failures::toString );
	}

	/**
	 * A flow that never completes fails the run with a diagnostic naming it,
	 * instead of hanging the build.
	 */
	@Test
	void hungFlowFailsTheRunWithinTheProgressTimeout() throws Exception {
		Flow h = flow( "h" );
		Gated gated = Gated.model( h, flow( "x", h ) );
		gated.configure = r -> r.progressTimeout( Duration.ofMillis( 300 ) );
		gated.hold( "h" );
		Thread launcher = gated.launch( true );
		try {
			gated.awaitStart( "h" );
			// The factory gives up on x after the timeout, while h is still held; x can
			// only observe that by never starting.
			assertFalse( gated.start( "x" ).await( 1, TimeUnit.SECONDS ) );
		}
		finally {
			gated.release( "h" );
		}
		Run run = gated.join( launcher );
		assertFalse( gated.started( "x" ) );
		assertTrue( run.failures.stream().anyMatch( f -> f instanceof IllegalStateException
				&& f.getMessage().contains( "h []" ) && f.getMessage().contains( "PT0.3S" ) ),
				run.failures::toString );
	}

	/**
	 * The report is closed by the class-context resource on normal completion and
	 * when the factory itself is aborted.
	 *
	 * @param dir Isolated artifact directory
	 */
	@Test
	void reportIsPublishedOnCompletionAndOnAbortedFactory( @TempDir Path dir ) {
		try( Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( dir.toString() );
				Temporary name = AssertionOptions.REPORT_NAME.temporarily( "published" ) ) {
			Gated gated = Gated.model( flow( "a" ), flow( "x" ) );
			gated.configure = r -> r.reporting( Reporting.QUIETLY );
			Run run = gated.join( gated.launch( true ) );
			assertEquals( List.of(), run.failures, run.failures::toString );
			assertEquals( 2, new Reader( dir.resolve( "published" ) ).read().entries.size() );

			Gated aborted = Gated.model( flow( "never" ) );
			aborted.configure = r -> r.reporting( Reporting.QUIETLY );
			aborted.abortFactory = true;
			run = aborted.join( aborted.launch( true ) );
			assertEquals( List.of(), run.results );
			assertEquals( 0, new Reader( dir.resolve( "published" ) ).read().entries.size() );
		}
	}

	/**
	 * Fixture shared by the ordering tests: a model whose flow bodies record
	 * start/finish events and can be held open by the test.
	 */
	@FlowTest
	static class Gated {
		static Gated current;

		List<Flow> flows;
		Consumer<PreparedFlocessor> configure = r -> {
		};
		boolean abortFactory;
		final List<String> events = Collections.synchronizedList( new ArrayList<>() );
		final Map<String, CountDownLatch> holds = new ConcurrentHashMap<>();
		final Map<String, CountDownLatch> starts = new ConcurrentHashMap<>();
		final Map<String, Thread> threads = new ConcurrentHashMap<>();
		Run run;

		static Gated model( Flow... flows ) {
			Gated gated = new Gated();
			gated.flows = List.of( flows );
			current = gated;
			return gated;
		}

		@TestFactory
		Stream<DynamicNode> flows( FlowExecution execution ) {
			Gated gated = current;
			PreparedFlocessor runner = execution
					.flocessor( "gated", modelOf( gated.flows ) )
					.system( State.FUL, Actrs.BEN )
					.behaviour( a -> {
						String name = a.flow().meta().description();
						gated.threads.put( name, Thread.currentThread() );
						gated.events.add( "start:" + name );
						gated.start( name ).countDown();
						CountDownLatch hold = gated.holds.get( name );
						if( hold != null ) {
							await( hold );
						}
						a.actual().response( a.expected().response().content() );
						gated.events.add( "finish:" + name );
					} );
			gated.configure.accept( runner );
			Stream<DynamicNode> tests = runner.tests();
			Assumptions.assumeFalse( gated.abortFactory, "aborted after preparation" );
			return tests;
		}

		CountDownLatch start( String name ) {
			return starts.computeIfAbsent( name, n -> new CountDownLatch( 1 ) );
		}

		void hold( String name ) {
			holds.put( name, new CountDownLatch( 1 ) );
		}

		void release( String name ) {
			holds.get( name ).countDown();
		}

		void awaitStart( String name ) {
			await( start( name ) );
		}

		boolean started( String name ) {
			return start( name ).getCount() == 0;
		}

		Thread thread( String name ) {
			return threads.get( name );
		}

		Thread launch( boolean parallel ) {
			Thread launcher = new Thread( () -> run = execute( Gated.class, parallel ), "launcher" );
			launcher.start();
			return launcher;
		}

		Run join( Thread launcher ) {
			try {
				launcher.join( 30_000 );
			}
			catch( InterruptedException e ) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException( e );
			}
			assertFalse( launcher.isAlive(), "Launcher did not finish" );
			return run;
		}

		void assertBefore( String first, String second ) {
			List<String> snapshot = List.copyOf( events );
			int before = snapshot.indexOf( first );
			int after = snapshot.indexOf( second );
			assertTrue( before >= 0 && after > before,
					first + " must precede " + second + " in " + snapshot );
		}
	}

	private static void await( CountDownLatch latch ) {
		try {
			assertTrue( latch.await( 10, TimeUnit.SECONDS ), "coordination timed out" );
		}
		catch( InterruptedException e ) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException( e );
		}
	}

	private static Flow flow( String name, Flow... prerequisites ) {
		return Creator.build( f -> {
			f.meta( m -> m.description( name ) )
					.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN ).request( new Fields( "left:right" ) )
							.response( new Msg( "response" ) ) );
			for( Flow prerequisite : prerequisites ) {
				f.prerequisite( prerequisite );
			}
		} );
	}

	private static Flow flow( String name, String tag ) {
		return Creator.build( f -> f.meta( m -> m.description( name ).tags( t -> t.add( tag ) ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN ).request( new Fields( "left:right" ) )
						.response( new Msg( "response" ) ) ) );
	}

	private static Flow contextual( String name ) {
		return Creator.build( f -> f.meta( m -> m.description( name ) ).context( new Setting( name ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN ).request( new Fields( "left:right" ) )
						.response( new Msg( "response" ) ) ) );
	}

	static Model modelOf( List<Flow> flows ) {
		return new Mdl() {
			@Override
			public Stream<Flow> flows( Set<String> include, Set<String> exclude ) {
				return flows.stream();
			}
		};
	}

	/** Outcome of one Launcher run */
	static final class Run {
		final List<String> results = Collections.synchronizedList( new ArrayList<>() );
		final Map<String, String> reasons = new ConcurrentHashMap<>();
		final List<Integer> lines = Collections.synchronizedList( new ArrayList<>() );
		final List<Throwable> failures = Collections.synchronizedList( new ArrayList<>() );
		final List<String> diagnostics = Collections.synchronizedList( new ArrayList<>() );
	}

	static Run execute( Class<?> factory, boolean parallel, TestExecutionListener... extra ) {
		Run run = new Run();
		TestExecutionListener listener = new TestExecutionListener() {
			@Override
			public void executionFinished( TestIdentifier id, TestExecutionResult result ) {
				result.getThrowable().ifPresent( failures -> {
					run.failures.add( failures );
					if( id.isTest() && result.getStatus() == TestExecutionResult.Status.ABORTED ) {
						run.reasons.put( id.getDisplayName(), failures.getMessage() );
					}
				} );
				if( id.isTest() ) {
					run.results.add( id.getDisplayName() + ":" + result.getStatus() );
					if( id.getSource().orElse( null )instanceof ClassSource source
							&& source.getPosition().isPresent() ) {
						run.lines.add( source.getPosition().get().getLine() );
					}
					else {
						run.diagnostics.add( "Missing or incorrect Flow source: " + id.getSource() );
					}
				}
				else if( result.getStatus() == TestExecutionResult.Status.FAILED ) {
					run.diagnostics.add( "Container failure: " + id + ": " + result );
				}
			}
		};
		List<TestExecutionListener> listeners = new ArrayList<>( List.of( listener ) );
		listeners.addAll( List.of( extra ) );
		LauncherDiscoveryRequestBuilder request = LauncherDiscoveryRequestBuilder.request()
				.selectors( selectClass( factory ) )
				.configurationParameter( "junit.jupiter.execution.parallel.enabled",
						String.valueOf( parallel ) )
				.configurationParameter( "junit.jupiter.execution.parallel.mode.default", "concurrent" )
				.configurationParameter( "junit.jupiter.execution.parallel.config.strategy", "fixed" )
				.configurationParameter( "junit.jupiter.execution.parallel.config.fixed.parallelism", "3" );
		LauncherConfig config = LauncherConfig.builder()
				.enableLauncherSessionListenerAutoRegistration( false )
				.enableTestExecutionListenerAutoRegistration( false )
				.enableLauncherDiscoveryListenerAutoRegistration( false ).build();
		LauncherFactory.create( config ).execute( request.build(),
				listeners.toArray( TestExecutionListener[]::new ) );
		return run;
	}

	/** Two independently addressable fields, with actual parse/get/set behaviour */
	static class Fields extends Msg {
		private final String[] values;

		Fields( String content ) {
			super( content );
			values = content.split( ":" );
		}

		@Override
		public Msg child() {
			return new Fields( assertable() );
		}

		@Override
		public Msg peer( byte[] bytes ) {
			return new Fields( new String( bytes, UTF_8 ) );
		}

		@Override
		public String assertable( com.mastercard.test.flow.Unpredictable... masks ) {
			return String.join( ":", values );
		}

		@Override
		public byte[] content() {
			return assertable().getBytes( UTF_8 );
		}

		@Override
		public Object get( String field ) {
			return values[field.equals( "left" ) ? 0 : 1];
		}

		@Override
		public Msg set( String field, Object value ) {
			values[field.equals( "left" ) ? 0 : 1] = String.valueOf( value );
			return this;
		}
	}

	private record Setting(String name) implements Context {
		@Override
		public Set<Actor> domain() {
			return Set.of( Actrs.BEN );
		}

		@Override
		public Context child() {
			return new Setting( name );
		}
	}

	/** A flow whose basis can be pointed anywhere, including back at itself */
	private static final class Cyclic implements Flow {
		private final Flow contents;
		Flow basis;

		Cyclic( Flow contents ) {
			this.contents = contents;
		}

		@Override
		public Metadata meta() {
			return contents.meta();
		}

		@Override
		public Flow basis() {
			return basis;
		}

		@Override
		public Interaction root() {
			return contents.root();
		}

		@Override
		public Stream<Actor> implicit() {
			return contents.implicit();
		}

		@Override
		public Stream<Dependency> dependencies() {
			return contents.dependencies();
		}

		@Override
		public Stream<Context> context() {
			return contents.context();
		}

		@Override
		public Stream<Residue> residue() {
			return contents.residue();
		}
	}
}
