package com.mastercard.test.flow.report;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.builder.Deriver;
import com.mastercard.test.flow.msg.txt.Text;
import com.mastercard.test.flow.report.Mdl.Actrs;

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
				+ "  1 x detail/08535047C5991FED96BECB327EAFF8E7.html\n"
				+ "  1 x detail/0D943F64D05D282F91C856027DF72923.html\n"
				+ "  1 x detail/4C5FFE22176C7ABC272D95A0E5D62262.html\n"
				+ "  1 x detail/823B8031950E57346DCE6FFD4BE56F54.html\n"
				+ "  1 x index.html\n"
				+ "  1 x res/3rdpartylicenses.txt\n"
				+ " 27 x res/_digits_.<hash>.js\n"
				+ "  1 x res/common.<hash>.js\n"
				+ "  1 x res/favicon.ico\n"
				+ "  1 x res/main.<hash>.js\n"
				+ "  1 x res/polyfills.<hash>.js\n"
				+ "  1 x res/runtime.<hash>.js\n"
				+ "  1 x res/styles.<hash>.css",
				Files.walk( dir )
						.filter( Files::isRegularFile )
						.map( dir::relativize )
						.map( String::valueOf )
						.map( s -> s.replace( '\\', '/' ) )
						// files produced by the angular build have a content-hash suffix
						// to avoid cached versions being used
						.map( s -> s.replaceAll( "\\.[a-f0-9]{16}\\.(js|css)", ".<hash>.$1" ) )
						.map( s -> s.replaceAll( "(res/)\\d+(\\.<hash>.js)", "$1_digits_$2" ) )
						.collect( groupingBy( s -> s ) )
						.entrySet().stream()
						.sorted( comparing( Entry::getKey ) )
						.map( e -> String.format( "%3d x %s", e.getValue().size(), e.getKey() ) )
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

	/**
	 * Demonstrates that the report will cope with a missing basis - we'll hoist to
	 * the nearest ancestor that <i>is</i> available.
	 */
	@Test
	void basisChaining() {
		Flow gramps = Creator.build( flow -> flow
				.meta( data -> data
						.description( "gramps" ) )
				.call( a -> a.from( Actrs.AVA ).to( Actrs.BEN )
						.request( new Text( "hi ben!" ) )
						.response( new Text( "hi ava!" ) ) ) );

		Flow pops = Deriver.build( gramps, flow -> flow
				.meta( data -> data
						.description( "pops" ) ) );

		Flow junior = Deriver.build( pops, flow -> flow
				.meta( data -> data
						.description( "junior" ) ) );

		Flow zygote = Deriver.build( junior, flow -> flow
				.meta( data -> data
						.description( "zygote" ) ) );

		Path dir = Paths.get( "target", "WriterTest", "basisChaining" );

		Writer writer = new Writer( "model", "test", dir );
		Reader reader = new Reader( dir );

		writer.with( zygote );
		assertHierarchy( reader, ""
				+ "zygote's basis is unknown basis:null",
				"zygote has an ancestry, but none of them are in the report" );
		assertWriterMissingBases( writer, ""
				+ "zygote : [ junior, pops, gramps ]" );

		writer.with( pops );
		assertHierarchy( reader, ""
				+ "zygote's basis is pops\n"
				+ "  pops's basis is unknown basis:null",
				"An ancestor has been added, zygote is updated" );
		assertWriterMissingBases( writer, ""
				+ "  pops : [ gramps ]\n"
				+ "zygote : [ junior ]" );

		writer.with( gramps );
		assertHierarchy( reader, ""
				+ "zygote's basis is pops\n"
				+ "  pops's basis is gramps\n"
				+ "gramps's basis is unknown basis:null",
				"A more distant ancestor has been added, no update" );
		assertWriterMissingBases( writer, ""
				+ "zygote : [ junior ]" );

		writer.with( junior );
		assertHierarchy( reader, ""
				+ "zygote's basis is junior\n"
				+ "  pops's basis is gramps\n"
				+ "gramps's basis is unknown basis:null\n"
				+ "junior's basis is pops",
				"The direct basis is added, zygote is updated again" );
		assertWriterMissingBases( writer, "" );
	}

	private void assertHierarchy( Reader reader, String expected, String comment ) {
		Map<String, String> idx = reader.read().entries.stream()
				.collect( Collectors.toMap( e -> e.detail, e -> e.description ) );

		assertEquals( expected,
				reader.read().entries.stream()
						.map( reader::detail )
						.map( d -> String.format(
								"%6s's basis is %s",
								d.description,
								idx.getOrDefault( d.basis, "unknown basis:" + d.basis ) ) )
						.collect( joining( "\n" ) ),
				comment );
	}

	private void assertWriterMissingBases( Writer writer, String expected ) {
		assertEquals( expected,
				writer.missingBases().entrySet().stream()
						.map( e -> String.format(
								"%6s : [ %s ]",
								e.getKey().meta().description(),
								e.getValue().stream().map( f -> f.meta().description() )
										.collect( joining( ", " ) ) ) )
						.sorted()
						.collect( joining( "\n" ) ) );
	}
}
