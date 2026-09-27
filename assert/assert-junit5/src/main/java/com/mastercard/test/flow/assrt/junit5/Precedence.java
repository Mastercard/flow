package com.mastercard.test.flow.assrt.junit5;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.assrt.Order;
import com.mastercard.test.flow.util.Flows;
import com.mastercard.test.flow.util.Tags;

/**
 * Direct admission constraints between flows in canonical order.
 */
final class Precedence {

	private final List<Set<Integer>> successors;
	private final int[] predecessors;
	private final List<Integer> roots;

	/**
	 * @param flows Flows in canonical order
	 */
	Precedence( List<Flow> flows ) {
		this( flows, false );
	}

	/**
	 * @param flows     Flows in canonical order
	 * @param replaying Whether every flow must be serialized for replay
	 */
	Precedence( List<Flow> flows, boolean replaying ) {
		Map<Flow, Integer> indices = new IdentityHashMap<>();
		List<Set<Integer>> edges = new ArrayList<>();
		for( int i = 0; i < flows.size(); i++ ) {
			if( indices.put( flows.get( i ), i ) != null ) {
				throw new IllegalArgumentException( "Duplicate selected Flow reference" );
			}
			edges.add( new TreeSet<>() );
		}

		dependencyPrecedence( flows, indices, edges );
		basisPrecedence( flows, indices, edges );
		publicationPrecedence( flows, indices, edges );
		chainPrecedence( flows, edges );
		contextPrecedence( flows, edges );
		if( replaying ) {
			for( int i = 1; i < flows.size(); i++ ) {
				edges.get( i - 1 ).add( i );
			}
		}

		predecessors = new int[flows.size()];
		edges.forEach( targets -> targets.forEach( target -> predecessors[target]++ ) );
		List<Integer> cyclic = cyclic( edges, predecessors );
		if( !cyclic.isEmpty() ) {
			throw new IllegalArgumentException( "Hard flow precedence cycle: "
					+ cyclic.stream().map( i -> flows.get( i ).meta().id() ).sorted().toList() );
		}
		successors = edges.stream().map( Collections::unmodifiableSet ).toList();
		List<Integer> initial = new ArrayList<>();
		for( int i = 0; i < predecessors.length; i++ ) {
			if( predecessors[i] == 0 ) {
				initial.add( i );
			}
		}
		roots = Collections.unmodifiableList( initial );
	}

	private static void dependencyPrecedence( List<Flow> flows, Map<Flow, Integer> indices,
			List<Set<Integer>> edges ) {
		for( int dependent = 0; dependent < flows.size(); dependent++ ) {
			Flow flow = flows.get( dependent );
			int target = dependent;
			try( Stream<
					Flow> sources = flow.dependencies().map( dependency -> dependency.source().flow() ) ) {
				for( Flow source : sources.filter( candidate -> candidate != null && candidate != flow )
						.toList() ) {
					Integer predecessor = indices.get( source );
					if( predecessor == null ) {
						throw new IllegalArgumentException( "Absent Flow prerequisite" );
					}
					edges.get( predecessor ).add( target );
				}
			}
		}
	}

	private static void basisPrecedence( List<Flow> flows, Map<Flow, Integer> indices,
			List<Set<Integer>> edges ) {
		for( int descendant = 0; descendant < flows.size(); descendant++ ) {
			Set<Flow> seen = Collections.newSetFromMap( new IdentityHashMap<>() );
			Flow ancestor = flows.get( descendant ).basis();
			while( ancestor != null ) {
				if( !seen.add( ancestor ) ) {
					throw new IllegalArgumentException( "Cyclic Flow basis" );
				}
				Integer predecessor = indices.get( ancestor );
				if( predecessor != null ) {
					edges.get( predecessor ).add( descendant );
					break;
				}
				ancestor = ancestor.basis();
			}
		}
	}

	private static void publicationPrecedence( List<Flow> flows, Map<Flow, Integer> indices,
			List<Set<Integer>> edges ) {
		Map<Flow, NavigableSet<Integer>> destinations = new IdentityHashMap<>();
		Map<Message, NavigableSet<Integer>> messages = new IdentityHashMap<>();
		for( int i = 0; i < flows.size(); i++ ) {
			Flow flow = flows.get( i );
			Interaction root = flow.root();
			Stream<Interaction> interactions = root == null
					? Stream.empty()
					: Stream.concat( Stream.of( root ), Flows.descendents( root ) );
			int owner = i;
			interactions.forEach( interaction -> {
				addParticipant( messages, interaction.request(), owner );
				addParticipant( messages, interaction.response(), owner );
			} );
			flow.dependencies()
					.filter(
							dependency -> dependency.source().isComplete() && dependency.sink().isComplete() )
					.forEach( dependency -> {
						Integer publisher = indices.get( dependency.source().flow() );
						if( publisher == null ) {
							throw new IllegalArgumentException( "Absent Flow prerequisite" );
						}
						destinations.computeIfAbsent( dependency.sink().flow(), key -> new TreeSet<>() )
								.add( publisher );
						dependency.source().getMessage()
								.ifPresent( message -> addParticipant( messages, message, publisher ) );
						dependency.sink().getMessage()
								.ifPresent( message -> addParticipant( messages, message, publisher ) );
					} );
		}
		orderGroups( destinations.values(), edges );
		orderGroups( messages.values(), edges );
	}

	private static void addParticipant( Map<Message, NavigableSet<Integer>> participants,
			Message message, int owner ) {
		if( message != null ) {
			participants.computeIfAbsent( message, key -> new TreeSet<>() ).add( owner );
		}
	}

	private static void chainPrecedence( List<Flow> flows, List<Set<Integer>> edges ) {
		Map<String, List<Integer>> chains = new HashMap<>();
		for( int i = 0; i < flows.size(); i++ ) {
			Optional<String> chain = Tags.suffix( flows.get( i ).meta().tags(), Order.CHAIN_TAG_PREFIX );
			if( chain.isPresent() ) {
				chains.computeIfAbsent( chain.get(), key -> new ArrayList<>() ).add( i );
			}
		}
		for( List<Integer> members : chains.values() ) {
			int first = members.get( 0 );
			int last = members.get( members.size() - 1 );
			for( int i = 0; i < first; i++ ) {
				edges.get( i ).add( first );
			}
			for( int i = 1; i < members.size(); i++ ) {
				edges.get( members.get( i - 1 ) ).add( members.get( i ) );
			}
			for( int i = last + 1; i < flows.size(); i++ ) {
				edges.get( last ).add( i );
			}
		}
	}

	private static void contextPrecedence( List<Flow> flows, List<Set<Integer>> edges ) {
		for( int barrier = 0; barrier < flows.size(); barrier++ ) {
			if( flows.get( barrier ).context().findAny().isPresent() ) {
				for( int earlier = 0; earlier < barrier; earlier++ ) {
					edges.get( earlier ).add( barrier );
				}
				for( int later = barrier + 1; later < flows.size(); later++ ) {
					edges.get( barrier ).add( later );
				}
			}
		}
	}

	private static void orderGroups( Iterable<? extends NavigableSet<Integer>> groups,
			List<Set<Integer>> edges ) {
		for( NavigableSet<Integer> group : groups ) {
			Integer previous = null;
			for( int member : group ) {
				if( previous != null ) {
					edges.get( previous ).add( member );
				}
				previous = member;
			}
		}
	}

	private static List<Integer> cyclic( List<Set<Integer>> edges, int[] predecessors ) {
		int[] remaining = predecessors.clone();
		Deque<Integer> ready = new ArrayDeque<>();
		for( int i = 0; i < remaining.length; i++ ) {
			if( remaining[i] == 0 ) {
				ready.add( i );
			}
		}
		while( !ready.isEmpty() ) {
			for( int successor : edges.get( ready.removeFirst() ) ) {
				if( --remaining[successor] == 0 ) {
					ready.add( successor );
				}
			}
		}
		List<Integer> stuck = new ArrayList<>();
		for( int i = 0; i < remaining.length; i++ ) {
			if( remaining[i] != 0 ) {
				stuck.add( i );
			}
		}
		return stuck;
	}

	/**
	 * @return Canonical indices with no unfinished predecessor
	 */
	List<Integer> roots() {
		return roots;
	}

	/**
	 * @param index A canonical flow index
	 * @return Indices that directly wait for that flow
	 */
	Set<Integer> successors( int index ) {
		return successors.get( index );
	}

	/**
	 * @return Fresh mutable readiness for one run
	 */
	Readiness readiness() {
		return new Readiness();
	}

	/** Tracks unfinished direct predecessors for one run. */
	final class Readiness {
		private final int[] remaining = predecessors.clone();

		/**
		 * @param index A finished canonical flow index
		 * @return Successors newly made ready, in canonical order
		 */
		List<Integer> finished( int index ) {
			List<Integer> ready = new ArrayList<>();
			for( int successor : successors.get( index ) ) {
				if( --remaining[successor] == 0 ) {
					ready.add( successor );
				}
			}
			return ready;
		}
	}
}
