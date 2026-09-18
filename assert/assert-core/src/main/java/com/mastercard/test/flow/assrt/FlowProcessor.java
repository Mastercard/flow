package com.mastercard.test.flow.assrt;

import static com.mastercard.test.flow.assrt.History.Result.NOT_OBSERVED;
import static java.time.Instant.now;
import static java.time.ZoneId.systemDefault;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Residue;
import com.mastercard.test.flow.assrt.filter.Filter;
import com.mastercard.test.flow.report.Writer;
import com.mastercard.test.flow.report.data.AssertedData;
import com.mastercard.test.flow.report.data.FlowData;
import com.mastercard.test.flow.report.data.InteractionData;
import com.mastercard.test.flow.report.data.LogEvent;
import com.mastercard.test.flow.report.data.ResidueData;
import com.mastercard.test.flow.report.data.TransmissionData;
import com.mastercard.test.flow.report.duct.Duct;
import com.mastercard.test.flow.util.Dependencies;
import com.mastercard.test.flow.util.Flows;

/**
 * Flow processing shared by the fluent adapters. One processor uses one
 * configuration, dependency publisher and History across all flows; each call
 * to {@link #process(Flow)} keeps its evidence and failures local to that
 * invocation. Enumerating flows neither completes a run nor closes a report.
 */
class FlowProcessor {

	/**
	 * The adapter that owns this processor. It supplies the live statefulness
	 * setting, the log source name and the framework-specific skip and comparison
	 * behaviour.
	 */
	private final AbstractFlocessor<?> owner;
	private FlowConfiguration config;
	private final History history;
	private Dependencies dependencies;
	private final Map<Class<? extends Context>, Context> currentContext = new HashMap<>();
	private boolean concurrentContexts;
	private Writer report;
	private RuntimeException reportFailure;
	private boolean reportError;
	private int active;
	private boolean closed;
	private boolean closing;
	/** Run-owned correlated capture, created on first processing while enabled */
	private LogCollector collector;
	/** Makes generated correlation identifiers distinct across concurrent runs */
	private final String runToken = String.format( "%016x",
			ThreadLocalRandom.current().nextLong() );
	private final AtomicInteger executions = new AtomicInteger();

	/**
	 * @param owner   The adapter that this processor works on behalf of
	 * @param config  Configuration owned by the caller
	 * @param history The one History shared with the adapter's result recording
	 */
	FlowProcessor( AbstractFlocessor<?> owner, FlowConfiguration config, History history ) {
		this.owner = owner;
		this.config = config;
		this.history = history;
	}

	/**
	 * Freeze registrations without replacing the processor, History or domain
	 * objects.
	 */
	void freezeConfiguration() {
		config = config.snapshot();
	}

	/**
	 * Context-applying flows are serialised by the caller's ordering, but flows
	 * without contexts may run alongside them. Such flows must then leave applied
	 * state untouched rather than removing it.
	 */
	void concurrentContexts() {
		concurrentContexts = true;
	}

	private String logSource() {
		return owner.getClass().getName();
	}

	/**
	 * Comparison on behalf of an {@link Assertion}, which has no reference to the
	 * adapter.
	 *
	 * @param message  Description of the comparison
	 * @param expected Expected content
	 * @param actual   Observed content
	 */
	void compare( String message, String expected, String actual ) {
		owner.compare( message, expected, actual );
	}

	/** @return The live set of actors under test */
	Set<Actor> system() {
		return config.systemUnderTest;
	}

	/** @return Selected flows in the legacy execution order */
	Stream<Flow> flows() {
		return flows( flow -> {
			/* Legacy selection has no declaration resolver. */ } );
	}

	/**
	 * @param prepare Per-flow preparation before chain ordering
	 * @return Selected and dependency-expanded flows in canonical order
	 */
	Stream<Flow> flows( Consumer<Flow> prepare ) {
		// per system properties, find out which flows we want to exercise and save
		// those settings for future runs
		config.progress.filtering();
		Set<Flow> toRun;
		if( AssertionOptions.SUPPRESS_FILTER.isTrue() ) {
			toRun = config.model.flows().collect( toCollection( FlowProcessor::identities ) );
		}
		else {
			Filter fltr = new Filter( config.model );
			config.filterCfg.accept( fltr );
			fltr.load()
					.blockForUpdates()
					.save();

			// find the flows that pass the user-controlled filter
			toRun = fltr.flows().collect( toCollection( FlowProcessor::identities ) );

			// refine by the programmatic filter
			toRun = toRun.stream()
					.map( f -> {
						if( config.flowFilter.test( f ) ) {
							return f;
						}
						config.filterRejectionLog.accept( String.format(
								"Flow '%s' rejected by .exercising() filter",
								f.meta().id() ) );
						return null;
					} )
					.filter( Objects::nonNull )
					.collect( toCollection( FlowProcessor::identities ) );
		}

		// collect dependencies of those flows - we need them in the execution too.
		// A worklist rather than recursion, as dependency paths can be long
		config.progress.dependencies();
		Deque<Flow> pending = new ArrayDeque<>( toRun );
		while( !pending.isEmpty() ) {
			try( Stream<Flow> prerequisites = pending.removeFirst().dependencies()
					.map( d -> d.source().flow() ).filter( Objects::nonNull ) ) {
				prerequisites.filter( toRun::add ).forEach( pending::addLast );
			}
		}

		// gather the data dependencies for processing
		toRun.forEach( prepare );
		dependencies = new Dependencies( toRun.stream() );

		// find the execution order
		config.progress.ordering();
		Order order = new Order( toRun.stream(), config.applicators.values() );
		return order.order();
	}

	private static Set<Flow> identities() {
		return Collections.newSetFromMap( new IdentityHashMap<>() );
	}

	/**
	 * Processes a flow synchronously on the calling thread.
	 *
	 * @param flow The flow to process after selection has indexed dependencies
	 */
	void process( Flow flow ) {
		synchronized( this ) {
			if( closed ) {
				throw new IllegalStateException( "Flow processing is closed" );
			}
			active++;
			if( collector == null && config.correlatedCapture != null && config.reporting.writing() ) {
				collector = new LogCollector( config.correlatedCapture );
			}
		}
		try {
			new Invocation( flow ).process();
		}
		finally {
			synchronized( this ) {
				active--;
			}
		}
	}

	/** The evidence and deferred failures of one flow's processing */
	private final class Invocation {

		private final Flow flow;
		private final List<Consumer<FlowData>> reportUpdates = new ArrayList<>();
		private final List<String> skipReasons = new ArrayList<>();
		private final List<AssertionError> comparisonFailures = new ArrayList<>();
		private final List<RuntimeException> executionFailures = new ArrayList<>();
		private final List<Assertion> actualMessages = new ArrayList<>();
		private final Capture capture = new Capture();
		private final Correlation correlation;

		private Invocation( Flow flow ) {
			this.flow = flow;
			String extracted = config.correlation == null ? null : config.correlation.apply( flow );
			String id = extracted != null ? extracted
					: "flow-" + runToken + "-" + executions.incrementAndGet();
			correlation = new Correlation() {
				@Override
				public String id() {
					return id;
				}

				@Override
				public void alias( String alias ) {
					capture.bind( alias );
				}
			};
		}

		private void process() {
			try( Capture owned = capture ) {
				owned.start();
				execute();
			}
		}

		private void execute() {
			config.progress.flow( flow );

			Collection<Interaction> toExercise = Flows.interactions( flow )
					// exercise interactions that *enter* the system, not intra-system
					.filter( i -> config.systemUnderTest.contains( i.responder() )
							&& !config.systemUnderTest.contains( i.requester() ) )
					.collect( toList() );

			if( toExercise.isEmpty() ) {
				if( flow.root() != null && config.autonomous.contains( flow.root().requester() ) ) {
					history.recordResult( flow, NOT_OBSERVED );
					reportAndSkip( flow, String.format(
							"No interactions with system [%s], but autonomous actor '%s' is assumed to be doing something",
							config.systemUnderTest.stream()
									.map( Actor::name )
									.sorted()
									.collect( Collectors.joining( "," ) ),
							flow.root().requester().name() ) );
				}
				else {
					reportAndSkip( flow, String.format(
							"No interactions with system [%s]",
							config.systemUnderTest.stream()
									.map( Actor::name )
									.sorted()
									.collect( Collectors.joining( "," ) ) ) );
				}
			}

			if( config.replay.hasData() ) {
				// we're replaying data from a report, no need to look for reasons to skip
				// or to apply contexts
			}
			else {
				checkPreconditions( flow );
				applyContexts( flow, executionFailures );
			}

			Map<Residue, Message> expectedResidue = expectedResidue( flow );

			// keeps track of how many assertions we make - we don't want to tag a flow as a
			// pass if we don't actually test anything
			AtomicInteger assertionCount = new AtomicInteger( 0 );

			toExercise.forEach( ntr -> assertionCount.addAndGet( processInteraction( ntr ) ) );
			// we've processed all of the appropriate interactions

			assertionCount.addAndGet( checkResidue( expectedResidue ) );
			Throwable primary = !executionFailures.isEmpty() ? executionFailures.get( 0 )
					: comparisonFailures.isEmpty() ? null : comparisonFailures.get( 0 );
			preserving( primary, () -> finaliseReport( assertionCount.get() ) );
			preserving( primary, () -> config.progress.flowComplete( flow ) );

			// throw any deferred failures
			if( !executionFailures.isEmpty() ) {
				throw executionFailures.get( 0 );
			}
			if( !comparisonFailures.isEmpty() ) {
				throw comparisonFailures.get( 0 );
			}
			if( !skipReasons.isEmpty() ) {
				owner.skip( skipReasons.get( 0 ) );
			}
			if( assertionCount.get() == 0 ) {
				// we're not really skipping anything here (we've already processed the flow),
				// but this will make things more obvious to whatever is driving the test
				owner.skip( "No assertions made" );
			}
		}

		private int processInteraction( Interaction ntr ) throws AssertionError {
			config.progress.interaction( ntr );
			// provoke the system with input data and capture the outputs
			Assertion assrt = new Assertion( flow, ntr, FlowProcessor.this, correlation );

			try {
				if( config.replay.hasData() ) {
					warn( reportUpdates, "Replaying data from " + config.replaySource );
					String sr = config.replay.populate( assrt );
					if( sr != null ) {
						warn( reportUpdates, sr );
						skipReasons.add( sr );
					}
				}
				else {
					config.test.accept( assrt );
				}
			}
			// Sonar would rather we just catch Exception here, but we're not trying to
			// *recover* from the failure (it gets rethrown below), we're just trying to
			// make sure it gets recorded to the report
			catch( Throwable e ) {
				preserving( e, () -> {
					reportUpdates.add( d -> d.tags.add( Writer.ERROR_TAG ) );
					List<LogEvent> logs = capture.snapshot();
					reportUpdates.add( d -> d.logs.addAll( logs ) );
					reportUpdates
							.add( d -> d.logs.add( error( "Encountered error: " + LogEvent.stackTrace( e ) ) ) );
					reportUpdates
							.add( d -> d.motivation = config.motivationCustomizer.apply( d.motivation, assrt ) );
					report( w -> w.with( flow, reportUpdates.stream().reduce( d -> {
						// no-op
					}, Consumer::andThen ) ), true );
				} );
				throw e;
			}

			// parse the data that we extracted from the system and compare it against the
			// model
			int assertionCount = 0;
			List<Assertion> harvested = assrt.collect( new ArrayList<>() );
			for( MessageAssertion ma : MessageAssertion.values() ) {
				for( Assertion assertion : harvested ) {
					assertionCount += processMessage( assertion, ma );
					actualMessages.add( assertion );
				}
			}
			return assertionCount;
		}

		private int processMessage( Assertion assertion, MessageAssertion ma ) throws AssertionError {
			if( ma.actual( assertion ) != null ) {
				reportUpdates.add( d -> d.root.update(
						i -> i.peer == assertion.expected(),
						i -> ma.report( i ).full.actualBytes = ma.actual( assertion ) ) );

				try {
					checkResult( flow, assertion.expected(), ma.name().toLowerCase(),
							ma.expected( assertion ),
							ma.actual( assertion ),
							ar -> reportUpdates.add( d -> d.root.update(
									i -> i.peer == assertion.expected(),
									i -> {
										TransmissionData td = ma.report( i );
										td.full.actual = ar.fullActual;
										td.asserted.expect = ar.maskedExpect;
										td.asserted.actual = ar.maskedActual;
									} ) ) );
				}
				catch( AssertionError e ) {
					if( !config.reporting.writing()
							&& !AssertionOptions.SUPPRESS_ASSERTION_FAILURE.isTrue() ) {
						// we're not generating a report or suppressing failures, so fail immediately
						throw e;
					}
					// otherwise just store these up - we want to compare all the messages we can
					// (populating the report as a side effect) before failing
					comparisonFailures.add( e );
				}
				catch( RuntimeException e ) {
					if( !config.reporting.writing()
							&& !AssertionOptions.SUPPRESS_ASSERTION_FAILURE.isTrue() ) {
						// we're not generating a report, so fail immediately
						throw e;
					}
					// otherwise just store these up - we want to compare all the messages we can
					// (which populates the report) before failing
					executionFailures.add( e );
				}
				finally {
					reportUpdates
							.add( d -> d.motivation = config.motivationCustomizer.apply( d.motivation,
									assertion ) );
				}
				return 1;
			}
			return 0;
		}

		private void finaliseReport( int assertionCount ) {
			List<LogEvent> logs = capture.snapshot();
			String resultTag = resultTag( assertionCount, comparisonFailures, executionFailures );
			reportUpdates.add( d -> d.tags.add( resultTag ) );
			if( assertionCount == 0 ) {
				warn( reportUpdates, "No assertions made" );
			}
			reportUpdates.add( d -> d.logs.addAll( logs ) );
			reportUpdates.add( d -> executionFailures.stream()
					.map( e -> error( LogEvent.stackTrace( e ) ) )
					.forEach( d.logs::add ) );
			reportUpdates.add( d -> config.systemUnderTest.stream()
					.map( Actor::name )
					.forEach( d.exercised::add ) );
			report( w -> w.with( flow, reportUpdates.stream()
					.reduce( d -> {
						// no-op
					}, Consumer::andThen ) ),
					// error condition
					!comparisonFailures.isEmpty() );
		}

		private void checkPreconditions( Flow checked ) {
			FlowProcessor.this.checkPreconditions( checked, reason -> reportAndSkip( checked, reason ) );
		}

		private void reportAndSkip( Flow skipped, String reason ) {
			try {
				owner.skip( reason );
			}
			catch( RuntimeException | Error primary ) {
				preserving( primary, () -> reportSkip( skipped, reason ) );
				throw primary;
			}
			reportSkip( skipped, reason );
		}

		private void reportSkip( Flow skipped, String reason ) {
			List<LogEvent> logs = capture.snapshot();
			report( w -> w.with( skipped, d -> {
				d.tags.add( Writer.SKIP_TAG );
				d.logs.addAll( logs );
				d.logs.add( warn( "Skipping flow: " + reason ) );
			} ), false );
		}

		/** The log capture of one invocation */
		private final class Capture implements AutoCloseable {
			private final LogCapture source = collector != null ? collector : config.logCapture;
			private boolean begun;
			private boolean ended;
			private List<LogEvent> logs = Collections.emptyList();

			void start() {
				if( config.reporting.writing() ) {
					try {
						source.start( flow );
						begun = true;
						bind( correlation.id() );
					}
					catch( RuntimeException e ) {
						diagnose( "begin", e );
					}
				}
			}

			/** Identifiers only route while this execution's capture is open. */
			void bind( String id ) {
				if( begun && !ended && source == collector ) {
					collector.bind( flow, id );
				}
			}

			List<LogEvent> snapshot() {
				close();
				return logs;
			}

			@Override
			public void close() {
				if( begun && !ended ) {
					ended = true;
					try( Stream<LogEvent> events = source.end( flow ) ) {
						logs = events.map( e -> new LogEvent( e.time, e.level, e.source, e.message ) )
								.collect( Collectors.toUnmodifiableList() );
					}
					catch( RuntimeException e ) {
						diagnose( "end/materialize/close", e );
					}
				}
			}

			private void diagnose( String operation, RuntimeException failure ) {
				if( !ordinaryPeripheralFailure( failure ) ) {
					throw failure;
				}
				String message = "Log capture " + operation + " failed: " + failure.getClass().getName();
				diagnostic( message );
				List<LogEvent> diagnosed = new ArrayList<>( logs );
				diagnosed.add( warn( message ) );
				logs = Collections.unmodifiableList( diagnosed );
			}
		}

		private int checkResidue( Map<Residue, Message> expectedResidue ) {
			AtomicInteger assertionCount = new AtomicInteger();

			expectedResidue.forEach( ( residue, expected ) -> {
				config.progress.after( residue );
				byte[] harvested = null;
				try {
					harvested = checker( residue ).actual( residue, actualMessages );
				}
				catch( Exception e ) {
					IllegalStateException ise = new IllegalStateException(
							"Failed to extract actual residue data for " + residue.name(), e );
					if( !config.reporting.writing() ) {
						throw ise;
					}
					executionFailures.add( ise );
				}
				if( harvested != null ) {
					try {
						Message actual = expected.peer( harvested );
						CheckMessages cm = new CheckMessages(
								actual.assertable(),
								expected.assertable( config.masks ),
								actual.assertable( config.masks ) );

						reportUpdates.add(
								fd -> {
									ResidueData residueData = fd.residue
											.stream()
											.filter( r -> residue.name().equals( r.name ) )
											.findFirst()
											.orElseGet( () -> {
												ResidueData rd = new ResidueData( residue.name(), residue, null, null );
												fd.residue.add( rd );
												return rd;
											} );
									residueData.masked = new AssertedData( cm.maskedExpect, cm.maskedActual );
									residueData.full = new AssertedData( expected.assertable(), cm.fullActual );
								} );

						assertionCount.incrementAndGet();
						owner.compare( String.format( "Residue '%s'", residue.name() ),
								cm.maskedExpect,
								cm.maskedActual );
					}
					catch( AssertionError ae ) {
						if( !config.reporting.writing() ) {
							throw ae;
						}
						comparisonFailures.add( ae );
					}
					catch( Exception e ) {
						IllegalArgumentException iae = new IllegalArgumentException(
								"Failed to parse actual residue data for " + residue.name(), e );
						if( !config.reporting.writing() ) {
							throw iae;
						}
						executionFailures.add( iae );
					}
				}
			} );

			return assertionCount.get();
		}
	}

	/**
	 * Retains secondary faults without replacing primary execution/control
	 * evidence.
	 */
	private static void preserving( Throwable primary, Runnable action ) {
		try {
			action.run();
		}
		catch( RuntimeException | Error secondary ) {
			if( primary == null ) {
				throw secondary;
			}
			if( primary != secondary ) {
				primary.addSuppressed( secondary );
			}
		}
	}

	/**
	 * A capture or reporting fault is ordinary when it is a runtime or I/O
	 * exception that is not an interruption or cancellation and whose cause chain
	 * holds no {@link Error} and no test-control exception. Ordinary faults become
	 * diagnostics; anything else must fail or abort the test.
	 *
	 * @param failure The fault
	 * @return <code>true</code> if the fault may be reduced to a diagnostic
	 */
	private static boolean ordinaryPeripheralFailure( Throwable failure ) {
		if( !(failure instanceof RuntimeException || failure instanceof IOException) ) {
			return false;
		}
		Set<Throwable> seen = Collections.newSetFromMap( new IdentityHashMap<>() );
		for( Throwable cause = failure; cause != null && seen.add( cause ); cause = cause.getCause() ) {
			if( cause instanceof Error || cause instanceof InterruptedException
					|| cause instanceof InterruptedIOException || cause instanceof ClosedByInterruptException
					|| cause instanceof CancellationException || testControl( cause ) ) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Test abort and skip signals are runtime exceptions from the test framework.
	 * The frameworks are not compile-time dependencies of this module, so they are
	 * recognised by package.
	 */
	private static boolean testControl( Throwable failure ) {
		for( Class<?> type = failure.getClass(); type != null; type = type.getSuperclass() ) {
			String name = type.getName();
			if( name.startsWith( "org.opentest4j." ) || name.startsWith( "org.junit." ) ) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Runner diagnostics for report and capture faults that did not fail the test
	 *
	 * @param message What went wrong, without stack trace or source message text
	 */
	private static void diagnostic( String message ) {
		System.err.println( "Flow: " + message );
	}

	private void checkPreconditions( Flow flow, Consumer<String> reportAndSkip ) {
		if( !AssertionOptions.SUPPRESS_SYSTEM_CHECK.isTrue() ) {
			// If there are implied system dependencies that the system cannot satisfy...
			flow.implicit()
					.filter( a -> !config.systemUnderTest.contains( a ) )
					.findFirst()
					.ifPresent( a -> reportAndSkip.accept(
							"Implicitly depends on " + a + ", which is not part of the system under test" ) );
		}

		// If the history suggests we're going to fail...
		// (this could be missing flow dependencies or a failing basis)
		history.skipReason( flow, owner.statefulness, config.systemUnderTest )
				.ifPresent( reportAndSkip );
	}

	private void applyContexts( Flow flow, List<RuntimeException> executionFailures ) {
		try {
			// work out the context updates
			Set<Context> contextUpdates = new TreeSet<>(
					Comparator.comparing( ctx -> ctx.getClass().getName() ) );
			flow.context()
					.filter( ctx -> ctx.domain().stream().anyMatch( config.systemUnderTest::contains ) )
					.forEach( contextUpdates::add );
			if( concurrentContexts && contextUpdates.isEmpty() ) {
				return;
			}
			synchronized( currentContext ) {
				Set<Class<? extends Context>> unupdated = new HashSet<>( currentContext.keySet() );
				contextUpdates.forEach( ctx -> unupdated.remove( ctx.getClass() ) );

				// deactivate the orphaned context types - those that existed on the previous
				// flow but not on the current one. We're doing this *before* the normal context
				// changes as there can be dependencies between contexts - the ones on the new
				// flow might not cope with the ones on the old flow that they know nothing
				// about
				unupdated.forEach( this::removeContext );

				// apply the context for the new flow
				contextUpdates.forEach( this::updateContext );
			}
		}
		catch( RuntimeException e ) {
			if( !config.reporting.writing() ) {
				// we're not generating a report, so fail immediately
				throw e;
			}
			// otherwise just store these up - we want to compare all the messages we can
			// (which populates the report) before failing
			executionFailures.add( e );
		}
	}

	@SuppressWarnings("unchecked")
	private <C extends Context> void updateContext( C ctx ) {
		config.progress.context( ctx );
		Class<? extends Context> ctxt = ctx.getClass();
		Applicator<C> apl = (Applicator<C>) applicator( ctxt );
		C current = (C) currentContext.get( ctxt );
		apl.transition( current, ctx );
		currentContext.put( ctxt, ctx );
	}

	@SuppressWarnings("unchecked")
	private <C extends Context> void removeContext( Class<C> ctxt ) {
		Applicator<C> apl = applicator( ctxt );
		C current = (C) currentContext.get( ctxt );
		apl.transition( current, null );
		currentContext.remove( ctxt );
	}

	private <C extends Context> Applicator<C> applicator( Class<C> ctxt ) {
		@SuppressWarnings("unchecked")
		Applicator<C> apl = (Applicator<C>) config.applicators.get( ctxt );
		if( apl == null ) {
			throw new IllegalStateException( "No applicator for context type " + ctxt );
		}
		return apl;
	}

	private Map<Residue, Message> expectedResidue( Flow flow ) {
		Map<Residue, Message> expected = new HashMap<>();
		flow.residue()
				.filter( r -> config.checkers.containsKey( r.getClass() ) )
				.forEach( r -> {
					config.progress.before( r );
					expected.put( r, checker( r ).expected( r ) );
				} );
		return expected;
	}

	@SuppressWarnings("unchecked")
	private <R extends Residue> Checker<R> checker( R rsd ) {
		return (Checker<R>) config.checkers.get( rsd.getClass() );
	}

	private static String resultTag( int assertionCount,
			List<AssertionError> compareFailures, List<RuntimeException> parseFailures ) {
		if( !parseFailures.isEmpty() ) {
			// we choked on data extracted from the system
			return Writer.ERROR_TAG;
		}
		if( !compareFailures.isEmpty() ) {
			// The data that we extracted was not as expected
			return Writer.FAIL_TAG;
		}
		if( assertionCount == 0 ) {
			// We failed to extract any data from the system
			return Writer.SKIP_TAG;
		}
		return Writer.PASS_TAG;
	}

	private void warn( List<Consumer<FlowData>> reportUpdates, String msg ) {
		reportUpdates.add( d -> d.logs.add( warn( msg ) ) );
	}

	private LogEvent warn( String msg ) {
		return new LogEvent( Instant.now(), "WARN", logSource(), msg );
	}

	private LogEvent error( String msg ) {
		return new LogEvent( Instant.now(), "ERROR", logSource(), msg );
	}

	private enum MessageAssertion {

		REQUEST(
				a -> a.actual().request(),
				a -> a.expected().request(),
				i -> i.request),
		RESPONSE(
				a -> a.actual().response(),
				a -> a.expected().response(),
				i -> i.response);

		MessageAssertion( Function<Assertion, byte[]> actual,
				Function<Assertion, Message> expected,
				Function<InteractionData, TransmissionData> report ) {
			this.actual = actual;
			this.expected = expected;
			this.report = report;
		}

		private final Function<Assertion, byte[]> actual;
		private final Function<Assertion, Message> expected;
		private final Function<InteractionData, TransmissionData> report;

		public byte[] actual( Assertion assrt ) {
			return actual.apply( assrt );
		}

		public Message expected( Assertion assrt ) {
			return expected.apply( assrt );
		}

		public TransmissionData report( InteractionData ntr ) {
			return report.apply( ntr );
		}
	}

	private void checkResult( Flow flow, Interaction interaction, String type, Message expected,
			byte[] actual, Consumer<CheckMessages> reportUpdate ) {
		try {
			Message am = dependencies.publish( flow, interaction, expected, actual );

			CheckMessages messages = new CheckMessages(
					am.assertable(),
					expected.assertable( config.masks ),
					am.assertable( config.masks ) );
			reportUpdate.accept( messages );
			owner.compare(
					String.format( "%s%n%s %s->%s %s %s",
							flow.meta().id(), flow.meta().trace(),
							interaction.requester(), interaction.responder(), interaction.tags(), type ),
					messages.maskedExpect,
					messages.maskedActual );
		}
		catch( Exception e ) {
			throw new IllegalArgumentException(
					String.format( "Failed to parse %s message from actual data", type ), e );
		}
	}

	private static class CheckMessages {

		public final String fullActual;
		public final String maskedExpect;
		public final String maskedActual;

		public CheckMessages( String fullActual, String maskedExpect, String maskedActual ) {
			this.fullActual = fullActual;
			this.maskedExpect = maskedExpect;
			this.maskedActual = maskedActual;
		}
	}

	private static final Supplier<String> RUN_DATETIME = () -> DateTimeFormatter
			.ofPattern( "yyMMdd-HHmmss" )
			.format( now().atZone( systemDefault() ) );

	private void report( Consumer<Writer> data, boolean error ) {
		if( !config.reporting.writing() || reportFailed() )
			return;
		try {
			updateReport( data, error );
		}
		catch( RuntimeException failure ) {
			if( !config.finalOnlyReporting || !ordinaryPeripheralFailure( failure ) )
				throw failure;
			reportFailed( failure );
		}
	}

	private void updateReport( Consumer<Writer> data, boolean error ) {
		Path reportDir;
		Writer target;
		synchronized( this ) {
			reportError |= error;
			reportDir = null;
			if( report == null ) {

				String testTitle = config.title;

				Path testDir = Paths.get( AssertionOptions.ARTIFACT_DIR.value(), config.reportPath );

				// work out what the report directory should be called
				String name = AssertionOptions.REPORT_NAME.value();
				if( name == null ) {
					name = RUN_DATETIME.get();
				}
				if( config.replay.hasData() ) {
					// reports that have been generated from replaying historic data don't really
					// imply anything about the behaviour of the system under test, so we want them
					// to be really obvious. Hence we're giving them a directory name suffix and an
					// addendum to the test report title
					name += Replay.REPLAYED_SUFFIX;
					testTitle += " (replay)";
					// The dir name suffix also stops us overwriting the data source when
					// the REPORT_NAME property is the same as the REPLAY property
				}

				reportDir = testDir.resolve( name );

				report = new Writer( config.model.title(), testTitle, reportDir,
						config.finalOnlyReporting ? Writer.Indexing.FINAL_ONLY
								: Writer.Indexing.IMMEDIATE );
				if( !"latest".equals( reportDir.getFileName().toString() ) ) {
					linkLatest( testDir.resolve( "latest" ), reportDir );
				}
			}
			target = report;
		}

		data.accept( target );

		if( reportDir != null && !config.finalOnlyReporting ) {
			// We've just created a new report: if appropriate, open a browser to it.
			present( target, error );
		}
	}

	private void present( Writer target, boolean error ) {
		if( !config.reporting.shouldOpen( error ) )
			return;
		try {
			if( AssertionOptions.DUCT.isTrue() ) {
				// if you've traced a ClassNotFoundException or NoClassDefFoundError to here,
				// then you've forgotten to add the duct module to your dependencies.
				Duct.serve( target.path() );
			}
			else {
				target.browse();
			}
		}
		catch( RuntimeException failure ) {
			if( !config.finalOnlyReporting || !ordinaryPeripheralFailure( failure ) )
				throw failure;
			diagnostic( "Report presentation failed: " + failure.getClass().getName() );
		}
	}

	private synchronized boolean reportFailed() {
		return reportFailure != null;
	}

	private synchronized void reportFailed( RuntimeException failure ) {
		if( reportFailure == null ) {
			reportFailure = failure;
			diagnostic( "Report failed: " + failure.getClass().getName() );
		}
	}

	/** Creates the final-only report so that an empty run still publishes one */
	void initializeReport() {
		if( config.finalOnlyReporting )
			report( ignored -> {
				// no flow data to add
			}, false );
	}

	private static void linkLatest( Path linkPath, Path reportDir ) {
		try {
			boolean shouldLink;
			// Ordinary files and directories at latest may be user-owned.
			if( Files.exists( linkPath, LinkOption.NOFOLLOW_LINKS ) ) {
				if( Files.isSymbolicLink( linkPath ) ) {
					Files.delete( linkPath );
					shouldLink = true;
				}
				else {
					shouldLink = false;
				}
			}
			else {
				shouldLink = true;
			}

			if( shouldLink ) {
				Files.createSymbolicLink( linkPath, linkPath.getParent().relativize( reportDir ) );
			}
		}
		catch( @SuppressWarnings("unused") IOException ioe ) {
			// The symlink to the latest report is a nice-to-have. Some platforms (e.g.:
			// windows) restrict the ability to create symlinks so we can't count on it
			// working.
		}
	}

	/** @return The report path, or null while disabled or after creation failure */
	synchronized Path report() {
		return report == null ? null : report.path();
	}

	/**
	 * Closes the report and the log source. Called once the owning adapter has
	 * finished processing; enumerating flows does not complete a run.
	 */
	void complete() {
		initializeReport();
		Writer closingReport;
		boolean finalPublication;
		LogCollector capture;
		synchronized( this ) {
			if( active != 0 || closing ) {
				throw new IllegalStateException( "Flow processing is still active or completing" );
			}
			finalPublication = config.finalOnlyReporting && !closed;
			closed = true;
			closing = true;
			closingReport = reportFailure == null ? report : null;
			capture = collector;
		}
		try {
			// close the log source first, so that late events can go into the report
			if( capture != null ) {
				capture.close( FlowProcessor::ordinaryPeripheralFailure );
				capture.late().forEach( ( flow, events ) -> report(
						writer -> writer.with( flow, detail -> detail.logs.addAll( events ) ), false ) );
			}
			// a failed writer is kept so that repeated close rethrows its failure
			if( closingReport != null ) {
				try {
					closingReport.close();
					if( finalPublication )
						present( closingReport, reportError );
				}
				catch( RuntimeException failure ) {
					if( !config.finalOnlyReporting || !ordinaryPeripheralFailure( failure ) )
						throw failure;
					reportFailed( failure );
				}
			}
		}
		finally {
			synchronized( this ) {
				closing = false;
			}
		}
	}

}
