package com.mastercard.test.flow.assrt.filter.mock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.util.TaggedGroup;

/**
 * A hardcoded model that useful for testing assertion components
 */
public class Mdl implements Model {

	private TaggedGroup tags = new TaggedGroup();
	private final List<Flow> flows = new ArrayList<>();
	private final List<Model> models = new ArrayList<>();
	private boolean flowsHaveBeenAccessed = false;

	/**
	 * @param flws the details of flows to add to the model
	 * @return <code>this</code>
	 */
	public Mdl withFlows( String... flws ) {
		Stream.of( flws )
				.map( Flw::new )
				.forEach( flows::add );

		refreshTags();
		return this;
	}

	/**
	 * @param flws The flows to add
	 * @return <code>this</code>
	 */
	public Mdl withFlows( Flow... flws ) {
		Collections.addAll( flows, flws );
		refreshTags();
		return this;
	}

	private void refreshTags() {
		tags = new TaggedGroup( flows.get( 0 ).meta().tags() );
		for( Flow flow : flows ) {
			tags.combine( new TaggedGroup( flow.meta().tags() ) );
		}
	}

	/**
	 * @param mdl A submodel
	 * @return <code>this</code>
	 */
	public Mdl withSubModel( Model mdl ) {
		models.add( mdl );
		return this;
	}

	@Override
	public String title() {
		return "Mdl";
	}

	@Override
	public TaggedGroup tags() {
		return tags;
	}

	@Override
	public Stream<Flow> flows( Set<String> include, Set<String> exclude ) {
		flowsHaveBeenAccessed = true;
		return flows.stream();
	}

	/**
	 * Checks if the flows in the model have been accessed yet. This is important to
	 * know - the whole point of flow filtering is to minimise the number of flows
	 * that we need to construct by filtering on the statically-known tags before we
	 * access (which implies construction) any flows.
	 *
	 * @return <code>true</code> if flows have been accessed.
	 */
	public boolean flowAccess() {
		return flowsHaveBeenAccessed;
	}

	@Override
	public Stream<Model> subModels() {
		return models.stream();
	}

	@Override
	public Model withListener( Listener l ) {
		throw new UnsupportedOperationException();
	}

}
