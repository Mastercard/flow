package com.mastercard.test.flow.example.app.queue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastercard.test.flow.example.app.Core;
import com.mastercard.test.flow.example.app.Core.Request;
import com.mastercard.test.flow.example.app.Core.Response;
import com.mastercard.test.flow.example.app.Queue;
import com.mastercard.test.flow.example.app.Store;

/**
 * Implementation of the {@link Queue} service
 */
public class QueueImp implements Queue {

	private static final Logger LOG = LoggerFactory.getLogger( QueueImp.class );
	private static final ObjectMapper JSON = new ObjectMapper();

	/**
	 * How we process our tasks
	 */
	@Dependency
	public Core core;

	/**
	 * Where we store the task text and results
	 */
	@Dependency
	public Store store;

	private final Deque<String> queue = new ArrayDeque<>();

	private final Set<String> completed = new HashSet<>();

	/**
	 * Whether scheduled processing is in effect
	 */
	boolean active = true;

	/***/
	public QueueImp() {
		Timer timer = new Timer( true );
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				if( active ) {
					process();
				}
			}
		};
		timer.scheduleAtFixedRate( task, 1000, 1000 );
	}

	/**
	 * Processes a single deferred task
	 */
	synchronized void process() {
		String id = queue.poll();
		if( id != null ) {
			LOG.info( "Processing {}", id );
			String text = store.retrieve( id );
			try {
				Response res = core.process( new Request( false, null, text ) );
				store.store( id, JSON.writeValueAsString( res.result ) );
			}
			catch( Exception e ) {
				LOG.error( "Failed to process " + id, e );
			}
			completed.add( id );
		}
	}

	@Override
	public String enqueue( String text ) {
		String id = UUID.randomUUID().toString();
		LOG.info( "enqueuing {}", id );
		store.store( id, text );
		queue.addLast( id );
		return id;
	}

	@Override
	public synchronized Status status( String id ) {
		LOG.info( "querying {}", id );
		if( completed.contains( id ) ) {
			return Status.COMPLETE;
		}
		if( queue.contains( id ) ) {
			return Status.PENDING;
		}
		return Status.NO_SUCH_TASK;
	}

	@Override
	public synchronized Map<String, Integer> result( String id ) {
		LOG.info( "returning {}", id );
		if( !completed.contains( id ) ) {
			LOG.warn( "No such item in completed set!" );
			return null;
		}

		completed.remove( id );
		String encoded = store.delete( id );
		if( encoded == null ) {
			LOG.warn( "No such result in store!" );
			return null;
		}

		try {
			return JSON.readValue( encoded,
					new TypeReference<HashMap<String, Integer>>() {
						// type hint only
					} );
		}
		catch( Exception e ) {
			LOG.error( "Failed to decode '" + encoded + "'", e );
			return null;
		}
	}

	@Override
	public void delete( String id ) {
		LOG.info( "deleting {}", id );
		store.delete( id );
		queue.remove( id );
		completed.remove( id );
	}

	@Override
	public void configure( Map<String, String> config ) {

		active = Optional.ofNullable( config )
				.map( c -> c.get( "active" ) )
				.map( Boolean::parseBoolean )
				.orElse( true );
		LOG.info( "Activation : {}", active );

		int pc = Optional.ofNullable( config )
				.map( c -> c.get( "process" ) )
				.filter( s -> s.matches( "\\d+" ) )
				.map( Integer::parseInt )
				.orElse( 0 );
		if( pc > 0 ) {
			LOG.info( "Processing {} tasks", pc );
			for( int i = 0; i < pc && !queue.isEmpty(); i++ ) {
				process();
			}
		}

		boolean clear = Optional.ofNullable( config )
				.map( c -> c.get( "clear" ) )
				.map( Boolean::parseBoolean )
				.orElse( false );
		if( clear ) {
			LOG.info( "Clearing queue" );
			queue.clear();
			// store.clear();
			completed.clear();
		}
	}
}
