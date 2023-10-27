package com.mastercard.test.flow.report.duct;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.report.Writer;
import com.mastercard.test.flow.report.data.Index;
import com.mastercard.test.flow.report.duct.HttpClient.Response;

/**
 * An application that lives in the system tray and serves flow reports.
 */
public class Duct {

	/**
	 * The preference name where we save our index directories
	 */
	static final String SERVED_REPORT_PATHS_PREF = "served_report_paths";
	/**
	 * Our preferences object
	 */
	static final Preferences PREFS = Preferences.userNodeForPackage( Duct.class );

	/**
	 * Application entrypoint
	 *
	 * @param args List of report paths (absolute) to serve and browse
	 */
	public static void main( String[] args ) {
		Duct duct = new Duct();
		duct.start();
		Stream.of( args )
				.map( Paths::get )
				.map( duct::add )
				.forEach( served -> {
					try {
						Desktop.getDesktop().browse( served.toURI() );
					}
					catch( Exception e ) {
						System.err.println( "Failed to browse " + served + " due to " + e.getMessage() );
					}
				} );
	}

	/**
	 * Makes a best-effort attempt at serving a report via {@link Duct} and opening
	 * a browser to it. This might involve starting a fresh instance of the duct
	 * application. It might fail silently
	 *
	 * @param report The report directory to serve
	 */
	public static void serve( Path report ) {
		// try adding via http request
		URL added = tryAdd( report );
		if( added != null ) {
			// there's an existing instance!
			try {
				Desktop.getDesktop().browse( added.toURI() );
			}
			catch( @SuppressWarnings("unused") IOException | URISyntaxException e ) {
				// oh well, we tried
			}
		}
		else {
			// we'll have to spawn our own instance
			try {
				// this process will persist after the demise of the current JVM
				ProcessBuilder pb = new ProcessBuilder(
						"java",
						// re-use the current JVM's classpath. It's running this class, so it should
						// also have the dependencies we need. The classpath will be bigger than duct
						// strictly needs, but the cost of that is negligible
						"-cp", System.getProperty( "java.class.path" ),
						// invoke this class's main method
						Duct.class.getName(),
						// pass the report path on the commandline - the above main method will take
						// care of adding and browsing it
						report.toAbsolutePath().toString() );
				pb.start();
			}
			catch( @SuppressWarnings("unused") IOException e ) {
				// oh well, we tried
			}
		}
	}

	/**
	 * Attempts to add a report to an existing duct instance
	 *
	 * @return The URL of the served report, or <code>null</code> if the request
	 *         failed, perhaps because there <i>was</i> no existing duct instance
	 */
	private static URL tryAdd( Path report ) {
		try {
			Response<String> res = HttpClient.request(
					"http://localhost:" + PORT + "/add",
					"POST",
					report.toAbsolutePath().toString(),
					b -> new String( b, UTF_8 ) );
			return new URL( res.body );
		}
		catch( @SuppressWarnings("unused") Exception e ) {
			// A failure on this request is not unexpected - it could just be a signal that
			// we need to start a new instance of duct
			return null;
		}
	}

	/**
	 * How long a {@link Duct} instance will live without heartbeat requests
	 */
	private static final Duration LIFESPAN = Duration.of( 90, ChronoUnit.SECONDS );

	/**
	 * The port that {@link Duct} will serve on
	 */
	public static final int PORT = 2276;

	/**
	 * The directory that holds our index application and log file
	 */
	static final Path INDEX_DIRECTORY = Paths.get( System.getProperty( "java.io.tmpdir" ) )
			.resolve( "mctf_duct" );

	private static final Logger LOG;
	static {
		// logger initialisation has to happen *after* the index directory is known
		try {
			Files.createDirectories( INDEX_DIRECTORY );
			System.setProperty( "org.slf4j.simpleLogger.logFile",
					INDEX_DIRECTORY.resolve( "log.txt" ).toAbsolutePath().toString() );
			LOG = LoggerFactory.getLogger( Duct.class );

			LOG.info( "Creating index files" );
			Writer.writeDuctIndex( INDEX_DIRECTORY );
		}
		catch( Exception e ) {
			throw new IllegalStateException( "Failed to create index directory", e );
		}
	}

	private final Gui gui;
	private final Server server = new Server( this, INDEX_DIRECTORY, PORT );
	private Instant expiry = Instant.now();
	private Map<Path, ReportSummary> index = new HashMap<>();

	/**
	 * Constructs a new {@link Duct} instance
	 */
	public Duct() {
		if( GraphicsEnvironment.isHeadless() ) {
			gui = new HeadlessGui();
		}
		else {
			gui = new SystrayGui( this );
		}
	}

	/**
	 * Starts duct. The GUI will be shown and the server kicked off
	 */
	public void start() {
		gui.show();

		server.start();
		// map the index page routes
		server.map( "/", INDEX_DIRECTORY );

		// load the saved report directories
		Stream.of( PREFS.get( SERVED_REPORT_PATHS_PREF, "" )
				.split( File.pathSeparator ) )
				.filter( s -> !s.isEmpty() )
				.map( Paths::get )
				.forEach( p -> index.put( p, null ) );

		reindex();

		expiry = Instant.now().plus( LIFESPAN );
		new Reaper( this ).start();
	}

	/**
	 * Shuts down the server and hides the GUI. The JVM will be free to exit after
	 * this.
	 */
	public void stop() {
		server.stop();
		gui.hide();
	}

	/**
	 * Extends the lifespan
	 *
	 * @return the new expiry
	 */
	public Instant heartbeat() {
		expiry = Instant.now().plus( LIFESPAN );
		LOG.debug( "beep! life extended to {}", expiry );
		return expiry;
	}

	/**
	 * Gets the time at which duct should shut down
	 *
	 * @return The expiry time
	 */
	Instant expiry() {
		return expiry;
	}

	/**
	 * Adds a report to be served
	 *
	 * @param source The report directory
	 * @return The served report URL, or <code>null</code> on failure
	 */
	public URL add( Path source ) {
		heartbeat();

		if( !Reader.isReportDir( source ) ) {
			LOG.error( "{} is not a report", source );
			return null;
		}

		LOG.info( "Adding {}", source );

		try {
			Index idx = new Reader( source ).read();
			Instant ts = Instant.ofEpochMilli( idx.meta.timestamp );

			String path = String.format( "/%s/%s/%s/",
					idx.meta.modelTitle.replaceAll( "\\W+", "_" ),
					idx.meta.testTitle.replaceAll( "\\W+", "_" ),
					ts.toString().replaceAll( "\\W+", "_" ) );

			path = "/" + source.toString().replaceAll( "\\W+", "_" ) + "/";

			server.map( path, source );
			index.put( source, new ReportSummary( idx, path ) );

			PREFS.put( SERVED_REPORT_PATHS_PREF, index.keySet().stream()
					.map( Path::toString )
					.collect( joining( File.pathSeparator ) ) );

			return new URL( "http://localhost:" + PORT + path );
		}
		catch( Exception e ) {
			LOG.error( "Failed to add {}", source, e );
			return null;
		}
	}

	/**
	 * @return The port that {@link Duct} is serving on
	 */
	int port() {
		return server.port();
	}

	/**
	 * Clears the index
	 */
	public void clearIndex() {
		index.keySet().forEach( server::unmap );
		index.clear();
		PREFS.remove( SERVED_REPORT_PATHS_PREF );
	}

	/**
	 * Regenerates the served index list
	 */
	public void reindex() {
		LOG.info( "Regenerating index" );
		List<Path> dirs = new ArrayList<>( index.keySet() );
		List<Path> toRemove = new ArrayList<>();

		// try to remap each of our existing reports
		dirs.forEach( dir -> {
			if( add( dir ) == null ) {
				// if they failed to map then we should remove it from the index
				toRemove.add( dir );
			}
		} );

		toRemove.forEach( index::remove );
	}

	/**
	 * Gets a summary of served reports
	 *
	 * @return served report summaries
	 */
	Collection<ReportSummary> index() {
		return index.values();
	}
}
