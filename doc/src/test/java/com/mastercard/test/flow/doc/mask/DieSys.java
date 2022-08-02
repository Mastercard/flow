package com.mastercard.test.flow.doc.mask;

import java.util.Random;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dice-rolling system
 */
class DieSys implements UnaryOperator<String> {

	private static final Pattern DICE_DEF = Pattern.compile( "([0-9]+)d([0-9]+)" );

	/**
	 * @param dice A string describing the count and order of the dice
	 * @return The summed result of rolling the specified dice, or <code>null</code>
	 *         if a bad dice definition was supplied
	 */
	@Override
	public String apply( String dice ) {
		if( dice != null ) {
			Matcher m = DICE_DEF.matcher( dice );
			if( m.matches() ) {
				return String.valueOf( roll(
						Integer.parseInt( m.group( 1 ) ),
						Integer.parseInt( m.group( 2 ) ) ) );
			}
		}
		return null;
	}

	private static int roll( int count, int max ) {
		Random rng = new Random();
		int sum = 0;
		for( int i = 0; i < count; i++ ) {
			sum += rng.nextInt( max ) + 1;
		}
		return sum;
	}
}
