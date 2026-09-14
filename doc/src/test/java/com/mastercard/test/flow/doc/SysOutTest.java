
package com.mastercard.test.flow.doc;

import static org.junit.Assert.fail;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import com.mastercard.test.flow.report.QuietFiles;

/**
 * Apparently I'm very fond of using System.out.println to debug and then
 * failing to clean up after myself. This isn't strictly a test for
 * documentation, but all the files-spelunking functionality is here, so...
 */
@SuppressWarnings("static-method")
class SysOutTest {

	private static final Map<Path, Set<String>> accepted = new HashMap<>();
	static {
		accept( "../example/app-assert/src/main/java/com/mastercard/test/flow/example/app/assrt/"
				+ "Browser.java",
				"System.err.println( \"Shutting down Chrome\" );" );
		accept( "../report/report-core/src/test/java/com/mastercard/test/flow/report/seq/"
				+ "Browser.java",
				"System.err.println( \"Shutting down browser\" );" );
		accept( "../report/report-core/src/test/java/com/mastercard/test/flow/report/seq/"
				+ "AbstractSequence.java",
				"System.out.println( operation + \" \" + Arrays.toString( args ) );",
				"System.out.print( \"Hit enter to continue\" );",
				"System.out.flush();" );
		accept( "../example/app-itest/src/test/java/com/mastercard/test/flow/example/app/itest/"
				+ "IntegrationTest.java",
				"System.out.println( \"Serving \" + reportDir );" );
		accept( "../report/duct/src/main/java/com/mastercard/test/flow/report/duct/"
				+ "Duct.java",
				"System.err.println( \"Failed to browse \" + served );" );
		// Required capture-failure diagnostics must remain visible when reporting
		// itself is unavailable, without feeding back into a captured log backend.
		accept( "../assert/assert-core/src/main/java/com/mastercard/test/flow/assrt/"
				+ "FlowProcessor.java",
				"System.err.println( diagnostic );" );
		// Standalone compatibility runners deliberately print their native test
		// summaries and failures for the same-binary, cross-runtime evidence logs.
		for( String runner : new String[] { "FlowLauncherBridgeRuntime", "FlowNativeProfileRuntime",
				"FlowParallelRuntime" } ) {
			accept( "../assert/assert-junit5/src/test/java/com/mastercard/test/flow/assrt/junit5/"
					+ runner + ".java",
					"listener.getSummary().printTo( new PrintWriter( System.out, true ) );",
					"listener.getSummary().printFailuresTo( new PrintWriter( System.out, true ) );" );
		}
	}

	private static void accept( String file, String... line ) {
		Collections.addAll( accepted.computeIfAbsent( Paths.get( file ), p -> new TreeSet<>() ), line );
	}

	/**
	 * Scans our source files for usages of sysout and syserr
	 *
	 * @return The tests
	 */
	@TestFactory
	Stream<DynamicTest> sysScan() {
		return Util.javaFiles()
				.filter( p -> !(SysOutTest.class.getSimpleName() + ".java")
						.equals( p.getFileName().toString() ) )
				.map( path -> dynamicTest(
						path.getFileName().toString(),
						() -> QuietFiles.lines( path )
								.filter( line -> line.contains( "System.out" ) || line.contains( "System.err" ) )
								.findAny()
								.map( String::trim )
								.filter( l -> !accepted.getOrDefault( path, Collections.emptySet() ).contains( l ) )
								.ifPresent( violation -> fail(
										String.format( "File\n%s\ncontains system terminal usage\n%s",
												path.toString().replace( '\\', '/' ), violation ) ) ) ) );
	}
}
