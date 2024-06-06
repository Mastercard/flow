package com.mastercard.test.flow.builder;

import java.util.function.Consumer;
import java.util.function.Predicate;

import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.builder.mutable.MutableDependency;
import com.mastercard.test.flow.builder.mutable.MutableFlow;

/**
 * Convenience class for defining a {@link Flow} based on an existing instance
 */
@SkipTrace
public class Deriver extends Builder<Deriver> {

	private Deriver( Flow basis ) {
		super( new MutableFlow( basis ) );
	}

	/**
	 * Constructs a derived {@link Flow}
	 *
	 * @param basis The {@link Flow} on which to build
	 * @param steps How to update the new {@link Flow}
	 * @return The resulting {@link Flow}
	 */
	@SafeVarargs
	public static Flow build( Flow basis, Consumer<? super Deriver>... steps ) {
		Deriver d = new Deriver( basis );

		// I can't think of any situation where you'd want to inherit a chain tag, so
		// let's default to removing them
		Chain.unlink().accept( d );

		for( Consumer<? super Deriver> step : steps ) {
			step.accept( d );
		}
		return d.build();
	}

	/**
	 * <p>
	 * Imports dependencies from the basis {@link Flow}. The {@link Dependency}
	 * endpoint {@link Flow}s will be updated:
	 * </p>
	 * <ul>
	 * <li>{@link Dependency#source()} updated per the arguments to this method</li>
	 * <li>{@link Dependency#sink()} updated to the flow under construction</li>
	 * </ul>
	 * <p>
	 * All other aspects of the imported {@link Dependency} will remain the same.
	 * </p>
	 *
	 * @param oldDep The {@link Flow} upon which the basis {@link Flow} depended
	 * @param newDep The {@link Flow} upon which this {@link Flow} will depend in
	 *               the same way
	 * @return <code>this</code>
	 */
	public Deriver inheritDependencies( Flow oldDep, Flow newDep ) {
		return inheritDependencies( d -> d.source().flow() == oldDep, newDep );
	}

	/**
	 * <p>
	 * Imports all dependencies from the basis {@link Flow}. The {@link Dependency}
	 * endpoint {@link Flow}s will be updated:
	 * </p>
	 * <ul>
	 * <li>{@link Dependency#source()} updated per the arguments to this method</li>
	 * <li>{@link Dependency#sink()} updated to the flow under construction</li>
	 * </ul>
	 * <p>
	 * All other aspects of the imported {@link Dependency} will remain the same.
	 * </p>
	 *
	 * @param newDep The {@link Flow} upon which this {@link Flow} will depend in
	 *               the same way as the basis {@link Flow} did
	 * @return <code>this</code>
	 */
	public Deriver inheritDependencies( Flow newDep ) {
		return inheritDependencies( d -> true, newDep );
	}

	private Deriver inheritDependencies( Predicate<Dependency> target, Flow newSource ) {
		flow.basis().dependencies()
				.filter( target )
				.map( MutableDependency::new )
				.map( d -> d
						.source( s -> s.flow( newSource ) )
						.sink( s -> s.flow( SELF ) ) )
				.forEach( flow::dependency );
		return this;
	}

	private Flow build() {
		return flow.build();
	}

}
