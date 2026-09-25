
package com.mastercard.test.flow.assrt;

import static java.util.stream.Collectors.joining;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.Residue;
import com.mastercard.test.flow.Unpredictable;
import com.mastercard.test.flow.assrt.filter.Filter;
import com.mastercard.test.flow.assrt.filter.FilterConfiguration;
import com.mastercard.test.flow.assrt.filter.FilterOptions;

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

	private FlowConfiguration config;
	private final FlowProcessor processor;

	/**
	 * Tracks the outcome of processing {@link Flow}s to inform further processing
	 */
	protected final History history;

	/**
	 * Whether the system's subsequent behaviour will be changed by the processing
	 * of a {@link Flow}
	 */
	protected State statefulness;

	/**
	 * @param title The title of this test
	 * @param model The model to process
	 */
	protected AbstractFlocessor( String title, Model model ) {
		this( title, model, new History() );
	}

	/**
	 * Shares the prepared run's publication monitor without copying processing.
	 * 
	 * @param title   The title of this test
	 * @param model   The model to process
	 * @param history The run-owned processing history
	 */
	protected AbstractFlocessor( String title, Model model, History history ) {
		this.history = history;
		config = new FlowConfiguration( title, model );
		processor = new FlowProcessor( this, config, history );
	}

	/**
	 * Controls report generation
	 *
	 * @param r    Whether or not to generate a report, and whether or not to
	 *             display it at the conclusion of testing
	 * @param path The directory path underneath the flow artifact directory in
	 *             which to write the report
	 * @return <code>this</code>
	 */
	public T reporting( Reporting r, String... path ) {
		beforeConfiguration();
		config.reporting = r;
		config.reportPath = path;
		config.replaySource = Replay.source( path );
		config.replay = new Replay( config.replaySource );
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
		beforeConfiguration();
		config.masks = sources.clone();
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
		beforeConfiguration();
		statefulness = state;
		config.systemUnderTest.clear();
		Collections.addAll( config.systemUnderTest, actors );
		return self();
	}

	/**
	 * Defines the set of system components that are capable of independent action
	 * in the system under test
	 *
	 * @param actors independent {@link Actor}s
	 * @return <code>this</code>
	 */
	public T autonomous( Actor... actors ) {
		beforeConfiguration();
		config.autonomous.clear();
		Collections.addAll( config.autonomous, actors );
		if( !config.systemUnderTest.containsAll( config.autonomous ) ) {
			throw new IllegalArgumentException( String.format(
					"Autonomous actors '%s' must be a subset of system '%s'",
					config.autonomous.stream().map( Actor::name ).sorted().collect( joining( "," ) ),
					config.systemUnderTest.stream().map( Actor::name ).sorted().collect( joining( "," ) ) ) );
		}
		return self();
	}

	/**
	 * Adds {@link Context} {@link Applicator}s to the test
	 *
	 * @param applctrs How to apply {@link Context} data to the system under test
	 * @return <code>this</code>
	 */
	public T applicators( Applicator<?>... applctrs ) {
		beforeConfiguration();
		for( Applicator<?> applicator : applctrs ) {
			config.applicators.put( applicator.contextType(), applicator );
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
		beforeConfiguration();
		for( Checker<?> checker : chckrs ) {
			config.checkers.put( checker.residueType(), checker );
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
		beforeConfiguration();
		config.logCapture = lc;
		config.correlatedCapture = null;
		return self();
	}

	/**
	 * Configures correlation-attributed log capture, which remains accurate when
	 * {@link Flow}s execute concurrently. Replaces any {@link LogCapture}.
	 *
	 * @param source A source of log events carrying correlation identifiers
	 * @return <code>this</code>
	 * @see Assertion#correlation()
	 */
	public T logs( CorrelatedCapture source ) {
		beforeConfiguration();
		config.correlatedCapture = source;
		config.logCapture = LogCapture.NO_OP;
		return self();
	}

	/**
	 * Configures how the correlation identifier for a {@link Flow} execution is
	 * chosen. Use this when the flow's messages already carry a unique identifier
	 * that the system under test logs. Without it the runner generates one.
	 * <p>
	 * The identifier must be unique to one execution within the run: if two
	 * executions present the same identifier, whether from two flows or from the
	 * same flow processed twice, events carrying it belong to neither and the
	 * completion diagnostic counts them as claimed by more than one flow.
	 *
	 * @param extractor Returns the identifier for a flow, or <code>null</code> to
	 *                  fall back to a generated identifier for that flow
	 * @return <code>this</code>
	 */
	public T correlation( Function<Flow, String> extractor ) {
		beforeConfiguration();
		config.correlation = extractor;
		return self();
	}

	/**
	 * Configures progress listening behaviour
	 *
	 * @param prg An object that will be informed as processing proceeds
	 * @return <code>this</code>
	 */
	public T listening( Listener prg ) {
		beforeConfiguration();
		config.progress = prg;
		return self();
	}

	/**
	 * Defines subset of the system that is under test
	 *
	 * @return The set of actors being exercised
	 */
	Set<Actor> system() {
		return processor.system();
	}

	/**
	 * Defines the default behaviour of the {@link Filter} controlling which
	 * {@link Flow}s are constructed for the test run. This configuration is applied
	 * prior to the behaviour controlled by {@link FilterOptions}
	 *
	 * @param cfg How to configure the {@link Filter}
	 * @return <code>this</code>
	 */
	public T filtering( Consumer<FilterConfiguration> cfg ) {
		beforeConfiguration();
		config.filterCfg = cfg;
		return self();
	}

	/**
	 * Limits which flows will be exercised by the test. Note that:
	 * <ul>
	 * <li>This filter operates independently of, and after, that controlled by
	 * {@link FilterOptions} and {@link #filtering(Consumer)}.</li>
	 * <li>This filter will <i>not</i> block dependency {@link Flow}s being included
	 * in the execution order.</li>
	 * </ul>
	 * <p>
	 * Think carefully before using this method - standard assertion behaviour will
	 * produce a visible skip result in testing harness output and the execution
	 * report for {@link Flow}s that are not appropriate for the system under test.
	 * {@link Flow}s that are rejected by the filter defined by this method will
	 * only appear in the <code>rejectionLog</code> argument, which will probably be
	 * much <i>less</i> visible. This is the exact reason why you'd use this method,
	 * but bear in mind that it will complicate any "why isn't my flow being
	 * exercised?" debugging efforts that you find yourself in.
	 *
	 * @param filter       Returns true for flows that should be exercised in this
	 *                     test
	 * @param rejectionLog Will be supplied with human-readable messages detailing
	 *                     the rejection of {@link Flow}s by the filter
	 * @return <code>this</code>
	 * @see AssertionOptions#SUPPRESS_FILTER
	 */
	public T exercising( Predicate<Flow> filter, Consumer<String> rejectionLog ) {
		beforeConfiguration();
		config.flowFilter = filter;
		config.filterRejectionLog = rejectionLog;
		return self();
	}

	/**
	 * How to evaluate the model's expected behaviour against the system. Under
	 * concurrent execution this callback is invoked from several threads at once,
	 * so it must be thread-safe.
	 *
	 * @param t Captures system behaviour
	 * @return <code>this</code>
	 */
	public T behaviour( Consumer<Assertion> t ) {
		beforeConfiguration();
		config.test = t;
		return self();
	}

	/**
	 * Configures the motivation customizer behavior. The `MotivationCustomizer`
	 * interface allows you to customize the motivation text in the report. This
	 * method sets the customizer that will be used to modify the motivation text
	 * based on the original motivation and the test results.
	 *
	 * @param customizer The custom `MotivationCustomizer` implementation.
	 * @return <code>this</code> for method chaining.
	 */
	public T motivation( MotivationCustomizer customizer ) {
		beforeConfiguration();
		config.motivationCustomizer = customizer;
		return self();
	}

	/**
	 * @return The {@link Flow}s to process, in order
	 */
	protected Stream<Flow> flows() {
		return processor.flows();
	}

	/**
	 * Adapter-specific mutation guard. The default imposes no restrictions.
	 */
	protected void beforeConfiguration() {
		// callers may keep configuring after enumeration
	}

	/**
	 * Freezes configuration and selects the flows to process.
	 *
	 * @return Selected flows in canonical serial order
	 */
	protected final Stream<Flow> prepareFlows() {
		return prepareFlows( flow -> {
			// no per-flow preparation
		} );
	}

	/**
	 * Freezes configuration and selects the flows to process, calling back for each
	 * one after dependency expansion and before ordering. The callback must not
	 * exercise the system under test.
	 *
	 * @param prepare Called once for each selected or required flow
	 * @return Selected flows in canonical serial order
	 */
	protected final Stream<Flow> prepareFlows( Consumer<Flow> prepare ) {
		// the adapter's config stays mutable but detached: later configuration does
		// not reach the frozen snapshot that the processor now works from
		processor.freezeConfiguration();
		return processor.flows( prepare );
	}

	/**
	 * Rejects configuration that cannot serve concurrently executing flows.
	 * Interval-attributed capture and replay remain serial-only; correlated capture
	 * is supported.
	 */
	protected final void requireConcurrentConfiguration() {
		FlowConfiguration current = processor.configuration();
		if( current.logCapture != LogCapture.NO_OP ) {
			throw new IllegalStateException(
					"Interval-based LogCapture cannot attribute events to concurrent flows; "
							+ "configure logs( CorrelatedCapture ) instead" );
		}
		if( current.replay.hasData() ) {
			throw new IllegalStateException( "Replay is not supported for concurrent flows" );
		}
	}

	/** Publishes the report once, on completion, rather than after every flow */
	protected final void finalOnlyReporting() {
		config.finalOnlyReporting = true;
		processor.configuration().finalOnlyReporting = true;
	}

	/**
	 * Declares that context-applying flows are serialised by the adapter's ordering
	 * while flows without contexts may run alongside them. Context-free flows then
	 * leave applied state untouched instead of removing it.
	 */
	protected final void concurrentContexts() {
		processor.concurrentContexts();
	}

	/**
	 * Closes the report and log source. Call this when the adapter has finished
	 * processing flows, not when it has merely enumerated them.
	 */
	protected final void completeProcessing() {
		processor.complete();
	}

	/** Opens final-only reporting once preparation has succeeded */
	protected final void initializeReporting() {
		processor.initializeReport();
	}

	/**
	 * Call this to process a flow
	 *
	 * @param flow The {@link Flow} to process
	 */
	protected void process( Flow flow ) {
		processor.process( flow );
	}

	/**
	 * Processes a flow and records its outcome in the {@link #history}, so that
	 * later flows can be skipped when they depend on this one
	 *
	 * @param flow   The {@link Flow} to process
	 * @param isSkip Whether a thrown exception is the framework's skip signal
	 */
	protected final void processRecording( Flow flow, Predicate<RuntimeException> isSkip ) {
		try {
			process( flow );
			history.recordResult( flow, History.Result.SUCCESS );
		}
		catch( AssertionError e ) {
			history.recordResult( flow, History.Result.UNEXPECTED );
			throw e;
		}
		catch( RuntimeException e ) {
			history.recordResult( flow, isSkip.test( e ) ? History.Result.SKIP : History.Result.ERROR );
			throw e;
		}
		catch( Exception e ) {
			// checked exceptions can escape a callback via sneaky throws, e.g. from
			// Jupiter's assertTimeout; precise rethrow keeps the original
			history.recordResult( flow, History.Result.ERROR );
			throw e;
		}
	}

	/**
	 * @return <code>this</code>
	 */
	@SuppressWarnings("unchecked")
	private T self() {
		return (T) this;
	}

	/**
	 * Gets the path of the execution report produced by this flocessor. This will
	 * only be meaningful after the first {@link Flow} has been processed (which
	 * creates the report directory)
	 *
	 * @return the execution report path
	 */
	public Path report() {
		return processor.report();
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
