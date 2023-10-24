package com.mastercard.test.flow.report.duct;

import java.nio.file.Path;
import java.nio.file.Paths;

import spark.Service;

/**
 * Handles the webser functionality
 */
class Server {

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
	}

	void start() {
		spark.init();
		spark.awaitInitialization();
	}

	void stop() {
		spark.stop();
	}

	int port() {
		return spark.port();
	}
}
