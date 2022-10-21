package com.mastercard.test.flow.util;

import static com.mastercard.test.flow.util.Transmission.Type.REQUEST;
import static com.mastercard.test.flow.util.Transmission.Type.RESPONSE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Metadata;

/**
 * Utility methods for working with {@link Flow}s
 */
public class Flows {

	private Flows() {
		// no instances
	}

	/**
	 * Sorts {@link Flow}s into ascending order of {@link Metadata#id()}
	 */
	public static final Comparator<Flow> ID_ORDER = Comparator.comparing( f -> f.meta().id() );

	/**
	 * Finds the basis-ancestors of a {@link Flow}
	 *
	 * @param flow A {@link Flow}
	 * @return A stream of the supplied {@link Flow}'s ancestors, in order of
	 *         proximity, i.e.: the basis, the basis' basis, etc
	 */
	public static Stream<Flow> ancestors( Flow flow ) {
		Set<Flow> visited = new HashSet<>();
		Iterable<Flow> iterable = () -> new Iterator<Flow>() {
			Flow current = flow;

			@Override
			public boolean hasNext() {
				return current.basis() != null && !visited.contains( current.basis() );
			}

			@Override
			public Flow next() {
				current = current.basis();
				visited.add( current );
				return current;
			}
		};

		return StreamSupport.stream( iterable.spliterator(), false );
	}

	/**
	 * Extracts the {@link Interaction}s from a {@link Flow}
	 *
	 * @param flow A {@link Flow}
	 * @return A stream of all the {@link Interaction}s in the flow, in
	 *         chronological order of their request
	 */
	public static Stream<Interaction> interactions( Flow flow ) {
		return Stream.concat( Stream.of( flow.root() ), descendents( flow.root() ) );
	}

	/**
	 * Looks for an {@link Interaction} in a {@link Flow}
	 *
	 * @param flow        The {@link Flow} to search
	 * @param interaction How to recognise the {@link Interaction} of interest
	 * @return The chronologically first {@link Interaction} in the {@link Flow}
	 *         that satisfies the supplied condition
	 * @see InteractionPredicate
	 */
	public static Optional<Interaction> find( Flow flow, Predicate<Interaction> interaction ) {
		return interactions( flow ).filter( interaction ).findFirst();
	}

	/**
	 * Extracts a single {@link Interaction} from a {@link Flow}
	 *
	 * @param flow        The {@link Flow} to search
	 * @param interaction How to recognise the {@link Interaction} of interest
	 * @return The chronologically first {@link Interaction} in the {@link Flow}
	 *         that satisfies the supplied condition
	 * @throws IllegalArgumentException if there is no such {@link Interaction}
	 * @see InteractionPredicate
	 */
	public static Interaction get( Flow flow, Predicate<Interaction> interaction ) {
		return find( flow, interaction ).orElseThrow( () -> new IllegalArgumentException(
				"No interaction matching '" + interaction + "' in " + flow.meta().id() ) );
	}

	/**
	 * Collects the consequences of an interaction
	 *
	 * @param ntr An interaction
	 * @return A stream of the interactions that are caused by the supplied
	 *         instance, in chronological order of their request
	 */
	public static Stream<Interaction> descendents( Interaction ntr ) {
		return gatherInteractions( ntr, new ArrayList<>() ).stream();
	}

	private static List<Interaction> gatherInteractions( Interaction ntr, List<Interaction> list ) {
		ntr.children().forEach( child -> {
			list.add( child );
			gatherInteractions( child, list );
		} );
		return list;
	}

	/**
	 * Extracts the message {@link Transmission}s from a flow
	 *
	 * @param flow A {@link Flow}
	 * @return A list of all the {@link Message} {@link Transmission}s in the
	 *         {@link Flow} in chronological order
	 */
	public static List<Transmission> transmissions( Flow flow ) {
		return transmissions( flow.root(), new ArrayList<>(), 0 );
	}

	/**
	 * Extracts the transmissions from an {@link Interaction}
	 *
	 * @param ntr   The {@link Interaction} to extract from
	 * @param list  The list in which to collect the {@link Transmission}s
	 * @param depth The starting depth of the supplied {@link Transmission}
	 * @return The populated list
	 */
	private static List<Transmission> transmissions( Interaction ntr, List<Transmission> list,
			int depth ) {
		list.add( new Transmission( ntr, REQUEST, depth ) );
		ntr.children().forEach( c -> transmissions( c, list, depth + 1 ) );
		list.add( new Transmission( ntr, RESPONSE, depth ) );
		return list;
	}

	/**
	 * Determines if a {@link Flow} intersects with a system
	 *
	 * @param flow   A {@link Flow}
	 * @param system The set of {@link Actor}s in a system
	 * @return <code>true</code> if the {@link Flow}'s {@link Interaction}s hit any
	 *         of the {@link Actor}s in the system
	 */
	public static boolean intersects( Flow flow, Set<Actor> system ) {
		return interactions( flow )
				.anyMatch( ntr -> system.contains( ntr.requester() )
						|| system.contains( ntr.responder() ) );
	}

	/**
	 * Determines if a {@link Flow} intersects with a system
	 *
	 * @param flow   A {@link Flow}
	 * @param system The set of {@link Actor}s in a system
	 * @return <code>true</code> if the {@link Flow}'s {@link Interaction}s hit any
	 *         of the {@link Actor}s in the system
	 */
	public static boolean intersects( Flow flow, Actor... system ) {
		return intersects( flow, Stream.of( system ).collect( Collectors.toSet() ) );
	}

	/**
	 * Produces a human-readable representation of a {@link Flow}'s
	 * {@link Interaction} structure
	 *
	 * @param flow A {@link Flow}
	 * @return A human-readable dump of the flow structure, or <code>null</code> if
	 *         <code>null</code> is supplied
	 */
	public static String structure( Flow flow ) {
		if( flow != null ) {
			return String.format( "%s %s\n%s\n%s",
					flow.meta().description(), flow.meta().tags(),
					flow.meta().trace(),
					transmissions( flow ).stream()
							.map( Transmission::toString )
							.collect( Collectors.joining( "\n" ) ) );
		}
		return null;
	}

	/**
	 * Collects all of the dependencies (direct and transitive) of a {@link Flow}.
	 * Note that the same flow may be added to the collection more than once.
	 *
	 * @param flow A {@link Flow}
	 * @param deps The list to add to
	 * @return The supplied collection, populated with the {@link Flow}s that the
	 *         supplied instance depends on in transitive depth order
	 */
	public static Collection<Flow> dependencies( Flow flow, Collection<Flow> deps ) {
		flow.dependencies().forEach( d -> d
				.source()
				.getFlow()
				.ifPresent( f -> {
					deps.add( f );
					dependencies( f, deps );
				} ) );
		return deps;
	}
}
