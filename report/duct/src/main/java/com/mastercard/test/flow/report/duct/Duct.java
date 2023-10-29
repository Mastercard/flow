package com.mastercard.test.flow.report.duct;

import static com.mastercard.test.flow.report.FailureSink.SILENT;
import static java.util.stream.Collectors.joining;

import java.awt.GraphicsEnvironment;
import java.io.File;
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
import java.util.Objects;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mastercard.test.flow.report.FailureSink;
import com.mastercard.test.flow.report.LocalBrowse;
import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.report.Writer;
import com.mastercard.test.flow.report.data.Index;
import com.mastercard.test.flow.report.duct.HttpClient.Response;
import com.mastercard.test.flow.util.Option;

/**
 * An application that lives in the system tray and serves flow reports.
 */
public class Duct {

	/**
	 * Will be supplied with diagnostic data for duct initialisation failures. This
	 * framework does not assume that clients use a logging framework, and it tries
	 * to keep silent on stdout. <i>This</i> class uses slf4j for the bulk of
	 * operations, but those will be running in a different process to the test. The
	 * interaction between the test and the duct process (where we can't use slf4j)
	 * does a bunch of failure-prone things though, so it's nice to have the option
	 * of seeing the issues when you're wondering why your report is not being
	 * served.
	 */
	private static FailureSink debug = SILENT;

	/**
	 * Allows insight into failures of duct launch behaviour
	 *
	 * @param sink Will be supplied with duct launch failure diagnostics
	 */
	public static void debuggingTo( FailureSink sink ) {
		debug = sink;
	}

	/**
	 * Allows control over whether a duct gui is shown or not
	 */
	public static final Option GUI_SUPPRESS = new Option.Builder()
			.property( "mctf.suppress.duct.gui" )
			.description( "Supply 'true' to suppress the duct gui" );

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
	 * @param args List of report paths to serve and browse
	 */
	public static void main( String... args ) {
		Duct duct = new Duct();
		duct.start();
		Stream.of( args )
				.map( Paths::get )
				.map( Path::toAbsolutePath )
				.map( duct::add )
				.filter( Objects::nonNull )
				.forEach( served -> LocalBrowse.WITH_AWT.to( served, debug ) );
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
			LocalBrowse.WITH_AWT.to( added, debug );
		}
		else {
			Spawn.launchFor( report, debug );
		}
	}

	/**
	 * Attempts to add a report to an existing duct instance
	 *
	 * @return The URL of the served report, or <code>null</code> if the request
	 *         failed, perhaps because there <i>was</i> no existing duct instance
	 */
	private static URL tryAdd( Path report ) {
		Response<String> res = HttpClient.request(
				"http://localhost:" + PORT + "/add",
				"POST",
				report.toAbsolutePath().toString() );

		if( res.code != 200 ) {
			debug.log( "Unsuccessful addition response\n:{}", res );
			return null;
		}

		if( !res.body.matches( "[\\w/]+" ) ) {
			debug.log( "Declining to browse dubious path '{}", res.body );
			return null;
		}

		try {
			return new URL( String.format( "http://localhost:%s/%s", PORT, res.body ) );
		}
		catch( Exception e ) {
			debug.log( "Failed to parse '{}' as a url", res.body, e );
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
	private final Server server = new Server( this, PORT );
	private Instant expiry = Instant.now();
	private Map<Path, ReportSummary> index = new HashMap<>();

	/**
	 * Constructs a new {@link Duct} instance
	 */
	public Duct() {
		if( GUI_SUPPRESS.isTrue() || GraphicsEnvironment.isHeadless() ) {
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
	 * @return The served report path, or <code>null</code> on failure
	 */
	public String add( Path source ) {
		heartbeat();

		if( !Reader.isReportDir( source ) ) {
			LOG.error( "{} is not a report", source );
			return null;
		}

		LOG.info( "Adding {}", source );

		try {
			Index idx = new Reader( source ).read();

			String servedPath = String.format( "/%s/", source.toString().replaceAll( "\\W+", "_" ) );
			server.map( servedPath, source );
			index.put( source, new ReportSummary( idx, servedPath ) );

			PREFS.put( SERVED_REPORT_PATHS_PREF, index.keySet().stream()
					.map( Path::toString )
					.collect( joining( File.pathSeparator ) ) );

			return servedPath;
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
