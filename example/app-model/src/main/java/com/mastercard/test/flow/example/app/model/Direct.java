package com.mastercard.test.flow.example.app.model;

import static com.mastercard.test.flow.example.app.model.Interactions.CORE;
import static com.mastercard.test.flow.example.app.model.Interactions.HISTOGRAM;
import static com.mastercard.test.flow.example.app.model.Interactions.UI;
import static com.mastercard.test.flow.example.app.model.Messages.coreReq;
import static com.mastercard.test.flow.example.app.model.Messages.coreRes;
import static com.mastercard.test.flow.example.app.model.Messages.jsonRes;
import static com.mastercard.test.flow.example.app.model.Messages.pathUpdate;
import static com.mastercard.test.flow.example.app.model.Messages.rq;
import static com.mastercard.test.flow.example.app.model.Messages.rs;
import static com.mastercard.test.flow.example.app.model.Messages.textReq;
import static com.mastercard.test.flow.example.app.model.Messages.valueFill;
import static com.mastercard.test.flow.msg.AbstractMessage.DELETE;
import static com.mastercard.test.flow.util.Tags.add;

import java.util.Arrays;
import java.util.List;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.builder.Deriver;
import com.mastercard.test.flow.example.app.model.ExampleSystem.Actors;
import com.mastercard.test.flow.model.EagerModel;
import com.mastercard.test.flow.msg.http.HttpMsg;
import com.mastercard.test.flow.msg.http.HttpReq;
import com.mastercard.test.flow.util.TaggedGroup;
import com.mastercard.test.flow.util.Tags;

/**
 * Flows that exercise basic histogram functions using synchronous processing
 */
public class Direct extends EagerModel {

	/**
	 * Tags of flows in this model
	 */
	public static final TaggedGroup MODEL_TAGS = new TaggedGroup( "direct", "histogram" )
			.union( "subset" );

	/**
	 * The empty flow
	 */
	public final Flow empty;

	/**
	 * The hello World flow
	 */
	public final Flow hello;

	/***/
	public Direct() {
		super( MODEL_TAGS );

		// snippet-start:nested
		empty = Creator.build( flow -> flow
				.meta( data -> data
						.description( "empty" )
						.tags( add( "direct", "histogram" ) )
						.motivation( "Counting zero characters while-you-wait" ) )
				.call( a -> a
						.from( Actors.USER )
						.to( Actors.UI )
						.request( textReq( "POST", "/histogram", "" ) )
						.call( b -> b
								.to( Actors.CORE )
								.request( coreReq( "POST", "/process", "text", "" ) )
								.call( c -> c
										.to( Actors.HISTOGRAM )
										.request( textReq( "POST", "/count/all", "" ) )
										.response( jsonRes() ) )
								.response( coreRes() ) )
						.response( jsonRes() ) ) );
		// snippet-end:nested

		String text = "Hello world!";
		List<Object> counts = Arrays.asList(
				" ", 1, "!", 1, "H", 1, "d", 1, "e", 1, "l", 3, "o", 2, "r", 1, "w", 1 );
		hello = Deriver.build( empty, flow -> flow
				.meta( data -> data
						.description( "hello" )
						.motivation( m -> m.replace( "zero", "the traditional" ) ) )
				.update( UI,
						rq( "", text ),
						rs( counts ) )
				.update( CORE,
						rq( "text", text ),
						rs( pathUpdate( p -> "result." + p, counts ) ) )
				.update( HISTOGRAM,
						rq( "", text ),
						rs( counts ) ) );

		Flow yodel = Deriver.build( hello, flow -> flow
				.meta( data -> data
						.description( "yodel" )
						.motivation( m -> m + ", in Switzerland" ) )
				.update( UI,
						rq( "o", "oOoOo" ),
						rs( "O", 4,
								"o", 6 ) )
				.update( CORE,
						rq( "text", "HelloOoOo woOoOorld!" ),
						rs( "result.o", 6,
								"result.O", 4 ) )
				.update( HISTOGRAM,
						rq( "o", "oOoOo" ),
						rs( "O", 4,
								"o", 6 ) ) );

		List<Object> deletes = valueFill( DELETE,
				" ", "!", "H", "d", "l", "r", "w" );
		Flow vowels = Deriver.build( hello, flow -> flow
				.meta( data -> data.description( "vowels" )
						.motivation( "Counting only vowels" )
						.tags( Tags.add( "subset" ) ) )
				.update( UI,
						rq( HttpReq.PATH, "/histogram/subset",
								HttpMsg.header( "characters" ), "aeiou" ),
						rs( deletes ) )
				.update( CORE,
						rq( "characters", "aeiou" ),
						rs( pathUpdate( p -> "result." + p, deletes ) ) )
				.update( HISTOGRAM,
						rq( HttpReq.PATH, "/count/subset?characters=aeiou" ),
						rs( deletes ) ) );

		members( flatten( empty, hello, yodel, vowels ) );
	}

}
