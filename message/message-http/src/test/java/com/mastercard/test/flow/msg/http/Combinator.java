package com.mastercard.test.flow.msg.http;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Generates combinations of list members
 *
 * @param <T> The combination member type
 * @param <C> The collection type to return
 */
class Combinator<T, C extends Collection<T>> implements Iterator<C> {

	private final boolean[] included;
	private boolean fullSetReturned = false;
	private final List<T> elements;
	private final Supplier<C> collection;

	/**
	 * @param collection How to build a collection of the desired return type
	 * @param members    The members to combine
	 */
	@SafeVarargs
	public Combinator( Supplier<C> collection, T... members ) {
		elements = Arrays.asList( members );
		included = new boolean[elements.size()];
		this.collection = collection;
	}

	@Override
	public boolean hasNext() {
		return !fullSetReturned;
	}

	@Override
	public C next() {

		C result = collection.get();
		for( int i = 0; i < included.length; i++ ) {
			if( included[i] ) {
				result.add( elements.get( i ) );
			}
		}

		fullSetReturned = allSet();

		for( int i = 0; i < included.length; i++ ) {
			if( !included[i] ) {
				included[i] = true;
				break;
			}
			included[i] = false;
		}
		return result;
	}

	/**
	 * @return A stream of the combinations
	 */
	public Stream<C> stream() {
		return StreamSupport.stream(
				Spliterators.spliteratorUnknownSize( this, Spliterator.ORDERED ),
				false );
	}

	private boolean allSet() {
		for( int i = 0; i < included.length; i++ ) {
			if( !included[i] ) {
				return false;
			}
		}
		return true;
	}
}
