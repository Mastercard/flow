package com.mastercard.test.flow.example.app.core;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mastercard.test.flow.assrt.Replay;
import com.mastercard.test.flow.example.app.Core;
import com.mastercard.test.flow.example.app.assrt.AbstractServiceTest;
import com.mastercard.test.flow.example.app.assrt.MockInstance;
import com.mastercard.test.flow.example.app.model.ExampleSystem.Actors;
import com.mastercard.test.flow.example.framework.Instance;

/**
 * Test that exercises the {@link Core} service in isolation by standing up an
 * instance of it and hitting it with requests. We use a {@link MockInstance} to
 * take the part of the rest of the application.
 */
class CoreTest extends AbstractServiceTest {
	private static final Logger LOG = LoggerFactory.getLogger( CoreTest.class );

	private static Instance service;

	/**
	 * Starts the service
	 */
	@BeforeAll
	public static void startService() {
		if( !Replay.isActive() ) {
			service = new Main().build().start();
		}
	}

	/**
	 * Stops the service
	 */
	@AfterAll
	public static void stopService() {
		if( !Replay.isActive() ) {
			service.stop();
		}
	}

	/***/
	public CoreTest() {
		super( service, Actors.CORE, LOG );
	}
}
