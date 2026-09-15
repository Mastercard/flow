package com.mastercard.test.flow.assrt.resource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.Order;
import com.mastercard.test.flow.util.Tags;

/**
 * Frozen selected-chain membership and whole-interval resource requirements.
 */
public final class ChainPlan {
	private final int[] first;
	private final int[] last;
	private final int[] next;
	private final ResourceRequirements[] requirements;

	/**
	 * Groups actual selected members only, retaining their canonical member order.
	 * Unchained flows have distinct units even if their IDs equal a chain name.
	 *
	 * @param flows    Selected/dependency-expanded flows in canonical order
	 * @param resolved Requirements resolved before ordering, now in the same order
	 */
	public ChainPlan( List<Flow> flows, List<ResourceRequirements> resolved ) {
		this( flows, resolved, Map.of() );
	}

	/**
	 * @param flows    Selected flows in canonical order
	 * @param resolved Stored resource requirements
	 * @param isolated Whole-chain IDs and their named outside-overlap audits
	 */
	ChainPlan( List<Flow> flows, List<ResourceRequirements> resolved,
			Map<String, Set<String>> isolated ) {
		first = new int[flows.size()];
		last = new int[flows.size()];
		next = new int[flows.size()];
		requirements = new ResourceRequirements[flows.size()];
		Arrays.fill( next, -1 );
		Map<Object, List<Integer>> groups = new LinkedHashMap<>();
		for( int i = 0; i < flows.size(); i++ ) {
			Object chain = Tags.suffix( flows.get( i ).meta().tags(), Order.CHAIN_TAG_PREFIX )
					.<Object>map( name -> name ).orElseGet( Object::new );
			groups.computeIfAbsent( chain, key -> new ArrayList<>() ).add( i );
		}
		groups.forEach( ( key, members ) -> {
			Set<String> audits = isolated.getOrDefault( key, Set.of() );
			ResourceRequirements whole = key instanceof String
					? ResourceRequirements.union( members.stream().map( resolved::get ).toList(),
							audits.isEmpty(), audits )
					: resolved.get( members.get( 0 ) );
			for( int i = 0; i < members.size(); i++ ) {
				int member = members.get( i );
				first[member] = members.get( 0 );
				last[member] = members.get( members.size() - 1 );
				next[member] = i + 1 < members.size() ? members.get( i + 1 ) : -1;
				requirements[member] = whole;
			}
		} );
	}

	/**
	 * @param index Selected member index
	 * @return First selected member of its unit
	 */
	public int first( int index ) {
		return first[index];
	}

	/**
	 * @param index Selected member index
	 * @return Last selected member of its unit
	 */
	public int last( int index ) {
		return last[index];
	}

	/**
	 * @param index Selected member index
	 * @return Next member, or -1 at the unit end
	 */
	public int next( int index ) {
		return next[index];
	}

	/**
	 * @param index Selected member index
	 * @return Its unit's complete reservation
	 */
	public ResourceRequirements requirements( int index ) {
		return requirements[index];
	}
}
