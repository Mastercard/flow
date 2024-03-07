package com.mastercard.test.flow.report;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.report.data.Entry;
import com.mastercard.test.flow.report.data.FlowData;
import com.mastercard.test.flow.report.data.Index;
import com.mastercard.test.flow.util.Tags;

/**
 * Exercises {@link Reader}
 */
@SuppressWarnings("static-method")
class ReaderTest {

	/**
	 * Creates a report, then reads the data back in
	 */
	@Test
	void read() {
		Path dir = Paths.get( "target", "ReaderTest", "read" );
		try {
			WriterTest.writeReport( dir );
		}
		catch( IllegalStateException ise ) {
			if( !"Failed to find /com/mastercard/test/flow/report/manifest.txt"
					.equals( ise.getMessage() ) ) {
				throw ise;
			}
			Assertions.fail( "This failure is characteristic of bad IDE classpath configuration.\n"
					+ "Try adding target/classes as a source directory of the report-ng project." );
		}

		Reader r = new Reader( dir );
		Index idx = r.read();

		Assertions.assertEquals( "model title", idx.meta.modelTitle );
		Assertions.assertEquals( "test title", idx.meta.testTitle );
		Assertions.assertEquals( ""
				+ "0D943F64D05D282F91C856027DF72923\n"
				+ "823B8031950E57346DCE6FFD4BE56F54\n"
				+ "08535047C5991FED96BECB327EAFF8E7\n"
				+ "4C5FFE22176C7ABC272D95A0E5D62262",
				idx.entries.stream().map( e -> e.detail )
						.collect( joining( "\n" ) ) );

		Assertions.assertEquals( "0D943F64D05D282F91C856027DF72923", idx.entries.get( 0 ).detail );
		FlowData basis = r.detail( idx.entries.get( 0 ) );
		Assertions.assertEquals( "basis", basis.description );
		Assertions.assertEquals( "[PASS, abc, def]", basis.tags.toString() );
		Assertions.assertEquals( "Hello!", basis.root.request.full.expect );
		Assertions.assertEquals( "!olleH", basis.root.response.full.expect );

		FlowData child = r.detail( idx.entries.get( 1 ) );
		Assertions.assertEquals( "child", child.description );
		Assertions.assertEquals( "[FAIL, abc, def, extra!, ghi]", child.tags.toString() );
		Assertions.assertEquals( "0D943F64D05D282F91C856027DF72923", child.basis,
				"basis link as expected" );

		Assertions.assertEquals( "08535047C5991FED96BECB327EAFF8E7", idx.entries.get( 2 ).detail );
		FlowData dependency = r.detail( idx.entries.get( 2 ) );
		Assertions.assertEquals( "dependency", dependency.description );
		Assertions.assertEquals( "[SKIP, abc, ghi, jkl, mno]", dependency.tags.toString() );

		FlowData dependent = r.detail( idx.entries.get( 3 ) );
		Assertions.assertEquals( "dependent", dependent.description );
		Assertions.assertEquals( "[ERROR, mno, pqr, stu]", dependent.tags.toString() );
		Assertions.assertEquals( "08535047C5991FED96BECB327EAFF8E7",
				dependent.dependencies.keySet().iterator().next(),
				"dependency link as expected" );
	}

	/**
	 * What happens when you try to read a non-existent report
	 */
	@Test
	void noReport() {
		Reader r = new Reader( Paths.get( "no", "such", "directory" ) );
		assertEquals( null, r.read() );
	}

	/**
	 * What happens when we can't read the index
	 *
	 * @throws Exception unexpected oopsy
	 */
	@Test
	@EnabledOnOs(OS.WINDOWS)
	void badReadWindows() throws Exception {
		Path dir = Paths.get( "target", "ReaderTest", "badReadWindows" );
		WriterTest.writeReport( dir );
		File index = dir.resolve( "index.html" ).toFile();

		try( RandomAccessFile raf = new RandomAccessFile( index, "rw" );
				FileChannel channel = raf.getChannel();
				FileLock lock = channel.lock() ) {

			Reader r = new Reader( dir );
			IllegalStateException ise = assertThrows( IllegalStateException.class,
					() -> r.read() );
			assertTrue( ise.getMessage().matches(
					"Failed to read file:.*ReaderTest/badReadWindows/index.html" ),
					ise.getMessage() );
		}
	}

	/**
	 * What happens when we can't read the index /
	 *
	 * @throws Exception unexpected oopsy
	 */
	@Test
	@EnabledOnOs(OS.LINUX)
	void badReadLinux() throws Exception {
		Path dir = Paths.get( "target", "ReaderTest", "badReadLinux" );
		WriterTest.writeReport( dir );
		File index = dir.resolve( "index.html" ).toFile();

		index.setReadable( false );

		Reader r = new Reader( dir );
		IllegalStateException ise = assertThrows( IllegalStateException.class,
				() -> r.read() );
		assertTrue( ise.getMessage().matches(
				"Failed to read file:.*ReaderTest/badReadLinux/index.html" ),
				ise.getMessage() );
	}

	/**
	 * Illustrates matching report index entries to flows
	 */
	@Test
	void matches() {
		Entry entry = new Entry( "description", Arrays.asList( "abc", "def", "PASS" ), "detail" );

		{
			Flow match = Creator.build( flow -> flow.meta( data -> data
					.description( "description" )
					.tags( Tags.set( "abc", "def", "FAIL" ) ) ) );
			assertTrue( Reader.matches( entry, match ), "Result tags are ignored" );
		}
		{
			Flow badDescription = Creator.build( flow -> flow.meta( data -> data
					.description( "mismatch" )
					.tags( Tags.set( "abc", "def", "FAIL" ) ) ) );
			assertFalse( Reader.matches( entry, badDescription ), "descriptions must match" );
		}
		{
			Flow badTags = Creator.build( flow -> flow.meta( data -> data
					.description( "description" )
					.tags( Tags.set( "abc", "def", "foo", "FAIL" ) ) ) );
			assertFalse( Reader.matches( entry, badTags ), "tags must match" );
		}
	}

	/**
	 * Exercises recognition of reports
	 *
	 * @throws Exception If our file shenanigans go wrong
	 */
	@Test
	void isReportDir() throws Exception {
		Path ws = Paths.get( "target", "ReaderTest", "isReportDir" );
		QuietFiles.recursiveDelete( ws );
		{
			Path nonexistent = ws.resolve( "nowt" );
			assertFalse( Reader.isReportDir( nonexistent ) );
		}
		{
			Path file = ws.resolve( "file" );
			Files.createDirectories( file.getParent() );
			Files.write( file, "Just a file".getBytes( UTF_8 ) );
			assertFalse( Reader.isReportDir( file ) );
		}
		{
			Path empty = ws.resolve( "empty" );
			Files.createDirectories( empty );
			assertFalse( Reader.isReportDir( empty ) );
		}
		{
			Path noIndex = ws.resolve( "noIndex" );
			Files.createDirectories( noIndex );
			Files.createDirectory( noIndex.resolve( "detail" ) );
			assertFalse( Reader.isReportDir( noIndex ) );
		}
		{
			Path noDetail = ws.resolve( "noDetail" );
			Files.createDirectories( noDetail );
			Files.write( noDetail.resolve( "index.html" ), "index".getBytes( UTF_8 ) );
			assertFalse( Reader.isReportDir( noDetail ) );
		}
		{
			Path complete = ws.resolve( "complete" );
			Files.createDirectories( complete );
			Files.write( complete.resolve( "index.html" ), "index".getBytes( UTF_8 ) );
			Files.createDirectory( complete.resolve( "detail" ) );
			assertTrue( Reader.isReportDir( complete ) );
		}
	}

	/**
	 * Exercises searching for the most recent execution report
	 *
	 * @throws Exception If our file shenanigans go wrong
	 */
	@Test
	void mostRecent() throws Exception {
		Path ws = Paths.get( "target", "ReaderTest", "mostRecent" );
		QuietFiles.recursiveDelete( ws );

		assertNull( Reader.mostRecent( ws.toString(), p -> true ) );

		WriterTest.writeReport( ws.resolve( "first" ) );
		WriterTest.writeReport( ws.resolve( "second" ) );
		WriterTest.writeReport( ws.resolve( "third" ) );
		WriterTest.writeReport( ws.resolve( "fourth" ) );

		// render one report unrecognisable
		Files.delete( ws.resolve( "third" ).resolve( "index.html" ) );

		assertEquals( "target/ReaderTest/mostRecent/second",
				Reader.mostRecent( ws.toString(),
						// filter out another
						p -> !p.toString().contains( "four" ) )
						.toString().replace( '\\', '/' ) );
	}
}
