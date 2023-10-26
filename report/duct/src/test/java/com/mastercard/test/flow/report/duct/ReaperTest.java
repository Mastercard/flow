package com.mastercard.test.flow.report.duct;

import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * You might be a king or a little street sweeper, but sooner or later you dance
 * with the {@link Reaper}
 */
@SuppressWarnings("static-method")
class ReaperTest {

	/**
	 * Instant reaping - the expiry has already been exceeded
	 *
	 * @throws InterruptedException unexpected
	 */
	@Test
	void pastDue() throws InterruptedException {
		Duct duct = Mockito.mock( Duct.class );
		when( duct.expiry() ).thenReturn( Instant.now().minus( 1, SECONDS ) );

		Reaper reaper = new Reaper( duct );

		reaper.start();
		reaper.join();

		// we accessed the expiry once
		Mockito.verify( duct ).expiry();
		// and immediately shut things down
		Mockito.verify( duct ).stop();
		// and that's it!
		Mockito.verifyNoMoreInteractions( duct );
	}

	/**
	 * Exercises the case where the expiry is in the future but is not extended
	 *
	 * @throws InterruptedException unexpected
	 */
	@Test
	void thisTime() throws InterruptedException {
		new ReaperHarness()
				.withExpiries( 10, 10 )
				.withClocks( 0, 11 )
				.expect( ""
						+ "clock checked, it's  1970-01-01T00:00:00Z\n"
						+ "expiry checked, it's 1970-01-01T00:00:10Z\n"
						+ "Sleeping for PT10S\n"
						+ "clock checked, it's  1970-01-01T00:00:11Z\n"
						+ "expiry checked, it's 1970-01-01T00:00:10Z\n"
						+ "stopped!" );
	}

	/**
	 * Exercises the case where the expiry is extended once
	 *
	 * @throws InterruptedException unexpected
	 */
	@Test
	void nextTime() throws InterruptedException {
		new ReaperHarness()
				.withExpiries( 10, 20, 20 )
				.withClocks( 0, 11, 21 )
				.expect( ""
						+ "clock checked, it's  1970-01-01T00:00:00Z\n"
						+ "expiry checked, it's 1970-01-01T00:00:10Z\n"
						+ "Sleeping for PT10S\n"
						+ "clock checked, it's  1970-01-01T00:00:11Z\n"
						+ "expiry checked, it's 1970-01-01T00:00:20Z\n"
						+ "Sleeping for PT9S\n"
						+ "clock checked, it's  1970-01-01T00:00:21Z\n"
						+ "expiry checked, it's 1970-01-01T00:00:20Z\n"
						+ "stopped!" );
	}

	/**
	 * Exercises the case where the expiry is extended twice
	 *
	 * @throws InterruptedException unexpected
	 */
	@Test
	void theTimeAfterThat() throws InterruptedException {
		new ReaperHarness()
				.withExpiries( 10, 20, 30, 30 )
				.withClocks( 0, 11, 21, 31 )
				.expect( ""
						+ "clock checked, it's  1970-01-01T00:00:00Z\n"
						+ "expiry checked, it's 1970-01-01T00:00:10Z\n"
						+ "Sleeping for PT10S\n"
						+ "clock checked, it's  1970-01-01T00:00:11Z\n"
						+ "expiry checked, it's 1970-01-01T00:00:20Z\n"
						+ "Sleeping for PT9S\n"
						+ "clock checked, it's  1970-01-01T00:00:21Z\n"
						+ "expiry checked, it's 1970-01-01T00:00:30Z\n"
						+ "Sleeping for PT9S\n"
						+ "clock checked, it's  1970-01-01T00:00:31Z\n"
						+ "expiry checked, it's 1970-01-01T00:00:30Z\n"
						+ "stopped!" );
	}

	/**
	 * Checks that our default clock implementations behave themselves
	 *
	 * @throws InterruptedException unexpected
	 */
	@Test
	void realSleep() throws InterruptedException {

		Duct duct = Mockito.mock( Duct.class );

		Instant start = Instant.now();

		when( duct.expiry() )
				.thenReturn( start.plus( 1, SECONDS ) );

		Reaper reaper = new Reaper( duct );
		reaper.start();
		reaper.join();

		Instant end = Instant.now();

		Mockito.verify( duct ).stop();

		long actualSleep = Duration.between( start, end ).toMillis();

		assertTrue( 1000 < actualSleep && actualSleep < 2000,
				"sleep duration should be longer than 1 second and shorter than 2, but it was "
						+ actualSleep + "ms" );
	}

	/**
	 * Convenient way to exercise the {@link Reaper}
	 */
	static class ReaperHarness {
		private static final Instant START = Instant.EPOCH;

		private final List<String> log = new ArrayList<>();
		private final Deque<Instant> expiries = new ArrayDeque<>();
		private final Deque<Instant> clocks = new ArrayDeque<>();

		/**
		 * Sets the expiry values that will be returned from successive calls to
		 * {@link Duct#expiry()}
		 *
		 * @param delays A sequence of seconds past the epoch
		 * @return <code>this</code>
		 */
		ReaperHarness withExpiries( int... delays ) {
			for( int d : delays ) {
				expiries.add( START.plus( d, SECONDS ) );
			}
			return this;
		}

		/**
		 * Sets the values that will be returned from successive calls to the
		 * {@link Reaper}'s clock
		 *
		 * @param times A sequence of seconds past the epoch
		 * @return <code>this</code>
		 */
		ReaperHarness withClocks( int... times ) {
			for( int t : times ) {
				clocks.add( START.plus( t, SECONDS ) );
			}
			return this;
		}

		/**
		 * Invokes the reaper and then asserts on the activity
		 *
		 * @param events expected event log
		 */
		void expect( String events ) {
			int expiryCount = expiries.size();

			Duct duct = Mockito.mock( Duct.class );
			Mockito.when( duct.expiry() )
					.thenAnswer( e -> {
						log.add( "expiry checked, it's " + expiries.getFirst() );
						return expiries.removeFirst();
					} );
			Mockito.doAnswer( e -> log.add( "stopped!" ) ).when( duct ).stop();

			Reaper reaper = new Reaper( duct )
					.withClock(
							() -> {
								log.add( "clock checked, it's  " + clocks.getFirst() );
								return clocks.removeFirst();
							},
							d -> log.add( "Sleeping for " + d ) );

			reaper.start();
			try {
				reaper.join();
			}
			catch( InterruptedException e ) {
				throw new IllegalStateException( e );
			}

			assertEquals( "[]", expiries.toString(), "unexpended expiries" );
			assertEquals( "[]", clocks.toString(), "unexpended clocks" );
			assertEquals( events, log.stream().collect( joining( "\n" ) ) );

			Mockito.verify( duct, times( expiryCount ) ).expiry();
			Mockito.verify( duct, times( 1 ) ).stop();
			Mockito.verifyNoMoreInteractions( duct );
		}
	}
}
