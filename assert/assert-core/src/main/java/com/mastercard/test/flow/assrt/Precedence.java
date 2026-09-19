package com.mastercard.test.flow.assrt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.util.Flows;

/**
 * Direct precedence between selected flows, built once from what the model
 * already declares and consumed as a ready-set.
 * <p>
 * Edge sources, in this order: dependency bindings between flows; selected
 * basis ancestors and descendants, in canonical order; flows that publish into
 * or read the same destination message, in canonical order; chain contraction
 * (edges into or out of a chain member are lifted to the chain's first/last
 * member and consecutive members are linked); and all flows that apply a
 * {@link com.mastercard.test.flow.Context}, in canonical order.
 */
public final class Precedence {

	private final List<Set<Integer>> successors;
	private final int[] predecessors;
	private final List<Integer> roots;

	/**
	 * Builds direct precedence from the canonical order produced by {@link Order}.
	 * This is the one place a hard prerequisite cycle is detected: {@link Order}
	 * breaks such cycles to produce a sequence, and prepared adapters reject the
	 * result here.
	 *
	 * @param flows Selected flows in canonical order
	 * @throws IllegalArgumentException On duplicate references, absent
	 *                                  prerequisites, cyclic bases and hard cycles,
	 *                                  including those created by chain contraction
	 */
	public Precedence( List<Flow> flows ) {
		List<Set<Integer>> edges = new ArrayList<>();
		Map<Flow, Integer> indices = new IdentityHashMap<>();
		for( int i = 0; i < flows.size(); i++ ) {
			if( indices.put( flows.get( i ), i ) != null ) {
				throw new IllegalArgumentException( "Duplicate selected Flow reference" );
			}
			edges.add( new HashSet<>() );
		}
		dependencyPrecedence( flows, indices, edges );
		basisPrecedence( flows, indices, edges );
		publicationPrecedence( flows, indices, edges );
		chainPrecedence( flows, edges );
		contextPrecedence( flows, edges );
		predecessors = new int[flows.size()];
		for( Set<Integer> targets : edges )
			for( int target : targets )
				predecessors[target]++;
		List<Integer> cyclic = cyclic( edges, predecessors );
		if( !cyclic.isEmpty() )
			throw new IllegalArgumentException( "Hard prerequisite cycle (including contracted chains): "
					+ cyclic.stream().map( i -> flows.get( i ).meta().id() ).sorted().toList() );
		successors = edges.stream().map( Collections::unmodifiableSet ).toList();
		List<Integer> initial = new ArrayList<>();
		for( int i = 0; i < predecessors.length; i++ )
			if( predecessors[i] == 0 )
				initial.add( i );
		roots = Collections.unmodifiableList( initial );
	}

	/**
	 * A prerequisite ordered after its dependent is a cycle that {@link Order} had
	 * to break; the cycle check reports it.
	 */
	private static void dependencyPrecedence( List<Flow> flows, Map<Flow, Integer> indices,
			List<Set<Integer>> edges ) {
		for( int i = 0; i < flows.size(); i++ ) {
			Flow flow = flows.get( i );
			try( Stream<Flow> sources = flow.dependencies().map( d -> d.source().flow() ) ) {
				for( Flow source : sources.filter( f -> f != null && f != flow ).toList() ) {
					Integer before = indices.get( source );
					if( before == null ) {
						throw new IllegalArgumentException( "Absent Flow prerequisite" );
					}
					edges.get( before ).add( i );
				}
			}
		}
	}

	/**
	 * @return The number of ordered flows
	 */
	public int size() {
		return predecessors.length;
	}

	/**
	 * @return Indices with no predecessor, ascending
	 */
	public List<Integer> roots() {
		return roots;
	}

	/**
	 * @param index Flow index
	 * @return Indices that directly wait for that flow
	 */
	public Set<Integer> successors( int index ) {
		return successors.get( index );
	}

	/**
	 * @return A fresh readiness tracker over this precedence
	 */
	public Readiness readiness() {
		return new Readiness();
	}

	/**
	 * Kahn's algorithm over remaining-predecessor counts: a flow is ready once
	 * every direct predecessor has finished.
	 */
	public final class Readiness {
		private final int[] remaining = predecessors.clone();

		private Readiness() {
		}

		/**
		 * @param index A finished flow
		 * @return Successors that became ready by that completion, ascending
		 */
		public List<Integer> finished( int index ) {
			List<Integer> ready = new ArrayList<>();
			for( int successor : successors.get( index ) )
				if( --remaining[successor] == 0 )
					ready.add( successor );
			ready.sort( Integer::compareTo );
			return ready;
		}
	}

	/**
	 * @param edges        Successor sets
	 * @param predecessors Predecessor counts
	 * @return Indices that can never become ready, empty when the graph is acyclic
	 */
	private static List<Integer> cyclic( List<Set<Integer>> edges, int[] predecessors ) {
		int[] remaining = predecessors.clone();
		Deque<Integer> ready = new ArrayDeque<>();
		for( int i = 0; i < remaining.length; i++ )
			if( remaining[i] == 0 )
				ready.add( i );
		while( !ready.isEmpty() )
			for( int successor : edges.get( ready.removeFirst() ) )
				if( --remaining[successor] == 0 )
					ready.add( successor );
		List<Integer> stuck = new ArrayList<>();
		for( int i = 0; i < remaining.length; i++ )
			if( remaining[i] != 0 )
				stuck.add( i );
		return stuck;
	}

	private static void chainPrecedence( List<Flow> flows, List<Set<Integer>> edges ) {
		int[] first = new int[flows.size()];
		int[] last = new int[flows.size()];
		int[] next = new int[flows.size()];
		Arrays.fill( next, -1 );
		Map<Object, List<Integer>> chains = new LinkedHashMap<>();
		for( int i = 0; i < flows.size(); i++ ) {
			chains.computeIfAbsent( Order.chainKey( flows.get( i ) ), key -> new ArrayList<>() ).add( i );
		}
		for( List<Integer> members : chains.values() ) {
			for( int i = 0; i < members.size(); i++ ) {
				int member = members.get( i );
				first[member] = members.get( 0 );
				last[member] = members.get( members.size() - 1 );
				next[member] = i + 1 < members.size() ? members.get( i + 1 ) : -1;
			}
		}
		List<Set<Integer>> original = edges.stream().map( Set::copyOf ).toList();
		for( int i = 0; i < edges.size(); i++ ) {
			for( int successor : original.get( i ) ) {
				if( first[i] != first[successor] )
					edges.get( last[i] ).add( first[successor] );
			}
			if( next[i] >= 0 )
				edges.get( i ).add( next[i] );
		}
	}

	private static void contextPrecedence( List<Flow> flows, List<Set<Integer>> edges ) {
		NavigableSet<Integer> applying = new TreeSet<>();
		for( int i = 0; i < flows.size(); i++ )
			if( flows.get( i ).context().findAny().isPresent() )
				applying.add( i );
		orderGroups( List.of( applying ), edges );
	}

	private static void publicationPrecedence( List<Flow> flows, Map<Flow, Integer> indices,
			List<Set<Integer>> edges ) {
		Map<Flow, NavigableSet<Integer>> destinations = new IdentityHashMap<>();
		Map<Message, NavigableSet<Integer>> participants = new IdentityHashMap<>();
		for( Flow flow : flows ) {
			int owner = indices.get( flow );
			Interaction root = flow.root();
			Stream<Interaction> interactions = root == null ? Stream.empty()
					: Stream.concat( Stream.of( root ), Flows.descendents( root ) );
			interactions.forEach( interaction -> {
				for( Message message : new Message[] { interaction.request(), interaction.response() } ) {
					if( message != null )
						participants.computeIfAbsent( message, m -> new TreeSet<>() ).add( owner );
				}
			} );
			flow.dependencies().filter( d -> d.source().isComplete() && d.sink().isComplete() )
					.forEach( dependency -> {
						int publisher = indices.get( dependency.source().flow() );
						destinations.computeIfAbsent( dependency.sink().flow(), f -> new TreeSet<>() )
								.add( publisher );
						dependency.source().getMessage().ifPresent( message -> participants
								.computeIfAbsent( message, m -> new TreeSet<>() ).add( publisher ) );
						dependency.sink().getMessage().ifPresent( message -> participants
								.computeIfAbsent( message, m -> new TreeSet<>() ).add( publisher ) );
					} );
		}
		// Whole-flow destination groups do not protect aliases read by another flow,
		// so message users are ordered as well as publishers
		orderGroups( destinations.values(), edges );
		orderGroups( participants.values(), edges );
	}

	/**
	 * Links the members of each group consecutively, in canonical order
	 */
	private static void orderGroups( Iterable<? extends NavigableSet<Integer>> groups,
			List<Set<Integer>> edges ) {
		for( NavigableSet<Integer> ranks : groups ) {
			Integer previous = null;
			for( int rank : ranks ) {
				if( previous != null )
					edges.get( previous ).add( rank );
				previous = rank;
			}
		}
	}

	/**
	 * Flows related by basis ancestry keep their canonical order: each flow's path
	 * of selected ancestors is linked in rank order, whichever end the basis is at.
	 */
	private static void basisPrecedence( List<Flow> flows, Map<Flow, Integer> indices,
			List<Set<Integer>> edges ) {
		// Cache the nearest selected ancestor across shared unselected paths. Selected
		// identities are stopping points, but each one's own basis is still read once.
		Map<Flow, Integer> nearest = new IdentityHashMap<>( indices );
		int[] parent = new int[flows.size()];
		for( int i = 0; i < flows.size(); i++ )
			parent[i] = nearestSelectedAncestor( flows.get( i ), nearest );
		for( int i = 0; i < flows.size(); i++ ) {
			NavigableSet<Integer> path = new TreeSet<>();
			for( int rank = i; rank >= 0; rank = parent[rank] )
				if( !path.add( rank ) )
					throw new IllegalArgumentException( "Cyclic Flow basis" );
			orderGroups( List.of( path ), edges );
		}
	}

	/**
	 * @param flow    A selected flow
	 * @param nearest Selected ranks by flow, extended with the unselected flows
	 *                visited on the way to them
	 * @return The rank of the nearest selected basis ancestor, or -1 if there is
	 *         none
	 */
	private static int nearestSelectedAncestor( Flow flow, Map<Flow, Integer> nearest ) {
		Flow ancestor = flow.basis();
		List<Flow> path = new ArrayList<>();
		while( ancestor != null && !nearest.containsKey( ancestor ) ) {
			nearest.put( ancestor, -2 ); // Unresolved on this path: a repeat is a cycle.
			path.add( ancestor );
			ancestor = ancestor.basis();
		}
		int parent = ancestor == null ? -1 : nearest.get( ancestor );
		if( parent == -2 )
			throw new IllegalArgumentException( "Cyclic Flow basis" );
		for( Flow absent : path )
			nearest.put( absent, parent );
		return parent;
	}
}
