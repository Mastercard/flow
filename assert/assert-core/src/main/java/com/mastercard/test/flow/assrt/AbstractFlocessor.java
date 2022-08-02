package com.mastercard.test.flow.assrt;

import static java.time.Instant.now;
import static java.time.ZoneId.systemDefault;
import static java.util.stream.Collectors.toList;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.Residue;
import com.mastercard.test.flow.Unpredictable;
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
 * Core behaviour for comparing {@link Flow} data against the system under test
 *
 * @param <T> self type
 */
public abstract class AbstractFlocessor<T extends AbstractFlocessor<T>> {

	/**
	 * Defines whether the system under test is stateful or not.
	 * <p>
	 * Note that a stateless system could either be:
	 * <ul>
	 * <li><i>Actually</i> stateless - no data storage of any kind</li>
	 * <li>Stateful, but with the storage being cleared down to some known baseline
	 * between each {@link Flow}</li>
	 * </ul>
	 * <p>
	 * Which of these situations apply depends on the behaviour of the assertion
	 * component, which is why statefulness is configured here rather than on the
	 * {@link Actor}
	 * </p>
	 */
	public enum State {
		/**
		 * System behaviour is independent of the {@link Flow}s that are processed.
		 * Missing a dependency {@link Flow} is not a reason to skip a subsequent
		 * {@link Flow}.
		 */
		LESS,
		/**
		 * System behaviour is changed by the processing of {@link Flow}s. If any of a
		 * {@link Flow}'s dependencies have not been processed, the {@link Flow} will be
		 * skipped.
		 */
		FUL
	}

	private final Model model;
	private final String title;
	private final Dependencies dependencies;
	private Reporting reporting = Reporting.NEVER;
	private Writer report;

	/**
	 * Tracks the outcome of processing {@link Flow}s to inform further processing
	 */
	protected final History history = new History();

	/**
	 * The set of actors that are being exercised in this test.
	 */
	private final Set<Actor> systemUnderTest = new HashSet<>();

	/**
	 * Whether the system's subsequent behaviour will be changed by the processing
	 * of a {@link Flow}
	 */
	protected State statefulness;

	/**
	 * The current state of the system under test
	 */
	private final Map<Class<? extends Context>, Context> currentContext = new HashMap<>();

	/**
	 * The applicators for enforcing {@link Context} data on the system under test
	 */
	private final Map<Class<? extends Context>, Applicator<?>> applicators = new HashMap<>();

	/**
	 * The checkers for asserting on residual impacts on the system under test
	 */
	private final Map<Class<? extends Residue>, Checker<?>> checkers = new HashMap<>();

	/**
	 * The path to the execution report that we're replaying data from, or
	 * <code>null</code> if we're not replaying
	 */
	private final String replaySource = Replay.source();
	/**
	 * If we're replaying, we'll use this as the source of data for assertion
	 */
	private final Replay replay = new Replay( replaySource );

	/**
	 * Test action
	 */
	private Consumer<Assertion> test = a -> {
		throw new IllegalStateException( "No test behaviour specified" );
	};

	private Unpredictable[] masks = {};

	/**
	 * How system logs are captured into the report
	 */
	private LogCapture logCapture = LogCapture.NO_OP;

	/**
	 * @param title The title of this test
	 * @param model The model to process
	 */
	protected AbstractFlocessor( String title, Model model ) {
		this.title = title;
		this.model = model;
		dependencies = new Dependencies( model );
	}

	/**
	 * Controls report generation
	 *
	 * @param r Whether or not to generate a report, and whether or not to display
	 *          it at the conclusion of testing
	 * @return <code>this</code>
	 */
	public T reporting( Reporting r ) {
		reporting = r;
		return self();
	}

	/**
	 * Controls field masking
	 *
	 * @param sources The sources of unpredictable data in the system under test, in
	 *                the order in which the resulting fields should be masked
	 * @return <code>this</code>
	 */
	public T masking( Unpredictable... sources ) {
		this.masks = sources.clone();
		return self();
	}

	/**
	 * Sets the scope of this test
	 *
	 * @param state  Whether the system under test can carry behaviour-affecting
	 *               state from one {@link Flow} to the next
	 * @param actors The actors that are being exercised in the test
	 * @return <code>this</code>
	 */
	public T system( State state, Actor... actors ) {
		statefulness = state;
		systemUnderTest.clear();
		Collections.addAll( systemUnderTest, actors );
		return self();
	}

	/**
	 * Adds {@link Context} {@link Applicator}s to the test
	 *
	 * @param applctrs How to apply {@link Context} data to the system under test
	 * @return <code>this</code>
	 */
	public T applicators( Applicator<?>... applctrs ) {
		for( Applicator<?> applicator : applctrs ) {
			applicators.put( applicator.contextType(), applicator );
		}
		return self();
	}

	/**
	 * Adds {@link Residue} {@link Checker}s to the test
	 *
	 * @param chckrs How to check {@link Residue} data in the system under test
	 * @return <code>this</code>
	 */
	public T checkers( Checker<?>... chckrs ) {
		for( Checker<?> checker : chckrs ) {
			checkers.put( checker.residueType(), checker );
		}
		return self();
	}

	/**
	 * Configures log capturing behaviour
	 *
	 * @param lc A source of log events that occurred during the exercise of the
	 *           {@link Flow}.
	 * @return <code>this</code>
	 */
	public T logs( LogCapture lc ) {
		logCapture = lc;
		return self();
	}

	/**
	 * Defines subset of the system that is under test
	 *
	 * @return The set of actors being exercised
	 */
	Set<Actor> system() {
		return systemUnderTest;
	}

	/**
	 * How to evaluate the model's expected behaviour against the system
	 *
	 * @param t Captures system behaviour
	 * @return <code>this</code>
	 */
	public T behaviour( Consumer<Assertion> t ) {
		test = t;
		return self();
	}

	/**
	 * @return The {@link Flow}s to process, in order
	 */
	protected Stream<Flow> flows() {
		// per system properties, find out which flows we want to exercise and save
		// those settings for future runs
		Filter fltr = new Filter( model )
				.load()
				.blockForUpdates()
				.save();

		// find the flows that pass the filter
		Set<Flow> toRun = fltr.flows().collect( Collectors.toCollection( HashSet::new ) );

		// collect dependencies of those flows
		Set<Flow> deps = new HashSet<>();
		for( Flow flow : toRun ) {
			deps.addAll( Flows.dependencies( flow, new ArrayList<>() ) );
		}
		toRun.addAll( deps );

		// find the execution order
		Order order = new Order( toRun.stream(), applicators.values() );
		return order.order();
	}

	/**
	 * Call this to process a flow
	 *
	 * @param flow The {@link Flow} to process
	 */
	protected void process( Flow flow ) {
		if( reporting.writing() ) {
			logCapture.start( flow );
		}

		Collection<Interaction> toExercise = Flows.interactions( flow )
				// exercise interactions that *enter* the system, not intra-system
				.filter( i -> systemUnderTest.contains( i.responder() )
						&& !systemUnderTest.contains( i.requester() ) )
				.collect( toList() );

		if( toExercise.isEmpty() ) {
			reportAndSkip( flow, "No interactions with system " + systemUnderTest );
		}

		List<Consumer<FlowData>> reportUpdates = new ArrayList<>();
		List<String> skipReasons = new ArrayList<>();
		List<AssertionError> comparisonFailures = new ArrayList<>();
		List<RuntimeException> executionFailures = new ArrayList<>();
		List<Assertion> actualMessages = new ArrayList<>();

		if( replay.hasData() ) {
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

		toExercise.forEach( ntr -> {
			int ac = processInteraction( flow, ntr, actualMessages,
					reportUpdates, skipReasons, comparisonFailures, executionFailures );
			assertionCount.addAndGet( ac );
		} );
		// we've processed all of the appropriate interactions

		assertionCount.addAndGet( checkResidue(
				expectedResidue, actualMessages,
				reportUpdates,
				comparisonFailures, executionFailures ) );

		finaliseReport( flow, reportUpdates,
				comparisonFailures, executionFailures, assertionCount.get() );

		// throw any deferred failures
		if( !executionFailures.isEmpty() ) {
			throw executionFailures.get( 0 );
		}
		if( !comparisonFailures.isEmpty() ) {
			throw comparisonFailures.get( 0 );
		}
		if( !skipReasons.isEmpty() ) {
			skip( skipReasons.get( 0 ) );
		}
		if( assertionCount.get() == 0 ) {
			// we're not really skipping anything here (we've already processed the flow),
			// but this will make things more obvious to whatever is driving the test
			skip( "No assertions made" );
		}
	}

	private int processInteraction( Flow flow, Interaction ntr, List<Assertion> actualMessages,
			List<Consumer<FlowData>> reportUpdates, List<String> skipReasons,
			List<AssertionError> comparisonFailures, List<RuntimeException> executionFailures )
			throws AssertionError {
		// provoke the system with input data and capture the outputs
		Assertion assrt = new Assertion( flow, ntr, this );

		try {
			if( replay.hasData() ) {
				warn( reportUpdates, "Replaying data from " + replaySource );
				String sr = replay.populate( assrt );
				if( sr != null ) {
					warn( reportUpdates, sr );
					skipReasons.add( sr );
				}
			}
			else {
				test.accept( assrt );
			}
		}
		// Sonar would rather we just catch Exception here, but we're not trying to
		// *recover* from the failure (it gets rethrown below), we're just trying to
		// make sure it gets recorded to the report
		catch( Throwable e ) {
			// something has gone wrong when we tried to exercise the system. We can't
			// really take this flow any further, so let's just update the report and
			// rethrow the failure
			reportUpdates.add( d -> d.tags.add( Writer.ERROR_TAG ) );
			reportUpdates.add( d -> logCapture.end( flow ).forEach( d.logs::add ) );
			reportUpdates.add( d -> d.logs.add( error(
					"Encountered error: " + LogEvent.stackTrace( e ) ) ) );
			report( w -> w.with( flow, reportUpdates.stream()
					.reduce( d -> {
						// no-op
					}, Consumer::andThen ) ),
					// error condition - we might want to browse the report
					true );
			throw e;
		}

		// parse the data that we extracted from the system and compare it against the
		// model
		int assertionCount = 0;
		List<Assertion> harvested = assrt.collect( new ArrayList<>() );
		for( MessageAssertion ma : MessageAssertion.values() ) {
			for( Assertion assertion : harvested ) {
				assertionCount += processMessage( flow, assertion, ma,
						reportUpdates, comparisonFailures, executionFailures );
				actualMessages.add( assertion );
			}
		}
		return assertionCount;
	}

	private int processMessage( Flow flow, Assertion assertion,
			MessageAssertion ma,
			List<Consumer<FlowData>> reportUpdates,
			List<AssertionError> comparisonFailures,
			List<RuntimeException> parseFailures )
			throws AssertionError {
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
				if( !reporting.writing() && !Options.SUPPRESS_ASSERTION_FAILURE.isTrue() ) {
					// we're not generating a report or suppressing failures, so fail immediately
					throw e;
				}
				// otherwise just store these up - we want to compare all the messages we can
				// (populating the report as a side effect) before failing
				comparisonFailures.add( e );
			}
			catch( RuntimeException e ) {
				if( !reporting.writing() && !Options.SUPPRESS_ASSERTION_FAILURE.isTrue() ) {
					// we're not generating a report, so fail immediately
					throw e;
				}
				// otherwise just store these up - we want to compare all the messages we can
				// (which populates the report) before failing
				parseFailures.add( e );
			}
			return 1;
		}
		return 0;
	}

	private void finaliseReport( Flow flow,
			List<Consumer<FlowData>> reportUpdates,
			List<AssertionError> comparisonFailures,
			List<RuntimeException> executionFailures,
			int assertionCount ) {
		String resultTag = resultTag( assertionCount, comparisonFailures, executionFailures );
		reportUpdates.add( d -> d.tags.add( resultTag ) );
		if( assertionCount == 0 ) {
			warn( reportUpdates, "No assertions made" );
		}
		reportUpdates.add( d -> logCapture.end( flow ).forEach( d.logs::add ) );
		reportUpdates.add( d -> executionFailures.stream()
				.map( e -> error( LogEvent.stackTrace( e ) ) )
				.forEach( d.logs::add ) );
		report( w -> w.with( flow, reportUpdates.stream()
				.reduce( d -> {
					// no-op
				}, Consumer::andThen ) ),
				// error condition
				!comparisonFailures.isEmpty() );
	}

	/**
	 * Looks for reasons to skip processing a {@link Flow}. This will throw a
	 * framework-specific throwable to abort processing if any are found
	 *
	 * @param flow
	 * @see #skip(String)
	 */
	private void checkPreconditions( Flow flow ) {
		if( !Options.SUPPRESS_SYSTEM_CHECK.isTrue() ) {
			// If there are implied system dependencies that the system cannot satisfy...
			flow.implicit()
					.filter( a -> !systemUnderTest.contains( a ) )
					.findFirst()
					.ifPresent( a -> reportAndSkip( flow,
							"Implicitly depends on " + a + ", which is not part of the system under test" ) );
		}

		// If the history suggests we're going to fail...
		// (this could be missing flow dependencies or a failing basis)
		history.skipReason( flow, statefulness, systemUnderTest )
				.ifPresent( r -> reportAndSkip( flow, r ) );
	}

	/**
	 * Applies the {@link Context}s that are appropriate for the system under test
	 *
	 * @param flow
	 * @param executionFailures Failures will be added to this if a report is being
	 *                          generated
	 */
	private void applyContexts( Flow flow, List<RuntimeException> executionFailures ) {
		try {
			// work out the context updates
			Set<Class<? extends Context>> unupdated = new HashSet<>( currentContext.keySet() );
			List<Context> contextUpdates = new ArrayList<>();
			flow.context()
					.filter( ctx -> ctx.domain().stream().anyMatch( systemUnderTest::contains ) )
					.forEach( ctx -> {
						contextUpdates.add( ctx );
						unupdated.remove( ctx.getClass() );
					} );

			// deactivate the orphaned context types - those that existed on the previous
			// flow but not on the current one. We're doing this *before* the normal context
			// changes as there can be dependencies between contexts - the ones on the new
			// flow might not cope with the ones on the old flow that they know nothing
			// about
			unupdated.forEach( this::removeContext );

			// apply the context for the new flow
			contextUpdates.forEach( this::updateContext );
		}
		catch( RuntimeException e ) {
			if( !reporting.writing() ) {
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
		Class<? extends Context> ctxt = ctx.getClass();
		Applicator<C> apl = (Applicator<C>) applicator( ctxt );
		C current = (C) currentContext.get( ctxt );
		apl.transition( current, ctx );
		currentContext.put( ctxt, ctx );
	}

	@SuppressWarnings("unchecked")
	private <C extends Context> void removeContext( Class<C> ctxt ) {
		Applicator<C> apl = applicator( ctxt );
		C current = (C) currentContext.remove( ctxt );
		apl.transition( current, null );
	}

	private <C extends Context> Applicator<C> applicator( Class<C> ctxt ) {
		@SuppressWarnings("unchecked")
		Applicator<C> apl = (Applicator<C>) applicators.get( ctxt );
		if( apl == null ) {
			throw new IllegalStateException( "No applicator for context type " + ctxt );
		}
		return apl;
	}

	private Map<Residue, Message> expectedResidue( Flow flow ) {
		Map<Residue, Message> expected = new HashMap<>();
		flow.residue()
				.filter( r -> checkers.containsKey( r.getClass() ) )
				.forEach( r -> expected.put( r, checker( r ).expected( r ) ) );
		return expected;
	}

	@SuppressWarnings("unchecked")
	private <R extends Residue> Checker<R> checker( R rsd ) {
		return (Checker<R>) checkers.get( rsd.getClass() );
	}

	private int checkResidue(
			Map<Residue, Message> expectedResidue, List<Assertion> actualMessages,
			List<Consumer<FlowData>> reportUpdates,
			List<AssertionError> comparisonFailures, List<RuntimeException> executionFailures ) {
		AtomicInteger assertionCount = new AtomicInteger();

		expectedResidue.forEach( ( residue, expected ) -> {
			byte[] harvested = null;
			try {
				harvested = checker( residue ).actual( residue, actualMessages );
			}
			catch( Exception e ) {
				IllegalStateException ise = new IllegalStateException(
						"Failed to extract actual residue data for " + residue.name(), e );
				if( !reporting.writing() ) {
					throw ise;
				}
				executionFailures.add( ise );
			}
			if( harvested != null ) {
				try {
					Message actual = expected.peer( harvested );
					CheckMessages cm = new CheckMessages(
							actual.assertable(),
							expected.assertable( masks ),
							actual.assertable( masks ) );

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
					compare( String.format( "Residue '%s'", residue.name() ),
							cm.maskedExpect,
							cm.maskedActual );
				}
				catch( AssertionError ae ) {
					if( !reporting.writing() ) {
						throw ae;
					}
					comparisonFailures.add( ae );
				}
				catch( Exception e ) {
					IllegalArgumentException iae = new IllegalArgumentException(
							"Failed to parse actual residue data for " + residue.name(), e );
					if( !reporting.writing() ) {
						throw iae;
					}
					executionFailures.add( iae );
				}
			}
		} );

		return assertionCount.get();
	}

	private static String resultTag(
			int assertionCount,
			List<AssertionError> compareFailures,
			List<RuntimeException> parseFailures ) {
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

	private void reportAndSkip( Flow flow, String reason ) {
		// ... add the skip tag to the flow ...
		report( w -> w.with( flow, d -> {
			d.tags.add( Writer.SKIP_TAG );
			logCapture.end( flow ).forEach( d.logs::add );
			d.logs.add( warn( "Skipping transaction: " + reason ) );
		} ),
				// ... but don't bother browsing the report
				false );
		skip( reason );
	}

	private void warn( List<Consumer<FlowData>> reportUpdates, String msg ) {
		reportUpdates.add( d -> d.logs.add( warn( msg ) ) );
	}

	private LogEvent warn( String msg ) {
		return new LogEvent( Instant.now(),
				"WARN", getClass().getName(), msg );
	}

	private LogEvent error( String msg ) {
		return new LogEvent( Instant.now(),
				"ERROR", getClass().getName(), msg );
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
					expected.assertable( masks ),
					am.assertable( masks ) );
			reportUpdate.accept( messages );
			compare(
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

	/**
	 * @return <code>this</code>
	 */
	@SuppressWarnings("unchecked")
	private T self() {
		return (T) this;
	}

	private static final Supplier<String> RUN_DATETIME = () -> DateTimeFormatter
			.ofPattern( "yyMMdd-HHmmss" )
			.format( now().atZone( systemDefault() ) );

	private void report( Consumer<Writer> data, boolean error ) {
		if( reporting.writing() ) {
			boolean alreadyBrowsing = true;
			if( report == null ) {

				String testTitle = title;

				// work out what the report directory should be called
				String name = Options.REPORT_NAME.value();
				if( name == null ) {
					name = RUN_DATETIME.get();
				}
				if( replay.hasData() ) {
					// reports that have been generated from replaying historic data don't really
					// imply anything about the behaviour of the system under test, so we want them
					// to be really obvious. Hence we're giving them a directory name suffix and an
					// addendum to the test report title
					name += Replay.REPLAYED_SUFFIX;
					testTitle += " (replay)";
					// The dir name suffix also stops us overwriting the data source when
					// the REPORT_NAME property is the same as the REPLAY property
				}

				report = new Writer( model.title(), testTitle,
						Paths.get( Options.ARTIFACT_DIR.value() ).resolve( name ) );
				alreadyBrowsing = false;
			}
			data.accept( report );

			if( !alreadyBrowsing && reporting.shouldOpen( error ) ) {
				report.browse();
			}
		}
	}

	/**
	 * Gets the path of the execution report produced by this flocessor. This will
	 * only be meaningful after the first {@link Flow} has been processed (which
	 * creates the report directory)
	 *
	 * @return the execution report path
	 */
	public Path report() {
		if( report != null ) {
			return report.path();
		}
		return null;
	}

	/**
	 * Implement this to signal to the test framework that the current flow should
	 * be skipped.
	 *
	 * @param reason The reason why we should skip
	 */
	protected abstract void skip( String reason );

	/**
	 * Implement this to compare expected and actual message content
	 *
	 * @param message  The message to accompany an expectation failure
	 * @param expected The expected message content
	 * @param actual   The actual message content
	 */
	protected abstract void compare( String message, String expected, String actual );

}
