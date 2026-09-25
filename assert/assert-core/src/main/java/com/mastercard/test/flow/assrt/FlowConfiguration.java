package com.mastercard.test.flow.assrt;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.Residue;
import com.mastercard.test.flow.Unpredictable;
import com.mastercard.test.flow.assrt.filter.FilterConfiguration;

/**
 * Configuration shared by selection and invocation processing. The fluent
 * adapters own and mutate this object. Prepared adapters freeze it by taking a
 * {@link #snapshot()}; registered callbacks and domain objects keep their
 * identities in the copy. Runtime state (dependencies, History, applied
 * contexts and the report) belongs to the processor.
 */
final class FlowConfiguration {

	/** The single model being exercised. */
	final Model model;
	/** The title of this test. */
	final String title;
	/** Whether to write or display an execution report. */
	Reporting reporting = Reporting.NEVER;
	/** Whether this prepared owner publishes its report only at completion. */
	boolean finalOnlyReporting;
	/** The report location under the artifact directory. */
	String[] reportPath = {};
	/** Actors exercised by the test. */
	final Set<Actor> systemUnderTest = new HashSet<>();
	/** Actors capable of independent action. */
	final Set<Actor> autonomous = new HashSet<>();
	/** Applicators for the actual system's context domains. */
	final Map<Class<? extends Context>, Applicator<?>> applicators = new HashMap<>();
	/** Checkers for residual impacts on the system. */
	final Map<Class<? extends Residue>, Checker<?>> checkers = new HashMap<>();
	/** The report supplying replay data, if any. */
	String replaySource;
	/** Data source used in place of live behaviour during replay. */
	Replay replay;
	/** Synchronous test action. */
	Consumer<Assertion> test = a -> {
		throw new IllegalStateException( "No test behaviour specified" );
	};
	/** Ordered sources of unpredictable data. */
	Unpredictable[] masks = {};
	/** Interval-attributed source of captured system logs. */
	LogCapture logCapture = LogCapture.NO_OP;
	/** Correlation-attributed source of captured system logs, if configured. */
	CorrelatedCapture correlatedCapture;
	/** Extracts the correlation identifier from a flow; null means generated. */
	Function<Flow, String> correlation;
	/** Synchronous processing listener. */
	Listener progress = new Listener() {
		// default to no-op behaviour
	};
	/** Configuration applied before the user-controlled construction filter. */
	Consumer<FilterConfiguration> filterCfg = f -> {
		// no-op
	};
	/** Programmatic filter of constructed flows. */
	Predicate<Flow> flowFilter = f -> true;
	/** Receives programmatic filter rejection messages. */
	Consumer<String> filterRejectionLog = l -> {
		// no-op
	};
	/** Synchronous report motivation customizer. */
	MotivationCustomizer motivationCustomizer = ( motivation, assrt ) -> motivation;

	/**
	 * @param title The test title
	 * @param model The single model to exercise
	 */
	FlowConfiguration( String title, Model model ) {
		this.title = title;
		this.model = model;
		replaySource = Replay.source();
		replay = new Replay( replaySource );
	}

	/**
	 * Copies configuration ownership, not domain/callback identities.
	 *
	 * @param source The configuration to copy
	 */
	private FlowConfiguration( FlowConfiguration source ) {
		title = source.title;
		model = source.model;
		reporting = source.reporting;
		finalOnlyReporting = source.finalOnlyReporting;
		reportPath = source.reportPath.clone();
		systemUnderTest.addAll( source.systemUnderTest );
		autonomous.addAll( source.autonomous );
		applicators.putAll( source.applicators );
		checkers.putAll( source.checkers );
		replaySource = source.replaySource;
		replay = source.replay;
		test = source.test;
		masks = source.masks.clone();
		logCapture = source.logCapture;
		correlatedCapture = source.correlatedCapture;
		correlation = source.correlation;
		progress = source.progress;
		filterCfg = source.filterCfg;
		flowFilter = source.flowFilter;
		filterRejectionLog = source.filterRejectionLog;
		motivationCustomizer = source.motivationCustomizer;
	}

	/**
	 * Used only by explicitly prepared adapters; legacy enumeration remains
	 * mutable.
	 *
	 * @return A detached configuration snapshot
	 */
	FlowConfiguration snapshot() {
		return new FlowConfiguration( this );
	}
}
