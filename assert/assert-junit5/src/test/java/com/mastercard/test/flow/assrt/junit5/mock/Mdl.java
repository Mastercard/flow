package com.mastercard.test.flow.assrt.junit5.mock;

import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.builder.Deriver;
import com.mastercard.test.flow.util.TaggedGroup;

/**
 * A mocked {@link Model} for testing assertion components
 */
public class Mdl implements Model {
	private static Consumer<Creator> call = c -> c
			.call( a -> a.from( Actrs.AVA ).to( Actrs.BEN )
					.request( new Msg( "Hi, I'm Ava" ) )
					.response( new Msg( "Hi Ava, I'm Ben" ) ) );

	private Flow success = Creator.build( flow -> flow
			.meta( data -> data
					.description( "success" ) ),
			call );

	private Flow failure = Creator.build( flow -> flow
			.meta( data -> data
					.description( "failure" ) ),
			call );

	private Flow error = Creator.build( flow -> flow
			.meta( data -> data
					.description( "error" ) ),
			call );

	private Flow successChild = Deriver.build( success, flow -> flow
			.meta( data -> data
					.description( "successChild" ) ) );

	private Flow failureChild = Deriver.build( failure, flow -> flow
			.meta( data -> data
					.description( "failureChild" ) ) );

	private Flow errorChild = Deriver.build( error, flow -> flow
			.meta( data -> data
					.description( "errorChild" ) ) );

	private Flow successDependent = Creator.build( flow -> flow
			.meta( data -> data
					.description( "successDependent" ) )
			.prerequisite( success ),
			call );

	private Flow failureDependent = Creator.build( flow -> flow
			.meta( data -> data
					.description( "failureDependent" ) )
			.prerequisite( failure ),
			call );

	private Flow errorDependent = Creator.build( flow -> flow
			.meta( data -> data
					.description( "errorDependent" )
					.trace( ad -> ad.add( "addenda" ) ) )
			.prerequisite( error ),
			call );

	@Override
	public Stream<Flow> flows( Set<String> include, Set<String> exclude ) {
		return Stream.of( success, failure, error,
				successChild, failureChild, errorChild,
				successDependent, failureDependent, errorDependent );
	}

	@Override
	public Model listener( Listener l ) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String title() {
		return "Mdl";
	}

	@Override
	public TaggedGroup tags() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Stream<Model> subModels() {
		return Stream.empty();
	}

}
