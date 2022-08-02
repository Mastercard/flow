package com.mastercard.test.flow.example.app.ui;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mastercard.test.flow.example.app.Core;
import com.mastercard.test.flow.example.app.Core.Request;
import com.mastercard.test.flow.example.app.Core.Response;
import com.mastercard.test.flow.example.app.Ui;

/**
 * Implementation of the gateway service
 */
public class UiImp implements Ui {

	private static final Logger LOG = LoggerFactory.getLogger( UiImp.class );

	/**
	 * Provides app functionality
	 */
	@Dependency
	public Core core;

	@Override
	public Map<String, Integer> histogram( String text ) {
		LOG.info( "histogram of {}", text );
		return core.process( new Request( false, null, text ) ).result;
	}

	@Override
	public Map<String, Integer> histogram( String text, String characters ) {
		LOG.info( "histogram of {}, subset {}", text, characters );
		return core.process( new Request( false, characters, text ) ).result;
	}

	@Override
	public String deferred( String text ) {
		LOG.info( "deferring '{}'", text );
		return core.process( new Request( true, null, text ) ).deferredID;
	}

	@Override
	public String status( String id ) {
		LOG.info( "querying {}", id );
		Response res = core.query( id );
		LOG.info( "response {}", res.deferredStatus );
		return res.deferredStatus.name();
	}

	@Override
	public Map<String, Integer> results( String id ) {
		LOG.info( "returning {}", id );
		return core.get( id ).result;
	}
}
