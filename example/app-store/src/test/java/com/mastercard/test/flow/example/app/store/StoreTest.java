package com.mastercard.test.flow.example.app.store;

import java.util.function.Consumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.Replay;
import com.mastercard.test.flow.assrt.junit5.Flocessor;
import com.mastercard.test.flow.example.app.Store;
import com.mastercard.test.flow.example.app.assrt.AbstractServiceTest;
import com.mastercard.test.flow.example.app.model.ExampleSystem.Actors;
import com.mastercard.test.flow.example.framework.Instance;
import com.mastercard.test.flow.util.Flows;

/**
 * Test that exercises the {@link Store} service in isolation by standing up an
 * instance of it and hitting it with requests. The {@link Store} instance uses
 * a real database.
 */
class StoreTest extends AbstractServiceTest {
	private static final Logger LOG = LoggerFactory.getLogger( StoreTest.class );

	private static final Instance service = new Main().build();

	/**
	 * Starts the service
	 */
	@BeforeAll
	public static void startService() {
		if( !Replay.isActive() ) {
			service.start();
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
	public StoreTest() {
		super( service, Actors.STORE, LOG );
	}

	@Override
	protected Consumer<Flocessor> custom() {
		return flocessor -> flocessor
				// stateless data stores are rarely useful. Note that we're also including the
				// DB in the system under test
				.system( State.FUL, Actors.STORE, Actors.DB )
				// there are only a few flows that actually hit the store, so let's avoid having
				// to ignore the skip results for the others
				.exercising( flow -> Flows.intersects( flow, Actors.STORE ), LOG::info );
	}
}
