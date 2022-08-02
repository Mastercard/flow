package com.mastercard.test.flow.example.app.assrt;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mastercard.test.flow.example.framework.Discovery;
import com.mastercard.test.flow.example.framework.Instance;
import com.mastercard.test.flow.example.framework.Main;
import com.mastercard.test.flow.example.framework.Service;

/**
 * This class will pretend to be an {@link Instance} in a cluster - it'll
 * advertise services and accept socket requests, but its behaviour is
 * controlled by the test
 */
public class MockInstance {
	private static final Logger LOG = LoggerFactory.getLogger( MockInstance.class );

	private InetAddress clusterAddress;
	{
		try {
			clusterAddress = InetAddress.getByName( Main.DEFAULT_CLUSTER_IP );
		}
		catch( UnknownHostException e ) {
			throw new IllegalStateException( e );
		}
	}
	private int clusterPort = Main.DEFAULT_CLUSTER_PORT;
	private ServerSocket serverSocket;
	private Discovery discovery;
	private boolean shouldStop = false;

	/**
	 * Cluster that this mock will be a part of
	 *
	 * @param ip  multicast IP address
	 * @param prt port number
	 * @return <code>this</code>
	 */
	public MockInstance cluster( String ip, int prt ) {
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
	 * Advertises {@link Service}s to the cluster, starts a thread that will handle
	 * requests for those services
	 *
	 * @param services  The service to advertise
	 * @param port      The port to advertise the services being available on. Pass
	 *                  zero to find an available port
	 * @param behaviour How to formulate a service response, given request bytes
	 */
	public void start( Set<String> services, int port, Function<byte[], byte[]> behaviour ) {

		// we have to hand the client socket to a child thread for processing, so we
		// can't close it in the thread where we got it, hence...
		@SuppressWarnings("resource")
		Thread serverThread = new Thread( () -> {
			try( ServerSocket server = new ServerSocket( port ) ) {
				LOG.info( "Listening on {}", server.getLocalPort() );
				serverSocket = server; // save the ref so we can shut it down
				discovery = new Discovery( new InetSocketAddress( clusterAddress, clusterPort ) );
				discovery.advertise( "http", server.getLocalPort(), MockService.SERVICE_CLASS_NAMES );

				while( !shouldStop ) {
					try {
						handle( server.accept(), behaviour );
					}
					catch( SocketException se ) {
						if( shouldStop ) {
							// expected consequence of stop()
						}
						else {
							throw new UncheckedIOException( "unexpected closure!", se );
						}
					}
				}
			}
			catch( IOException ioe ) {
				throw new UncheckedIOException( ioe );
			}
		}, "mock instance" );
		serverThread.setDaemon( true );
		serverThread.start();
	}

	private static void handle( Socket socket, Function<byte[], byte[]> behaviour ) {
		String name = String.format( "%s:%s", socket.getInetAddress(), socket.getPort() );
		Thread handler = new Thread( () -> {
			try( Socket client = socket;
					InputStream in = client.getInputStream();
					OutputStream out = client.getOutputStream() ) {
				LOG.info( "Connection from {}", name );

				while( true ) {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					byte[] buff = new byte[1024];
					int read = -1;
					while( !httpRequestComplete( baos.toByteArray() )
							&& (read = in.read( buff )) != -1 ) {
						baos.write( buff, 0, read );
					}

					byte[] request = baos.toByteArray();
					byte[] response = behaviour.apply( request );
					if( LOG.isInfoEnabled() ) {
						LOG.info( "Request\n{}", new String( request, UTF_8 ) );
						LOG.info( "Response\n{}", new String( response, UTF_8 ) );
					}
					out.write( response );
					out.flush();
				}
			}
			catch( IOException ioe ) {
				LOG.warn( "On client " + name, ioe );
			}
		}, "Handling client " + name );
		handler.setDaemon( true );
		handler.start();
	}

	private static final Pattern CONTENT_LENGTH = Pattern.compile( "Content-Length: (\\d+)" );

	/**
	 * {@link HttpURLConnection} likes to leave the socket connected for subsequent
	 * requests, so the {@link InputStream#read()} never returns -1. Thus we have to
	 * look at the content of the HTTP request to know if we should stop reading and
	 * handle the data we have
	 *
	 * @param bytes The bytes read from a socket
	 * @return <code>true</code> if we have all of the expected bytes of a complete
	 *         http request
	 */
	private static boolean httpRequestComplete( byte[] bytes ) {
		String request = new String( bytes, UTF_8 );
		Matcher clm = CONTENT_LENGTH.matcher( request );
		int endOfHeader = request.indexOf( "\r\n\r\n" );
		if( endOfHeader != -1 ) {
			if( clm.find() ) {
				int expected = Integer.parseInt( clm.group( 1 ) );
				int actual = bytes.length - endOfHeader - 4;
				return actual >= expected;
			}
			else if( request.contains( "Transfer-Encoding: chunked" ) ) {
				throw new UnsupportedOperationException(
						"Need to implement the recognition of chunked transfers" );
			}
			// we've seen the end of the header, but have no content length nor has chunked
			// encoding been flagged
			// it's probably safe at this point to assume that there is no body
			return true;
		}
		return false;
	}

	/**
	 * Starts mocking standard HTTP-using services
	 *
	 * @param service mock behaviour
	 */
	public void start( MockService service ) {
		start( MockService.SERVICE_CLASS_NAMES, 0, service );
	}

	/**
	 * Stops the mock instance server and discovery threads
	 */
	public void stop() {
		shouldStop = true;
		if( discovery != null ) {
			discovery.stop();
		}
		if( serverSocket != null ) {
			try {
				serverSocket.close();
			}
			catch( IOException e ) {
				throw new UncheckedIOException( e );
			}
		}
	}
}
