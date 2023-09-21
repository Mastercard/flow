
package com.mastercard.test.flow.model;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.util.TaggedGroup;
import com.mastercard.test.flow.util.Tags;

/**
 * A {@link Model} where the constituent {@link Flow} instances are built in the
 * constructor. Call {@link #members(Collection)} at the end of your constructor
 * implementation to register the {@link Flow} instances. If the {@link Flow}s
 * in one instance depends on those in another, simply declare the dependency
 * model as a constructor parameter and register both with the same
 * {@link LazyModel}.
 */
public abstract class EagerModel extends TitledModel {

	private final TaggedGroup tags;
	private Set<Flow> members;

	/**
	 * @param title A human-readable title for this group of {@link Flow}s
	 * @param tags  The tags on the {@link Flow}s in this {@link Model}
	 */
	protected EagerModel( String title, TaggedGroup tags ) {
		super( title );
		this.tags = tags;
	}

	/**
	 * @param tags The tags on the {@link Flow}s in this {@link Model}
	 */
	protected EagerModel( TaggedGroup tags ) {
		this.tags = tags;
	}

	@Override
	public TaggedGroup tags() {
		return tags;
	}

	/**
	 * Extracts the tags from an {@link EagerModel} type
	 *
	 * @param type The type
	 * @return The contents of the <code>public static TaggedGroup TAGS</code> field
	 *         that is assumed to exist
	 * @throws IllegalArgumentException if the field does not exist or is
	 *                                  <code>null</code>
	 */
	public static TaggedGroup typeTags( Class<? extends EagerModel> type ) {
		try {
			Field f = type.getDeclaredField( LazyModel.MODEL_TAGS_FIELD_NAME );

			int mod = f.getModifiers();
			if( !(Modifier.isPublic( mod )
					&& Modifier.isStatic( mod )
					&& Modifier.isFinal( mod )
					&& TaggedGroup.class.isAssignableFrom( f.getType() )) ) {
				throw new IllegalArgumentException( String.format(
						"%s must have a `public static final %s %s` field",
						type, TaggedGroup.class.getSimpleName(), LazyModel.MODEL_TAGS_FIELD_NAME ) );
			}

			return (TaggedGroup) f.get( null );
		}
		catch( NoSuchFieldException | IllegalAccessException e ) {
			throw new IllegalArgumentException( String.format(
					"%s must have a `public static final %s %s` field",
					type, TaggedGroup.class.getSimpleName(), LazyModel.MODEL_TAGS_FIELD_NAME ) );
		}
	}

	@Override
	public final Stream<Flow> flows( Set<String> include, Set<String> exclude ) {
		if( members == null ) {
			throw new IllegalStateException( String.format(
					"No flows registered in %s. Have you called members() in the constructor?",
					getClass() ) );
		}
		return members.stream()
				.filter( f -> Tags.filter( f.meta().tags(), include, exclude ) );
	}

	/**
	 * Sets the membership of this model. This should be called at the end of the
	 * model constructor.
	 *
	 * @param flows All {@link Flow} instances in this {@link Model}
	 * @see #flatten(Object...)
	 */
	protected void members( Collection<Flow> flows ) {
		if( members != null ) {
			throw new IllegalStateException( "InstanceModel members must be set exactly once" );
		}

		members = new LinkedHashSet<>( flows );
	}

	/**
	 * Convenience method for when you've built your {@link Flow} instances as
	 * individual instances, in collections and streams, and you want to register
	 * them all in one go
	 *
	 * @param flows A lumpy sequence of {@link Flow}s and collections/streams
	 *              thereof
	 * @return A flat collection of {@link Flow}s
	 */
	@SuppressWarnings("unchecked")
	protected static Collection<Flow> flatten( Object... flows ) {
		Collection<Flow> flat = new ArrayList<>();

		for( Object o : flows ) {
			if( o instanceof Flow ) {
				flat.add( (Flow) o );
			}
			else if( o instanceof Collection<?> ) {
				flat.addAll( (Collection<Flow>) o );
			}
			else if( o instanceof Stream<?> ) {
				((Stream<Flow>) o).forEach( flat::add );
			}
		}

		return flat;
	}

	@Override
	public Model listener( Listener l ) {
		if( l != null ) {
			l.count( this, 0, members.size() );
		}
		return this;
	}

	@Override
	public Stream<Model> subModels() {
		return Stream.empty();
	}
}
