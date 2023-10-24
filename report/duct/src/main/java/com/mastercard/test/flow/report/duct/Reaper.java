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
				Thread.sleep( delay.toMillis() );
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
