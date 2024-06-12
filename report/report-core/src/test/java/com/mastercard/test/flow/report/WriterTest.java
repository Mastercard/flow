package com.mastercard.test.flow.report;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
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
		Assertions.assertEquals( Copy.pasta(
				"  1 x detail/08535047C5991FED96BECB327EAFF8E7.html",
				"  1 x detail/0D943F64D05D282F91C856027DF72923.html",
				"  1 x detail/4C5FFE22176C7ABC272D95A0E5D62262.html",
				"  1 x detail/823B8031950E57346DCE6FFD4BE56F54.html",
				"  1 x index.html",
				"  1 x res/3rdpartylicenses.txt",
				" 31 x res/_digits_.<hash>.js",
				"  1 x res/common.<hash>.js",
				"  1 x res/favicon.ico",
				"  1 x res/main.<hash>.js",
				"  1 x res/polyfills.<hash>.js",
				"  1 x res/runtime.<hash>.js",
				"  1 x res/styles.<hash>.css" ),
				summariseReportFiles( dir ) );

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
				+ "  \"exercised\" : [ ],\n"
				+ "  \"logs\" : [ ]\n"
				+ "}\n"
				+ "      // END_JSON_DATA",
				detail.substring(
						detail.indexOf( "// START_JSON_DATA" ),
						detail.indexOf( "// END_JSON_DATA" ) + "// END_JSON_DATA".length() )
						.replaceAll( ":\\d+", ":##" ) );
	}

	private String summariseReportFiles( Path dir ) throws IOException {
		return Copy.pasta( Files.walk( dir )
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
	}

	/**
	 * Exercises {@link Writer#writeDuctIndex(Path)}
	 *
	 * @throws Exception on error
	 */
	@Test
	void writeDuctIndex() throws Exception {

		Path dir = Paths.get( "target", "WriterTest", "writeDuctIndex" );

		Files.createDirectories( dir );

		Writer.writeDuctIndex( dir );

		// check file listing of report
		Assertions.assertEquals( Copy.pasta(
				"  1 x index.html",
				"  1 x res/3rdpartylicenses.txt",
				" 31 x res/_digits_.<hash>.js",
				"  1 x res/common.<hash>.js",
				"  1 x res/favicon.ico",
				"  1 x res/main.<hash>.js",
				"  1 x res/polyfills.<hash>.js",
				"  1 x res/runtime.<hash>.js",
				"  1 x res/styles.<hash>.css" ),
				summariseReportFiles( dir ) );
	}

	/**
	 * Illustrates the effect of the edit we make to the runtime.js file so that all
	 * javascript files can be hidden away in the <code>res</code> dir
	 *
	 * @throws Exception on failure
	 */
	@Test
	void chunkLoadingPath() throws Exception {
		Path dir = Paths.get( "target", "WriterTest", "chunkLoadingPath" );

		Files.createDirectories( dir );
		Writer w = writeReport( dir );

		Path runtimeFile = QuietFiles.list( w.path().resolve( "res" ) )
				.filter( p -> p.getFileName().toString()
						.matches( "runtime\\.[0-9a-f]+\\.js" ) )
				.findAny()
				.orElseThrow( () -> new IllegalStateException(
						"Failed to find runtime.<hash>.js file in " + w.path() ) );

		// The file that was written in the report
		String written = new String( QuietFiles.readAllBytes( runtimeFile ), UTF_8 );
		// The resource that was generated in report-ng
		String resource = resource( runtimeFile.getFileName().toString() );

		// let's examine the changes that we've made to the runtime file
		Patch<String> patch = DiffUtils.diffInline( resource, written );

		assertEquals( 1, patch.getDeltas().size(),
				"edit count" );
		AbstractDelta<String> delta = patch.getDeltas().get( 0 );
		assertEquals( DeltaType.INSERT, delta.getType(),
				"edit type" );
		assertEquals( "[\"res/\"+]", delta.getTarget().getLines().toString(),
				"inserted lines count" );

		int context = 14;
		String before = resource.substring(
				delta.getSource().getPosition() - context,
				delta.getSource().getPosition() + context )
				.replaceAll( "\\d", "#" );
		String after = written.substring(
				delta.getTarget().getPosition() - context,
				delta.getTarget().getPosition() + context
						+ delta.getTarget().getLines().get( 0 ).length() )
				.replaceAll( "\\d", "#" );

		assertEquals( "),[])),a.u=e=>(###===e?\"comm",
				before, "raw resource runtime snippet" );
		assertEquals( "),[])),a.u=e=>\"res/\"+(###===e?\"comm",
				after, "written runtime snippet" );
	}

	private static String resource( String name ) {
		try( InputStream is = WriterTest.class.getResourceAsStream( name );
				ByteArrayOutputStream os = new ByteArrayOutputStream() ) {
			byte[] buff = new byte[8192];
			int read;
			while( (read = is.read( buff )) != -1 ) {
				os.write( buff, 0, read );
			}
			return new String( os.toByteArray(), UTF_8 );
		}
		catch( Exception e ) {
			throw new IllegalStateException( "Failed to read resource " + name, e );
		}
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
