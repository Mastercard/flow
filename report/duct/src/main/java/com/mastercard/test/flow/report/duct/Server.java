package com.mastercard.test.flow.report.duct;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import spark.Service;

/**
 * Handles the webser functionality
 */
class Server {
	private static final Logger LOG = LoggerFactory.getLogger( Server.class );
	private static final ObjectMapper JSON = new ObjectMapper()
			.enable( SerializationFeature.INDENT_OUTPUT );

	private Service spark;

	Server( Duct duct, Path servedDirectory, int port ) {
		spark = Service.ignite()
				.port( port )
				.externalStaticFileLocation( servedDirectory.toAbsolutePath().toString() );
		spark.staticFiles.header( "Access-Control-Allow-Origin", "*" );

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

	void start() {
		LOG.info( "Starting server" );
		spark.init();
		spark.awaitInitialization();
	}

	void stop() {
		LOG.info( "Stopping server" );
		spark.stop();
	}

	int port() {
		return spark.port();
	}
}
