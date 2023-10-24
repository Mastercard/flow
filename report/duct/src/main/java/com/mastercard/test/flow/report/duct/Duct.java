package com.mastercard.test.flow.report.duct;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.stream.Collectors.joining;

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
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.report.data.Index;

/**
 * An application that serves flow reports
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
		// try adding via http
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
				// Thanks to our maven-shade-plugin config, the jar that provides this class
				// contains all of its dependencies. Thus we can find it via the classloader and
				// `java -jar`-invoke it to create a new instance

				// this process will persist after the demise of the current JVM
				ProcessBuilder pb = new ProcessBuilder(
						"java", "-jar",
						// The classloader knows where this class came from - inspect it to find the jar
						Paths.get( Duct.class.getProtectionDomain()
								.getCodeSource()
								.getLocation()
								.toURI() ).toAbsolutePath().toString(),
						// pass the report path on the commandline - the above main method will take
						// care of adding and browsing it
						report.toAbsolutePath().toString() );
				pb.start();
			}
			catch( URISyntaxException e ) {
				throw new IllegalStateException( "Failed to find duct jar", e );
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
		catch( Exception e ) {
			e.printStackTrace();
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

	private static final Path servedDirectory = Paths.get( System.getProperty( "java.io.tmpdir" ) )
			.resolve( "mctf_duct" );
	private static final Logger LOG;
	static {
		// logger initialisation has to happen *after* the content directory is known
		try {
			Files.createDirectories( servedDirectory );
			System.setProperty( "org.slf4j.simpleLogger.logFile",
					servedDirectory.resolve( "log.txt" ).toAbsolutePath().toString() );
			LOG = LoggerFactory.getLogger( Duct.class );
		}
		catch( Exception e ) {
			throw new IllegalStateException( "Failed to create content directory", e );
		}
	}

	private static final String INDEX_TEMPLATE = resource( "index.html" );

	private final Gui gui = new Gui( this );
	private final Server server = new Server( this, servedDirectory, PORT );
	private Instant expiry;

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
		LOG.info( "Adding {}" + source );
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
			Index index = new Reader( source ).read();

			Path sink = servedDirectory
					.resolve( index.meta.modelTitle.replaceAll( "\\W+", "_" ) )
					.resolve( index.meta.testTitle.replaceAll( "\\W+", "_" ) )
					.resolve( Instant.ofEpochMilli( index.meta.timestamp ).toString()
							.replaceAll( "\\W+", "_" ) );

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
			return new URL( "http://localhost:" + PORT + "/"
					+ servedDirectory.relativize( sink ).toString()
							.replace( '\\', '/' )
					+ "/" );
		}
		catch( Exception e ) {
			LOG.error( "Failed to add {}", source, e );
			return null;
		}
	}

	/**
	 * @return The port that duct is serving on
	 */
	int port() {
		return server.port();
	}

	/**
	 * @return The directory that holds the served content
	 */
	Path servedDirectory() {
		return servedDirectory;
	}

	/**
	 * Regenerates the served index file
	 */
	private void reindex() {
		LOG.info( "Regenerating index" );
		try {
			String content = INDEX_TEMPLATE
					.replace( "%__SERVED_DIR__%",
							servedDirectory.toAbsolutePath().toString() )
					.replace( "%__REPORT_LIST__%",
							Search.find( servedDirectory )
									.map( dir -> servedDirectory.relativize( dir ) )
									.map( path -> path.toString().replace( '\\', '/' ) )
									.map( path -> "			<li><a href=\"" + path + "/\">" + path + "</a></li>" )
									.collect( joining( "\n" ) ) );

			Files.write(
					servedDirectory.resolve( "index.html" ),
					content.getBytes( UTF_8 ) );
		}
		catch( IOException e ) {
			LOG.error( "Failed to reindex", e );
		}
	}

	private static String resource( String name ) {
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
