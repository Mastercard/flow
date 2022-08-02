package com.mastercard.test.flow.doc.mask;

import static com.mastercard.test.flow.doc.mask.Actors.AVA;
import static com.mastercard.test.flow.doc.mask.Actors.BEN;
import static com.mastercard.test.flow.doc.mask.Actors.DIE;
import static com.mastercard.test.flow.doc.mask.Unpredictables.RNG;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.builder.Deriver;
import com.mastercard.test.flow.model.EagerModel;
import com.mastercard.test.flow.msg.txt.Text;
import com.mastercard.test.flow.util.TaggedGroup;

/**
 * Documents dice-rolling behaviours
 */
@SuppressWarnings("javadoc")
class Rolling extends EagerModel {

	private Flow standard = Creator.build( flow -> flow
			.meta( data -> data.description( "single die" ) )
			.call( a -> a.from( AVA ).to( BEN )
					.request( new Text( "Please roll 1 normal dice for me" ) )
					.call( b -> b.to( DIE )
							.request( new Text( "1d6" ) )
							.response( new Text( "4" )
									.masking( RNG, m -> m.replace( ".+", "?" ) ) ) )
					.response( new Text( "Those summed to 4" )
							.masking( RNG, m -> m.replace( "\\d+", "?" ) ) ) ) );

	private Flow derived = Deriver.build( standard, flow -> flow
			.meta( data -> data.description( "caltrops" ) )
			.update( i -> i.responder() == BEN,
					i -> i.request().set( "1", "50" ).set( "normal", "spiky" ),
					i -> i.response().set( "4", "133" ) )
			.update( i -> i.responder() == DIE,
					i -> i.request().set( ".+", "50d4" ),
					i -> i.response().set( ".+", "133" ) ) );

	public Rolling() {
		super( new TaggedGroup( "" ) );
		members( flatten( standard, derived ) );
	}
}
