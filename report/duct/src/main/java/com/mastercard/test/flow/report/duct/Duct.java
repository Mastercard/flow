package com.mastercard.test.flow.report.duct;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Service;

/**
 * An application that serves flow reports
 */
public class Duct {

	/**
	 * Application entrypoint
	 *
	 * @param args List of report paths (absolute) to serve
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
	 * Makes a best-effort attempt at serving a report via duct and opening a
	 * browser to it. This might involve starting a fresh instance of the duct
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
			Path jarSrc = Stream.of( System.getProperty( "java.class.path" )
					.split( File.pathSeparator ) )
					.filter( p -> p.matches( ".*duct-.*\\.jar" ) )
					.findAny()
					.map( Paths::get )
					.orElseThrow( () -> new IllegalStateException(
							"Failed to find duct jar" ) );

			ProcessBuilder pb = new ProcessBuilder(
					"java", "-jar",
					jarSrc.toAbsolutePath().toString(),
					report.toAbsolutePath().toString() );
			try {
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
		catch( Exception e ) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * How long a duct instance will live without heartbeat requests
	 */
	private static final Duration LIFESPAN = Duration.of( 90, ChronoUnit.SECONDS );

	/**
	 * The port that Duct will serve on
	 */
	public static final int PORT = 2276;

	private static final Path servedDirectory = Paths.get( System.getProperty( "java.io.tmpdir" ) )
			.resolve( "mctf_duct" );
	private static final Logger LOG;
	static {
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

	private final TrayIcon trayIcon;
	private Service service;
	private Instant expiry;

	/**
	 * @param servedDirectory The directory to serve reports from
	 */
	public Duct() {
		try {
			Image icon = ImageIO.read( Duct.class.getClassLoader().getResource( "duct.png" ) );
			trayIcon = new TrayIcon( icon, "duct", buildMenu() );
			Dimension d = trayIcon.getSize();
			// manually resize so we can use smooth scaling
			trayIcon.setImage( icon.getScaledInstance( d.width, d.height, Image.SCALE_SMOOTH ) );
		}
		catch( Exception e ) {
			LOG.error( "Failed to read icon", e );
			throw new IllegalStateException( e );
		}
	}

	private PopupMenu buildMenu() {
		PopupMenu menu = new PopupMenu();

		MenuItem index = new MenuItem( "Duct index" );
		index.addActionListener( ev -> {
			try {
				Desktop.getDesktop().browse( new URI( "http://localhost:" + service.port() ) );
			}
			catch( Exception e ) {
				LOG.error( "Failed to provoke browser", e );
			}
		} );
		menu.add( index );

		MenuItem add = new MenuItem( "Add report..." );
		add.addActionListener( ev -> {
			JFileChooser chooser = new JFileChooser();
			chooser.setFileSelectionMode( JFileChooser.DIRECTORIES_ONLY );

			int result = chooser.showDialog( null, "duct: Serve report" );
			if( result == JFileChooser.APPROVE_OPTION ) {
				URL serving = add( chooser.getSelectedFile().toPath() );
				try {
					Desktop.getDesktop().browse( serving.toURI() );
				}
				catch( Exception e ) {
					LOG.error( "Failed to provoke browser", e );
				}
			}
		} );
		menu.add( add );

		menu.addSeparator();

		MenuItem exit = new MenuItem( "Exit" );
		exit.addActionListener( ev -> stop() );
		menu.add( exit );

		return menu;
	}

	/**
	 * Starts the server, add the system tray icon
	 *
	 * @throws Exception on failure
	 */
	public void start() throws Exception {
		service = Service.ignite()
				.port( PORT )
				.externalStaticFileLocation( servedDirectory.toAbsolutePath().toString() );
		service.staticFiles.header( "Access-Control-Allow-Origin", "*" );

		service.get( "/heartbeat",
				( req, res ) -> "Expiry at " + heartbeat() );

		service.get( "/shutdown",
				( req, res ) -> {
					stop();
					return "Shutting down";
				} );

		service.post( "/add",
				( req, res ) -> add( Paths.get( req.body().trim() ) ).toString() );

		service.init();
		service.awaitInitialization();

		reindex();

		SystemTray.getSystemTray().add( trayIcon );

		expiry = Instant.now().plus( LIFESPAN );
		Thread reaper = new Thread( () -> {
			Duration delay = Duration.between( Instant.now(), expiry );
			while( !delay.isNegative() ) {
				try {
					Thread.sleep( delay.toMillis() );
				}
				catch( InterruptedException e ) {
					LOG.warn( "unexpected interruption", e );
					Thread.currentThread().interrupt();
				}
				delay = Duration.between( Instant.now(), expiry );
			}

			stop();
		}, "duct reaper" );
		reaper.setDaemon( true );
		reaper.start();
	}

	/**
	 * Shuts down the server
	 */
	public void stop() {
		SystemTray.getSystemTray().remove( trayIcon );
		service.stop();
	}

	/**
	 * Extends the lifespan
	 *
	 * @return the new expiry
	 */
	public Instant heartbeat() {
		expiry = Instant.now().plus( LIFESPAN );
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
			Path sink = servedDirectory.resolve( Instant.now().toString()
					.replaceAll( "\\W+", "_" ) );
			Files.createDirectories( sink );
			try( Stream<Path> files = Files.walk( source ) ) {
				files.forEach( from -> {
					Path to = sink.resolve( source.relativize( from ) );
					try {
						Files.copy( from, to );
					}
					catch( IOException e ) {
						LOG.error( "Failed to copy {} to {}", from, to, e );
					}
				} );
			}
			reindex();
			return new URL( "http://localhost:" + PORT + "/" + servedDirectory.relativize( sink ) + "/" );
		}
		catch( Exception e ) {
			LOG.error( "Failed to add {}", source, e );
			return null;
		}
	}

	/**
	 * Regenerates the served index file
	 */
	private void reindex() {
		try {
			String content = INDEX_TEMPLATE
					.replace( "%__SERVED_DIR__%",
							servedDirectory.toAbsolutePath().toString() )
					.replace( "%__REPORT_LIST__%",
							Files.list( servedDirectory )
									.filter( Files::isDirectory )
									.filter( d -> {
										try {
											return Files.list( d )
													.anyMatch( c -> "index.html".equals( c.getFileName().toString() ) );
										}
										catch( IOException e ) {
											LOG.error( "Failed to list {}", d, e );
											return false;
										}
									} )
									.map( d -> servedDirectory.relativize( d ) )
									.map( d -> "  <li><a href=\"" + d + "/\">" + d + "</a></li>\n" )
									.collect( joining() ) );

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
