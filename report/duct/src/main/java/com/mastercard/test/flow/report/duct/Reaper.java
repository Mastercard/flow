package com.mastercard.test.flow.report.duct;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Thread} that monitors {@link Duct#expiry()}. When the expiry has
 * been reached the {@link Duct} is shut down
 */
class Reaper extends Thread {
	private static final Logger LOG = LoggerFactory.getLogger( Reaper.class );

	private final Duct duct;

	/**
	 * @param duct The {@link Duct} instance to control
	 */
	Reaper( Duct duct ) {
		super( "duct reaper" );
		this.duct = duct;
		setDaemon( true );
	}

	@Override
	public void run() {
		Duration delay = Duration.between( Instant.now(), duct.expiry() );
		while( !delay.isNegative() ) {
			try {
				LOG.info( "Sleeping for a bit over" + delay );
				// if we sleep for exactly the time left then we get a busy-loop of
				// zero-duration sleeps right before dying. Allowing 10ms grace for another
				// heartbeat to come in avoids pointless log noise
				Thread.sleep( delay.toMillis() + 10 );
			}
			catch( InterruptedException e ) {
				LOG.warn( "unexpected interruption", e );
				Thread.currentThread().interrupt();
			}
			delay = Duration.between( Instant.now(), duct.expiry() );
		}

		LOG.info( "Expiry breached by {}, shutting down duct", delay.abs() );
		duct.stop();
	}
}
