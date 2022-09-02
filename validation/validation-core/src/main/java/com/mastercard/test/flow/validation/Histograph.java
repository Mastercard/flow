package com.mastercard.test.flow.validation;

import static java.util.stream.Collectors.joining;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Produces text-formatted histogram plots.
 */
/*
 * I'd have liked to do something with the Block Elements characters, but font
 * support for those seems to be patchy
 */
public class Histograph {

	private final int minimum;
	private final int maximum;
	private final int height;

	/**
	 * @param minimum The minimum value to be plotted
	 * @param maximum The maximum value to be plotted
	 * @param height  The number of plot lines
	 */
	protected Histograph( int minimum, int maximum, int height ) {
		this.minimum = minimum;
		this.maximum = maximum;
		this.height = height;
	}

	/**
	 * @param values A map from value to value counts
	 * @return A textual representation of the distribution of the values
	 */
	public String graph( Map<Integer, Integer> values ) {
		List<String> lines = new ArrayList<>();
		int total = values.values().stream().mapToInt( Integer::intValue ).sum();
		if( total == 0 ) {
			total = 1; // avoid divide by zero
		}

		int step = (int) Math.max( 1, Math.ceil( (maximum - minimum) / (float) height ) );
		{
			int underCount = values.entrySet().stream().filter( e -> e.getKey() < minimum )
					.mapToInt( Entry::getValue ).sum();
			if( underCount > 0 ) {
				lines.add( String.format( "<%8d  %5.2f%%", underCount, 100.0 * underCount / total ) );
			}
		}

		for( int i = 1; i <= height; i++ ) {
			int prev = (i - 1) * step + minimum;
			int curr = i * step + minimum;
			int count = 0;
			for( int j = prev; j < curr; j++ ) {
				count += values.getOrDefault( j, 0 );
			}
			lines.add( String.format( " %8d  %5.2f%%", count, 100.0 * count / total ) );
		}

		{
			int overCount = values.entrySet().stream().filter( e -> e.getKey() > maximum )
					.mapToInt( Entry::getValue ).sum();
			if( overCount > 0 ) {
				lines.add( String.format( ">%8d  %5.2f%%", overCount, 100.0 * overCount / total ) );
			}
		}

		return lines.stream()
				.collect( joining( "\n" ) );
	}

	/**
	 * @return The minimum value to be plotted
	 */
	public int getMinimum() {
		return minimum;
	}

	/**
	 * @return The maximum value to be plotted
	 */
	public int getMaximum() {
		return maximum;
	}
}
