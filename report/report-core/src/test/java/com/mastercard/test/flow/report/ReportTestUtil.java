package com.mastercard.test.flow.report;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.mastercard.test.flow.Flow;

import spark.Service;

/**
 * Utility methods for testing execution reports
 */
public class ReportTestUtil {

	/**
	 * Base directory for where we write our reports
	 */
	static final Path REPORT_ROOT = Paths.get( "target", "report" );

	/**
	 * Encapsulates the useful details of a served report - where to find it with a
	 * browser and a means of shutting it down
	 */
	public static class Served implements AutoCloseable {

		private final String path;
		private final Service server;

		/**
		 * @param server The {@link Service}
		 * @param path   The report path
		 */
		Served( Service server, String path ) {
			this.server = server;
			this.path = path;
		}

		/**
		 * @return The http URL at which the report is available
		 */
		public String url() {
			return "http://localhost:" + port() + "/" + path + "/";
		}

		/**
		 * @return The file URL at which the report index is available
		 */
		public String fileUrl() {
			return "file:"
					+ REPORT_ROOT.resolve( path ).toAbsolutePath().toString()
					+ "/index.html";
		}

		/**
		 * @return The port of the server
		 */
		public int port() {
			return server.port();
		}

		@Override
		public void close() {
			server.stop();
			server.awaitStop();
		}

	}

	/**
	 * Creates a report and serves it
	 *
	 * @param dir   The directory under target/report to write the report to
	 * @param flows The set of {@link Flow}s to put in the report
	 * @return The served report details
	 * @throws Exception if something fails
	 */
	public static Served serve( String dir, Flow... flows ) throws Exception {
		return serve( dir, "Model title", w -> Stream.of( flows ).forEach( w::with ) );
	}

	/**
	 * Creates a report and serves it
	 *
	 * @param dir        The directory under target/report to write the report to
	 * @param modelTitle The title of the system model
	 * @param flows      The set of {@link Flow}s to put in the report
	 * @return The served report details
	 * @throws Exception if something fails
	 */
	public static Served serve( String dir, String modelTitle, Flow... flows ) throws Exception {
		return serve( dir, modelTitle, w -> Stream.of( flows ).forEach( w::with ) );
	}

	/**
	 * Creates a report and serves it
	 *
	 * @param dir        The directory under target/report to write the report to
	 * @param modelTitle The title of the system model
	 * @param data       How to populate the report
	 * @return The served report details
	 * @throws Exception if something fails
	 */
	public static Served serve( String dir, String modelTitle, Consumer<Writer> data )
			throws Exception {
		Path reportDir = REPORT_ROOT.resolve( dir );

		Writer w = new Writer( modelTitle, "Test title", reportDir );
		data.accept( w );

		// fiddle with the index so it's more testable
		Path indexPath = reportDir.resolve( "index.html" );
		String content = new String( Files.readAllBytes( indexPath ), UTF_8 );
		content = content.replaceAll( "(\"timestamp\" :) \\d+", "$1 1234567890123" );
		Files.write( indexPath, content.getBytes( UTF_8 ) );

		Service server = Service.ignite()
				.port( 0 )
				.externalStaticFileLocation( REPORT_ROOT.toString() );
		server.staticFiles.header( "Access-Control-Allow-Origin", "*" );
		server.init();
		server.awaitInitialization();

		return new Served( server, dir );
	}

}
