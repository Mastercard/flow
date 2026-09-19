package com.mastercard.test.flow.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.report.ReportTestUtil.Served;

/**
 * Exercises sequential report reuse and failures through the browser fixture.
 */
@SuppressWarnings("static-method")
class ReportTestUtilTest {

	/**
	 * @throws Exception On report serving or cleanup failure
	 */
	@Test
	void servesSequentialReplacements() throws Exception {
		try( Served first = ReportTestUtil.serve( "helper-reuse", Mdl.BASIS );
				Served second = ReportTestUtil.serve( "helper-reuse", Mdl.CHILD ) ) {
			assertEquals( 1234567890123L, new Reader( URI.create( first.url() ) ).read().meta.timestamp );
			assertEquals( 1, new Reader( URI.create( second.url() ) ).read().entries.size() );
		}
	}

	/**
	 * Cleanup must not replace the population failure.
	 *
	 * @throws Exception On report serving or cleanup failure
	 */
	@Test
	void preservesPopulationFailure() throws Exception {
		IllegalStateException failure = new IllegalStateException( "population failed" );
		assertSame( failure, assertThrows( IllegalStateException.class,
				() -> ReportTestUtil.serve( "helper-failure", "model", writer -> {
					writer.with( Mdl.BASIS );
					throw failure;
				} ) ) );
		try( Served served = ReportTestUtil.serve( "helper-failure", Mdl.CHILD ) ) {
			assertEquals( 1234567890123L,
					new Reader( URI.create( served.url() ) ).read().meta.timestamp );
		}
	}
}
