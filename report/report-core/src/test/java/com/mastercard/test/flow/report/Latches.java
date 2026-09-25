package com.mastercard.test.flow.report;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Bounded latch waits for concurrency fixtures
 */
class Latches {

	private Latches() {
		// no instances
	}

	/**
	 * Waits for a latch, failing the test rather than hanging it
	 *
	 * @param latch The latch to wait on
	 */
	static void await( CountDownLatch latch ) {
		try {
			assertTrue( latch.await( 10, TimeUnit.SECONDS ), "Fixture did not progress" );
		}
		catch( InterruptedException e ) {
			Thread.currentThread().interrupt();
			throw new AssertionError( e );
		}
	}
}
