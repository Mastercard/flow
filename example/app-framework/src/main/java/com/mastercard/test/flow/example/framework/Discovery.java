package com.mastercard.test.flow.example.framework;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastercard.test.flow.example.framework.Service.Dependency;

/**
 * Mechanism by which {@link Instance}s can find each other so they can satisfy
 * the {@link Dependency}s of their resident {@link Service}s
 */
public class Discovery {
	private static final Logger LOG = LoggerFactory.getLogger( Discovery.class );

	private static final ObjectMapper JSON = new ObjectMapper();

	private final InetSocketAddress group;
	private long advertiseTimeout = -1;
	private MulticastSocket listenSocket;
	private long listenTimeout = -1;
	private volatile boolean shouldStop = false;

	/**
	 * @param group The multicast address on which to advertise and listen for
	 *              {@link Service}s
	 */
	public Discovery( InetSocketAddress group ) {
		this.group = group;
		LOG.trace( "Joining cluster {}", group );
	}

	/**
	 * Eventually stops the listen and advertisement threads, if they exist
	 */
	public void stop() {
		shouldStop = true;
		if( listenSocket != null ) {
			listenSocket.close();
		}
	}

	/**
	 * Controls how long our services will be advertised for
	 *
	 * @param count The number of units. Supply a negative number to advertise
	 *              forever.
	 * @param unit  The unit duration
	 * @return <code>this</code>
	 * @see #advertise(String, int, Set)
	 */
	public Discovery stoppingAfter( int count, TimeUnit unit ) {
		advertiseTimeout = unit.toMillis( count );
		return this;
	}

	/**
	 * Starts advertising the {@link Service}s offered by this {@link Instance}
	 *
	 * @param protocol The protocol to use for the advertised services
	 * @param port     The port on which the advertised services are available
	 * @param services The set of {@link Service} class names to advertise
	 * @return <code>this</code>
	 * @see #stoppingAfter(int, TimeUnit)
	 */
	public Discovery advertise( String protocol, int port, Set<String> services ) {

		Thread advertise = new Thread( () -> {
			DatagramPacket advert;
			try {
				String json = JSON.writeValueAsString( new Advert( protocol, port, services ) );
				byte[] data = json.getBytes( UTF_8 );
				advert = new DatagramPacket( data, data.length, group.getAddress(), group.getPort() );
			}
			catch( IOException e ) {
				throw new UncheckedIOException( e );
			}

			try( MulticastSocket socket = new MulticastSocket( group.getPort() ) ) {
				socket.joinGroup( group.getAddress() );

				long limit = System.currentTimeMillis() + advertiseTimeout;
				boolean limitBreached = false;
				while( !shouldStop && !limitBreached ) {
					limitBreached = advertiseTimeout > 0 && System.currentTimeMillis() > limit;

					if( LOG.isTraceEnabled() ) {
						LOG.trace( "Sending {}", new String( advert.getData(), UTF_8 ) );
					}

					socket.send( advert );

					try {
						Thread.sleep( 1000 );
					}
					catch( @SuppressWarnings("unused") InterruptedException e ) {
						// no-one cares
						Thread.currentThread().interrupt();
					}
				}
				LOG.trace( "Advertisement ended" );
			}
			catch( IOException ioe ) {
				throw new UncheckedIOException( ioe );
			}
		}, "Discovery advertise" );
		advertise.setDaemon( true );
		advertise.start();

		return this;
	}

	/**
	 * Controls how long we'll wait for dependencies to be found before giving up.
	 *
	 * @param count The number of units. Supply a negative number to wait forever.
	 * @param unit  The unit duration
	 * @return <code>this</code>
	 * @see #listen(BiPredicate)
	 */
	public Discovery abortingAfter( int count, TimeUnit unit ) {
		listenTimeout = unit.toMillis( count );
		return this;
	}

	/**
	 * Starts listening for services advertised on the cluster
	 *
	 * @param action What to do with discovered remote {@link Service}s. Return true
	 *               if all dependencies are satisfied
	 * @return <code>this</code>
	 * @see #abortingAfter(int, TimeUnit)
	 */
	public Discovery listen( BiPredicate<String, URL> action ) {

		Thread listen = new Thread( () -> {
			byte[] data = new byte[8096];
			DatagramPacket pkt = new DatagramPacket( data, data.length );
			try( MulticastSocket socket = new MulticastSocket( group.getPort() ) ) {
				listenSocket = socket;
				socket.joinGroup( group.getAddress() );
				// this will turn to true when all of our dependencies have been satisfied
				boolean satisfied = false;
				long limit = System.currentTimeMillis() + listenTimeout;
				while( !satisfied && !shouldStop ) {

					if( listenTimeout > 0 && System.currentTimeMillis() > limit ) {
						throw new IllegalStateException( "Missing dependencies after " + listenTimeout + "ms" );
					}

					socket.receive( pkt );
					if( LOG.isTraceEnabled() ) {
						LOG.trace( "Received {}", new String( data, pkt.getOffset(), pkt.getLength(), UTF_8 ) );
					}
					Advert advert = JSON.readValue( data, Advert.class );

					URL remote = new URL(
							advert.protocol,
							pkt.getAddress().getHostAddress(), advert.port(),
							"" );
					satisfied = advert.services()
							.anyMatch( svc -> action.test( svc, remote ) );
				}

				LOG.debug( "Listen shutdown" );
			}
			catch( SocketException se ) {
				if( !shouldStop ) {
					throw new UncheckedIOException( se );
				}
			}
			catch( IOException ioe ) {
				throw new UncheckedIOException( ioe );
			}
		}, "Discovery listen" );
		listen.setDaemon( true );
		listen.start();

		return this;
	}

	private static class Advert {
		@JsonProperty("protocol")
		public final String protocol;
		@JsonProperty("port")
		public final int port;
		@JsonProperty("services")
		public final Set<String> services;

		public Advert(
				@JsonProperty("protocol") String protocol,
				@JsonProperty("port") int port,
				@JsonProperty("services") Set<String> services ) {
			this.protocol = protocol;
			this.port = port;
			this.services = services;
		}

		public int port() {
			return port;
		}

		public Stream<String> services() {
			return services.stream();
		}

		@Override
		public String toString() {
			return port + ":" + services;
		}
	}
}
