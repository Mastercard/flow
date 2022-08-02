package com.mastercard.test.flow.builder;

import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Utility for set operations
 */
public class Sets {
	private Sets() {
		// no instances
	}

	/**
	 * Set clearance
	 *
	 * @param <T> member type
	 * @return an operation to clear a set
	 */
	public static <T> Consumer<Set<T>> clear() {
		return Set::clear;
	}

	/**
	 * Value addition
	 *
	 * @param <T>     Member type
	 * @param members members to add
	 * @return An operation that adds members
	 */
	@SafeVarargs
	public static <T> Consumer<Set<T>> add( final T... members ) {
		return s -> Collections.addAll( s, members );
	}

	/**
	 * Value removal
	 *
	 * @param <T>     Member type
	 * @param members members to remove
	 * @return An operation that removes members
	 */
	@SafeVarargs
	public static <T> Consumer<Set<T>> remove( final T... members ) {
		return s -> {
			for( T t : members ) {
				s.remove( t );
			}
		};
	}

	/**
	 * Set definition
	 *
	 * @param <T>     Member type
	 * @param members Set membership
	 * @return An operation that defines set membership
	 */
	@SafeVarargs
	public static <T> Consumer<Set<T>> set( T... members ) {
		return s -> {
			s.clear();
			Collections.addAll( s, members );
		};
	}
}
