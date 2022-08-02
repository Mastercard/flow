package com.mastercard.test.flow.example.app.histogram;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mastercard.test.flow.example.app.Histogram;

/**
 * Implementation of the {@link Histogram} service
 */
public class HistogramImp implements Histogram {

	private static final Logger LOG = LoggerFactory.getLogger( HistogramImp.class );

	@Override
	public Map<String, Integer> histogram( String text ) {
		LOG.info( "Counting characters in '{}'", text );
		return histogram( text, c -> true );
	}

	@Override
	public Map<String, Integer> histogram( String text, String characters ) {
		LOG.info( "Counting [{}] characters in '{}'", characters, text );
		Optional<Set<Character>> queried = Optional.ofNullable( characters )
				.map( s -> s.chars()
						.mapToObj( i -> (char) i )
						.collect( Collectors.toSet() ) );
		return histogram( text,
				ch -> queried
						.map( set -> set.contains( ch ) )
						.orElse( true ) );
	}

	private static Map<String, Integer> histogram( String text, Predicate<Character> test ) {
		Map<String, Integer> m = new TreeMap<>();
		Optional.ofNullable( text )
				.ifPresent( s -> s.chars()
						.mapToObj( i -> (char) i )
						.filter( test )
						.forEach( c -> m.compute( String.valueOf( c ),
								( k, v ) -> v == null ? 1 : v + 1 ) ) );
		return m;
	}
}
