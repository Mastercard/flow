package com.mastercard.test.flow.report.duct;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import spark.Filter;
import spark.Service;
import spark.Spark;
import spark.staticfiles.StaticFilesConfiguration;

/**
 * Handles the webser functionality
 */
class Server {
	private static final Logger LOG = LoggerFactory.getLogger( Server.class );
	private static final ObjectMapper JSON = new ObjectMapper()
			.enable( SerializationFeature.INDENT_OUTPUT );

	/**
	 * Restricts our server to only working with local clients. This application
	 * will merrily serve up the contents of a directory, so we have to be mindful
	 * of security issues and avoid providing an exfiltration route. Hence we're
	 * going to restrict our server so that it only responds to requests coming from
	 * localhost
	 */
	private static final Filter LOCAL_ORIGIN_ONLY = ( request, response ) -> {
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

	private Service spark;

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

		// we have to handle static files like this rather than
		// spark.externalStaticFileLocation( ) in order for our IP filter to be applied
		StaticFilesConfiguration sfc = new StaticFilesConfiguration();
		sfc.configureExternal( servedDirectory.toAbsolutePath().toString() );
		sfc.putCustomHeader( "Access-Control-Allow-Origin", "*" );
		spark.before( ( req, res ) -> {
			if( sfc.consume( req.raw(), res.raw() ) ) {
				// the request is for one of our static files, we can stop here and avoid loads
				// of unmapped-path logging
				Spark.halt();
			}
		} );

		spark.get( "/heartbeat",
				( req, res ) -> "Expiry at " + duct.heartbeat() );

		spark.get( "/shutdown",
				( req, res ) -> {
					duct.stop();
					return "Shutting down";
				} );

		spark.post( "/add",
				( req, res ) -> duct.add( Paths.get( req.body().trim() ) ).toString() );

		spark.get( "/list",
				( req, res ) -> duct.index(), JSON::writeValueAsString );
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
