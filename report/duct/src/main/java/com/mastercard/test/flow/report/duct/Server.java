package com.mastercard.test.flow.report.duct;

import static com.mastercard.test.flow.report.Writer.DETAIL_DIR_NAME;
import static java.util.Collections.emptySet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import spark.Filter;
import spark.Route;
import spark.Service;
import spark.Spark;
import spark.resource.ExternalResource;
import spark.staticfiles.MimeType;

/**
 * Handles the webser functionality
 */
class Server {
	private static final Logger LOG = LoggerFactory.getLogger( Server.class );
	private static final ObjectMapper JSON = new ObjectMapper()
			.enable( SerializationFeature.INDENT_OUTPUT );

	/**
	 * Restricts our server to only working with local clients. Duct will merrily
	 * serve up the contents of directories, so we have to be mindful of security
	 * issues and avoid providing a data exfiltration route. Hence we're going to
	 * restrict it so that it only responds to requests coming from localhost
	 */
	private static final Filter LOCAL_ORIGIN_ONLY = ( request, response ) -> {
		// SECURITY-CRITICAL BEHAVIOUR
		try {
			InetAddress addr = InetAddress.getByName( request.ip() );
			if( !addr.isLoopbackAddress() ) {
				if( LOG.isWarnEnabled() ) {
					LOG.warn( "Rejecting non-local request from {} to {}",
							addr, request.pathInfo() );
				}
				Spark.halt( 403 );
			}
		}
		catch( UnknownHostException | SecurityException e ) {
			LOG.error( "Failed to determine origin of {}, defaulting to rejection",
					request.ip(), e );
			Spark.halt( 403 ); // never let them see you bleed
		}
		// Think carefully before changing this!
	};

	private final Service spark;
	private final Map<Path, Set<String>> routes = new HashMap<>();

	/**
	 * @param duct The instance to control
	 * @param port The port to serve on
	 */
	Server( Duct duct, int port ) {
		spark = Service.ignite()
				.port( port );

		// SECURITY-CRITICAL BEHAVIOUR
		spark.before( LOCAL_ORIGIN_ONLY );
		// Think carefully before changing this!

		spark.get( "/heartbeat",
				( req, res ) -> "Expiry at " + duct.heartbeat() );

		spark.post( "/shutdown",
				( req, res ) -> {
					duct.stop();
					return "Shutting down";
				} );

		spark.post( "/add",
				( req, res ) -> {
					Path reportDir = Paths.get( req.body().trim() );
					return duct.add( reportDir );
				} );

		spark.get( "/list",
				( req, res ) -> duct.index(), JSON::writeValueAsString );
	}

	/**
	 * Starts the server
	 */
	void start() {
		LOG.info( "Starting server" );
		spark.awaitInitialization();
	}

	/**
	 * Stops the server
	 */
	void stop() {
		LOG.info( "Stopping server" );
		spark.stop();
	}

	/**
	 * Stops the server and doesn't return untill it's dead
	 */
	void awaitStop() {
		stop();
		spark.awaitStop();
	}

	/**
	 * @return The port that the server is running on
	 */
	int port() {
		return spark.port();
	}

	/**
	 * Adds routes to serve the files in the supplied directory. The mapping
	 * behaviour is specific to the report structure:
	 * <ul>
	 * <li>An <code>index.html</code> file at the root</li>
	 * <li>A <code>res</code> directory that already contains all the files it ever
	 * will</li>
	 * <li>a <code>detail</code> directory that exists, but there might be more
	 * details files added to it later.</li>
	 * </ul>
	 *
	 * @param path The base request path
	 * @param dir  The directory that holds the files
	 */
	void map( String path, Path dir ) {

		LOG.info( "Mapping {}", dir );

		// clear any lingering paths for that directory
		unmap( dir );
		Set<String> paths = new TreeSet<>();

		String requestPath = path + dir.relativize( dir ).toString().replace( '\\', '/' );
		Path idxp = dir.resolve( "index.html" );

		if( !Files.exists( idxp ) ) {
			LOG.warn( "No index found at {}", idxp );
			return;
		}

		Route idxr = respondWithFileBytes( idxp );
		// special treatment for the index: serve it when the directory is requested
		Stream.of( requestPath, requestPath + "index.html" )
				.forEach( getPath -> {
					LOG.info( "Routing GET {} to {}", getPath, idxp );
					paths.add( getPath );
					spark.get( getPath, idxr );
				} );

		Path resp = dir.resolve( "res" );
		if( Files.isDirectory( resp ) ) {
			try( Stream<Path> resFiles = Files.list( resp ) ) {
				resFiles
						.filter( Files::isRegularFile )
						.filter( f -> f.toString().endsWith( ".js" )
								|| f.endsWith( "favicon.ico" )
								|| f.toString().endsWith( ".css" ) )
						.map( p -> {
							String getPath = requestPath + "res/" + p.getFileName().toString();
							LOG.debug( "Routing GET {} to {}", getPath, idxp );
							spark.get( getPath, respondWithFileBytes( p ) );
							return getPath;
						} )
						.forEach( paths::add );
			}
			catch( IOException ioe ) {
				LOG.error( "Failed to map resource directory {}", resp, ioe );
			}
		}

		Path detp = dir.resolve( DETAIL_DIR_NAME );
		if( Files.isDirectory( detp ) ) {
			String getPath = requestPath + DETAIL_DIR_NAME + "/*";
			LOG.debug( "Routing GET {} to detail files in {}", getPath, detp );
			paths.add( getPath );
			spark.get( getPath, ( req, res ) -> {
				if( req.splat()[0].matches( "[A-F0-9]{32}.html" ) ) {
					Path file = dir.resolve( DETAIL_DIR_NAME ).resolve( req.splat()[0] );
					return respondWithFileBytes( file ).handle( req, res );
				}
				LOG.warn( "Rejecting request for non-detail file {}", req.pathInfo() );
				res.status( 404 );
				return "";
			} );
		}

		// save the mapped paths so we can unmap them later
		routes.put( dir, paths );
		LOG.info( "Mapped {} paths under {}", paths.size(), path );
	}

	private static Route respondWithFileBytes( Path f ) {
		return ( req, res ) -> {
			if( !Files.isRegularFile( f ) ) {
				res.status( 404 );
				return "";
			}

			res.header( "Content-Type", MimeType.fromResource(
					new ExternalResource( f.toString() ) ) );

			byte[] buff = new byte[8192];
			try( InputStream is = Files.newInputStream( f );
					OutputStream os = res.raw().getOutputStream(); ) {
				int read;
				while( (read = is.read( buff )) != -1 ) {
					os.write( buff, 0, read );
				}
			}
			catch( IOException ioe ) {
				LOG.error( "Failed GET {} {}", req.pathInfo(), f, ioe );
				res.status( 500 );
			}
			return null; // vestigial, we've already written the body
		};
	}

	/**
	 * Removes the routes that were serving the files in a directory
	 *
	 * @param dir The directory that holds the files
	 */
	void unmap( Path dir ) {
		routes.getOrDefault( dir, emptySet() )
				.forEach( path -> {
					LOG.debug( "unmapping {}", path );
					spark.unmap( path );
				} );
	}
}
