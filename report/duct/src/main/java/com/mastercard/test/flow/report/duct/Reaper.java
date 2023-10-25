package com.mastercard.test.flow.report.duct;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Thread} that monitors {@link Duct#expiry()}. When the expiry has
 * been reached the {@link Duct} is shut down
 */
class Reaper extends Thread {
	private static final Logger LOG = LoggerFactory.getLogger( Reaper.class );

	private final Duct duct;

	private Supplier<Instant> now = Instant::now;

	private Consumer<Duration> sleep = delay -> {
		try {
			LOG.info( "Sleeping for a bit over" + delay );
			// if we sleep for *exactly* the time left then we get a busy-loop of
			// zero-duration sleeps right before dying. Allowing 10ms grace for another
			// heartbeat to come in avoids pointless log noise
			Thread.sleep( delay.toMillis() + 10 );
		}
		catch( InterruptedException e ) {
			LOG.warn( "unexpected interruption", e );
			Thread.currentThread().interrupt();
		}
	};

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
		Duration delay = Duration.between( now.get(), duct.expiry() );
		while( !delay.isNegative() ) {
			sleep.accept( delay );
			delay = Duration.between( now.get(), duct.expiry() );
		}

		LOG.info( "Expiry breached by {}, shutting down duct", delay.abs() );
		duct.stop();
	}

	/**
	 * For use in unit tests, overrides the default wall-clock behaviours
	 *
	 * @param n How to find out what time it is
	 * @param s How to wait for a defined duration
	 * @return <code>this</code>
	 */
	Reaper withClock( Supplier<Instant> n, Consumer<Duration> s ) {
		now = n;
		sleep = s;
		return this;
	}
}
