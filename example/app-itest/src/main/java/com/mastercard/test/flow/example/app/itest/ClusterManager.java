/*
 * Copyright (c) 2022 MasterCard. All rights reserved.
 */

package com.mastercard.test.flow.example.app.itest;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mastercard.test.flow.example.framework.Discovery;
import com.mastercard.test.flow.example.framework.Instance;
import com.mastercard.test.flow.example.framework.Main;

/**
 * Manages startup and shutdown of the cluster of services that form the example
 * application.
 */
public class ClusterManager {

	private static final Logger LOG = LoggerFactory.getLogger( ClusterManager.class );

	/**
	 * Allows system interaction via web browser
	 */
	public final Instance webUi;
	/**
	 * The entrypoint to the system
	 */
	public final Instance ui;
	/**
	 * Service orchestration
	 */
	public final Instance core;
	/**
	 * Where deferred tasks are stored and processed
	 */
	public final Instance queue;
	/**
	 * Data storage
	 */
	public final Instance store;
	/**
	 * Histogram computation
	 */
	public final Instance histogram;

	/**
	 * The services that comprise the application, in inverted dependency order
	 */
	private final Instance[] services;

	/**
	 * Builds a cluster, coordinating {@link Discovery} at the default address.
	 *
	 * @see Main#DEFAULT_CLUSTER_IP
	 * @see Main#DEFAULT_CLUSTER_PORT
	 */
	public ClusterManager() {
		this( Main.DEFAULT_CLUSTER_IP, Main.DEFAULT_CLUSTER_PORT );
	}

	/**
	 * Builds a cluster, coordinating {@link Discovery} at the specified address
	 *
	 * @param clusterIP   The IP of the multicast address at which the constituent
	 *                    {@link Instance}s will find each other
	 * @param clusterPort The port of the multicast address at which the constituent
	 *                    {@link Instance}s will find each other
	 */
	public ClusterManager( String clusterIP, int clusterPort ) {
		webUi = new com.mastercard.test.flow.example.app.webui.Main()
				.cluster( clusterIP, clusterPort )
				.build();
		ui = new com.mastercard.test.flow.example.app.ui.Main()
				.cluster( clusterIP, clusterPort )
				.build();
		core = new com.mastercard.test.flow.example.app.core.Main()
				.cluster( clusterIP, clusterPort )
				.build();
		queue = new com.mastercard.test.flow.example.app.queue.Main()
				.cluster( clusterIP, clusterPort )
				.build();
		store = new com.mastercard.test.flow.example.app.store.Main()
				.cluster( clusterIP, clusterPort )
				.build();
		histogram = new com.mastercard.test.flow.example.app.histogram.Main()
				.cluster( clusterIP, clusterPort )
				.build();

		services = new Instance[] { histogram, store, queue, core, ui, webUi };
	}

	/**
	 * Start all services in the cluster
	 */
	public void startCluster() {
		// service.start() blocks until a service is completely up with all
		// dependencies fulfilled. As we have dependency cycles (e.g.: core and queue
		// need each other) we have to start up all of the services at once from
		// separate threads rather than one-at-a-time from a single thread
		ExecutorService starter = new ThreadPoolExecutor(
				services.length, services.length,
				1, SECONDS, new ArrayBlockingQueue<>( services.length ) );
		for( Instance service : services ) {
			LOG.info( "starting {}", service.getClass() );
			starter.execute( service::start );
		}
		starter.shutdown();
		try {
			LOG.info( "awaiting upness" );
			if( !starter.awaitTermination( 10, TimeUnit.SECONDS ) ) {
				LOG.error( "Timed out before {} services could come up", starter.shutdownNow().size() );
			}
		}
		catch( InterruptedException e ) {
			LOG.warn( "Interrupted!", e );
			Thread.currentThread().interrupt();
		}
		LOG.info( "Startup complete" );
	}

	/**
	 * Stop all services in the cluster
	 */
	public void stopCluster() {
		// no such drama when shutting the services down, just switch off all the lights
		for( int i = services.length - 1; i >= 0; i-- ) {
			services[i].stop();
		}
	}

	/**
	 * Get the port that the UI is listening on, which is where the user will want
	 * to send requests when interacting with the application
	 *
	 * @return int port of the UI service
	 */
	public int getUiPort() {
		return ui.port();
	}

	/**
	 * Gets the port that the Web UI is listening on, which is where the user will
	 * want to browse to
	 *
	 * @return port of the web UI service
	 */
	public int getWebUiPort() {
		return webUi.port();
	}

}
