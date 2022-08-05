package com.mastercard.test.flow.msg;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toCollection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Utility methods for working with acyclic graphs of {@link List}s and
 * {@link Map}s
 */
public class Forest {

	private Forest() {
		// no instances
	}

	/**
	 * Traverses through the data structure and does something at a path's
	 * destination
	 *
	 * @param data       The root data object
	 * @param path       The path to the desired element - a sequence of map keys
	 *                   with optional list index suffices
	 * @param vivify     <code>true</code> to update the data structure to satisfy
	 *                   the path.
	 * @param mapAction  What to do if the path ends in a map member
	 * @param listAction What to do if the path ends in a list member
	 */
	public static void traverse( Map<String, Object> data, Deque<String> path,
			boolean vivify,
			BiConsumer<Map<String, Object>, String> mapAction,
			ObjIntConsumer<List<Object>> listAction ) {
		// avoid altering the path that we're supplied
		Deque<String> workingPath = new ArrayDeque<>( path );

		PathElement child = new PathElement( workingPath.removeFirst() );
		if( workingPath.isEmpty() ) {
			leaf( data, child, vivify, mapAction, listAction );
		}
		else {
			recurse( data, child, workingPath, vivify, mapAction, listAction );
		}
	}

	/**
	 * Called when the end of a path has been reached
	 *
	 * @param data       The data object
	 * @param child      The child element to apply the action to
	 * @param vivify     <code>true</code> to update the data structure to satisfy
	 *                   the path.
	 * @param mapAction  What to do if the named child is a map member
	 * @param listAction What to do if the named child is a list member
	 */
	private static void leaf( Map<String, Object> data, PathElement child,
			boolean vivify,
			BiConsumer<Map<String, Object>, String> mapAction, ObjIntConsumer<List<Object>> listAction ) {
		if( child.indices.isEmpty() ) {
			mapAction.accept( data, child.name );
		}
		else {
			int finalIndex = child.indices.removeLast();
			traverseIndices( data, child, vivify ).ifPresent( l -> {
				if( vivify ) {
					while( l.size() <= finalIndex ) {
						l.add( null );
					}
				}
				if( l.size() > finalIndex ) {
					listAction.accept( l, finalIndex );
				}
			} );
		}
	}

	/**
	 * Called when there are further path elements to navigate
	 *
	 * @param data       The data object
	 * @param child      The child element to navigate to
	 * @param path       The path from there on
	 * @param vivify     <code>true</code> to update the data structure to satisfy
	 *                   the path.
	 * @param mapAction  What to do if the named child is a map member
	 * @param listAction What to do if the named child is a list member
	 */
	private static void recurse( Map<String, Object> data, PathElement child, Deque<String> path,
			boolean vivify,
			BiConsumer<Map<String, Object>, String> mapAction, ObjIntConsumer<List<Object>> listAction ) {
		if( child.indices.isEmpty() ) {
			forceMap( data, child.name, vivify )
					.ifPresent( m -> traverse( m, path, vivify, mapAction, listAction ) );
		}
		else {
			int finalIndex = child.indices.removeLast();
			traverseIndices( data, child, vivify )
					.ifPresent( l -> forceMap( l, finalIndex, vivify )
							.ifPresent( m -> traverse( m, path, vivify, mapAction, listAction ) ) );
		}
	}

	/**
	 * Called when a path element ends in list indices
	 *
	 * @param data   The data object
	 * @param child  The child element to navigate to
	 * @param vivify <code>true</code> to update the data structure to satisfy the
	 *               path.
	 * @return The named list element
	 */
	private static Optional<List<Object>> traverseIndices( Map<String, Object> data,
			PathElement child, boolean vivify ) {
		Optional<List<Object>> list = forceList( data, child.name, vivify );
		while( list.isPresent() && !child.indices.isEmpty() ) {
			list = forceList( list.get(), child.indices.removeFirst(), vivify );
		}
		return list;
	}

	private static class PathElement {
		public final String name;
		public final Deque<Integer> indices;
		/**
		 * Any amount of anything except [, optionally followed by a non-zero amount of
		 * anything surrounded by square brackets
		 */
		private static final Pattern PATTERN = Pattern.compile( "^([^\\[]*?)(?:\\[(.+)\\])?$" );

		public PathElement( String pe ) {
			Matcher em = PATTERN.matcher( pe );
			// the regex will match on any input, so no need to check the return value
			em.matches();
			name = em.group( 1 );
			indices = Stream.of( ofNullable( em.group( 2 ) ).orElse( "" )
					.split( "]\\[" ) )
					.filter( s -> !s.isEmpty() )
					.map( Integer::parseInt )
					.collect( toCollection( ArrayDeque::new ) );

			for( Integer idx : indices ) {
				if( idx < 0 ) {
					throw new IllegalArgumentException(
							String.format( "Field path element '%s' has negative index", pe ) );
				}
			}
		}
	}

	/**
	 * Forces a map member to be a list, if it isn't already
	 *
	 * @param into   the map
	 * @param name   The map key at which we want a list
	 * @param vivify Whether to create the list if it doesn't already exist
	 * @return the list
	 */
	@SuppressWarnings("unchecked")
	private static Optional<List<Object>> forceList( Map<String, Object> into, String name,
			boolean vivify ) {
		return Optional.ofNullable( force(
				List.class, ArrayList::new,
				into, name, vivify ) );
	}

	/**
	 * Forces a map member to be a map, if it isn't already
	 *
	 * @param into   the map
	 * @param name   The map key at which we want a map
	 * @param vivify Whether to create the map if it doesn't already exist
	 * @return the map
	 */
	@SuppressWarnings("unchecked")
	private static Optional<Map<String, Object>> forceMap( Map<String, Object> into, String name,
			boolean vivify ) {
		return Optional.ofNullable( force(
				Map.class, TreeMap::new,
				into, name, vivify ) );
	}

	/**
	 * @param <T>    The type of the child node
	 * @param type   The type of the child node
	 * @param constr How to construct an object oif the desired typoe, if it doesn't
	 *               already exist
	 * @param into   The map in which we desire the child
	 * @param name   The name of the child in the map
	 * @param vivify Whether to create the child if it doesn't already exist of is
	 *               the wrong type
	 * @return The child node
	 */
	@SuppressWarnings("unchecked")
	private static <T> T force( Class<T> type, Supplier<T> constr,
			Map<String, Object> into, String name, boolean vivify ) {
		T t;
		Object o = into.get( name );
		if( o != null && type.isInstance( o ) ) {
			t = (T) o;
		}
		else if( vivify ) {
			t = constr.get();
			into.put( name, t );
		}
		else {
			t = null;
		}
		return t;
	}

	/**
	 * Forces a list member to be a list, if it isn't already
	 *
	 * @param into   the list
	 * @param index  The list index at which we want a list
	 * @param vivify Whether to create the list if it doesn't already exist
	 * @return the list
	 */
	@SuppressWarnings("unchecked")
	private static Optional<List<Object>> forceList( List<Object> into, int index, boolean vivify ) {
		return Optional.ofNullable( force(
				List.class, ArrayList::new,
				into, index, vivify ) );
	}

	/**
	 * Forces a list member to be a map, if it isn't already
	 *
	 * @param into   the list
	 * @param index  The list index at which we want a map
	 * @param vivify Whether to create the map if it doesn't already exist
	 * @return the map
	 */
	@SuppressWarnings("unchecked")
	private static Optional<Map<String, Object>> forceMap( List<Object> into, int index,
			boolean vivify ) {
		return Optional.ofNullable( force(
				Map.class, TreeMap::new,
				into, index, vivify ) );
	}

	@SuppressWarnings("unchecked")
	private static <T> T force( Class<T> type, Supplier<T> constr,
			List<Object> into, int index, boolean vivify ) {
		T t;
		if( vivify ) {
			while( into.size() <= index ) {
				into.add( null );
			}
		}
		if( index >= into.size() ) {
			return null;
		}
		Object o = into.get( index );
		if( o != null && type.isInstance( o ) ) {
			t = (T) o;
		}
		else if( vivify ) {
			t = constr.get();
			into.set( index, t );
		}
		else {
			t = null;
		}
		return t;
	}

	/**
	 * Visits all non-null leaf values in the structure
	 *
	 * @param separator Path separator
	 * @param o         The current object
	 * @param visitor   Action to take on each leaf. It will be supplied with the
	 *                  leaf paths and values
	 */
	public static void leaves( String separator, Object o,
			BiConsumer<String, Object> visitor ) {
		leaves( "", separator, o, visitor );
	}

	/**
	 * Visits all non-null leaf values in the structure
	 *
	 * @param path      The path of the current object
	 * @param separator The characters to use to separate path elements
	 * @param o         The current object
	 * @param visitor   The action to take on every non-null leaf node
	 */
	private static void leaves( String path, String separator, Object o,
			BiConsumer<String, Object> visitor ) {
		if( o instanceof Map ) {
			Map<?, ?> map = (Map<?, ?>) o;
			String prefix = path.isEmpty() ? "" : separator;
			for( Map.Entry<?, ?> e : map.entrySet() ) {
				leaves( path + prefix + e.getKey(), separator, e.getValue(), visitor );
			}
		}
		else if( o instanceof List ) {
			List<?> list = (List<?>) o;
			int idx = 0;
			for( Object l : list ) {
				if( l != null ) {
					leaves( path + "[" + idx + "]", separator, l, visitor );
				}
				idx++;
			}
		}
		else {
			visitor.accept( path, o );
		}
	}
}
