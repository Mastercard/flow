
package com.mastercard.test.flow.model;

import static java.util.stream.Collectors.toList;

import java.lang.reflect.Constructor;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.util.TaggedGroup;

/**
 * <p>
 * A {@link Model} implementation that lazily builds constituent {@link Model}s
 * as {@link Flow}s are requested. This class will take care of satisfying the
 * dependencies between {@link EagerModel} types.
 * </p>
 * <p>
 * Note that lazy construction of models based on tags implies that we have to
 * know the tags that are on the flows in the submodels <i>before we build those
 * models and flows</i>. Thus:
 * </p>
 * <ul>
 * <li>Subclasses of {@link EagerModel} that are supplied to
 * {@link #with(Class...)} are expected to have a
 * <code>public static final TaggedGroup MODEL_TAGS</code> field.</li>
 * <li>That field is expected to accurately reflect the flow tags in that
 * model.</li>
 * </ul>
 */
public class LazyModel extends TitledModel {

	private static final Comparator<Class<?>> cc = Comparator.comparing( Class::getName );
	private final Map<Class<? extends EagerModel>, TaggedGroup> types = new TreeMap<>( cc );
	private final Map<Class<? extends EagerModel>, Model> instances = new TreeMap<>( cc );
	private TaggedGroup tags = new TaggedGroup();
	private Optional<Listener> listener = Optional.empty();

	/**
	 */
	public LazyModel() {
	}

	/**
	 * @param title A human-readable title for this group of {@link Flow}s
	 */
	public LazyModel( String title ) {
		super( title );
	}

	/**
	 * Registers constituent models. Note that the supplied classes must have
	 * <code>public static TaggedGroup TAGS</code> field that holds the intersection
	 * and union of tag values of the {@link Flow}s that it can supply
	 *
	 * @param t The model types
	 * @return <code>this</code>
	 */
	@SafeVarargs
	public final LazyModel with( Class<? extends EagerModel>... t ) {
		for( Class<? extends EagerModel> type : t ) {
			validateDependencies( type );
			types.put( type, EagerModel.typeTags( type ) );
		}

		Iterator<TaggedGroup> ti = types.values()
				.stream()
				.filter( Objects::nonNull )
				.collect( toList() )
				.iterator();
		if( ti.hasNext() ) {
			tags = new TaggedGroup( ti.next() );
			while( ti.hasNext() ) {
				tags.combine( ti.next() );
			}
		}

		return this;
	}

	/**
	 * Checks that this model has the required dependencies to instantiate a type
	 *
	 * @param type The type to test
	 * @throws IllegalArgumentException If the supplied type requires a model
	 *                                  parameter that we cannot supply
	 */
	@SuppressWarnings("unchecked")
	private void validateDependencies( Class<? extends Model> type ) throws IllegalArgumentException {
		if( type.getConstructors().length != 1 ) {
			throw new IllegalArgumentException( type + " must have exactly 1 public constructor" );
		}
		Constructor<?> constructor = type.getConstructors()[0];
		Set<Class<? extends Model>> dependencies = Stream
				.of( constructor.getParameterTypes() )
				.map( p -> {
					if( !Model.class.isAssignableFrom( p ) ) {
						throw new IllegalArgumentException( "Non-model parameter to " + constructor );
					}
					return (Class<? extends Model>) p;
				} )
				.collect( Collectors.toSet() );
		dependencies.removeAll( types.keySet() );
		if( !dependencies.isEmpty() ) {
			throw new IllegalArgumentException(
					"Unsatisfied dependencies " + dependencies + " for " + constructor );
		}
	}

	@Override
	public TaggedGroup tags() {
		return tags;
	}

	@Override
	public Stream<Flow> flows( Set<String> include, Set<String> exclude ) {
		return types.entrySet().stream()
				.filter( e -> e.getValue() != null )
				.filter( e -> e.getValue().matches( include, exclude ) )
				.flatMap( e -> instantiate( e.getKey() )
						.flows( include, exclude ) );
	}

	@SuppressWarnings("unchecked")
	private <T extends EagerModel> Model instantiate( Class<T> type ) {
		if( !instances.containsKey( type ) ) {
			listener.ifPresent( l -> l.start( type ) );
			Constructor<?> constructor = type.getConstructors()[0];
			Object[] parameters = new Object[constructor.getParameterCount()];
			for( int i = 0; i < parameters.length; i++ ) {
				parameters[i] = instantiate(
						(Class<? extends EagerModel>) constructor.getParameterTypes()[i] );
			}
			try {
				T instance = (T) constructor.newInstance( parameters );
				instance.listener( listener.orElse( null ) );
				instances.put( type, instance );
			}
			catch( Exception e ) {
				throw new IllegalStateException( "Failed to instantiate with " + constructor, e );
			}
			listener.ifPresent( l -> l.end( instances.get( type ) ) );
		}
		return instances.get( type );
	}

	@Override
	public Stream<Model> subModels() {
		// first: build everything that isn't already built
		types.keySet().stream()
				.forEach( this::instantiate );
		return instances.values().stream();
	}

	@Override
	public Model listener( Listener l ) {
		listener = Optional.ofNullable( l );
		instances.values().forEach( i -> i.listener( l ) );
		return this;
	}
}
