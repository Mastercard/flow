package com.mastercard.test.flow.assrt.mock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.util.TaggedGroup;

/**
 * A hardcoded model that useful for testing
 */
public class Mdl implements Model {

	private TaggedGroup tags = new TaggedGroup();
	private final List<Flow> flows = new ArrayList<>();
	private final List<Model> models = new ArrayList<>();

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
		return flows.stream();
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
