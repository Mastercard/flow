package com.mastercard.test.flow.example.app.queue;

import java.util.function.Consumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.Replay;
import com.mastercard.test.flow.assrt.junit5.Flocessor;
import com.mastercard.test.flow.example.app.Queue;
import com.mastercard.test.flow.example.app.assrt.AbstractServiceTest;
import com.mastercard.test.flow.example.app.assrt.MockInstance;
import com.mastercard.test.flow.example.app.assrt.ctx.QueueProcessingApplicator;
import com.mastercard.test.flow.example.app.model.ExampleSystem.Actors;
import com.mastercard.test.flow.example.framework.Instance;

/**
 * Test that exercises the {@link Queue} service in isolation by standing up an
 * instance of it and hitting it with requests. We use a {@link MockInstance} to
 * take the part of the rest of the application.
 */
class QueueTest extends AbstractServiceTest {
	private static final Logger LOG = LoggerFactory.getLogger( QueueTest.class );

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
	public QueueTest() {
		super( service, Actors.QUEUE, LOG );
	}

	@Override
	protected Consumer<Flocessor> custom() {
		return f -> f
				// the queue is stateful...
				.system( State.FUL, Actors.QUEUE )
				// ...and has relevant context that we'll need to apply
				.applicators( new QueueProcessingApplicator( service ) );
	}
}
