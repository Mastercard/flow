package com.mastercard.test.flow.example.app.core;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mastercard.test.flow.example.app.Core;
import com.mastercard.test.flow.example.app.Histogram;
import com.mastercard.test.flow.example.app.Queue;
import com.mastercard.test.flow.example.app.Queue.Status;

/**
 * Implementation of the {@link Core} service
 */
public class CoreImp implements com.mastercard.test.flow.example.app.Core {

	private static final Logger LOG = LoggerFactory.getLogger( CoreImp.class );

	/**
	 * Provides character-counting functionality
	 */
	@Dependency
	public Histogram histogram;

	/**
	 * Provides deferred execution
	 */
	@Dependency
	public Queue queue;

	@Override
	public Response process( Request req ) {
		LOG.info( "Processing [ {}, {}, {} ]", req.defer, req.characters, req.text );
		if( req.defer ) {
			String id = queue.enqueue( req.text );
			return new Response( id, Status.PENDING, null );
		}

		Map<String, Integer> m = req.characters == null
				? histogram.histogram( req.text )
				: histogram.histogram( req.text, req.characters );

		return new Response( null, null, m );
	}

	@Override
	public Response query( String id ) {
		LOG.info( "Querying {}", id );
		return new Response( id, queue.status( id ), null );
	}

	@Override
	public Response get( String id ) {
		LOG.info( "returning {}", id );
		return new Response( id, null, queue.result( id ) );
	}

	@Override
	public void delete( String id ) {
		LOG.info( "deleting {}", id );
		queue.delete( id );
	}
}
