package com.mastercard.test.flow.report.index;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.report.Mdl;
import com.mastercard.test.flow.report.ReportTestUtil;
import com.mastercard.test.flow.report.ReportTestUtil.Served;
import com.mastercard.test.flow.report.seq.IndexSequence;

/**
 * Superclass for the tests that exercise the report index. Functionality that
 * is available when browsing the file system <i>and<i> via http is exercised
 * here
 */
abstract class AbstractIndexTest {

	/**
	 * The report to test
	 */
	protected static Served report;

	/**
	 * The sequence to use to drive testing
	 */
	protected final IndexSequence iseq;

	/**
	 * Create the report, serves it, builds a browser with which to test
	 *
	 * @throws Exception if something fails
	 */
	@BeforeAll
	static void setup() throws Exception {
		report = ReportTestUtil.serve( "IndexTest", "Model title", w -> w
				.with( Mdl.BASIS, f -> f.tags.add( "PASS" ) )
				.with( Mdl.CHILD, f -> f.tags.add( "FAIL" ) )
				.with( Mdl.DEPENDENCY, f -> f.tags.add( "SKIP" ) )
				.with( Mdl.DEPENDENT, f -> f.tags.add( "ERROR" ) ) );
	}

	/**
	 * @param url Where the report can be browsed
	 */
	protected AbstractIndexTest( String url ) {
		iseq = new IndexSequence( url );
	}

	/**
	 * Resets the browser to the default index view
	 */
	@BeforeEach
	void resetView() {
		iseq.stepping( false )
				.index();
	}

	/**
	 * Tears down the server
	 */
	@AfterAll
	static void teardown() {
		report.close();
	}

	/**
	 * Checks index title elements
	 */
	@Test
	void title() {
		iseq.hasTitle( "Model title | Test title @ 13/02/2009, 23:31:30" )
				.hasHeader(
						"Model title",
						"Test title",
						"13/02/2009, 23:31:30" );
	}

	/**
	 * Checks the list of all flows
	 */
	@Test
	void fullList() {
		iseq.hasFlows(
				"basis       [PASS, abc, def]",
				"child       [FAIL, abc, def, ghi]",
				"dependency  [SKIP, abc, ghi, jkl, mno]",
				"dependent   [ERROR, mno, pqr, stu]" )
				.expandTags()
				.hasTags( "11 tags on 4 flows _PASS_ 25.0% _FAIL_ 25.0% _SKIP_ 25.0% _ERROR_ 25.0%",
						"abc 75.0%",
						"def 50.0%",
						"ghi 50.0%",
						"mno 50.0%",
						"ERROR 25.0%",
						"FAIL 25.0%",
						"jkl 25.0%",
						"PASS 25.0%",
						"pqr 25.0%",
						"SKIP 25.0%",
						"stu 25.0%" )
				.toggleTagSort()
				.hasTags( "11 tags on 4 flows _PASS_ 25.0% _FAIL_ 25.0% _SKIP_ 25.0% _ERROR_ 25.0%",
						"ERROR 25.0%",
						"FAIL 25.0%",
						"PASS 25.0%",
						"SKIP 25.0%",
						"abc 75.0%",
						"def 50.0%",
						"ghi 50.0%",
						"jkl 25.0%",
						"mno 50.0%",
						"pqr 25.0%",
						"stu 25.0%" );
	}

	/**
	 * Demonstrates filtering by description text
	 */
	@Test
	void descriptionFilter() {
		// filter by typing in the text box
		iseq.expandFilters()
				.descriptionFilter( "n" )
				.hasUrlArgs( "dsc=n" )
				.hasFilters( "n", "", "" )
				.hasFlows(
						"dependency  [SKIP, abc, ghi, jkl, mno]",
						"dependent   [ERROR, mno, pqr, stu]" )
				.expandTags()
				.hasTags( "8 tags on 2 flows _SKIP_ 50.0% _ERROR_ 50.0%",
						"mno 100.0%",
						"abc 50.0%",
						"ERROR 50.0%",
						"ghi 50.0%",
						"jkl 50.0%",
						"pqr 50.0%",
						"SKIP 50.0%",
						"stu 50.0%" )
				.descriptionFilter( "t" )
				.hasUrlArgs( "dsc=nt" )
				.hasFlows(
						"dependent  [ERROR, mno, pqr, stu]" )
				.hasTags( "4 tags on 1 flow _ERROR_ 100.0%",
						"ERROR 100.0%",
						"mno 100.0%",
						"pqr 100.0%",
						"stu 100.0%" );

		// or by direct navigation
		iseq.index( "dsc=c" )
				.hasFilters( "c", "", "" )
				.hasFlows(
						"child       [FAIL, abc, def, ghi]",
						"dependency  [SKIP, abc, ghi, jkl, mno]" );
	}

	/**
	 * Demonstrates tag inclusion filtering. Note that filtered tags are no longer
	 * displayed in the index
	 */
	@Test
	void tagInclusion() {
		// tag inclusion filter can be populated by clicking on the tags
		iseq.clickTag( "def" )
				.hasUrlArgs( "inc=def" )
				.hasFilters( "", "def", "" )
				.hasFlows(
						"basis  [PASS, abc]",
						"child  [FAIL, abc, ghi]" )
				.expandTags()
				.hasTags( "4 tags on 2 flows _PASS_ 50.0% _FAIL_ 50.0%",
						"abc 100.0%",
						"FAIL 50.0%",
						"ghi 50.0%",
						"PASS 50.0%" )
				.clickTag( "ghi" )
				.hasUrlArgs( "inc=def", "inc=ghi" )
				.hasFilters( "", "def, ghi", "" )
				.hasFlows(
						"child  [FAIL, abc]" )
				.hasTags( "2 tags on 1 flow _FAIL_ 100.0%",
						"abc 100.0%",
						"FAIL 100.0%" );

		// or by direct navigation
		iseq.index( "inc=mno" )
				.hasFilters( "", "mno", "" )
				.hasFlows(
						"dependency  [SKIP, abc, ghi, jkl]",
						"dependent   [ERROR, pqr, stu]" );
	}

	/**
	 * Demonstrates tag exclusion filtering
	 */
	@Test
	void tagExclusion() {
		// tag exclusion filter can be populated by clicking on the tags and dragging
		// them to the exclude box
		iseq.clickTag( "abc" )
				.hasUrlArgs( "inc=abc" )
				.hasFilters( "", "abc", "" )
				.hasFlows(
						"basis       [PASS, def]",
						"child       [FAIL, def, ghi]",
						"dependency  [SKIP, ghi, jkl, mno]" )
				.expandTags()
				.hasTags(
						"7 tags on 3 flows _PASS_ 33.3% _FAIL_ 33.3% _SKIP_ 33.3%",
						"def 66.7%",
						"ghi 66.7%",
						"FAIL 33.3%",
						"jkl 33.3%",
						"mno 33.3%",
						"PASS 33.3%",
						"SKIP 33.3%" )
				.expandFilters()
				.dragToExclude( "abc" )
				.hasUrlArgs( "exc=abc" )
				.hasFilters( "", "", "abc" )
				.hasFlows(
						"dependent  [ERROR, mno, pqr, stu]" )
				.hasTags( "4 tags on 1 flow _ERROR_ 100.0%",
						"ERROR 100.0%",
						"mno 100.0%",
						"pqr 100.0%",
						"stu 100.0%" );

		// or by direct navigation
		iseq.index( "exc=stu" )
				.hasFilters( "", "", "stu" )
				.hasFlows(
						"basis       [PASS, abc, def]",
						"child       [FAIL, abc, def, ghi]",
						"dependency  [SKIP, abc, ghi, jkl, mno]" );
	}

	/**
	 * Demonstrates all filters working together
	 */
	@Test
	void combined() {
		iseq.clickTag( "abc" )
				.hasFlows(
						"basis       [PASS, def]",
						"child       [FAIL, def, ghi]",
						"dependency  [SKIP, ghi, jkl, mno]" )
				.clickTag( "mno" )
				.hasFlows(
						"dependency  [SKIP, ghi, jkl]" )
				.expandFilters()
				.dragToExclude( "mno" )
				.hasFlows(
						"basis  [PASS, def]",
						"child  [FAIL, def, ghi]" )
				.descriptionFilter( "c" )
				.hasUrlArgs( "dsc=c", "exc=mno", "inc=abc" )
				.hasFilters( "c", "abc", "mno" )
				.hasFlows( "child  [FAIL, def, ghi]" )
				.expandTags()
				.hasTags( "3 tags on 1 flow _FAIL_ 100.0%",
						"def 100.0%",
						"FAIL 100.0%",
						"ghi 100.0%" );
	}
}
