package com.mastercard.test.flow.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.validation.check.ChainOverlapCheck;
import com.mastercard.test.flow.validation.check.DependencyChronologyCheck;
import com.mastercard.test.flow.validation.check.DependencyInclusionCheck;
import com.mastercard.test.flow.validation.check.DependencyLoopCheck;
import com.mastercard.test.flow.validation.check.FlowIdentityCheck;
import com.mastercard.test.flow.validation.check.InteractionIdentityCheck;
import com.mastercard.test.flow.validation.check.MessageSharingCheck;
import com.mastercard.test.flow.validation.check.ModelTaggingCheck;
import com.mastercard.test.flow.validation.check.ResultTagCheck;
import com.mastercard.test.flow.validation.check.TraceUniquenessCheck;

/**
 * Superclass for processing {@link Validation}s against {@link Model}s
 *
 * @param <T> self type
 */
public abstract class AbstractValidator<T extends AbstractValidator<T>> {

	/**
	 * The suggested set of checks to subject your {@link Model}s to
	 *
	 * @return default set of validation checks
	 * @see #with(Validation...)
	 */
	public static final Validation[] defaultChecks() {
		return new Validation[] {
				new ChainOverlapCheck(),
				new DependencyChronologyCheck(),
				new DependencyLoopCheck(),
				new DependencyInclusionCheck(),
				new FlowIdentityCheck(),
				new InteractionIdentityCheck(),
				new MessageSharingCheck(),
				new ModelTaggingCheck(),
				new ResultTagCheck(),
				new TraceUniquenessCheck(),
		};
	}

	private final Set<Validation> checks = new TreeSet<>(
			Comparator.comparing( Validation::name ) );
	private Model model;

	private List<Predicate<Violation>> accepted = new ArrayList<>();

	/**
	 * Adds checks to be performed
	 *
	 * @param check The checks to perform
	 * @return <code>this</code>
	 * @see #defaultChecks()
	 */
	public T with( Validation... check ) {
		Collections.addAll( checks, check );
		return self();
	}

	/**
	 * Adds {@link Violation} acceptance criteria
	 *
	 * @param a returns <code>true</code> on violations that we should ignore
	 * @return <code>this</code>
	 */
	public T accepting( Predicate<Violation> a ) {
		accepted.add( a );
		return self();
	}

	/**
	 * Specifies the {@link Model} to validate
	 *
	 * @param m The model to validate
	 * @return <code>this</code>
	 */
	public T checking( Model m ) {
		this.model = m;
		return self();
	}

	/**
	 * Typesafe self-reference
	 *
	 * @return <code>this</code>
	 */
	@SuppressWarnings("unchecked")
	protected T self() {
		return (T) this;
	}

	/**
	 * Produces the validation checks that should be performed
	 *
	 * @return The checks to perform
	 */
	protected Stream<Validation> checks() {
		return checks.stream();
	}

	/**
	 * {@link Model} accessor
	 *
	 * @return The model to validate
	 */
	protected Model model() {
		return model;
	}

	/**
	 * Violation acceptance query
	 *
	 * @param violation A violation
	 * @return true if the violation should be ignored
	 */
	public boolean accepted( Violation violation ) {
		return accepted.stream().anyMatch( p -> p.test( violation ) );
	}
}
