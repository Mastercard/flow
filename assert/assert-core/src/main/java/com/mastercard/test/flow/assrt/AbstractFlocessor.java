
package com.mastercard.test.flow.assrt;

import static java.util.stream.Collectors.joining;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;
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

	private final FlowConfiguration config;
	private final FlowProcessor processor;

	/**
	 * Tracks the outcome of processing {@link Flow}s to inform further processing
	 */
	protected final History history = new History();

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
		config = new FlowConfiguration( title, model );
		processor = new FlowProcessor( config, history ) {
			@Override
			State statefulness() {
				// Keep the existing protected field live for legacy subclasses.
				return statefulness;
			}

			@Override
			String logSource() {
				return AbstractFlocessor.this.getClass().getName();
			}

			@Override
			void skip( String reason ) {
				AbstractFlocessor.this.skip( reason );
			}

			@Override
			void compare( String message, String expected, String actual ) {
				AbstractFlocessor.this.compare( message, expected, actual );
			}
		};
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
		return config.systemUnderTest;
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
	 * How to evaluate the model's expected behaviour against the system
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
	 * Adapter-specific mutation guard. The default deliberately imposes no new
	 * restrictions on legacy callers.
	 */
	protected void beforeConfiguration() {
		// Legacy callers remain mutable even after enumeration.
	}

	/**
	 * Snapshots registrations in the existing processor before selecting flows.
	 * Prepared adapters must enforce their one-preparation invocation contract.
	 *
	 * @return Selected flows in canonical serial order
	 */
	protected final Stream<Flow> prepareFlows() {
		processor.freezeConfiguration();
		return processor.flows();
	}

	/**
	 * Disposes existing processor-owned reporting only at an adapter's proven
	 * completion boundary. Never call merely because descriptions were enumerated.
	 */
	protected final void completeProcessing() {
		processor.complete();
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
