package com.mastercard.test.flow.assrt.junit5;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.opentest4j.TestAbortedException;

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
import com.mastercard.test.flow.assrt.History.Result;
import com.mastercard.test.flow.assrt.History;
import com.mastercard.test.flow.assrt.Listener;
import com.mastercard.test.flow.assrt.LogCapture;
import com.mastercard.test.flow.assrt.Reporting;
import com.mastercard.test.flow.assrt.junit5.mock.Actrs;
import com.mastercard.test.flow.assrt.junit5.mock.Mdl;
import com.mastercard.test.flow.assrt.junit5.mock.Msg;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.builder.Deriver;
import com.mastercard.test.flow.builder.concrete.ConcreteFlow;
import com.mastercard.test.flow.builder.concrete.ConcreteMetadata;
import com.mastercard.test.flow.builder.concrete.ConcreteRootInteraction;
import com.mastercard.test.flow.builder.mutable.MutableDependency;
import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.report.data.Index;
import com.mastercard.test.flow.report.data.LogEvent;
import com.mastercard.test.flow.util.Option.Temporary;
import com.mastercard.test.flow.util.Tags;
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
		Mdl reuse = new Mdl();
		assertThrows( IllegalStateException.class,
				() -> FrozenFactory.handle.flocessor( "reuse", reuse ) );
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
			Mdl second = new Mdl();
			assertThrows( IllegalStateException.class, () -> execution.flocessor( "second", second ) );
			Stream<DynamicNode> tests = runner.tests();
			assertThrows( IllegalStateException.class, () -> runner.tests() );
			List<Runnable> setters = List.of(
					() -> runner.system( State.FUL, Actrs.AVA ), () -> runner.autonomous( Actrs.BEN ),
					() -> runner.masking(), () -> runner.applicators(), () -> runner.checkers(),
					() -> runner.logs( (LogCapture) null ), () -> runner.logs( (CorrelatedCapture) null ),
					() -> runner.correlation( null ),
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
			assertEquals( Set.of( OutcomeFactory.factoryThread ), Set.copyOf( OutcomeFactory.threads ) );
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

	/**
	 * Independent flows are emitted together and run on more than one thread.
	 * <p>
	 * Once the factory has emitted its last leaf it joins the forked leaves, and a
	 * ForkJoinPool lets it run one inline while an idle worker is not signalled to
	 * steal the other. A rendezvous inside two held bodies can therefore park with
	 * no defect in this library, so overlap is shown through the threads that ran
	 * the flows. The ordering tests below are unaffected: each holds a flow while a
	 * successor is still pending, so the factory blocks in its own managed wait,
	 * which does compensate the pool.
	 */
	@Test
	void independentFlowsOverlap() {
		Set<Thread> threads = new HashSet<>();
		for( int attempt = 0; attempt < 5 && threads.size() < 2; attempt++ ) {
			Gated gated = Gated.model( flow( "a" ), flow( "b" ), flow( "c" ), flow( "d" ) );
			Run run = gated.join( gated.launch( true ) );
			assertEquals( List.of(), run.failures, run.failures::toString );
			assertEquals( 4, run.results.size() );
			threads.addAll( gated.threads.values() );
		}
		assertTrue( threads.size() > 1, "independent flows never left the factory thread" );
	}

	/** A flow waits for the flows it binds values from, and nothing else. */
	@Test
	void dependentStartsAfterItsSourceFinishes() {
		Flow m = flow( "m" );
		Gated gated = Gated.model( flow( "a" ), m, flow( "n", m ) );
		gated.hold( "m" );
		Thread launcher = gated.launch( true );
		gated.awaitStart( "a" );
		gated.awaitStart( "m" );
		assertFalse( gated.started( "n" ) );
		gated.release( "m" );
		Run run = gated.join( launcher );
		assertEquals( List.of(), run.failures, run.failures::toString );
		gated.assertBefore( "finish:m", "start:n" );
	}

	/**
	 * Chain members run in order, one after another, and the chain's outside edges
	 * attach to its first and last members. A chain does not exclude unrelated
	 * flows: {@code a} runs while the chain is still open.
	 */
	@Test
	void chainMembersAreContiguousAndOrdered() {
		Flow c1 = flow( "c1", "chain:C" );
		Flow c2 = flow( "c2", "chain:C" );
		Gated gated = Gated.model( flow( "a" ), c1, c2, flow( "d", c1 ) );
		gated.hold( "c1" );
		Thread launcher = gated.launch( true );
		gated.awaitStart( "a" );
		gated.awaitStart( "c1" );
		assertFalse( gated.started( "c2" ) );
		assertFalse( gated.started( "d" ) );
		gated.release( "c1" );
		Run run = gated.join( launcher );
		assertEquals( List.of(), run.failures, run.failures::toString );
		gated.assertBefore( "start:a", "finish:c1" );
		gated.assertBefore( "finish:c1", "start:c2" );
		gated.assertBefore( "finish:c2", "start:d" );
	}

	/** Flows that publish into the same destination never overlap. */
	@Test
	void sameDestinationPublishersAreSerialised() {
		Flow p = flow( "p" );
		Flow q = flow( "q" );
		Flow r = Creator.build( f -> f.meta( m -> m.description( "r" ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN ).request( new Fields( "left:right" ) )
						.response( new Msg( "response" ) ) )
				.dependency( p, d -> d.from( i -> true, Type.REQUEST, "left" ).to( i -> true, Type.REQUEST,
						"left" ) )
				.dependency( q, d -> d.from( i -> true, Type.REQUEST, "right" ).to( i -> true,
						Type.REQUEST, "right" ) ) );
		Gated gated = Gated.model( flow( "a" ), p, q, r );
		gated.hold( "p" );
		Thread launcher = gated.launch( true );
		gated.awaitStart( "a" );
		gated.awaitStart( "p" );
		assertFalse( gated.started( "q" ) );
		assertFalse( gated.started( "r" ) );
		gated.release( "p" );
		Run run = gated.join( launcher );
		assertEquals( List.of(), run.failures, run.failures::toString );
		gated.assertBefore( "finish:p", "start:q" );
		gated.assertBefore( "finish:q", "start:r" );
	}

	/**
	 * Context-applying flows never overlap each other, but do overlap flows that
	 * apply no context.
	 */
	@Test
	void contextApplyingFlowsAreSerialisedAmongThemselves() {
		Gated gated = Gated.model( flow( "a" ), contextual( "k1" ), contextual( "k2" ) );
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
		gated.awaitStart( "a" );
		gated.awaitStart( "k1" );
		assertFalse( gated.started( "k2" ) );
		gated.release( "k1" );
		Run run = gated.join( launcher );
		assertEquals( List.of(), run.failures, run.failures::toString );
		gated.assertBefore( "finish:k1", "start:k2" );
	}

	/**
	 * The factory may declare parameters other than the {@link FlowExecution}
	 * handle; the extension resolves only its own.
	 */
	@Test
	void factoryMayTakeOtherParameters() {
		Run run = execute( OtherParameterFactory.class, false );
		assertEquals( List.of(), run.failures, run.failures::toString );
		assertEquals( List.of( "a []:SUCCESSFUL" ), run.results );
		assertEquals( "flows(FlowExecution, TestInfo)", OtherParameterFactory.factoryName );
	}

	@FlowTest
	static class OtherParameterFactory {
		static String factoryName;

		@TestFactory
		Stream<DynamicNode> flows( FlowExecution execution, TestInfo info ) {
			factoryName = info.getDisplayName();
			return execution.flocessor( "other parameters", modelOf( List.of( flow( "a" ) ) ) )
					.system( State.LESS, Actrs.BEN )
					.behaviour( a -> a.actual().response( a.expected().response().content() ) )
					.tests();
		}
	}

	/**
	 * Independent flows are all ready at once, so the whole stream can be drawn
	 * without executing anything; each leaf is emitted exactly once.
	 */
	@Test
	void readyStreamEmitsEveryIndependentFlowWithoutExecution() {
		List<String> calls = new ArrayList<>();
		try( FlowExecution execution = new FlowExecution( false ) ) {
			List<DynamicNode> leaves = execution
					.flocessor( "direct", modelOf( List.of( flow( "a" ), flow( "b" ), flow( "c" ) ) ) )
					.system( State.LESS, Actrs.BEN )
					.behaviour( a -> calls.add( a.flow().meta().description() ) )
					.tests().toList();
			assertEquals( List.of( "a []", "b []", "c []" ),
					leaves.stream().map( DynamicNode::getDisplayName ).toList() );
			assertEquals( List.of(), calls );
		}
	}

	/**
	 * Under prepared execution a flow without contexts may run alongside
	 * context-applying flows, so it leaves the applied state in place rather than
	 * removing it.
	 *
	 * @throws Throwable on leaf failure
	 */
	@Test
	void contextFreeFlowsLeaveAppliedContextsInPlace() throws Throwable {
		List<String> events = new ArrayList<>();
		try( FlowExecution execution = new FlowExecution( false ) ) {
			List<DynamicNode> leaves = execution
					.flocessor( "contexts", modelOf( List.of( contextual( "k1" ), flow( "a" ) ) ) )
					.system( State.FUL, Actrs.BEN )
					.applicators( new Applicator<>( Setting.class, 1 ) {
						@Override
						public Comparator<Setting> order() {
							return Comparator.comparing( Setting::name );
						}

						@Override
						public void transition( Setting from, Setting to ) {
							events.add( "context:" + (to == null ? "none" : to.name()) );
						}
					} )
					.behaviour( a -> {
						events.add( "flow:" + a.flow().meta().description() );
						a.actual().response( a.expected().response().content() );
					} )
					.tests().toList();
			assertEquals( List.of( "a []", "k1 []" ),
					leaves.stream().map( DynamicNode::getDisplayName ).toList() );
			// both are ready at once; a context-free flow running after the contextual
			// one must not remove its context
			((DynamicTest) leaves.get( 1 )).getExecutable().execute();
			((DynamicTest) leaves.get( 0 )).getExecutable().execute();
		}
		assertEquals( List.of( "context:k1", "flow:k1", "flow:a" ), events );
	}

	/**
	 * A run that selects no flows still publishes its (empty) report on completion.
	 *
	 * @param dir Isolated artifact directory
	 */
	@Test
	void emptySelectionStillPublishesReport( @TempDir Path dir ) {
		try( Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( dir.toString() );
				Temporary name = AssertionOptions.REPORT_NAME.temporarily( "empty" );
				FlowExecution execution = new FlowExecution( false ) ) {
			PreparedFlocessor runner = execution
					.flocessor( "empty", modelOf( List.of( flow( "a" ) ) ) )
					.system( State.LESS, Actrs.BEN ).reporting( Reporting.QUIETLY )
					.exercising( f -> false, rejection -> {
					} );
			List<DynamicNode> leaves = runner.tests().toList();
			assertEquals( List.of(), leaves );
			assertNotNull( runner.report(), "preparation opens the report" );
			execution.close();
			Index index = new Reader( dir.resolve( "empty" ) ).read();
			assertNotNull( index, "report published for an empty run" );
			assertEquals( List.of(), index.entries );
		}
	}

	/** Every leaf outcome is recorded to the shared History. */
	@Test
	void leafOutcomesAreRecorded() {
		Flow success = flow( "success" );
		Flow unexpected = flow( "unexpected" );
		Flow error = flow( "error" );
		Flow skipped = Creator.build( f -> f.meta( m -> m.description( "skipped" ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.CHE ).request( new Msg( "req" ) )
						.response( new Msg( "rsp" ) ) ) );
		try( FlowExecution execution = new FlowExecution( false ) ) {
			Map<String, DynamicTest> leaves = new HashMap<>();
			execution.flocessor( "outcomes", modelOf( List.of( success, unexpected, error, skipped ) ) )
					.system( State.LESS, Actrs.BEN )
					.behaviour( a -> {
						switch( a.flow().meta().description() ) {
							case "success" -> a.actual().response( a.expected().response().content() );
							case "unexpected" -> a.actual().response( "wrong".getBytes( UTF_8 ) );
							default -> throw new IllegalStateException( "boom" );
						}
					} )
					.tests().forEach( leaf -> leaves.put( leaf.getDisplayName(), (DynamicTest) leaf ) );
			History history = execution.history();
			leaves.get( "success []" ).getExecutable().execute();
			assertEquals( Result.SUCCESS, history.get( success ) );
			assertThrows( AssertionError.class, leaves.get( "unexpected []" ).getExecutable()::execute );
			assertEquals( Result.UNEXPECTED, history.get( unexpected ) );
			assertThrows( IllegalStateException.class,
					leaves.get( "error []" ).getExecutable()::execute );
			assertEquals( Result.ERROR, history.get( error ) );
			assertThrows( TestAbortedException.class,
					leaves.get( "skipped []" ).getExecutable()::execute );
			assertEquals( Result.SKIP, history.get( skipped ) );
		}
		catch( Throwable e ) {
			throw new AssertionError( e );
		}
	}

	/** The progress timeout must be positive, and is frozen with the rest. */
	@Test
	void progressTimeoutIsValidated() {
		try( FlowExecution execution = new FlowExecution( false ) ) {
			PreparedFlocessor runner = execution.flocessor( "timeout", modelOf( List.of( flow( "a" ) ) ) )
					.system( State.LESS, Actrs.BEN );
			for( Duration invalid : List.of( Duration.ZERO, Duration.ofSeconds( -1 ) ) ) {
				assertEquals( "Progress timeout must be positive",
						assertThrows( IllegalArgumentException.class, () -> runner.progressTimeout( invalid ) )
								.getMessage() );
			}
			Duration valid = Duration.ofMinutes( 1 );
			assertEquals( runner, runner.progressTimeout( valid ) );
			runner.tests().toList();
			assertThrows( IllegalStateException.class, () -> runner.progressTimeout( valid ) );
		}
	}

	/**
	 * Interrupting the factory while it waits for a predecessor fails the run with
	 * a diagnostic that names the outstanding work.
	 *
	 * @throws InterruptedException On test interruption
	 */
	@Test
	void interruptedWaitFailsWithDiagnostic() throws InterruptedException {
		Flow a = flow( "a" );
		Flow b = flow( "b", a );
		try( FlowExecution execution = new FlowExecution( false ) ) {
			Iterator<
					DynamicNode> leaves = execution.flocessor( "interrupted", modelOf( List.of( a, b ) ) )
							.system( State.LESS, Actrs.BEN )
							.behaviour( x -> x.actual().response( x.expected().response().content() ) )
							.tests().iterator();
			assertEquals( "a []", leaves.next().getDisplayName() );
			List<Throwable> failures = new ArrayList<>();
			Thread waiter = new Thread( leaves::next, "waiter" );
			waiter.setUncaughtExceptionHandler( ( t, e ) -> failures.add( e ) );
			waiter.start();
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos( 10 );
			while( waiter.getState() != Thread.State.TIMED_WAITING ) {
				assertTrue( System.nanoTime() < deadline, "waiter did not park: " + waiter.getState() );
				Thread.onSpinWait();
			}
			waiter.interrupt();
			waiter.join( 10_000 );
			assertFalse( waiter.isAlive() );
			assertEquals( 1, failures.size(), failures::toString );
			assertTrue( failures.get( 0 ) instanceof IllegalStateException
					&& failures.get( 0 ).getCause() instanceof InterruptedException
					&& failures.get( 0 ).getMessage().contains( "running: [a []], not started: 1" ),
					failures::toString );
		}
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
		gated.configure = r -> r.progressTimeout( Duration.ofMillis( 300 ) )
				.reporting( Reporting.NEVER );
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
				&& f.getMessage().contains( "PT0.3S" )
				&& f.getMessage().contains( "running: [h []], not started: 1" ) ),
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
	 * A fault while decorating the report is classified by one rule: an ordinary
	 * runtime exception becomes a diagnostic and the flow still passes; an
	 * assertion failure or a test abort propagates to the flow's result.
	 *
	 * @param kind The kind of decoration fault
	 * @param dir  Isolated artifact directory
	 */
	@ParameterizedTest
	@ValueSource(strings = { "ordinary", "assertion", "abort" })
	void reportDecorationFaultsAreClassified( String kind, @TempDir Path dir ) {
		RuntimeException ordinary = new IllegalStateException( "decoration failed" );
		Throwable fault = switch( kind ) {
			case "ordinary" -> ordinary;
			case "assertion" -> new AssertionError( "decoration assertion" );
			default -> new TestAbortedException( "decoration abort" );
		};
		List<String> diagnostics = new ArrayList<>();
		Logger runner = Logger.getLogger( "com.mastercard.test.flow.assrt.FlowProcessor" );
		Handler handler = new Handler() {
			@Override
			public void publish( LogRecord record ) {
				diagnostics.add( record.getMessage() );
			}

			@Override
			public void flush() {
				// nothing buffered
			}

			@Override
			public void close() {
				// nothing held
			}
		};
		runner.addHandler( handler );
		try( Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( dir.toString() );
				Temporary name = AssertionOptions.REPORT_NAME.temporarily( "decorated" ) ) {
			Gated gated = Gated.model( flow( "a" ) );
			gated.configure = r -> r.reporting( Reporting.QUIETLY ).motivation( ( text, asrt ) -> {
				if( fault instanceof Error error ) {
					throw error;
				}
				throw (RuntimeException) fault;
			} );
			Run run = gated.join( gated.launch( true ) );
			String expected = switch( kind ) {
				case "ordinary" -> "SUCCESSFUL";
				case "assertion" -> "FAILED";
				default -> "ABORTED";
			};
			assertEquals( List.of( "a []:" + expected ), run.results );
			if( "ordinary".equals( kind ) ) {
				assertEquals( List.of(), run.failures, run.failures::toString );
				assertEquals( List.of( "Report failed: java.lang.IllegalStateException" ), diagnostics );
			}
			else {
				// The fault propagates to the flow; the writer it latched then fails the
				// class-level close as well
				assertEquals( fault, run.failures.get( 0 ), run.failures::toString );
				assertEquals( List.of(), diagnostics );
			}
		}
		finally {
			runner.removeHandler( handler );
		}
	}

	/**
	 * A concurrent run's report holds each executed flow once, with basis links
	 * resolved against final detail identities, and includes flows whose bodies
	 * were skipped.
	 *
	 * @param parallel Whether Jupiter parallel execution is enabled
	 * @param dir      Isolated artifact directory
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void reportLinksAndSkipsAreIdenticalInBothModes( boolean parallel, @TempDir Path dir ) {
		try( Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( dir.toString() );
				Temporary name = AssertionOptions.REPORT_NAME.temporarily( "linked" ) ) {
			Run run = execute( LinkedFactory.class, parallel );
			assertEquals( List.of(), run.diagnostics, run.diagnostics::toString );
			List<String> results = new ArrayList<>( run.results );
			Collections.sort( results );
			assertEquals( List.of( "error []:FAILED", "errorChild []:FAILED", "errorDependent []:ABORTED",
					"failure []:FAILED", "failureChild []:ABORTED", "failureDependent []:FAILED",
					"success []:SUCCESSFUL", "successChild []:SUCCESSFUL", "successDependent []:SUCCESSFUL" ),
					results );
			Reader reader = new Reader( dir.resolve( "linked" ) );
			var index = reader.read();
			Map<String, com.mastercard.test.flow.report.data.Entry> entries = new java.util.TreeMap<>();
			index.entries.forEach( e -> entries.put( e.description, e ) );
			assertEquals( 9, index.entries.size() );
			assertEquals( 9, entries.size(), "each executed flow appears exactly once" );
			// Result tags decorate details after their first write; children's basis
			// links must still point at the parent's final detail path.
			for( String child : List.of( "successChild", "failureChild", "errorChild" ) ) {
				String parent = child.replace( "Child", "" );
				assertEquals( entries.get( parent ).detail, reader.detail( entries.get( child ) ).basis,
						child + " basis" );
			}
			assertTrue( entries.get( "failureChild" ).tags.contains( "SKIP" ) );
			assertTrue( entries.get( "errorDependent" ).tags.contains( "SKIP" ) );
			assertTrue( entries.get( "success" ).tags.contains( "PASS" ) );
			assertTrue( entries.get( "failure" ).tags.contains( "FAIL" ) );
			assertTrue( entries.get( "error" ).tags.contains( "ERROR" ) );
		}
	}

	@FlowTest
	static class LinkedFactory {
		@TestFactory
		Stream<DynamicNode> flows( FlowExecution execution ) {
			return execution.flocessor( "linked", new Mdl() )
					.system( State.FUL, Actrs.BEN ).reporting( Reporting.QUIETLY )
					.behaviour( asrt -> {
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

	/**
	 * An order that cannot be honoured fails preparation, before any leaf is
	 * emitted or any flow body runs, with the one cycle message
	 *
	 * @param kind How the cycle is formed
	 */
	@ParameterizedTest
	@ValueSource(strings = { "hard", "inside-chain", "contracted" })
	void impossibleOrdersFailBeforeLeavesOrSutWork( String kind ) {
		InvalidFactory.kind = kind;
		InvalidFactory.bodies = 0;
		Run run = execute( InvalidFactory.class, false );
		assertEquals( List.of(), run.results );
		assertEquals( 0, InvalidFactory.bodies );
		assertTrue( run.failures.stream().anyMatch( f -> f instanceof IllegalArgumentException
				&& f.getMessage().contains( "Hard prerequisite cycle" ) ), run.failures::toString );
	}

	@FlowTest
	static class InvalidFactory {
		static String kind;
		static int bodies;

		@TestFactory
		Stream<DynamicNode> flows( FlowExecution execution ) {
			ConcreteFlow a = unfinished( "A1",
					kind.equals( "hard" ) ? new String[0] : new String[] { "chain:A" } );
			ConcreteFlow b = unfinished( "B1",
					kind.equals( "inside-chain" ) ? new String[] { "chain:A" } : new String[0] );
			ConcreteFlow c = unfinished( "A2",
					kind.equals( "hard" ) ? new String[0] : new String[] { "chain:A" } );
			b.with( new MutableDependency().source( s -> s.flow( a ) ).build( b ) );
			c.with( new MutableDependency().source( s -> s.flow( b ) ).build( c ) );
			if( !kind.equals( "contracted" ) ) {
				a.with( new MutableDependency().source( s -> s.flow( c ) ).build( a ) );
			}
			return execution
					.flocessor( "invalid order",
							modelOf( List.of( c.complete(), b.complete(), a.complete() ) ) )
					.system( State.FUL, Actrs.BEN ).reporting( Reporting.NEVER )
					.behaviour( assertion -> {
						bodies++;
						assertion.actual().response( assertion.expected().response().content() );
					} ).tests();
		}

		/** Keeps construction open until the cyclic references have been connected */
		private static ConcreteFlow unfinished( String name, String... tags ) {
			Flow template = Creator.build( f -> f
					.meta( m -> m.description( name ).tags( t -> t.addAll( List.of( tags ) ) ) )
					.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN ).request( new Fields( "left:right" ) )
							.response( new Msg( "response" ) ) ) );
			return new ConcreteFlow( null, (ConcreteMetadata) template.meta(),
					(ConcreteRootInteraction) template.root(), Set.of(), Map.of(), Map.of() );
		}
	}

	/**
	 * Tag filtering and the programmatic filter select flows, their prerequisites
	 * are pulled in, and every field binding between them is applied, in either
	 * execution mode
	 *
	 * @param parallel Whether Jupiter parallel execution is enabled
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void filtersThenExactClosurePreserveAllFieldBindings( boolean parallel ) {
		SelectionFactory.bodies.clear();
		SelectionFactory.mutations.clear();
		SelectionFactory.exercised.clear();
		SelectionFactory.orderings = 0;
		Run run = execute( SelectionFactory.class, parallel );
		assertEquals( List.of(), run.failures, run.failures::toString );
		assertEquals( List.of( "A [chain:scenario]:SUCCESSFUL",
				"B [chain:scenario, pick]:SUCCESSFUL" ), run.results );
		assertEquals( List.of( "A", "B" ), List.copyOf( SelectionFactory.bodies ) );
		assertEquals( Set.of( "B", "rejected" ), Set.copyOf( SelectionFactory.exercised ) );
		assertEquals( 2, SelectionFactory.exercised.size() );
		assertEquals( List.of( "left", "right" ), List.copyOf( SelectionFactory.mutations ) );
		assertEquals( 1, SelectionFactory.orderings );
	}

	@FlowTest
	static class SelectionFactory {
		static final List<String> bodies = Collections.synchronizedList( new ArrayList<>() );
		static final List<String> mutations = Collections.synchronizedList( new ArrayList<>() );
		static final List<String> exercised = new ArrayList<>();
		static int orderings;

		@TestFactory
		Stream<DynamicNode> flows( FlowExecution execution ) {
			Flow a = flow( "A", "chain:scenario" );
			Flow basis = flow( "basis" );
			Flow b = Deriver.build( basis, f -> f
					.meta( m -> m.description( "B" )
							.tags( t -> t.addAll( Set.of( "pick", "chain:scenario" ) ) ) )
					.dependency( a, d -> d.from( i -> true, Type.REQUEST, "left" )
							.mutate( value -> {
								mutations.add( "left" );
								return "new-" + value;
							} ).to( i -> true, Type.REQUEST, "left" ) )
					.dependency( a, d -> d.from( i -> true, Type.REQUEST, "right" )
							.mutate( value -> {
								mutations.add( "right" );
								return "new-" + value;
							} ).to( i -> true, Type.REQUEST, "right" ) ) );
			Flow c = Creator.build( f -> f.meta( m -> m.description( "C" ) ).prerequisite( b ) );
			Flow sameChain = flow( "same-chain-only", "chain:scenario" );
			Flow otherChain = flow( "other-chain", "chain:other" );
			Flow rejectedSource = flow( "rejected-source" );
			Flow rejected = Creator
					.build( f -> f.meta( m -> m.description( "rejected" ).tags( t -> t.add( "pick" ) ) )
							.prerequisite( rejectedSource ) );
			List<Flow> all = List.of( c, b, a, basis, sameChain, otherChain, rejected, rejectedSource );
			PreparedFlocessor runner = execution.flocessor( "minimal selection", modelOf( all ) )
					.system( State.FUL, Actrs.BEN ).reporting( Reporting.NEVER )
					.filtering( filter -> filter.includedTags( Set.of( "pick" ) ) )
					.exercising( f -> {
						exercised.add( f.meta().description() );
						return f == b;
					}, rejection -> {
					} )
					.listening( new Listener() {
						@Override
						public void ordering() {
							orderings++;
						}
					} )
					.behaviour( assertion -> {
						bodies.add( assertion.flow().meta().description() );
						if( assertion.flow() == b ) {
							assertEquals( "new-left:new-right", assertion.expected().request().assertable() );
						}
						assertion.actual().request( assertion.expected().request().content() )
								.response( assertion.expected().response().content() );
					} );
			Stream<DynamicNode> tests = runner.tests();
			assertTrue( bodies.isEmpty() );
			assertTrue( mutations.isEmpty() );
			return tests;
		}
	}

	/**
	 * Configuration is snapshotted at preparation; the ambient options are not
	 * re-read.
	 */
	@Test
	void preparationSnapshotsConfiguration() {
		assertEquals( List.of(), execute( ReplaySnapshotFactory.class, false ).failures );
	}

	@FlowTest
	static class ReplaySnapshotFactory {
		@TestFactory
		Stream<DynamicNode> flows( FlowExecution execution ) {
			PreparedFlocessor runner = execution.flocessor( "snapshot", new Mdl() )
					.system( State.LESS, Actrs.BEN )
					.behaviour( a -> a.actual().response( a.expected().response().content() ) );
			try( Temporary replay = AssertionOptions.REPLAY.temporarily( "nonexistent-replay" ) ) {
				return runner.tests();
			}
		}
	}

	/** Duplicate flow identities are rejected before any flow body runs. */
	@Test
	void duplicateIdentitiesFailPreparation() {
		DuplicateFactory.bodies = 0;
		Run run = execute( DuplicateFactory.class, false );
		assertEquals( 0, DuplicateFactory.bodies );
		assertEquals( List.of(), run.results );
		assertTrue( run.failures.stream().anyMatch( f -> f instanceof IllegalArgumentException
				&& f.getMessage().contains( "Duplicate prepared Flow identity: success []" ) ),
				run.failures::toString );
	}

	@FlowTest
	static class DuplicateFactory {
		static int bodies;

		@TestFactory
		Stream<DynamicNode> flows( FlowExecution execution ) {
			return execution.flocessor( "ambiguous", modelOf( List.of(
					new Mdl().flows().findFirst().orElseThrow(),
					new Mdl().flows().findFirst().orElseThrow() ) ) )
					.system( State.LESS, Actrs.BEN ).behaviour( a -> {
						bodies++;
						a.actual().response( a.expected().response().content() );
					} ).tests();
		}
	}

	/**
	 * Interval-based log capture attributes by time, so it is accepted when the
	 * class runs serially and rejected at preparation when it runs concurrently. A
	 * class declared {@code @Execution(SAME_THREAD)} runs serially even with
	 * Jupiter parallelism enabled.
	 *
	 * @param parallel   Whether Jupiter parallel execution is enabled
	 * @param sameThread Whether the factory class opts out of concurrency
	 */
	@ParameterizedTest
	@CsvSource({ "false,false", "true,false", "true,true" })
	void intervalCaptureIsSerialOnly( boolean parallel, boolean sameThread ) {
		IntervalCaptureFactory.events.clear();
		Run run = execute( sameThread ? SameThreadIntervalCaptureFactory.class
				: IntervalCaptureFactory.class, parallel );
		if( parallel && !sameThread ) {
			assertEquals( List.of(), run.results );
			assertEquals( List.of(), List.copyOf( IntervalCaptureFactory.events ) );
			assertTrue( run.failures.stream().anyMatch( f -> f instanceof IllegalStateException
					&& f.getMessage().contains( "Interval-based LogCapture" ) ), run.failures::toString );
		}
		else {
			assertEquals( List.of(), run.failures, run.failures::toString );
			assertEquals( List.of( "a []:SUCCESSFUL" ), run.results );
			assertEquals( List.of( "start", "end" ), List.copyOf( IntervalCaptureFactory.events ) );
		}
	}

	@FlowTest
	static class IntervalCaptureFactory {
		static final List<String> events = new ArrayList<>();

		@TestFactory
		Stream<DynamicNode> flows( FlowExecution execution ) {
			return flows( execution, "interval capture" );
		}

		static Stream<DynamicNode> flows( FlowExecution execution, String title ) {
			return execution.flocessor( title, modelOf( List.of( flow( "a" ) ) ) )
					.system( State.LESS, Actrs.BEN ).reporting( Reporting.QUIETLY )
					.logs( new LogCapture() {
						@Override
						public void start( Flow flow ) {
							events.add( "start" );
						}

						@Override
						public Stream<LogEvent> end( Flow flow ) {
							events.add( "end" );
							return Stream.empty();
						}
					} )
					.behaviour( a -> a.actual().response( a.expected().response().content() ) ).tests();
		}
	}

	@FlowTest
	@Execution(ExecutionMode.SAME_THREAD)
	static class SameThreadIntervalCaptureFactory {
		@TestFactory
		Stream<DynamicNode> flows( FlowExecution execution ) {
			return IntervalCaptureFactory.flows( execution, "same-thread interval capture" );
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
			if( !latch.await( 10, TimeUnit.SECONDS ) ) {
				StringBuilder dump = new StringBuilder( "coordination timed out; threads:\n" );
				Thread.getAllStackTraces().forEach( ( thread, stack ) -> {
					dump.append( thread.getName() ).append( ' ' ).append( thread.getState() ).append( '\n' );
					for( StackTraceElement frame : stack ) {
						dump.append( "    " ).append( frame ).append( '\n' );
					}
				} );
				throw new AssertionError( dump.toString() );
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
					.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN ).request( new Fields( "left:right" ) )
							.response( new Msg( "response" ) ) );
			for( Flow prerequisite : prerequisites ) {
				f.prerequisite( prerequisite );
			}
		} );
	}

	private static Flow flow( String name, String tag ) {
		return flow( name, new String[] { tag } );
	}

	private static Flow flow( String name, String[] tags ) {
		return Creator
				.build( f -> f.meta( m -> m.description( name ).tags( t -> t.addAll( List.of( tags ) ) ) )
						.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN ).request( new Fields( "left:right" ) )
								.response( new Msg( "response" ) ) ) );
	}

	private static Flow contextual( String name ) {
		return Creator.build( f -> f.meta( m -> m.description( name ) ).context( new Setting( name ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN ).request( new Fields( "left:right" ) )
						.response( new Msg( "response" ) ) ) );
	}

	/**
	 * @param flows The flows in the model
	 * @return A model of exactly those flows, honouring tag filters
	 */
	static Model modelOf( List<Flow> flows ) {
		return new Mdl() {
			@Override
			public Stream<Flow> flows( Set<String> include, Set<String> exclude ) {
				return flows.stream().filter( f -> Tags.filter( f.meta().tags(), include, exclude ) );
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
