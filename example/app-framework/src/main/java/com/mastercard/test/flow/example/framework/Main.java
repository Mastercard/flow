package com.mastercard.test.flow.example.framework;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import picocli.CommandLine.Option;

/**
 * Application entrypoint superclass
 */
public class Main implements Runnable {

	/**
	 * The default IP used for service discovery multicast
	 */
	public static final String DEFAULT_CLUSTER_IP = "239.255.254.253";

	/**
	 * The default port used for service discovery multicast
	 */
	public static final int DEFAULT_CLUSTER_PORT = 6978;

	@Option(names = { "-p", "--port" },
			description = "The port number of the instance",
			defaultValue = "0")
	private int port = 0;

	@Option(names = { "-ca", "--cluster-address" },
			description = "The multicast IP address of the cluster",
			defaultValue = DEFAULT_CLUSTER_IP)
	private InetAddress clusterAddress;
	{
		try {
			clusterAddress = InetAddress.getByName( DEFAULT_CLUSTER_IP );
		}
		catch( UnknownHostException e ) {
			throw new IllegalStateException( e );
		}
	}

	@Option(names = { "-cp", "--cluster-port" },
			description = "The port number of the cluster",
			defaultValue = "" + DEFAULT_CLUSTER_PORT)
	private int clusterPort = DEFAULT_CLUSTER_PORT;

	private final List<Supplier<Service>> services;

	/**
	 * @param services How to build the services that we want to host
	 */
	@SafeVarargs
	protected Main( Supplier<Service>... services ) {
		this.services = Arrays.asList( services );
	}

	/**
	 * @param ip  The multicast IP address to use for {@link Instance}s clusters
	 * @param prt The multicast port to use for {@link Instance}s clusters
	 * @return <code>this</code>
	 */
	public Main cluster( String ip, int prt ) {
		clusterPort = prt;
		try {
			clusterAddress = InetAddress.getByName( ip );
		}
		catch( UnknownHostException e ) {
			throw new IllegalStateException( e );
		}
		return this;
	}

	/**
	 * @param p The port on which to expose the {@link Instance}
	 * @return <code>this</code>
	 */
	public Main port( int p ) {
		port = p;
		return this;
	}

	/**
	 * @return An {@link Instance} that hosts the services
	 */
	public Instance build() {
		Instance instance = new Instance( port, new InetSocketAddress( clusterAddress, clusterPort ) );
		services.forEach( s -> instance.with( s.get() ) );
		return instance;
	}

	@Override
	public void run() {
		build().start();
	}
}
