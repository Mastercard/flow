package com.mastercard.test.flow.assrt;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Flow;

/**
 * Sorts {@link Flow}s into an execution order that tries to minimise expensive
 * {@link Context} switches
 */
public class ContextOrder implements Comparator<Flow> {

	private final Map<Flow, Integer> order = new HashMap<>();

	/**
	 * @param flows       The {@link Flow}s to order
	 * @param applicators The relevant {@link Context} {@link Applicator}s
	 */
	public ContextOrder( Collection<Flow> flows,
			Collection<Applicator<?>> applicators ) {
		Applicator<?>[] appExpense = applicators.toArray( new Applicator<?>[applicators.size()] );
		// note increasing order of expense
		Arrays.sort( appExpense, ( a, b ) -> a.transitionCost() - b.transitionCost() );

		Flow[] flowOrder = flows.toArray( new Flow[flows.size()] );

		// start with alphabetical order
		Arrays.sort( flowOrder, Comparator.comparing( f -> f.meta().id() ) );

		// Sort the flows for each context. Arrays.sort() promises to be stable, so the
		// earlier sorts will not be completely undone by the later sorts
		for( Applicator<?> app : appExpense ) {
			sort( flowOrder, app );
		}

		// save the order indices for use in compare()
		for( int i = 0; i < flowOrder.length; i++ ) {
			order.put( flowOrder[i], i );
		}
	}

	@SuppressWarnings("unchecked")
	private static <T extends Context> void sort( Flow[] flows, Applicator<T> applicator ) {
		// maps from flow to context (or null if the flow has no context that is
		// relevant for the applicator)
		Map<Flow, T> ctxs = new HashMap<>();
		for( Flow flow : flows ) {
			ctxs.put( flow, (T) flow.context()
					.filter( c -> c.getClass().equals( applicator.contextType() ) )
					.findFirst()
					.orElse( null ) );
		}
		Comparator<T> appOrder = applicator.order();
		Arrays.sort( flows, Comparator.comparing( ctxs::get, ( a, b ) -> {
			if( a != null && b != null ) {
				return appOrder.compare( a, b );
			}
			if( a != null ) {
				return 1;
			}
			if( b != null ) {
				return -1;
			}
			return 0;
		} ) );
	}

	@Override
	public int compare( Flow a, Flow b ) {
		return order.getOrDefault( a, 0 ).compareTo( order.getOrDefault( b, 0 ) );
	}
}
