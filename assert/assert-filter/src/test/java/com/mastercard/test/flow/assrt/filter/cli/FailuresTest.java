package com.mastercard.test.flow.assrt.filter.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.assrt.filter.mock.Mdl;
import com.mastercard.test.flow.report.QuietFiles;
import com.mastercard.test.flow.report.Writer;

/**
 * Exercises the CLI functionality for selecting flows that failed in a previous
 * execution
 */
@SuppressWarnings("static-method")
class FailuresTest extends AbstractFilterTest {

	/**
	 * Shows that the filters can be loaded from historic executions
	 */
	@Test
	void failures() {
		Mdl mdl = new Mdl().withFlows(
				"first flow [foo, bar]",
				"second flow [bar, baz]",
				"third flow [baz, oof]" );
		Deque<String> resultTags = new ArrayDeque<>( Arrays.asList(
				Writer.ERROR_TAG,
				Writer.FAIL_TAG,
				Writer.PASS_TAG ) );
		Path report = Paths.get( "target", "mctf", "FailuresTest", "failures" );
		QuietFiles.recursiveDelete( report );
		Writer w = new Writer( "model", "test", report );
		mdl.flows().forEach( f -> w.with( f, fd -> fd.tags.add( resultTags.removeFirst() ) ) );

		new FilterCliHarness()
				.expect( "tags are displayed", ""
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "│ bar baz foo oof                                                              │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				.input( "\n" )
				.expect( "flows are displayed", ""
						+ "┌─ Flows ──────────────────────────────────────────────────────────────────────┐\n"
						+ "│ 1 first flow bar foo                                                         │\n"
						+ "│ 2 second flow bar baz                                                        │\n"
						+ "│ 3 third flow baz oof                                                         │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘\n"
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "│ bar baz foo oof                                                              │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				.input( "fa\t\n" )
				.expect( "successful flows excluded", ""
						+ "┌─ Flows ──────────────────────────────────────────────────────────────────────┐\n"
						+ "├─ Disabled ───────────────────────────────────────────────────────────────────┤\n"
						+ "│ 3 third flow baz oof                                                         │\n"
						+ "├─ Enabled ────────────────────────────────────────────────────────────────────┤\n"
						+ "│ 1 first flow bar foo                                                         │\n"
						+ "│ 2 second flow bar baz                                                        │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘\n"
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "│ bar baz foo oof                                                              │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				.input( "\n" )
				.on( mdl )
				.expectResults(
						"first flow [bar, foo]",
						"second flow [bar, baz]" );
	}

}
