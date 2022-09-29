/**
 * Copyright (c) 2021 Mastercard. All rights reserved.
 */

package com.mastercard.test.flow.validation.graph;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToIntBiFunction;

/**
 * A function that computes the distance between two item, caching intermediate
 * results for performance
 *
 * @param <S> item type
 */
public class CachingDiffDistance<S> implements ToIntBiFunction<S, S> {

	/**
	 * How to turn an item into a string to be diffed
	 */
	private final Function<S, String> stringify;

	/**
	 * How to compare two strings and compute the distance between them
	 */
	private final BiFunction<String, String, Integer> diff;

	/**
	 * Cache of stringified items
	 */
	private final Map<S, Reference<String>> stringCache = new HashMap<>();

	/**
	 * Cache of inter-item distances
	 */
	private final Map<S, Map<S, Integer>> diffCache = new HashMap<>();

	/**
	 * @param stringify How to turn items into strings that can be diffed
	 * @param diff      How to compare two strings and calculate the diff distance
	 *                  between them
	 */
	public CachingDiffDistance( Function<S, String> stringify,
			BiFunction<String, String, Integer> diff ) {
		this.stringify = stringify;
		this.diff = diff;
	}

	@Override
	public int applyAsInt( S a, S b ) {
		if( !diffCache.containsKey( a ) || !diffCache.get( a ).containsKey( b ) ) {
			String as = stringify( a );
			String bs = stringify( b );
			int distance = diff.apply( as, bs );
			diffCache.computeIfAbsent( a, s -> new HashMap<>() ).put( b, distance );
		}

		return diffCache.get( a ).get( b );
	}

	/**
	 * @param item An item
	 * @return A string version of the item
	 */
	public String stringify( S item ) {
		String s;
		Reference<String> cached = stringCache.get( item );
		if( cached == null || cached.get() == null ) {
			s = stringify.apply( item );
			stringCache.put( item, new SoftReference<>( s ) );
		}
		else {
			s = cached.get();
		}
		return s;
	}

}
