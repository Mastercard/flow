package com.mastercard.test.flow.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.util.TaggedGroup;

/**
 * Combines other {@link Model} instances
 */
public class CombineModel extends TitledModel {

	private final List<Model> children = new ArrayList<>();
	private TaggedGroup tags = new TaggedGroup();

	/**
	 */
	public CombineModel() {
	}

	/**
	 * @param title A human-readable title for this group of {@link Flow}s
	 */
	public CombineModel( String title ) {
		super( title );
	}

	/**
	 * Adds child models to this model
	 *
	 * @param m The model instances
	 * @return <code>this</code>
	 */
	public CombineModel with( Model... m ) {
		Collections.addAll( children, m );

		Iterator<Model> ci = children.iterator();
		if( ci.hasNext() ) {
			tags = new TaggedGroup( ci.next().tags() );
			while( ci.hasNext() ) {
				tags.combine( ci.next().tags() );
			}
		}

		return this;
	}

	@Override
	public TaggedGroup tags() {
		return tags;
	}

	@Override
	public Stream<Flow> flows( Set<String> include, Set<String> exclude ) {
		return children.stream().flatMap( c -> c.flows( include, exclude ) );
	}

	@Override
	public Stream<Model> subModels() {
		return children.stream();
	}

	@Override
	public Model listener( Listener l ) {
		children.forEach( c -> c.listener( l ) );
		return this;
	}

}
