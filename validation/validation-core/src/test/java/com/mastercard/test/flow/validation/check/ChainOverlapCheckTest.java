package com.mastercard.test.flow.validation.check;

import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Metadata;
import com.mastercard.test.flow.Model;

/**
 * Exercises {@link ChainOverlapCheck}
 */
class ChainOverlapCheckTest extends AbstractValidationTest {

	/***/
	ChainOverlapCheckTest() {
		super( new ChainOverlapCheck(),
				"Chain overlap",
				"A flow should only exist in a maximum of one execution chain" );
	}

	/**
	 * No checks on the empty model
	 */
	@Test
	void empty() {
		test( mdl() );
	}

	/**
	 * Flows with valid tags
	 */
	@Test
	void valid() {
		test( mdl(
				"no_tags|",
				"some_tags|foo,bar,baz",
				"one_chain|Chain:foo",
				"case_matters|ChAiN:foo,chain:barmCHAIN:baz" ),
				"no_tags [] : pass",
				"some_tags [bar, foo, baz] : pass",
				"one_chain [Chain:foo] : pass",
				"case_matters [ChAiN:foo, chain:barmCHAIN:baz] : pass" );
	}

	/**
	 * Flows that fail validation
	 */
	@Test
	void invalid() {
		test( mdl( "two|chain:foo,chain:bar", "three|chain:foo,chain:bar,chain:baz" ),
				"  details: Overlapping chains [chain:bar, chain:foo]\n"
						+ " expected: null\n"
						+ "   actual: null\n"
						+ "offenders: two [chain:foo, chain:bar]\n"
						+ "trace for two|chain:foo,chain:bar",
				"  details: Overlapping chains [chain:bar, chain:baz, chain:foo]\n"
						+ " expected: null\n"
						+ "   actual: null\n"
						+ "offenders: three [chain:baz, chain:foo, chain:bar]\n"
						+ "trace for three|chain:foo,chain:bar,chain:baz" );
	}

	private static Model mdl( String... ids ) {
		Model mdl = Mockito.mock( Model.class );
		Mockito.when( mdl.flows() )
				.thenReturn( Stream.of( ids )
						.map( id -> {
							String desc = id.substring( 0, id.indexOf( "|" ) );
							Set<String> tags = Stream.of( id.substring( id.indexOf( "|" ) + 1 ).split( "," ) )
									.collect( Collectors.toSet() );
							Metadata meta = Mockito.mock( Metadata.class );
							when( meta.description() ).thenReturn( desc );
							when( meta.tags() ).thenReturn( tags );
							when( meta.id() ).thenCallRealMethod();
							when( meta.trace() ).thenReturn( "trace for " + id );
							Flow flw = Mockito.mock( Flow.class );
							when( flw.meta() ).thenReturn( meta );
							return flw;
						} ) );

		return mdl;
	}
}
