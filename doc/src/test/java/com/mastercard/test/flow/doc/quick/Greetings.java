package com.mastercard.test.flow.doc.quick;

import static com.mastercard.test.flow.doc.quick.Actors.AVA;
import static com.mastercard.test.flow.doc.quick.Actors.BEN;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.builder.Deriver;
import com.mastercard.test.flow.model.EagerModel;
import com.mastercard.test.flow.msg.txt.Text;
import com.mastercard.test.flow.util.TaggedGroup;
import com.mastercard.test.flow.util.Tags;

/**
 * Trivial example of flow definition.
 */
@SuppressWarnings("javadoc")
class Greetings extends EagerModel {

	// snippet-start:first_flow
	private Flow polite = Creator.build( flow -> flow
			.meta( data -> data
					.description( "Happy Ava" )
					.tags( Tags.set( "polite", "greeting" ) )
					.motivation( "Shows what happens when Ava meets Ben while she's in a _good_ mood" ) )
			.call( hello -> hello
					.from( AVA ).to( BEN )
					.request( new Text( "Hello Ben, how are you today?" ) )
					.response( new Text( "I am well, thanks for asking." ) ) ) );
	// snippet-end:first_flow

	// snippet-start:derived_flow
	private Flow rude = Deriver.build( polite, flow -> flow
			.meta( data -> data
					.description( "Grumpy Ava" )
					.tags( Tags.remove( "polite" ), Tags.add( "rude" ) )
					.motivation( m -> m.replaceAll( "good", "bad" ) ) )
			.update( i -> i.responder() == BEN,
					i -> i.request().set( ", .*", ", I profoundly despise you!" ),
					i -> i.response().set( ".+", "The feeling is mutual!" ) ) );
	// snippet-end:derived_flow

	// snippet-start:group_construction
	public Greetings() {
		super( new TaggedGroup( "greeting" )
				.union( "polite", "rude" ) );
		members( flatten( polite, rude ) );
	}
	// snippet-end:group_construction
}
