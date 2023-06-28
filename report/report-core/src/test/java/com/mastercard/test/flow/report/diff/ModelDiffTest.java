package com.mastercard.test.flow.report.diff;

import static com.mastercard.test.flow.report.Mdl.Actrs.AVA;
import static com.mastercard.test.flow.report.Mdl.Actrs.BEN;
import static com.mastercard.test.flow.util.Tags.add;
import static com.mastercard.test.flow.util.Tags.remove;
import static com.mastercard.test.flow.util.Tags.set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.builder.Deriver;
import com.mastercard.test.flow.msg.txt.Text;
import com.mastercard.test.flow.report.ReportTestUtil;
import com.mastercard.test.flow.report.ReportTestUtil.Served;
import com.mastercard.test.flow.report.seq.Browser;
import com.mastercard.test.flow.report.seq.DiffSequence;
import com.mastercard.test.flow.report.seq.IndexSequence;

/**
 * Exercises the model diff tool
 */
@SuppressWarnings("static-method")
@ExtendWith(Browser.class)
class ModelDiffTest {

	private static Served from, to;
	private static DiffSequence dseq;
	private static String[] defaultArgs;

	/**
	 * Writes and serves two reports with some differences between them, then
	 * initialises the {@link DiffSequence}
	 *
	 * @throws Exception if something goes wrong
	 */
	@BeforeAll
	static void setup() throws Exception {
		Flow removed = Creator.build( flow -> flow
				.meta( data -> data
						.description( "removed" )
						.tags( set( "a", "b", "c" ) ) )
				.call( a -> a
						.from( AVA ).to( BEN )
						.request( new Text( "Hi Ben!" ) )
						.response( new Text( "Hello Ava!" ) ) ) );

		Flow constant = Deriver.build( removed, flow -> flow
				.meta( data -> data
						.description( "unchanged" )
						.tags( set( "b", "c", "d" ) ) ) );

		Flow before = Deriver.build( removed, flow -> flow
				.meta( data -> data
						.description( "updated" )
						.tags( set( "c", "d", "e", "PASS", "FAIL" ) ) ) );

		Flow after = Deriver.build( before, flow -> flow
				.meta( data -> data
						.tags( remove( "PASS", "FAIL" ), add( "SKIP", "ERROR" ) ) )
				.update( i -> true, i -> {
					i.request().set( "i", "iii" );
					i.request().set( "e", "eee" );
					i.response().set( "e", "eee" );
					i.response().set( "o", "ooo" );
					i.response().set( "a", "aaa" );
				} ) );

		Flow added = Deriver.build( removed, flow -> flow
				.meta( data -> data
						.description( "added" )
						.tags( set( "d", "e", "f" ) ) ) );

		from = ReportTestUtil.serve( "from", "before change", removed, constant, before );
		to = ReportTestUtil.serve( "to", "after change", constant, after, added );

		dseq = new IndexSequence( to.url() )
				.index()
				.diff()
				.toggleSourceExpansion()
				.from( from.url() )
				.to( to.url() )
				.awaitLoad();
		defaultArgs = dseq.getUrlArgs();
	}

	/**
	 * Resets the view
	 */
	@BeforeEach
	void resetView() {
		dseq.stepping( false )
				.diff( defaultArgs );
	}

	/**
	 * Stops serving the reports
	 */
	@AfterAll
	static void teardown() {
		from.close();
		to.close();
	}

	/**
	 * Exercises the link to the "from" index.
	 * <p>
	 * If this test is failing due to the format of the timestamps then the problem
	 * may be the browser locale configuration. Check comments in
	 * {@link Browser#get()} and the maven-surefire-plugin configuration in the pom
	 * file for more details on this issue. If you're running on linux then you'll
	 * need to set environment variable <code>LANG=en_GB</code>
	 * </p>
	 */
	@Test
	void fromLink() {
		dseq.fromIndex( "before change @ 13/02/2009, 23:31:30 | 3 flows" )
				.hasHeader( "before change", "Test title", "13/02/2009, 23:31:30" )
				.hasFlows(
						"removed    [a, b, c]",
						"unchanged  [b, c, d]",
						"updated    [FAIL, PASS, c, d, e]" );
	}

	/**
	 * Exercises the link to the "to" index.
	 * <p>
	 * If this test is failing due to the format of the timestamps then the problem
	 * may be the browser locale configuration. Check comments in
	 * {@link Browser#get()} and the maven-surefire-plugin configuration in the pom
	 * file for more details on this issue. If you're running on linux then you'll
	 * need to set environment variable <code>LANG=en_GB</code>
	 * </p>
	 */
	@Test
	void toLink() {
		dseq.toIndex( "after change @ 13/02/2009, 23:31:30 | 3 flows" )
				.hasHeader( "after change", "Test title", "13/02/2009, 23:31:30" )
				.hasFlows(
						"unchanged  [b, c, d]",
						"updated    [ERROR, SKIP, c, d, e]",
						"added      [d, e, f]" );
	}

	/**
	 * Asserts on the naturally-paired flows
	 */
	@Test
	void naturalPairs() {
		dseq.paired()
				.hasPairs(
						"unchanged b c d | unchanged b c d",
						"  updated c d e | updated c d e  " );
	}

	/**
	 * Asserts on the naturally-unpaired flows
	 */
	@Test
	void naturalUnpairs() {
		dseq.unpaired()
				.hasUnpairs(
						"removed a b c | added d e f  " );
	}

	/**
	 * Asserts on the naturally-found changes
	 */
	@Test
	void naturalChanges() {
		dseq.changes()
				.hasChangeList(
						"removed a b c",
						"added d e f",
						"updated c d e" );

		dseq.change( "removed a b c" )
				.hasDiff(
						" 1   - Identity:                ",
						" 2   -   removed                ",
						" 3   -   a                      ",
						" 4   -   b                      ",
						" 5   -   c                      ",
						" 6   - Motivation:              ",
						" 7   -                          ",
						" 8   - Context:                 ",
						" 9   -   {}                     ",
						"10   - Interactions:            ",
						"11   -   ┌REQUEST AVA => BEN [] ",
						"12   -   │Hi Ben!               ",
						"13   -   └                      ",
						"14   -   ┌RESPONSE AVA <= BEN []",
						"15   -   │Hello Ava!            ",
						"16   -   └                      " );

		dseq.change( "added d e f" )
				.hasDiff(
						"   1 + Identity:                ",
						"   2 +   added                  ",
						"   3 +   d                      ",
						"   4 +   e                      ",
						"   5 +   f                      ",
						"   6 + Motivation:              ",
						"   7 +                          ",
						"   8 + Context:                 ",
						"   9 +   {}                     ",
						"  10 + Interactions:            ",
						"  11 +   ┌REQUEST AVA => BEN [] ",
						"  12 +   │Hi Ben!               ",
						"  13 +   └                      ",
						"  14 +   ┌RESPONSE AVA <= BEN []",
						"  15 +   │Hello Ava!            ",
						"  16 +   └                      " );

		dseq.change( "updated c d e" )
				.hasDiff(
						" 1  1   Identity:                ",
						" 2  2     updated                ",
						" 3  3     c                      ",
						" 4  4     d                      ",
						"        3 unchanged lines        ",
						" 8  8   Context:                 ",
						" 9  9     {}                     ",
						"10 10   Interactions:            ",
						"11 11     ┌REQUEST AVA => BEN [] ",
						"12    -   │Hi Ben!               ",
						"   12 +   │Hiii Beeen!           ",
						"13 13     └                      ",
						"14 14     ┌RESPONSE AVA <= BEN []",
						"15    -   │Hello Ava!            ",
						"   15 +   │Heeellooo Avaaa!      ",
						"16 16     └                      " );

		dseq.hasUrlArgs(
				"ff=C38030BF0DCCF31DB770B2EAFA779DFC",
				"from=http%253A%252F%252Flocalhost%253A" + from.port() + "%252Ffrom%252F",
				"tab=2",
				"tf=C38030BF0DCCF31DB770B2EAFA779DFC",
				"to=http%253A%252F%252Flocalhost%253A" + to.port() + "%252Fto%252F" );

		// we can deeplink to a particular diff
		dseq.diff(
				"ff=C38030BF0DCCF31DB770B2EAFA779DFC",
				"from=http%253A%252F%252Flocalhost%253A" + from.port() + "%252Ffrom%252F",
				"tab=2",
				"tf=C38030BF0DCCF31DB770B2EAFA779DFC",
				"to=http%253A%252F%252Flocalhost%253A" + to.port() + "%252Fto%252F" )
				.hasDiff(
						" 1  1   Identity:                ",
						" 2  2     updated                ",
						" 3  3     c                      ",
						" 4  4     d                      ",
						"        3 unchanged lines        ",
						" 8  8   Context:                 ",
						" 9  9     {}                     ",
						"10 10   Interactions:            ",
						"11 11     ┌REQUEST AVA => BEN [] ",
						"12    -   │Hi Ben!               ",
						"   12 +   │Hiii Beeen!           ",
						"13 13     └                      ",
						"14 14     ┌RESPONSE AVA <= BEN []",
						"15    -   │Hello Ava!            ",
						"   15 +   │Heeellooo Avaaa!      ",
						"16 16     └                      " );
	}

	/**
	 * Shows that we can show more or less diff context by click buttons in the ui
	 */
	@Test
	void context() {
		dseq.diff(
				"ff=C38030BF0DCCF31DB770B2EAFA779DFC",
				"from=http%253A%252F%252Flocalhost%253A" + from.port() + "%252Ffrom%252F",
				"tab=2",
				"tf=C38030BF0DCCF31DB770B2EAFA779DFC",
				"to=http%253A%252F%252Flocalhost%253A" + to.port() + "%252Fto%252F" )
				.hasDiff(
						" 1  1   Identity:                ",
						" 2  2     updated                ",
						" 3  3     c                      ",
						" 4  4     d                      ",
						"        3 unchanged lines        ",
						" 8  8   Context:                 ",
						" 9  9     {}                     ",
						"10 10   Interactions:            ",
						"11 11     ┌REQUEST AVA => BEN [] ",
						"12    -   │Hi Ben!               ",
						"   12 +   │Hiii Beeen!           ",
						"13 13     └                      ",
						"14 14     ┌RESPONSE AVA <= BEN []",
						"15    -   │Hello Ava!            ",
						"   15 +   │Heeellooo Avaaa!      ",
						"16 16     └                      " );

		dseq.lessContext() // down to 2 lines of context
				.hasDiff(
						" 1  1   Identity:                ",
						" 2  2     updated                ",
						"        7 unchanged lines        ",
						"10 10   Interactions:            ",
						"11 11     ┌REQUEST AVA => BEN [] ",
						"12    -   │Hi Ben!               ",
						"   12 +   │Hiii Beeen!           ",
						"13 13     └                      ",
						"14 14     ┌RESPONSE AVA <= BEN []",
						"15    -   │Hello Ava!            ",
						"   15 +   │Heeellooo Avaaa!      ",
						"16 16     └                      " )
				.moreContext() // back up to 4
				.moreContext() // up to 8!
				.hasDiff(
						" 1  1   Identity:                ",
						" 2  2     updated                ",
						" 3  3     c                      ",
						" 4  4     d                      ",
						" 5  5     e                      ",
						" 6  6   Motivation:              ",
						" 7  7                            ",
						" 8  8   Context:                 ",
						" 9  9     {}                     ",
						"10 10   Interactions:            ",
						"11 11     ┌REQUEST AVA => BEN [] ",
						"12    -   │Hi Ben!               ",
						"   12 +   │Hiii Beeen!           ",
						"13 13     └                      ",
						"14 14     ┌RESPONSE AVA <= BEN []",
						"15    -   │Hello Ava!            ",
						"   15 +   │Heeellooo Avaaa!      ",
						"16 16     └                      " )
				.hasUrlArgs(
						"cl=8", // context config is in the url
						"ff=C38030BF0DCCF31DB770B2EAFA779DFC",
						"from=http%253A%252F%252Flocalhost%253A" + from.port() + "%252Ffrom%252F",
						"tab=2",
						"tf=C38030BF0DCCF31DB770B2EAFA779DFC",
						"to=http%253A%252F%252Flocalhost%253A" + to.port() + "%252Fto%252F" );
	}

	/**
	 * Asserts on the naturally-found change summary
	 */
	@Test
	void naturalAnalysis() {
		dseq.analysis()
				.hasSummary(
						"Removed 1 On tags: a b c 1 deleted: a",
						"Added 1 On tags: d e f 1 added: f",
						"Changed 1 On tags: c d e 1 always changed: e",
						"Unchanged 1 On tags: b c d 1 never changed: b" );

		dseq.analysisDiffs(
				"8 characters on 1 flow c d e ii ee oo aa" )
				.expandAnalysis( "8 characters on 1 flow c d e ii ee oo aa" )
				.hasSubjectFlows(
						"updated c d e | updated c d e" );

		dseq.clickDiff( 0 )
				.hasDiff(
						" 1  1   Identity:                ",
						" 2  2     updated                ",
						" 3  3     c                      ",
						" 4  4     d                      ",
						"        3 unchanged lines        ",
						" 8  8   Context:                 ",
						" 9  9     {}                     ",
						"10 10   Interactions:            ",
						"11 11     ┌REQUEST AVA => BEN [] ",
						"12    -   │Hi Ben!               ",
						"   12 +   │Hiii Beeen!           ",
						"13 13     └                      ",
						"14 14     ┌RESPONSE AVA <= BEN []",
						"15    -   │Hello Ava!            ",
						"   15 +   │Heeellooo Avaaa!      ",
						"16 16     └                      " );
	}

	/**
	 * Exercises manual pairing
	 */
	@Test
	void pairing() {

		dseq.unpaired()
				.hasUnpairs( "removed a b c | added d e f  " )
				.pair( 0 )
				.hasUnpairs();

		dseq.paired()
				.hasPairs(
						"  removed a b c | added d e f    ",
						"unchanged b c d | unchanged b c d",
						"  updated c d e | updated c d e  " );

		dseq.analysis()
				.hasSummary(
						"Removed 0 On tags: 1 deleted: a",
						"Added 0 On tags: 1 added: f",
						"Changed 2 On tags: a b c d e f 3 always changed: a e f",
						"Unchanged 1 On tags: b c d 0 never changed:" );

		dseq.changes()
				.hasChangeList(
						"updated c d e",
						"removed ↦ added a b c d e f" )
				.change( "removed ↦ added a b c d e f" )
				.hasDiff(
						" 1  1   Identity:                ",
						" 2    -   removed                ",
						" 3    -   a                      ",
						" 4    -   b                      ",
						" 5    -   c                      ",
						"    2 +   added                  ",
						"    3 +   d                      ",
						"    4 +   e                      ",
						"    5 +   f                      ",
						" 6  6   Motivation:              ",
						" 7  7                            ",
						" 8  8   Context:                 ",
						" 9  9     {}                     ",
						"        3 unchanged lines        ",
						"13 13     └                      ",
						"14 14     ┌RESPONSE AVA <= BEN []",
						"15 15     │Hello Ava!            ",
						"16 16     └                      " );

	}

	/**
	 * Exercises manual unpairing
	 */
	@Test
	void unpairing() {
		dseq.paired()
				.hasPairs(
						"unchanged b c d | unchanged b c d",
						"  updated c d e | updated c d e  " )
				.unpair( 0 )
				.unpair( 0 )
				.hasPairs();

		dseq.unpaired()
				.hasUnpairs(
						"  removed a b c | added d e f    ",
						"unchanged b c d | unchanged b c d",
						"  updated c d e | updated c d e  " );

		dseq.analysis()
				.hasSummary(
						"Removed 3 On tags: a b c d e 1 deleted: a",
						"Added 3 On tags: b c d e f 1 added: f",
						"Changed 0 On tags: 0 always changed:",
						"Unchanged 0 On tags: 0 never changed:" );
	}

	/**
	 * Exercises a more complicated pairing update
	 */
	@Test
	void repairing() {
		dseq.paired()
				.hasPairs(
						"unchanged b c d | unchanged b c d",
						"  updated c d e | updated c d e  " )
				.unpair( 0 )
				.unpair( 0 )
				.hasPairs();

		dseq.unpaired()
				.hasUnpairs(
						"  removed a b c | added d e f    ",
						"unchanged b c d | unchanged b c d",
						"  updated c d e | updated c d e  " );

		dseq.dragLeft( "updated c d e", "removed a b c" )
				.hasUnpairs(
						"  updated c d e | added d e f    ",
						"  removed a b c | unchanged b c d",
						"unchanged b c d | updated c d e  " )
				.pair( 0 );

		dseq.dragRight( "unchanged b c d", "updated c d e" )
				.hasUnpairs(
						"  removed a b c | updated c d e  ",
						"unchanged b c d | unchanged b c d" )
				.pair( 0 )
				.pair( 0 );

		dseq.hasUnpairs()
				.paired()
				.hasPairs(
						"  removed a b c | updated c d e  ",
						"unchanged b c d | unchanged b c d",
						"  updated c d e | added d e f    " );

		// pairing is captured in the url args - we can link to it
		dseq.hasUrlArgs(
				"ff=C38030BF0DCCF31DB770B2EAFA779DFC",
				"from=http%253A%252F%252Flocalhost%253A" + from.port() + "%252Ffrom%252F",
				"p=0-1",
				"p=2-2",
				"tf=0745FE2E9BAAF8062DCE73E98CDEF054",
				"to=http%253A%252F%252Flocalhost%253A" + to.port() + "%252Fto%252F",
				"u=2-1" );
		dseq.diff(
				"from=http%253A%252F%252Flocalhost%253A" + from.port() + "%252Ffrom%252F",
				"p=0-1",
				"p=2-2",
				"to=http%253A%252F%252Flocalhost%253A" + to.port() + "%252Fto%252F",
				"u=2-1" )
				.paired()
				.hasPairs(
						"  removed a b c | updated c d e  ",
						"unchanged b c d | unchanged b c d",
						"  updated c d e | added d e f    " )
				.analysis()
				.hasSummary(
						"Removed 0 On tags: 1 deleted: a",
						"Added 0 On tags: 1 added: f",
						"Changed 2 On tags: a b c d e f 3 always changed: a e f",
						"Unchanged 1 On tags: b c d 0 never changed:" )
				.analysisDiffs(
						"24 characters on 1 flow a b c d e remov updat a c b d c e ii ee oo aa",
						"14 characters on 1 flow c d e f upd t dd c f" );

	}
}
