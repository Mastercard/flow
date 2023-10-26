package com.mastercard.test.flow.report.duct;

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;

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
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mastercard.test.flow.report.Writer;

import spark.Filter;
import spark.Route;
import spark.Service;
import spark.Spark;

/**
 * Handles the webser functionality
 */
class Server {
	private static final Logger LOG = LoggerFactory.getLogger( Server.class );
	private static final ObjectMapper JSON = new ObjectMapper()
			.enable( SerializationFeature.INDENT_OUTPUT );

	/**
	 * Restricts our server to only working with local clients. This application
	 * will merrily serve up the contents of directories, so we have to be mindful
	 * of security issues and avoid providing a data exfiltration route. Hence we're
	 * going to restrict it so that it only responds to requests coming from
	 * localhost
	 */
	private static final Filter LOCAL_ORIGIN_ONLY = ( request, response ) -> {
		LOG.info( "REQUEST TO " + request.pathInfo() );
		// SECURITY-CRITICAL BEHAVIOUR
		try {
			InetAddress addr = InetAddress.getByName( request.ip() );
			if( !addr.isLoopbackAddress() ) {
				LOG.warn( "Rejecting non-local request from {} to {}",
						addr, request.pathInfo() );
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
	 * @param duct            The instance to control
	 * @param servedDirectory The directory to serve
	 * @param port            The port to serve on
	 */
	Server( Duct duct, Path servedDirectory, int port ) {
		spark = Service.ignite()
				.port( port );

		// SECURITY-CRITICAL BEHAVIOUR
		spark.before( LOCAL_ORIGIN_ONLY );
		// Think carefully before changing this!

		spark.get( "/heartbeat",
				( req, res ) -> "Expiry at " + duct.heartbeat() );

		spark.get( "/shutdown",
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

		spark.delete( "/list", ( req, res ) -> {
			duct.clearIndex();
			return "Index cleared";
		} );

		spark.patch( "/list", ( req, res ) -> {
			duct.reindex();
			return "Index refreshed";
		} );
	}

	/**
	 * Adds routes to serve the files in the supplied directory
	 *
	 * @param path The base request path
	 * @param dir  The directory that holds the files
	 */
	void map( String path, Path dir ) {

		LOG.info( "Adding {}", dir );
		// clear any lingering paths for that directory
		unmap( dir );

		try( Stream<Path> files = Files.walk( dir, 2 ) ) {
			Set<String> requestPaths = files
					.filter( p -> !Files.isDirectory( p ) )
					.map( f -> {

						// request response just returns the mapped file bytes
						Route route = respondWithFileBytes( f );

						// map that response to the file path
						String requestPath = path + dir.relativize( f ).toString().replace( '\\', '/' );
						LOG.debug( "Routing GET {} for {}", requestPath, f );
						spark.get( requestPath, route );

						// special case for index.html - map the bare directory path to it as well
						if( requestPath.endsWith( Writer.INDEX_FILE_NAME ) ) {
							requestPath = requestPath.substring( 0,
									requestPath.length() - Writer.INDEX_FILE_NAME.length() );
							LOG.debug( "Routing GET {} for {}", requestPath, f );
							spark.get( requestPath, route );
						}

						return requestPath;
					} )
					.collect( toSet() );

			// save these in case the directory changes and we need to unmap them
			routes.put( dir, requestPaths );
			LOG.info( "Mapped {} paths under {}", requestPaths.size(), path );
		}
		catch( IOException ioe ) {
			LOG.error( "Failed to map contents of " + dir, ioe );
		}
	}

	private static Route respondWithFileBytes( Path f ) {
		return ( req, res ) -> {
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
			return ""; // vestigial, we've already written the body
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

	/**
	 * Starts the server
	 */
	void start() {
		LOG.info( "Starting server" );
		spark.init();
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
	 * @return The port that the server is running on
	 */
	int port() {
		return spark.port();
	}
}
