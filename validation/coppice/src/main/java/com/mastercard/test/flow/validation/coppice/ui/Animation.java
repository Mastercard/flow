/**
 * Copyright (c) 2021 Mastercard. All rights reserved.
 */

package com.mastercard.test.flow.validation.coppice.ui;

/**
 * Graph algorithms are nice to watch, so let's slow them down a bit so humans
 * (and the graph layout) can keep up
 */
public class Animation {

	private static final boolean DISABLED = Boolean.getBoolean( "anim.disable" );

	/**
	 * How quickly flows are added to the actual hierarchy
	 */
	public static Animation ADDITION = new Animation( 20 );

	/**
	 * How many quickly potential parents are evaluated
	 */
	public static Animation SEARCH = new Animation( 50 );

	/**
	 * How quickly basis links are removed before optimisation
	 */
	public static Animation DISSOLVE = new Animation( 100 );

	/**
	 * How quickly basis links are added during optimisation
	 */
	public static Animation OPTIMISE = new Animation( 10 );

	/**
	 * Multiplying factor for the animation delays
	 */
	public static float scale = 1.0f;
	private long lastEvent = 0;
	private final long delay;

	private Animation( float targetRate ) {
		delay = (long) (1000 / targetRate);
	}

	/**
	 * This method will block until the minimum delay has been achieved
	 */
	public void event() {
		if( DISABLED ) {
			return;
		}

		long targetDelay = (long) (delay * scale);
		long delta = System.currentTimeMillis() - lastEvent;

		while( delta < targetDelay ) {
			try {
				Thread.sleep( targetDelay - delta );
			}
			catch( InterruptedException e ) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException( e );
			}
			delta = System.currentTimeMillis() - lastEvent;
		}

		lastEvent = System.currentTimeMillis();
	}
}
