package com.mastercard.test.flow.example.app.model;

import static com.mastercard.test.flow.example.app.model.Interactions.CORE;
import static com.mastercard.test.flow.example.app.model.Interactions.QUEUE;
import static com.mastercard.test.flow.example.app.model.Interactions.STORE;
import static com.mastercard.test.flow.example.app.model.Interactions.UI;
import static com.mastercard.test.flow.example.app.model.Messages.PATH_ID;
import static com.mastercard.test.flow.example.app.model.Messages.coreReq;
import static com.mastercard.test.flow.example.app.model.Messages.coreRes;
import static com.mastercard.test.flow.example.app.model.Messages.dbDelete;
import static com.mastercard.test.flow.example.app.model.Messages.dbInsert;
import static com.mastercard.test.flow.example.app.model.Messages.dbSelect;
import static com.mastercard.test.flow.example.app.model.Messages.httpReq;
import static com.mastercard.test.flow.example.app.model.Messages.httpRes;
import static com.mastercard.test.flow.example.app.model.Messages.jsonRes;
import static com.mastercard.test.flow.example.app.model.Messages.md5;
import static com.mastercard.test.flow.example.app.model.Messages.pathUpdate;
import static com.mastercard.test.flow.example.app.model.Messages.rq;
import static com.mastercard.test.flow.example.app.model.Messages.rs;
import static com.mastercard.test.flow.example.app.model.Messages.textReq;
import static com.mastercard.test.flow.example.app.model.Messages.textRes;
import static com.mastercard.test.flow.msg.http.HttpMsg.BODY;
import static com.mastercard.test.flow.msg.http.HttpReq.METHOD;
import static com.mastercard.test.flow.msg.http.HttpReq.PATH;
import static com.mastercard.test.flow.util.Tags.add;
import static com.mastercard.test.flow.util.Tags.remove;
import static com.mastercard.test.flow.util.Transmission.Type.REQUEST;
import static com.mastercard.test.flow.util.Transmission.Type.RESPONSE;

import java.util.Arrays;
import java.util.List;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.builder.Chain;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.builder.Deriver;
import com.mastercard.test.flow.example.app.model.ExampleSystem.Actors;
import com.mastercard.test.flow.example.app.model.ctx.QueueProcessing;
import com.mastercard.test.flow.example.app.model.rsd.DBItems;
import com.mastercard.test.flow.model.EagerModel;
import com.mastercard.test.flow.msg.json.Json;
import com.mastercard.test.flow.msg.sql.Result;
import com.mastercard.test.flow.util.TaggedGroup;

/**
 * Flows that explore the behaviour of deferred histogram processing
 */
public class Deferred extends EagerModel {

	/**
	 * Tags of flows in this model
	 */
	public static final TaggedGroup MODEL_TAGS = new TaggedGroup(
			"chain:provoked", "deferred", "histogram" )
					.union( "enqueue", "process", "query", "retrieve" );

	/**
	 * We want to provoke the task processing manually, so the queue will have to be
	 * inactive up to that point
	 */
	private static final QueueProcessing Q_OFF = new QueueProcessing()
			.active( false );
	/**
	 * We also don't want pre-existing tasks messing up our assumed state, so the
	 * enqueue flow will also clear the queue
	 */
	private static final QueueProcessing Q_OFF_EMPTY = Q_OFF.child()
			.cleared( true );

	private final Flow enqueue;
	private final Flow results;

	/***/
	public Deferred() {
		super( MODEL_TAGS );

		Chain provoked = new Chain( "provoked" );

		String deferredId = "29e2271a-e645-420e-8ef9-238d37ceb4cc";

		String text = "I'll be back!";
		List<Object> counts = Arrays.asList( " ", 2, "!", 1, "'", 1, "I", 1, "a", 1, "b", 2, "c", 1,
				"e", 1, "k", 1, "l", 2 );
		String countText = "{\" \":2,\"!\":1,\"'\":1,\"I\":1,\"a\":1,"
				+ "\"b\":2,\"c\":1,\"e\":1,\"k\":1,\"l\":2}";

		enqueue = Creator.build( provoked, flow -> flow
				.meta( data -> data
						.description( "enqueue" )
						.tags( add( "deferred", "enqueue", "histogram" ) )
						.motivation( "Queues up a histogram task that will be executed in the background" ) )
				.context( Q_OFF_EMPTY )
				.call( a -> a
						.from( Actors.USER )
						.to( Actors.UI )
						.request( textReq( "POST", "/histogram/deferred", text ) )
						.call( b -> b
								.to( Actors.CORE )
								.request( coreReq( "POST", "/process",
										"defer", true,
										"text", "I'll be back!" ) )
								.call( c -> c
										.to( Actors.QUEUE )
										.request( textReq( "POST", "/queue/add", text ) )
										.call( d -> d
												.to( Actors.STORE )
												.request( textReq( "PUT", "/data/" + PATH_ID, text )
														.set( PATH_ID, deferredId ) )
												.call( e -> e
														.to( Actors.DB )
														.request( dbInsert( deferredId, text ) )
														.response( new Result( "1" ) ) )
												.response( httpRes( null ) ) )
										.response( textRes( deferredId ) ) )
								.response( coreRes(
										"deferredID", deferredId,
										"deferredStatus", "PENDING",
										"result", null ) ) )
						.response( textRes( deferredId ) ) )
				.residue( new DBItems()
						.added( deferredId, text ) ) );

		Flow preQuery = Deriver.build( enqueue, provoked, flow -> flow
				.meta( data -> data
						.description( "pre" )
						.tags( remove( "enqueue" ), add( "query" ) )
						.motivation( "Checking the state of the task before it has been executed" ) )
				.context( Q_OFF )
				.removeCall( STORE )
				.update( UI,
						rq( METHOD, "GET",
								PATH, "/histogram/deferred/" + PATH_ID,
								PATH_ID, deferredId,
								BODY, null ),
						rs( ".+", "PENDING" ) )
				.update( CORE,
						rq( METHOD, "GET",
								PATH, "/query/" + PATH_ID,
								PATH_ID, deferredId,
								BODY, null ) )
				.update( QUEUE,
						rq( METHOD, "GET",
								PATH, "/queue/" + PATH_ID + "/status",
								PATH_ID, deferredId,
								BODY, null ),
						rs( ".+", "PENDING" ) )
				.residue( DBItems.class, null )
				.dependency( enqueue, d -> d
						.from( UI, RESPONSE, ".+" )
						.to( UI, REQUEST, PATH_ID ) )
				.dependency( enqueue, d -> d
						.from( CORE, RESPONSE, "deferredID" )
						.to( CORE, REQUEST, PATH_ID ) )
				.dependency( enqueue, d -> d
						.from( QUEUE, RESPONSE, ".+" )
						.to( QUEUE, REQUEST, PATH_ID ) ) );

		Flow process = Creator.build( provoked, flow -> flow
				.meta( data -> data
						.description( "process" )
						.tags( add( "deferred", "process", "histogram" ) )
						.motivation( "Provoking the execution of the queued task" ) )
				.context( Q_OFF )
				.call( a -> a.from( Actors.OPS )
						.to( Actors.QUEUE )
						.request( httpReq( "POST", "/queue/configure",
								new Json().set( "process", "1" ) ) )
						.call( b -> b
								.to( Actors.STORE )
								.tags( add( "get" ) )
								.request( httpReq( "GET", "/data/" + PATH_ID, null )
										.set( PATH_ID, deferredId ) )
								.call( c -> c
										.to( Actors.DB )
										.tags( add( "get" ) )
										.request( dbSelect( deferredId ) )
										.response( new Result( "data", "hash" )
												.set( "0:0", "I'll be back!" )
												.set( "0:1", md5( "I'll be back!" ) ) ) )
								.response( textRes( "I'll be back!" ) ) )
						.call( b -> b
								.to( Actors.CORE )
								.request( coreReq( "POST", "/process", "text", text ) )
								.call( c -> c
										.to( Actors.HISTOGRAM )
										.request( textReq( "POST", "/count/all", text ) )
										.response( jsonRes( counts ) ) )
								.response( coreRes( pathUpdate( p -> "result." + p, counts ) ) ) )
						.call( b -> b
								.to( Actors.STORE )
								.tags( add( "put" ) )
								.request( textReq( "PUT", "/data/" + PATH_ID, countText )
										.set( PATH_ID, deferredId ) )
								.call( c -> c
										.to( Actors.DB )
										.tags( add( "put" ) )
										.request( dbInsert( deferredId, countText ) )
										.response( new Result() ) )
								.response( httpRes( null ) ) )
						.response( httpRes( null ) ) )
				.residue( new DBItems()
						.updated( deferredId, text, countText ) )
				.prerequisite( preQuery ) );

		Flow postQuery = Deriver.build( preQuery, provoked, flow -> flow
				.meta( data -> data
						.description( "post" )
						.motivation( "Checking the state of the task after it has been executed" ) )
				.update( UI,
						rs( ".+", "COMPLETE" ) )
				.update( CORE,
						rs( "deferredStatus", "COMPLETE" ) )
				.update( QUEUE,
						rs( ".+", "COMPLETE" ) )
				.prerequisite( process ) );

		results = Deriver.build( postQuery, provoked, flow -> flow
				.meta( data -> data
						.description( "results" )
						.tags( remove( "query" ), add( "retrieve" ) )
						.motivation( "Collecting the results of the deferred task" ) )
				.addCall( QUEUE, a -> a
						.to( Actors.STORE )
						.tags( add( "delete" ) )
						.request( httpReq( "DELETE", "/data/" + PATH_ID, null )
								.set( PATH_ID, deferredId ) )
						.call( b -> b.to( Actors.DB )
								.tags( add( "get" ) )
								.request( dbSelect( deferredId ) )
								.response( new Result( "data", "hash" )
										.set( "0:0", countText )
										.set( "0:1", md5( countText ) ) ) )
						.call( b -> b.to( Actors.DB )
								.tags( add( "delete" ) )
								.request( dbDelete( deferredId ) )
								.response( new Result() ) )
						.response( textRes( countText ) ) )
				.update( UI,
						rq( METHOD, "POST" ),
						rs( BODY, new Json() ),
						rs( counts ) )
				.update( CORE,
						rq( METHOD, "POST",
								PATH, "/result/" + PATH_ID ),
						rs( BODY, new Json(),
								"deferredID", deferredId,
								"deferredStatus", null ),
						rs( pathUpdate( p -> "result." + p, counts ) ) )
				.update( QUEUE,
						rq( METHOD, "POST",
								PATH, "/queue/" + PATH_ID + "/result" ),
						rs( BODY, new Json() ),
						rs( counts ) )
				.residue( new DBItems()
						.removed( deferredId, countText ) )
				.prerequisite( postQuery ) );

		members( flatten( enqueue, preQuery, process, postQuery, results ) );
	}

	/**
	 * @return The flow that adds a deferred histogram task
	 */
	public Flow enqueue() {
		return enqueue;
	}

	/**
	 * @return The flow that retrieves the results of {@link #enqueue()}
	 */
	public Flow results() {
		return results;
	}
}
