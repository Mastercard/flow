package com.mastercard.test.flow.assrt;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.Residue;
import com.mastercard.test.flow.Unpredictable;
import com.mastercard.test.flow.assrt.filter.FilterConfiguration;

/**
 * Internal configuration shared by selection and invocation processing. Legacy
 * fluent callers own and mutate this object; it is deliberately not frozen or
 * copied on enumeration. Registered callbacks and domain objects retain their
 * identities. This is not a claim that those objects are safe for concurrent
 * use. Runtime state (dependencies, History, applied contexts and the report)
 * belongs to the processor, not to configuration or individual invocations.
 */
final class FlowConfiguration {

	/** The single model being exercised. */
	final Model model;
	/** The title of this test. */
	final String title;
	/** Whether to write or display an execution report. */
	Reporting reporting = Reporting.NEVER;
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
	/** Source of captured system logs. */
	LogCapture logCapture = LogCapture.NO_OP;
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

	private FlowConfiguration( FlowConfiguration source ) {
		title = source.title;
		model = source.model;
		replaySource = source.replaySource;
		replay = source.replay;
	}

	/**
	 * Copies configuration ownership, not domain/callback identities. Used only by
	 * explicitly prepared adapters; legacy enumeration remains mutable.
	 *
	 * @return A detached configuration snapshot
	 */
	FlowConfiguration snapshot() {
		FlowConfiguration copy = new FlowConfiguration( this );
		copy.reporting = reporting;
		copy.reportPath = reportPath.clone();
		copy.systemUnderTest.addAll( systemUnderTest );
		copy.autonomous.addAll( autonomous );
		copy.applicators.putAll( applicators );
		copy.checkers.putAll( checkers );
		copy.replaySource = replaySource;
		copy.replay = replay;
		copy.test = test;
		copy.masks = masks.clone();
		copy.logCapture = logCapture;
		copy.progress = progress;
		copy.filterCfg = filterCfg;
		copy.flowFilter = flowFilter;
		copy.filterRejectionLog = filterRejectionLog;
		copy.motivationCustomizer = motivationCustomizer;
		return copy;
	}
}
