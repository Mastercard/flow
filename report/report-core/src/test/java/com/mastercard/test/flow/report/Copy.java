package com.mastercard.test.flow.report;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Expectation formatting utility
 */
public class Copy {

	/**
	 * @param content Some strings
	 * @return A string that can be trivially copy/pasted into java source
	 */
	public static String pasta( String... content ) {
		return pasta( Stream.of( content ) );
	}

	/**
	 * @param content Some strings
	 * @return A string that can be trivially copy/pasted into java source
	 */
	public static String pasta( Collection<String> content ) {
		return pasta( content.stream() );
	}

	/**
	 * @param content Some strings
	 * @return A string that can be trivially copy/pasted into java source
	 */
	public static String pasta( Stream<String> content ) {
		return content
				.map( s -> s.replaceAll( "\r", "" ) )
				.flatMap( s -> Stream.of( s.split( "\n" ) ) )
				.map( s -> s.replaceAll( "\"", "'" ) )
				.collect( Collectors.joining( "\",\n\"", "\"", "\"" ) );
	}
}
