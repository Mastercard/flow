package com.mastercard.test.flow;

import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Exercises the default method of {@link Metadata}
 */
@SuppressWarnings("static-method")
class MetadataTest {

	/**
	 * Demonstrates how the id is formed
	 */
	@Test
	void id() {
		Metadata md = Mockito.mock( Metadata.class );
		Mockito.when( md.description() ).thenReturn( "short description" );
		Mockito.when( md.tags() ).thenReturn( Stream.of( "meaningful", "text", "tags" )
				.collect( Collectors.toCollection( TreeSet::new ) ) );
		Mockito.when( md.id() ).thenCallRealMethod();

		Assertions.assertEquals( "short description [meaningful, tags, text]", md.id() );
	}
}
