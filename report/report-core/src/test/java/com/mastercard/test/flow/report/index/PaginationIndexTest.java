package com.mastercard.test.flow.report.index;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.mastercard.test.flow.report.Mdl;
import com.mastercard.test.flow.report.ReportTestUtil;
import com.mastercard.test.flow.report.ReportTestUtil.Served;
import com.mastercard.test.flow.report.seq.Browser;
import com.mastercard.test.flow.report.seq.IndexSequence;

/** Exercises pagination with a generated Index larger than every page size. */
@ExtendWith(Browser.class)
class PaginationIndexTest {

	private static Served report;
	private final IndexSequence iseq;

	@BeforeAll
	static void setup() throws Exception {
		report = ReportTestUtil.serveLargeIndex( "PaginationIndexTest", Mdl.BASIS, 205 );
	}

	PaginationIndexTest() {
		iseq = new IndexSequence( report.url() );
	}

	@BeforeEach
	void resetView() {
		iseq.index();
	}

	@AfterAll
	static void teardown() {
		report.close();
	}

	@Test
	void navigationAndPageSizes() {
		assertSame( iseq, iseq.hasFlowCount( 50 )
				.hasFlowRange( "1 – 50 of 205" )
				.hasUrlArgs()
				.page( "Next" )
				.hasFlowRange( "51 – 100 of 205" )
				.page( "Last" )
				.hasFlowCount( 5 )
				.hasFlowRange( "201 – 205 of 205" )
				.page( "Previous" )
				.hasFlowRange( "151 – 200 of 205" )
				.page( "First" )
				.pageSize( 100 )
				.hasFlowRange( "1 – 100 of 205" )
				.index()
				.pageSize( 200 )
				.hasFlowRange( "1 – 200 of 205" )
				.hasNoDirectPageControls() );
	}

	@Test
	void ignoresRemovedPaginationQueryParameters() {
		assertSame( iseq, iseq.index( "pg=3", "psz=50" )
				.hasFlowCount( 50 )
				.hasFlowRange( "1 – 50 of 205" )
				.hasUrlArgs() );
	}

	@Test
	void filterResetAndNoResults() {
		assertSame( iseq, iseq.expandFilters()
				.descriptionFilter( "1" )
				.hasFlowRange( "1 – 50 of 120" )
				.hasUrlArgs( "dsc=1" )
				.descriptionFilter( " absent" )
				.hasNoFlows()
				.hasUrlArgs( "dsc=1+absent" ) );
	}

	@Test
	void directFileNavigation() {
		IndexSequence fileSequence = new IndexSequence( report.fileUrl() );
		assertSame( fileSequence, fileSequence
				.index()
				.hasFlowCount( 50 )
				.hasFlowRange( "1 – 50 of 205" )
				.page( "Next" )
				.hasFlowRange( "51 – 100 of 205" ) );
	}
}
