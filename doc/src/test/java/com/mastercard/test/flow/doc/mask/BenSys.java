package com.mastercard.test.flow.doc.mask;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trivial example of a system with an unpredictable behaviour.
 */
class BenSys {

	private static final Pattern REQUEST = Pattern.compile(
			"Please roll ([0-9]+) (\\w+) dice for me" );
	private static final Map<String, Integer> DIE_SIZE;
	static {
		Map<String, Integer> m = new HashMap<>();
		m.put( "spiky", 4 );
		m.put( "normal", 6 );
		m.put( "large", 12 );
		m.put( "gygaxian", 20 );
		m.put( "smooth", 100 );
		DIE_SIZE = Collections.unmodifiableMap( m );
	}

	private final UnaryOperator<String> dice;

	/**
	 * @param dice die-rolling implementation
	 */
	public BenSys( UnaryOperator<String> dice ) {
		this.dice = dice;
	}

	/**
	 * @param input A request to roll some dice
	 * @return The total value of those dice
	 */
	public String getDiceResponse( String input ) {
		Matcher m = REQUEST.matcher( input );
		if( m.matches() && DIE_SIZE.containsKey( m.group( 2 ) ) ) {
			return "Those summed to "
					+ dice.apply( m.group( 1 ) + "d" + DIE_SIZE.get( m.group( 2 ) ) );
		}
		return "I'm sorry, I don't understand";
	}
}
