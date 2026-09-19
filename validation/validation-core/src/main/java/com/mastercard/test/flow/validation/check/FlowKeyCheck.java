package com.mastercard.test.flow.validation.check;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.validation.Check;
import com.mastercard.test.flow.validation.Validation;
import com.mastercard.test.flow.validation.Violation;

/**
 * Convenience superclass for validation checks that require a per-flow key to
 * be unique across the model. Flows are grouped by key in a single pass, so the
 * number of {@link Check}s is bounded by the number of flows rather than the
 * number of flow pairs.
 */
public abstract class FlowKeyCheck implements Validation {

	private final String name;
	private final String explanation;
	private final String violation;

	/**
	 * @param name        to be returned from {@link #name()}
	 * @param explanation to be returned from {@link #explanation()}
	 * @param violation   {@link Violation#details()} when flows share a key
	 */
	protected FlowKeyCheck( String name, String explanation, String violation ) {
		this.name = name;
		this.explanation = explanation;
		this.violation = violation;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public String explanation() {
		return explanation;
	}

	@Override
	public Stream<Check> checks( Model model ) {
		Map<String, List<Flow>> byKey = new LinkedHashMap<>();
		model.flows().forEach( flow -> byKey
				.computeIfAbsent( key( flow ), k -> new ArrayList<>() )
				.add( flow ) );

		return byKey.values().stream()
				.map( flows -> new Check( this,
						flows.get( 0 ).meta().id(),
						() -> {
							if( flows.size() < 2 ) {
								return null;
							}
							Violation v = new Violation( this, violation );
							flows.forEach( v::offender );
							return v;
						} ) );
	}

	/**
	 * @param flow A flow
	 * @return The value that must be unique to this flow
	 */
	protected abstract String key( Flow flow );
}
