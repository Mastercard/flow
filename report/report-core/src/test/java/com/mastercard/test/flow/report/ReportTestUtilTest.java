package com.mastercard.test.flow.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.report.ReportTestUtil.Served;

/**
 * Exercises ownership of reports created by the browser test fixture.
 */
@SuppressWarnings("static-method")
class ReportTestUtilTest {

	@Test
	void releasesWriterBeforeServing() throws Exception {
		try( Served first = ReportTestUtil.serve( "helper-reuse", Mdl.BASIS );
				Served second = ReportTestUtil.serve( "helper-reuse", Mdl.CHILD ) ) {
			assertEquals( 1234567890123L, new Reader( URI.create( first.url() ) ).read().meta.timestamp );
			assertEquals( 1, new Reader( URI.create( second.url() ) ).read().entries.size() );
		}
	}

	@Test
	void releasesWriterWhenPopulationFails() throws Exception {
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
