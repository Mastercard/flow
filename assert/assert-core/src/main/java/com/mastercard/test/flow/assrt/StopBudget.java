package com.mastercard.test.flow.assrt;

import java.time.Duration;
import java.util.Objects;

/**
 * Internal passive policy shared by serial and native admission owners. The
 * caller supplies monotonic samples and guards all access with its own monitor;
 * this policy neither waits nor decides whether ownership is safe to release.
 */
public final class StopBudget {
	private long nanos = Duration.ofSeconds( 30 ).toNanos();
	private boolean started;
	private boolean finished;
	private long origin;
	private long elapsed;

	/** @param duration Finite positive duration, representable in nanoseconds */
	public void configure( Duration duration ) {
		Objects.requireNonNull( duration );
		long value = duration.toNanos();
		if( value <= 0 )
			throw new IllegalArgumentException( "Stop budget must be positive" );
		if( started || finished )
			throw new IllegalStateException( "Stop budget is fixed" );
		nanos = value;
	}

	/** @param now First owner-observed Stop, sampled before outside-lock effects */
	public void start( long now ) {
		if( !started && !finished ) {
			started = true;
			origin = now;
		}
	}

	/** @return Whether a clock sample is needed; healthy runs never sample */
	public boolean open() {
		return started && !finished;
	}

	/**
	 * @param now Current monotonic sample; subtraction supports signed-origin wrap
	 * @return Whether the owner observed a missed budget
	 */
	public boolean observe( long now ) {
		elapsed = now - origin;
		return elapsed >= nanos;
	}

	/** @return Remaining nanoseconds after the owner's latest observation */
	public long remaining() {
		return elapsed >= nanos ? 0 : nanos - elapsed;
	}

	/**
	 * Seals only after the owner has checked time and completed required cleanup.
	 */
	public void finish() {
		finished = true;
	}

	/** @return Configured duration, without reading a clock */
	public Duration duration() {
		return Duration.ofNanos( nanos );
	}

	/** @return Elapsed time at the last owner observation, not historical proof */
	public Duration elapsed() {
		return Duration.ofNanos( elapsed );
	}
}
