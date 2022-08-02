package com.mastercard.test.flow.validation.coppice.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;

/**
 * Controls a range value, where the minimum and maximum can vary from 0 to 1
 */
public class Range {

	private final String name;
	private float minimum = 0;
	private float maximum = 1;

	private List<Consumer<Range>> listeners = new ArrayList<>();

	/**
	 * @param name border text
	 */
	public Range( String name ) {
		this.name = name;
	}

	/**
	 * @return The component
	 */
	public JComponent controls() {
		Box panel = new Box( BoxLayout.Y_AXIS );
		panel.setBorder( new TitledBorder( name ) );
		JSlider min = new JSlider( SwingConstants.HORIZONTAL, 0, 100, 0 );
		JSlider max = new JSlider( SwingConstants.HORIZONTAL, 0, 100, 0 );
		max.setInverted( true );

		min.addChangeListener( e -> {
			minimum = ((float) min.getValue() - min.getMinimum()) / (min.getMaximum() - min.getMinimum());
			maximum = Math.max( minimum, maximum );
			max.setValue(
					max.getMinimum() + (int) ((1 - maximum) * (max.getMaximum() - max.getMinimum())) );
			listeners.forEach( l -> l.accept( this ) );
		} );
		max.addChangeListener( e -> {
			maximum = 1
					- ((float) max.getValue() - max.getMinimum()) / (max.getMaximum() - max.getMinimum());
			minimum = Math.min( minimum, maximum );
			min.setValue( min.getMinimum() + (int) (minimum * (min.getMaximum() - min.getMinimum())) );
			listeners.forEach( l -> l.accept( this ) );
		} );

		panel.add( min );
		panel.add( max );
		return panel;
	}

	/**
	 * @param l The thing that's interested in value changes
	 * @return <code>this</code>
	 */
	public Range withListener( Consumer<Range> l ) {
		listeners.add( l );
		l.accept( this );
		return this;
	}

	/**
	 * @return The minimum value
	 */
	public float minimum() {
		return minimum;
	}

	/**
	 * @return The maximum value
	 */
	public float maximum() {
		return maximum;
	}
}
