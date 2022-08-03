package com.mastercard.test.flow.report;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link Writer}
 */
@SuppressWarnings("static-method")
class WriterTest {

	/**
	 * @param dir The dir to create a report in
	 * @return The writer
	 */
	static Writer writeReport( Path dir ) {
		return new Writer( "model title", "test title", dir )
				.with( Mdl.BASIS, f -> f.tags.add( "PASS" ) )
				.with( Mdl.CHILD, f -> f.tags.add( "FAIL" ) )
				.with( Mdl.DEPENDENCY, f -> f.tags.add( "SKIP" ) )
				.with( Mdl.DEPENDENT, f -> f.tags.add( "ERROR" ) )

				// update flow that we've already written
				.with( Mdl.CHILD, f -> f.tags.add( "extra!" ) );
	}

	/**
	 * Creates a report and then illustrates the file structure
	 *
	 * @throws Exception IO failure
	 */
	@Test
	void write() throws Exception {
		Assumptions.assumeFalse( "none".equals( System.getProperty( "node" ) ),
				"The report webapp hasn't been compiled (due to system property node=none)" );

		Path dir = Paths.get( "target", "WriterTest", "write" );

		Files.createDirectories( dir );
		QuietFiles.write( dir.resolve( "pre-existing-file.txt" ),
				"This will be deleted".getBytes( UTF_8 ) );

		try {
			Writer w = writeReport( dir );
			assertEquals( "target/WriterTest/write", w.path().toString().replace( '\\', '/' ) );
		}
		catch( IllegalStateException ise ) {
			if( !"Failed to find /com/mastercard/test/flow/report/manifest.txt"
					.equals( ise.getMessage() ) ) {
				throw ise;
			}
			Assertions.fail( "This failure is characteristic of bad IDE classpath configuration.\n"
					+ "Try adding target/classes as a source directory of the report-ng project." );
		}

		// check file listing of report
		Assertions.assertEquals( ""
				+ "detail/08535047C5991FED96BECB327EAFF8E7.html\n"
				+ "detail/0D943F64D05D282F91C856027DF72923.html\n"
				+ "detail/4C5FFE22176C7ABC272D95A0E5D62262.html\n"
				+ "detail/823B8031950E57346DCE6FFD4BE56F54.html\n"
				+ "index.html\n"
				+ "res/3rdpartylicenses.txt\n"
				+ "res/favicon.ico\n"
				+ "res/index.html\n"
				+ "res/main.<hash>.js\n"
				+ "res/polyfills.<hash>.js\n"
				+ "res/runtime.<hash>.js\n"
				+ "res/styles.<hash>.css",
				Files.walk( dir )
						.filter( Files::isRegularFile )
						.map( dir::relativize )
						.map( String::valueOf )
						.map( s -> s.replace( '\\', '/' ) )
						// files produced by the angular build have a content-hash suffix
						// to avoid cached versions being used
						.map( s -> s.replaceAll( "\\.[a-f0-9]{20}\\.(js|css)", ".<hash>.$1" ) )
						.sorted()
						.collect( Collectors.joining( "\n" ) ) );

		String detail = new String( Files.readAllBytes( dir.resolve(
				"detail/4C5FFE22176C7ABC272D95A0E5D62262.html" ) ), UTF_8 );
		Assertions.assertEquals( ""
				+ "// START_JSON_DATA\n"
				+ "{\n"
				+ "  \"description\" : \"dependent\",\n"
				+ "  \"tags\" : [ \"ERROR\", \"mno\", \"pqr\", \"stu\" ],\n"
				+ "  \"motivation\" : \"\",\n"
				+ "  \"trace\" : \"com.mastercard.test.flow.report.Mdl.<clinit>(Mdl.java:##)\",\n"
				+ "  \"basis\" : null,\n"
				+ "  \"dependencies\" : {\n"
				+ "    \"08535047C5991FED96BECB327EAFF8E7\" : {\n"
				+ "      \"description\" : \"dependency\",\n"
				+ "      \"tags\" : [ \"abc\", \"ghi\", \"jkl\", \"mno\" ]\n"
				+ "    }\n"
				+ "  },\n"
				+ "  \"root\" : {\n"
				+ "    \"requester\" : \"AVA\",\n"
				+ "    \"responder\" : \"BEN\",\n"
				+ "    \"tags\" : [ ],\n"
				+ "    \"request\" : {\n"
				+ "      \"full\" : {\n"
				+ "        \"expect\" : \"Hello!\",\n"
				+ "        \"expectBytes\" : \"SGVsbG8h\",\n"
				+ "        \"actual\" : null,\n"
				+ "        \"actualBytes\" : null\n"
				+ "      },\n"
				+ "      \"asserted\" : {\n"
				+ "        \"expect\" : null,\n"
				+ "        \"actual\" : null\n"
				+ "      }\n"
				+ "    },\n"
				+ "    \"response\" : {\n"
				+ "      \"full\" : {\n"
				+ "        \"expect\" : \"!olleH\",\n"
				+ "        \"expectBytes\" : \"IW9sbGVI\",\n"
				+ "        \"actual\" : null,\n"
				+ "        \"actualBytes\" : null\n"
				+ "      },\n"
				+ "      \"asserted\" : {\n"
				+ "        \"expect\" : null,\n"
				+ "        \"actual\" : null\n"
				+ "      }\n"
				+ "    },\n"
				+ "    \"children\" : [ ]\n"
				+ "  },\n"
				+ "  \"context\" : {\n"
				+ "    \"Ctx\" : {\n"
				+ "      \"field\" : \"context value\"\n"
				+ "    }\n"
				+ "  },\n"
				+ "  \"residue\" : [ {\n"
				+ "    \"name\" : \"Rsd\",\n"
				+ "    \"raw\" : {\n"
				+ "      \"field\" : \"residue value\"\n"
				+ "    },\n"
				+ "    \"full\" : null,\n"
				+ "    \"masked\" : null\n"
				+ "  } ],\n"
				+ "  \"logs\" : [ ]\n"
				+ "}\n"
				+ "      // END_JSON_DATA",
				detail.substring(
						detail.indexOf( "// START_JSON_DATA" ),
						detail.indexOf( "// END_JSON_DATA" ) + "// END_JSON_DATA".length() )
						.replaceAll( ":\\d+", ":##" ) );
	}
}
