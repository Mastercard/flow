package com.mastercard.test.flow.doc.dep;

import static com.mastercard.test.flow.doc.dep.Actors.AVA;
import static com.mastercard.test.flow.doc.dep.Actors.BEN;
import static com.mastercard.test.flow.doc.dep.Unpredictables.RNG;
import static com.mastercard.test.flow.util.Transmission.Type.REQUEST;
import static com.mastercard.test.flow.util.Transmission.Type.RESPONSE;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.model.EagerModel;
import com.mastercard.test.flow.msg.txt.Text;
import com.mastercard.test.flow.util.TaggedGroup;

/**
 * A simple system model of data storage and retrieval
 */
class Storage extends EagerModel {

	private final Flow put = Creator.build( flow -> flow
			.meta( data -> data
					.description( "put" ) )
			.call( a -> a.from( AVA ).to( BEN )
					.request( new Text( "Please hang on to 'this stored value' for me" ) )
					.response( new Text( "No problem, just ask for "
							+ "'37bf9956-da60-4ec8-b404-ba962ae844be'"
							+ " when you want it back" )
									.masking( RNG, m -> m.replace( "'.*'", "'a random uuid'" ) ) ) ) );

	private final Flow get = Creator.build( flow -> flow
			.meta( data -> data
					.description( "get" ) )
			.call( a -> a.from( AVA ).to( BEN )
					.request( new Text( "Can I have '37bf9956-da60-4ec8-b404-ba962ae844be' back please?" ) )
					.response( new Text( "Here it is! 'this stored value'" ) ) )
			.dependency( put, dep -> dep
					.from( i -> i.responder() == BEN, RESPONSE, "'.*'" )
					.to( i -> i.responder() == BEN, REQUEST, "'.*'" ) ) );

	/***/
	Storage() {
		super( new TaggedGroup() );
		members( flatten( put, get ) );
	}
}
