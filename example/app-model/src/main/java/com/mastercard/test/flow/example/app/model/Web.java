package com.mastercard.test.flow.example.app.model;

import static com.mastercard.test.flow.example.app.model.Interactions.ALL;
import static com.mastercard.test.flow.example.app.model.Interactions.CORE;
import static com.mastercard.test.flow.example.app.model.Interactions.HISTOGRAM;
import static com.mastercard.test.flow.example.app.model.Interactions.UI;
import static com.mastercard.test.flow.example.app.model.Interactions.WEB_UI;
import static com.mastercard.test.flow.example.app.model.Interactions.replace;
import static com.mastercard.test.flow.example.app.model.Messages.rq;
import static com.mastercard.test.flow.example.app.model.Messages.rs;
import static com.mastercard.test.flow.util.Tags.add;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.builder.Deriver;
import com.mastercard.test.flow.example.app.model.ExampleSystem.Actors;
import com.mastercard.test.flow.model.EagerModel;
import com.mastercard.test.flow.msg.http.HttpMsg;
import com.mastercard.test.flow.msg.http.HttpReq;
import com.mastercard.test.flow.util.TaggedGroup;

/**
 * Flows that exercise the web interface
 */
public class Web extends EagerModel {

	/**
	 * Tags of flows in this model
	 */
	public static final TaggedGroup MODEL_TAGS = new TaggedGroup(
			"direct", "histogram", "web" )
					.union( "subset" );

	/**
	 * @param direct provides flow basis
	 */
	public Web( Direct direct ) {
		super( MODEL_TAGS );

		Flow empty = Deriver.build( direct.empty, flow -> flow
				.meta( data -> data
						.tags( add( "web" ) )
						.motivation( m -> m + ", via the web interface" ) )
				.update( ALL, replace( Actors.USER, Actors.WEB_UI ) )
				.superset( Actors.USER,
						Messages.directHistogram(),
						Messages.results() ) );

		Flow hello = Deriver.build( empty, flow -> flow
				.meta( data -> data
						.description( "hello" )
						.tags( add( "subset" ) )
						.motivation( s -> s.replace( "zero ", "vowel " ) ) )
				// snippet-start:parameter_update
				.update( WEB_UI,
						rq( "subject", "Hello web!",
								"characters", "aeiou" ),
						rs( "subject", "Hello web!",
								"characters", "aeiou",
								"results", "  e = 2\n  o = 1" ) )
				// snippet-end:parameter_update
				.update( UI,
						rq( HttpReq.PATH, "/histogram/subset",
								HttpMsg.header( "characters" ), "aeiou",
								"", "Hello web!" ),
						rs( "e", 2,
								"o", 1 ) )
				.update( CORE,
						rq( "characters", "aeiou",
								"text", "Hello web!" ),
						rs( "result.e", 2,
								"result.o", 1 ) )
				.update( HISTOGRAM,
						rq( HttpReq.PATH, "/count/subset?characters=aeiou",
								"", "Hello web!" ),
						rs( "e", 2,
								"o", 1 ) ) );

		members( flatten( empty, hello ) );
	}
}
