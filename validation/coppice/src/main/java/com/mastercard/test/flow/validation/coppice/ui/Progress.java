/**
 * Copyright (c) 2020 Mastercard. All rights reserved.
 */

package com.mastercard.test.flow.validation.coppice.ui;

import java.util.HashMap;
import java.util.Map;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * A stack of progress bars to track multiple tasks
 */
public class Progress extends Box implements ProgressSink {

	private static final long serialVersionUID = 1L;
	private Map<String, JProgressBar> bars = new HashMap<>();

	/***/
	public Progress() {
		super( BoxLayout.Y_AXIS );
		setName( "progress" );
	}

	@Override
	public void update( String name, String stage, int current, int total ) {
		SwingUtilities.invokeLater( () -> {
			// get or build the progress bar for this task
			JProgressBar bar = bars.computeIfAbsent( name, n -> {
				JProgressBar b = new JProgressBar();
				b.setStringPainted( true );
				add( b );
				revalidate();
				return b;
			} );

			// update it with the supplied progress data
			bar.setString( name + " - " + stage );
			if( current >= 0 ) {
				bar.setIndeterminate( false );
				bar.setValue( current );
				bar.setMaximum( total );
			}
			else {
				bar.setIndeterminate( true );
			}

			// if the task is done, remove the bar in three seconds' time
			if( current == total ) {
				Timer t = new Timer( 3000, e -> {
					JProgressBar b = bars.remove( name );
					if( b != null ) {
						remove( b );
						revalidate();
					}
				} );
				t.setRepeats( false );
				t.start();
			}
		} );
	}
}
