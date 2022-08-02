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
	 * The validation compares flows, so no checks when there is only a single flow
	 */
	@Test
	void single() {
		test( mdl( "single" ) );
	}

	/**
	 * A pair of flows means 1 check
	 */
	@Test
	void pair() {
		test( mdl( "left", "right" ),
				"left x right : pass" );
	}

	/**
	 * A triple of flows means 3 checks
	 */
	@Test
	void triple() {
		test( mdl( "left", "middle", "right" ),
				"left x middle : pass",
				"left x right : pass",
				"middle x right : pass" );
	}

	/**
	 * Flows with the same id trigger a violation
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
	 * A quad of flows means 6 checks
	 */
	@Test
	void quad() {
		test( mdl( "left", "middle", "right", "middle" ),
				"left x middle : pass",
				"left x right : pass",
				"left x middle : pass",
				"middle x right : pass",
				"  details: Shared trace\n"
						+ " expected: null\n"
						+ "   actual: null\n"
						+ "offenders: middle\n"
						+ "trace for middle",
				"right x middle : pass" );
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
