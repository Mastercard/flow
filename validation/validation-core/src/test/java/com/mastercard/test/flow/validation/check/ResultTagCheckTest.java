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
 * Exercises {@link ResultTagCheck}
 */
class ResultTagCheckTest extends AbstractValidationTest {

	/***/
	ResultTagCheckTest() {
		super( new ResultTagCheck(),
				"Result tag misuse",
				"Certain tag values are assumed by the report components to signal the outcome of testing a flow."
						+ " These values are reserved for that purpose" );
	}

	/**
	 * No flows, so no checks
	 */
	@Test
	void empty() {
		test( mdl() );
	}

	/**
	 * Valid flows
	 */
	@Test
	void valid() {
		test( mdl(
				"flow:tag,gta,atg",
				"lc_pass:pass",
				"lc_fail:fail",
				"lc_skip:skip",
				"lc_error:error" ),
				"flow [gta, atg, tag] : pass",
				"lc_pass [pass] : pass",
				"lc_fail [fail] : pass",
				"lc_skip [skip] : pass",
				"lc_error [error] : pass" );
	}

	/**
	 * Invalid flows
	 */
	@Test
	void invalid() {
		test( mdl(
				"uc_pass:PASS",
				"uc_fail:FAIL",
				"uc_skip:SKIP",
				"uc_error:ERROR",
				"multiple:PASS,FAIL,SKIP,ERROR" ),
				"  details: Use of reserved tags: PASS\n"
						+ " expected: null\n"
						+ "   actual: null\n"
						+ "offenders: uc_pass [PASS]\n"
						+ "trace for uc_pass:PASS",
				"  details: Use of reserved tags: FAIL\n"
						+ " expected: null\n"
						+ "   actual: null\n"
						+ "offenders: uc_fail [FAIL]\n"
						+ "trace for uc_fail:FAIL",
				"  details: Use of reserved tags: SKIP\n"
						+ " expected: null\n"
						+ "   actual: null\n"
						+ "offenders: uc_skip [SKIP]\n"
						+ "trace for uc_skip:SKIP",
				"  details: Use of reserved tags: ERROR\n"
						+ " expected: null\n"
						+ "   actual: null\n"
						+ "offenders: uc_error [ERROR]\n"
						+ "trace for uc_error:ERROR",
				"  details: Use of reserved tags: ERROR, FAIL, PASS, SKIP\n"
						+ " expected: null\n"
						+ "   actual: null\n"
						+ "offenders: multiple [PASS, SKIP, ERROR, FAIL]\n"
						+ "trace for multiple:PASS,FAIL,SKIP,ERROR" );
	}

	private static Model mdl( String... ids ) {
		Model mdl = Mockito.mock( Model.class );
		Mockito.when( mdl.flows() )
				.thenReturn( Stream.of( ids )
						.map( id -> {
							String desc = id.substring( 0, id.indexOf( ":" ) );
							Set<String> tags = Stream.of( id.substring( id.indexOf( ":" ) + 1 ).split( "," ) )
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
