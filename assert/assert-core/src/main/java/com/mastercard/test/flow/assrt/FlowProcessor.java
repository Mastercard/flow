package com.mastercard.test.flow.assrt;

import java.nio.file.Path;
import java.time.Instant;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mastercard.test.flow.assrt.History.Result.NOT_OBSERVED;
import static java.util.stream.Collectors.toCollection;

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
	private final ReportLifecycle report = new ReportLifecycle( () -> config );
	private int active;
	private boolean closed;
	private boolean closing;
	/** Run-owned correlated capture, created on first processing while enabled */
	private LogCollector collector;
	/** Makes generated correlation identifiers distinct across concurrent runs */
	private final String runId = String.format( "%016x",
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
		/**
		 * Customised outside the report writer: a customizer fault must not latch the
		 * writer, which would silently discard a final-only report
		 */
		private String motivation;
		/**
		 * Assertions awaiting motivation customisation. Customisation is deferred to
		 * report finalisation so that the customizer sees a closed, frozen log capture.
		 */
		private final List<Assertion> toCustomise = new ArrayList<>();

		private Invocation( Flow flow ) {
			this.flow = flow;
			motivation = flow.meta().motivation();
			String extracted = config.correlation == null ? null : config.correlation.apply( flow );
			String id = extracted != null ? extracted
					: "flow-" + runId + "-" + executions.incrementAndGet();
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
					.toList();

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
				checkPreconditions();
				applyContexts();
			}

			Map<Residue, Message> expectedResidue = expectedResidue();

			// keeps track of how many assertions we make - we don't want to tag a flow as a
			// pass if we don't actually test anything
			AtomicInteger assertionCount = new AtomicInteger( 0 );

			toExercise.forEach( ntr -> assertionCount.addAndGet( processInteraction( ntr ) ) );
			// we've processed all of the appropriate interactions

			assertionCount.addAndGet( checkResidue( expectedResidue ) );
			Throwable primary = null;
			if( !executionFailures.isEmpty() ) {
				primary = executionFailures.get( 0 );
			}
			else if( !comparisonFailures.isEmpty() ) {
				primary = comparisonFailures.get( 0 );
			}
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

		// Catching Throwable is deliberate: whatever the behaviour callback fails
		// with, the report must record it, and it is rethrown immediately.
		@SuppressWarnings("java:S1181")
		private int processInteraction( Interaction ntr ) throws AssertionError {
			config.progress.interaction( ntr );
			// provoke the system with input data and capture the outputs
			Assertion assrt = new Assertion( flow, ntr, FlowProcessor.this, correlation );

			try {
				if( config.replay.hasData() ) {
					reportWarning( "Replaying data from " + config.replaySource );
					String sr = config.replay.populate( assrt );
					if( sr != null ) {
						reportWarning( sr );
						skipReasons.add( sr );
					}
				}
				else {
					config.test.accept( assrt );
				}
			}
			// We're not trying to *recover* from the failure (it gets rethrown below),
			// we're just trying to make sure it gets recorded to the report
			catch( Throwable e ) {
				preserving( e, () -> {
					reportUpdates.add( d -> d.tags.add( Writer.ERROR_TAG ) );
					List<LogEvent> logs = capture.snapshot();
					reportUpdates.add( d -> d.logs.addAll( logs ) );
					reportUpdates
							.add( d -> d.logs.add( error( "Encountered error: " + LogEvent.stackTrace( e ) ) ) );
					// earlier interactions were customised in the normal order, then the failure
					toCustomise.forEach( this::customiseMotivation );
					customiseMotivation( assrt );
					reportUpdates.add( d -> d.motivation = motivation );
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
					checkResult( assertion.expected(), ma.name().toLowerCase(),
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
					toCustomise.add( assertion );
				}
				return 1;
			}
			return 0;
		}

		/**
		 * Applies the {@link MotivationCustomizer} to the report text. Nothing is
		 * customised when no report is written. An ordinary fault in the customizer
		 * costs only this flow's decoration when the report is final-only; anything
		 * else is the caller's failure to see.
		 */
		private void customiseMotivation( Assertion assertion ) {
			if( !config.reporting.writing() ) {
				return;
			}
			try {
				motivation = config.motivationCustomizer.apply( motivation, assertion );
			}
			catch( RuntimeException e ) {
				if( !config.finalOnlyReporting || !Faults.ordinary( e ) ) {
					throw e;
				}
				Faults.diagnostic( "Motivation customisation failed for " + flow.meta().id() + ": "
						+ e.getClass().getName() );
			}
		}

		private void finaliseReport( int assertionCount ) {
			List<LogEvent> logs = capture.snapshot();
			toCustomise.forEach( this::customiseMotivation );
			String resultTag = resultTag( assertionCount, comparisonFailures, executionFailures );
			reportUpdates.add( d -> d.tags.add( resultTag ) );
			reportUpdates.add( d -> d.motivation = motivation );
			if( assertionCount == 0 ) {
				reportWarning( "No assertions made" );
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

		/** Adds a warning to this flow's report entry */
		private void reportWarning( String msg ) {
			reportUpdates.add( d -> d.logs.add( warn( msg ) ) );
		}

		private void checkPreconditions() {
			if( !AssertionOptions.SUPPRESS_SYSTEM_CHECK.isTrue() ) {
				// If there are implied system dependencies that the system cannot satisfy...
				flow.implicit()
						.filter( a -> !config.systemUnderTest.contains( a ) )
						.findFirst()
						.ifPresent( a -> reportAndSkip( flow,
								"Implicitly depends on " + a + ", which is not part of the system under test" ) );
			}

			// If the history suggests we're going to fail...
			// (this could be missing flow dependencies or a failing basis)
			history.skipReason( flow, owner.statefulness, config.systemUnderTest )
					.ifPresent( reason -> reportAndSkip( flow, reason ) );
		}

		private void applyContexts() {
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
				transition( contextUpdates );
			}
			catch( RuntimeException e ) {
				deferExecutionFailure( e );
			}
		}

		private void reportAndSkip( Flow skipped, String reason ) {
			try {
				owner.skip( reason );
			}
			catch( RuntimeException primary ) {
				// framework skip signals are runtime exceptions
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
								.toList();
					}
					catch( RuntimeException e ) {
						diagnose( "end/materialize/close", e );
					}
				}
			}

			private void diagnose( String operation, RuntimeException failure ) {
				if( !Faults.ordinary( failure ) ) {
					throw failure;
				}
				String message = "Log capture " + operation + " failed: " + failure.getClass().getName();
				Faults.diagnostic( message );
				List<LogEvent> diagnosed = new ArrayList<>( logs );
				diagnosed.add( warn( message ) );
				logs = Collections.unmodifiableList( diagnosed );
			}
		}

		private int checkResidue( Map<Residue, Message> expectedResidue ) {
			int assertionCount = 0;
			for( Map.Entry<Residue, Message> e : expectedResidue.entrySet() ) {
				assertionCount += checkResidue( e.getKey(), e.getValue() );
			}
			return assertionCount;
		}

		/**
		 * @return The number of assertions made, which counts a comparison that failed
		 *         but not data that could not be extracted or parsed
		 */
		private int checkResidue( Residue residue, Message expected ) {
			config.progress.after( residue );
			byte[] harvested = null;
			try {
				harvested = checker( residue ).actual( residue, actualMessages );
			}
			catch( Exception e ) {
				deferExecutionFailure( new IllegalStateException(
						"Failed to extract actual residue data for " + residue.name(), e ) );
			}
			if( harvested == null ) {
				return 0;
			}
			int assertionCount = 0;
			try {
				Message actual = expected.peer( harvested );
				CheckMessages cm = new CheckMessages(
						actual.assertable(),
						expected.assertable( config.masks ),
						actual.assertable( config.masks ) );
				reportUpdates.add( fd -> {
					ResidueData residueData = residueData( fd, residue );
					residueData.masked = new AssertedData( cm.maskedExpect, cm.maskedActual );
					residueData.full = new AssertedData( expected.assertable(), cm.fullActual );
				} );
				assertionCount = 1;
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
				deferExecutionFailure( new IllegalArgumentException(
						"Failed to parse actual residue data for " + residue.name(), e ) );
			}
			return assertionCount;
		}

		/** Fails immediately when no report will hold the failure */
		private void deferExecutionFailure( RuntimeException failure ) {
			if( !config.reporting.writing() ) {
				throw failure;
			}
			executionFailures.add( failure );
		}

		private static ResidueData residueData( FlowData fd, Residue residue ) {
			return fd.residue.stream()
					.filter( r -> residue.name().equals( r.name ) )
					.findFirst()
					.orElseGet( () -> {
						ResidueData rd = new ResidueData( residue.name(), residue, null, null );
						fd.residue.add( rd );
						return rd;
					} );
		}

		@SuppressWarnings("unchecked")
		private <R extends Residue> Checker<R> checker( R rsd ) {
			return (Checker<R>) config.checkers.get( rsd.getClass() );
		}

		private Map<Residue, Message> expectedResidue() {
			Map<Residue, Message> expected = new HashMap<>();
			flow.residue()
					.filter( r -> config.checkers.containsKey( r.getClass() ) )
					.forEach( r -> {
						config.progress.before( r );
						expected.put( r, checker( r ).expected( r ) );
					} );
			return expected;
		}

		private LogEvent error( String msg ) {
			return new LogEvent( Instant.now(), "ERROR", logSource(), msg );
		}

		private LogEvent warn( String msg ) {
			return new LogEvent( Instant.now(), "WARN", logSource(), msg );
		}

		private String logSource() {
			return owner.getClass().getName();
		}

		/**
		 * Deactivates the context types that the previous flow applied but this one
		 * does not, then applies this flow's contexts. Removal comes first as there can
		 * be dependencies between contexts: the ones on the new flow might not cope
		 * with the ones on the old flow that they know nothing about.
		 */
		private void transition( Set<Context> contextUpdates ) {
			synchronized( currentContext ) {
				Set<Class<? extends Context>> unupdated = new HashSet<>( currentContext.keySet() );
				contextUpdates.forEach( ctx -> unupdated.remove( ctx.getClass() ) );
				unupdated.forEach( this::removeContext );
				contextUpdates.forEach( this::updateContext );
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

		private void checkResult( Interaction interaction, String type,
				Message expected, byte[] actual, Consumer<CheckMessages> reportUpdate ) {
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
	}

	/**
	 * Retains secondary faults without replacing primary execution/control
	 * evidence.
	 */
	private static void preserving( Throwable primary, Runnable action ) {
		try {
			action.run();
		}
		catch( RuntimeException | AssertionError secondary ) {
			if( primary == null ) {
				throw secondary;
			}
			if( primary != secondary ) {
				primary.addSuppressed( secondary );
			}
		}
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

	private void report( Consumer<Writer> data, boolean error ) {
		report.update( data, error );
	}

	/** Creates the final-only report so that an empty run still publishes one */
	void initializeReport() {
		report.initialize();
	}

	/** @return The report path, or null while disabled or after creation failure */
	Path report() {
		return report.path();
	}

	/**
	 * Closes the report and the log source. Called once the owning adapter has
	 * finished processing; enumerating flows does not complete a run.
	 */
	void complete() {
		initializeReport();
		LogCollector capture;
		synchronized( this ) {
			if( active != 0 || closing ) {
				throw new IllegalStateException( "Flow processing is still active or completing" );
			}
			closed = true;
			closing = true;
			capture = collector;
		}
		try {
			// close the log source first, so that late events can go into the report
			completeCapture( capture );
			report.close();
		}
		finally {
			synchronized( this ) {
				closing = false;
			}
		}
	}

	private void completeCapture( LogCollector capture ) {
		if( capture == null ) {
			return;
		}
		capture.close( Faults::ordinary );
		capture.late().forEach( ( flow, events ) -> report(
				writer -> writer.with( flow, detail -> detail.logs.addAll( events ) ), false ) );
		String unrouted = capture.unrouted();
		if( unrouted != null ) {
			Faults.diagnostic( unrouted );
		}
	}

}
