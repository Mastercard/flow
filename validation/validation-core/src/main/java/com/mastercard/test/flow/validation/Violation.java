package com.mastercard.test.flow.validation;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.util.Flows;

/**
 * The result of an unsuccessful {@link Check}
 */
public class Violation {

	private final Validation validation;
	private final String details;
	private final Map<Flow, Set<Interaction>> offenders = new TreeMap<>( Flows.ID_ORDER );
	private final String expected;
	private final String actual;

	/**
	 * Constructs a new {@link Violation} that signals a general failure
	 *
	 * @param validation The {@link Validation} that has been violated
	 * @param details    A human-readable description of the violation
	 */
	public Violation( Validation validation, String details ) {
		this( validation, details, null, null );
	}

	/**
	 * Constructs a new {@link Violation} that signals an expected-equality mismatch
	 *
	 * @param validation The {@link Validation} that has been violated
	 * @param details    A human-readable description of the violation
	 * @param expected   Expected half of an equality test
	 * @param actual     Actual half of an equality test
	 */
	public Violation( Validation validation, String details, String expected, String actual ) {
		this.validation = validation;
		this.details = details;
		this.expected = expected;
		this.actual = actual;
	}

	/**
	 * Adds the model data that provoked the violation
	 *
	 * @param flow The {@link Flow} that has violated this {@link Validation}
	 * @param ntrs The {@link Interaction}s that have violated this
	 *             {@link Validation}
	 * @return <code>this</code>
	 */
	public Violation offender( Flow flow, Interaction... ntrs ) {
		Collections.addAll( offenders.computeIfAbsent( flow,
				f -> new TreeSet<>( comparing( i -> String.format(
						"%s->%s %s",
						i.requester().name(), i.responder().name(), i.tags() ) ) ) ),
				ntrs );
		return this;
	}

	/**
	 * Parent accessor
	 *
	 * @return The {@link Validation} that has been violated
	 */
	public Validation validation() {
		return validation;
	}

	/**
	 * Produces a human-readable description of the violation
	 *
	 * @return A description of the violation
	 */
	public String details() {
		return details;
	}

	/**
	 * Comparison accessor
	 *
	 * @return Expected half of a comparison test, or <code>null</code> to just
	 *         signal a general test failure
	 */
	public String expected() {
		return expected;
	}

	/**
	 * Comparison accessor
	 *
	 * @return Actual half of a comparison test, or <code>null</code>to just signal
	 *         a general test failure
	 */
	public String actual() {
		return actual;
	}

	/**
	 * Gets the offending model data
	 *
	 * @return The bits of the {@link Model} that caused the violation
	 */
	public Map<Flow, Set<Interaction>> offenders() {
		return offenders;
	}

	/**
	 * Produces a human-readable dump of the model data that provoked the violation
	 *
	 * @return A descriptive string of the offending data
	 */
	public String offenderString() {
		return offenders().entrySet().stream()
				.map( e -> String.format( "%s\n%s%s",
						e.getKey().meta().id(), e.getKey().meta().trace(),
						e.getValue().stream()
								.map( ntr -> String.format( "\n  %s->%s %s",
										ntr.requester().name(), ntr.responder().name(), ntr.tags() ) )
								.collect( joining() ) ) )
				.collect( joining( "\n" ) );
	}
}
