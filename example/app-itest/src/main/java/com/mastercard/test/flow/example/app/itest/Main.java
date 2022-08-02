/*
 * Copyright (c) 2022 MasterCard. All rights reserved.
 */

package com.mastercard.test.flow.example.app.itest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mastercard.test.flow.example.app.WebUi;
import com.mastercard.test.flow.example.framework.Operation;

import picocli.CommandLine;

/**
 * Convenience class that launches all the services to easily get a working
 * cluster up and running.
 */
@CommandLine.Command(name = "example",
		mixinStandardHelpOptions = true,
		version = "0.1",
		description = "Launches all services in the example application")
public class Main {

	private static final Logger LOG = LoggerFactory.getLogger( Main.class );

	/**
	 * @param args from commandline, ignored
	 */
	public static void main( String[] args ) {
		ClusterManager manager = new ClusterManager(
				com.mastercard.test.flow.example.framework.Main.DEFAULT_CLUSTER_IP,
				// Use a non-default port so that the cluster you spawn manually doesn't
				// interfere with the clusters that are spun up by the test suites
				com.mastercard.test.flow.example.framework.Main.DEFAULT_CLUSTER_PORT + 1 );
		manager.startCluster();

		LOG.info( "Cluster started with UI on port {}",
				manager.getUiPort() );
		try {
			LOG.info( "Web UI at http://localhost:{}{}",
					manager.getWebUiPort(),
					WebUi.class.getMethod( "home" ).getAnnotation( Operation.class ).path() );
		}
		catch( NoSuchMethodException | SecurityException e ) {
			LOG.warn( "Failed to determine Web UI home: {}", e.getMessage() );
		}
	}
}
