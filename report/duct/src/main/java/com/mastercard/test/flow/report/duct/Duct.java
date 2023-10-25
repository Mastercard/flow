package com.mastercard.test.flow.report.duct;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.stream.Collectors.toList;

import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.report.Writer;
import com.mastercard.test.flow.report.data.Meta;

/**
 * An application that lives in the system tray and serves flow reports.
 */
public class Duct {

	/**
	 * Application entrypoint
	 *
	 * @param args List of report paths (absolute) to serve and browse
	 * @throws Exception on failure
	 */
	public static void main( String[] args ) throws Exception {
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
	 * @return The URL of the served report, or <code>null</code> if there was no
	 *         existing duct instance
	 */
	private static URL tryAdd( Path report ) {
		try {
			HttpURLConnection c = (HttpURLConnection) new URL( "http://localhost:" + PORT + "/add" )
					.openConnection();
			// we want to fail quickly if there is no duct instance
			c.setConnectTimeout( 250 );
			// but a large report might take a non-trivial duration to copy
			c.setReadTimeout( 10000 );
			c.setRequestMethod( "POST" );
			c.setDoOutput( true );
			try( OutputStream os = c.getOutputStream() ) {
				os.write( report.toAbsolutePath().toString().getBytes( UTF_8 ) );
			}
			int rc = c.getResponseCode();
			String body;
			try( InputStream in = c.getInputStream();
					InputStream er = c.getErrorStream(); ) {
				body = read( rc < HttpURLConnection.HTTP_BAD_REQUEST
						? in
						: er );
			}
			return new URL( body );
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
	 * The directory that holds our served content
	 */
	static final Path SERVED_DIRECTORY = Paths.get( System.getProperty( "java.io.tmpdir" ) )
			.resolve( "mctf_duct" );

	private static final Logger LOG;
	static {
		// logger initialisation has to happen *after* the content directory is known
		try {
			Files.createDirectories( SERVED_DIRECTORY );
			System.setProperty( "org.slf4j.simpleLogger.logFile",
					SERVED_DIRECTORY.resolve( "log.txt" ).toAbsolutePath().toString() );
			LOG = LoggerFactory.getLogger( Duct.class );

			Writer.writeDuctIndex( SERVED_DIRECTORY );
		}
		catch( Exception e ) {
			throw new IllegalStateException( "Failed to create content directory", e );
		}
	}

	private final Gui gui = new Gui( this );
	private final Server server = new Server( this, SERVED_DIRECTORY, PORT );
	private Instant expiry = Instant.now();
	private List<ReportSummary> index = new ArrayList<>();

	/**
	 * Starts the server, add the system tray icon
	 *
	 * @throws Exception on failure
	 */
	public void start() throws Exception {
		server.start();
		reindex();
		gui.show();

		expiry = Instant.now().plus( LIFESPAN );
		new Reaper( this ).start();
	}

	/**
	 * Shuts down the server
	 */
	public void stop() {
		gui.hide();
		server.stop();
	}

	/**
	 * Extends the lifespan
	 *
	 * @return the new expiry
	 */
	public Instant heartbeat() {
		expiry = Instant.now().plus( LIFESPAN );
		LOG.info( "beep! life extended to {}", expiry );
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
		LOG.info( "Adding {}", source );
		if( !Files.exists( source ) ) {
			LOG.error( "Nothing found at {}", source );
			return null;
		}

		if( !Files.isDirectory( source ) ) {
			LOG.error( "{} is not a directory", source );
			return null;
		}

		if( !Files.exists( source.resolve( "index.html" ) ) ) {
			LOG.error( "{} has no index", source );
			return null;
		}

		try {
			Meta meta = new Reader( source ).read().meta;
			Instant ts = Instant.ofEpochMilli( meta.timestamp );

			// structure is rooted at date so it's easy to, e.g.: delete all your elderly
			// reports
			Path sink = SERVED_DIRECTORY
					.resolve( DateTimeFormatter.ISO_LOCAL_DATE
							.withZone( ZoneId.systemDefault() )
							.format( ts ) )
					.resolve( meta.modelTitle.replaceAll( "\\W+", "_" ) )
					.resolve( (DateTimeFormatter.ISO_LOCAL_TIME
							.withZone( ZoneId.systemDefault() )
							.format( ts ) + "_" + meta.testTitle)
									.replaceAll( "\\W+", "_" ) );

			if( Files.exists( sink ) ) {
				// it seems unlikely that we'd have two distinct reports with the same model and
				// test names and the same second-precise timestamp. Let's avoid the IO load
				LOG.info( "Skippping copy for already-existing report" );
			}
			else {
				LOG.info( "Copying to {} ", sink );
				Files.createDirectories( sink );
				try( Stream<Path> files = Files.walk( source ) ) {
					files.forEach( from -> {
						Path to = sink.resolve( source.relativize( from ) );
						try {
							Files.copy( from, to, REPLACE_EXISTING );
						}
						catch( IOException e ) {
							LOG.error( "Failed to copy {} to {}", from, to, e );
						}
					} );
				}
				reindex();
			}
			return new URL( "http://localhost:" + PORT + "/"
					+ SERVED_DIRECTORY.relativize( sink ).toString()
							.replace( '\\', '/' )
					+ "/" );
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
	 * Regenerates the served index list
	 */
	public void reindex() {
		LOG.info( "Regenerating index" );
		index = Search.find( SERVED_DIRECTORY )
				.map( path -> new ReportSummary( new Reader( path ).read(),
						SERVED_DIRECTORY
								.relativize( path )
								.toString().replace( '\\', '/' ) ) )
				.collect( toList() );
	}

	/**
	 * Gets a summary of served reports
	 *
	 * @return served report summaries
	 */
	List<ReportSummary> index() {
		return index;
	}

	/**
	 * Loads a classpath resource
	 *
	 * @param name The resource name
	 * @return resource content
	 */
	static String resource( String name ) {
		try( InputStream resource = Duct.class.getClassLoader()
				.getResourceAsStream( name ); ) {
			return read( resource );
		}
		catch( IOException ioe ) {
			throw new UncheckedIOException( ioe );
		}
	}

	private static String read( InputStream is ) {
		try( ByteArrayOutputStream data = new ByteArrayOutputStream() ) {
			byte[] buff = new byte[1024];
			int read = 0;
			while( (read = is.read( buff )) >= 0 ) {
				data.write( buff, 0, read );
			}
			return new String( data.toByteArray(), UTF_8 );
		}
		catch( IOException ioe ) {
			throw new UncheckedIOException( ioe );
		}
	}
}
