package com.mastercard.test.flow.validation.check;

import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Metadata;
import com.mastercard.test.flow.Model;

/**
 * Exercises {@link TraceUniquenessCheck}
 */
class TraceUniquenessCheckTest extends AbstractValidationTest {

	/***/
	TraceUniquenessCheckTest() {
		super( new TraceUniquenessCheck(),
				"Trace uniqueness",
				"All flows in a model have a unique trace" );
	}

	/**
	 * No checks are performed on the empty model
	 */
	@Test
	void empty() {
		test( mdl() );
	}

	/**
	 * A single flow is checked against itself only
	 */
	@Test
	void single() {
		test( mdl( "single" ),
				"single : pass" );
	}

	/**
	 * One check per distinct trace
	 */
	@Test
	void pair() {
		test( mdl( "left", "right" ),
				"left : pass",
				"right : pass" );
	}

	/**
	 * Checks are linear in the number of flows
	 */
	@Test
	void triple() {
		test( mdl( "left", "middle", "right" ),
				"left : pass",
				"middle : pass",
				"right : pass" );
	}

	/**
	 * Flows with the same trace trigger a violation
	 */
	@Test
	void violation() {
		test( mdl( "shared", "shared" ),
				"  details: Shared trace\n"
						+ " expected: null\n"
						+ "   actual: null\n"
						+ "offenders: shared\n"
						+ "trace for shared" );
	}

	/**
	 * Shared traces are reported once, in the check for their first flow
	 */
	@Test
	void quad() {
		test( mdl( "left", "middle", "right", "middle" ),
				"left : pass",
				"  details: Shared trace\n"
						+ " expected: null\n"
						+ "   actual: null\n"
						+ "offenders: middle\n"
						+ "trace for middle",
				"right : pass" );
	}

	private static Model mdl( String... ids ) {
		Model mdl = Mockito.mock( Model.class );
		Mockito.when( mdl.flows() )
				.thenReturn( Stream.of( ids )
						.map( id -> {
							Metadata meta = Mockito.mock( Metadata.class );
							when( meta.id() ).thenReturn( id );
							when( meta.trace() ).thenReturn( "trace for " + id );
							Flow flw = Mockito.mock( Flow.class );
							when( flw.meta() ).thenReturn( meta );
							return flw;
						} ) );

		return mdl;
	}
}
