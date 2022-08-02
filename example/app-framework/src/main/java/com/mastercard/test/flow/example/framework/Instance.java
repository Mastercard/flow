package com.mastercard.test.flow.example.framework;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Route;

/**
 * Embeds a number of {@link Service}s in a HTTP service that exchanges JSON
 */
public class Instance {
	private static final Logger LOG = LoggerFactory.getLogger( Instance.class );

	private final InetSocketAddress cluster;
	private final spark.Service sparkService = spark.Service.ignite();
	private final Map<String, BiConsumer<String, Route>> methods = new HashMap<>();
	{
		methods.put( "GET", sparkService::get );
		methods.put( "PUT", sparkService::put );
		methods.put( "POST", sparkService::post );
		methods.put( "DELETE", sparkService::delete );
	}
	private final List<Service> services = new ArrayList<>();

	private boolean broken = false;
	private boolean down = false;

	/**
	 * @param port    The port to run this {@link Instance} on
	 * @param cluster The cluster ID. {@link Instance}s with the same cluster ID
	 *                will be able to find each other and cooperate
	 */
	public Instance( int port, InetSocketAddress cluster ) {
		sparkService.port( port );
		this.cluster = cluster;
	}

	/**
	 * @param service The {@link Service} to add to this {@link Instance}
	 * @return <code>this</code>
	 */
	public Instance with( Service service ) {
		services.add( service );

		return this;
	}

	/**
	 * @param <T>  the service type
	 * @param type the service type
	 * @return The resident instance of that service type
	 */
	public <T extends Service> T get( Class<T> type ) {
		return services.stream()
				.filter( type::isInstance )
				.findFirst()
				.map( type::cast )
				.orElse( null );
	}

	/**
	 * Ensures that the {@link Instance} is started
	 *
	 * @return <code>this</code>
	 */
	public Instance start() {

		sparkService.before( ( req, res ) -> {
			if( down ) {
				// should outlast the client timeout
				Thread.sleep( 6_000 );
			}
			if( down || broken ) {
				sparkService.halt( 500, "The service has been configured to be broken!" );
			}
		} );

		sparkService.exception( Exception.class, ( exception, request, response ) -> {
			if( LOG.isErrorEnabled() ) {
				LOG.error( String.format( "Failed to handle %s %s\n%s\n\n%s",
						request.requestMethod(), request.pathInfo(),
						request.headers().stream()
								.map( n -> n + ":" + request.headers( n ) + "\n" )
								.collect( joining() ),
						request.body() ),
						exception );
			}
		} );

		// build the spark endpoints
		services.forEach( service -> {
			Operations.get( service ).forEach( operation -> {
				Operation op = operation.getAnnotation( Operation.class );
				if( !methods.containsKey( op.method() ) ) {
					throw new IllegalStateException( "unsupported method " + op.method() );
				}

				LOG.info( "Mapping {} {} to {}", op.method(), op.path(), operation );
				methods.get( op.method() ).accept(
						op.path(),
						( req, res ) -> {
							res.type( op.resContentType() );
							if( LOG.isDebugEnabled() ) {
								LOG.debug( "Request {} {} {} {}", req.requestMethod(), req.pathInfo(),
										req.headers().stream()
												.map( n -> n + ":" + req.headers( n ) )
												.collect( Collectors.joining( "," ) ),
										req.body() );
							}

							String response = Operations.invokeLocal( service, operation,
									req::params, req::queryParams, req::headers, req::body );

							LOG.debug( "Response {}", response );
							return response;
						} );
			} );
		} );
		sparkService.awaitInitialization();

		if( LOG.isInfoEnabled() ) {
			LOG.info( "Instance {} hosting{}",
					sparkService.port(),
					services.stream()
							.flatMap( Service::getServiceTypes )
							.map( c -> "\n  " + c.getName() )
							.sorted()
							.collect( joining() ) );
		}

		// look for dependencies
		Set<Class<? extends Service>> required = services.stream()
				.flatMap( svc -> Service.dependencies( svc ).stream() )
				.collect( Collectors
						.toCollection( () -> new TreeSet<>( Comparator.comparing( Class::getName ) ) ) );

		if( !required.isEmpty() ) {
			if( LOG.isInfoEnabled() ) {
				LOG.info( "Requires{}",
						required.stream()
								.map( c -> "\n  " + c.getName() )
								.sorted()
								.collect( joining() ) );
			}

			// Some can be satisfied with local services
			for( Service local : services ) {
				Service.getServiceTypes( local )
						.forEach( type -> {
							if( required.remove( type ) ) {
								LOG.info( "Injecting local {}", type.getName() );
								services.forEach( svc -> Service.inject( local, svc ) );
							}
						} );
			}
		}

		// the rest will have to come from our cluster peers.
		// start advertising our own services...
		Discovery discovery = new Discovery( cluster )
				.advertise(
						"http", port(),
						services.stream()
								.flatMap( Service::getServiceTypes )
								.map( Class::getName )
								.collect( toCollection( TreeSet::new ) ) );

		// ... and listening for those that we require
		if( !required.isEmpty() ) {
			discovery.listen(
					( typeName, rmtUrl ) -> {
						@SuppressWarnings("unchecked")
						Class<? extends Service> type = Optional.of( typeName )
								.map( n -> {
									try {
										return Class.forName( n );
									}
									catch( Exception e ) {
										e.printStackTrace();
										return null;
									}
								} )
								.filter( Service.class::isAssignableFrom )
								.map( c -> (Class<? extends Service>) c )
								.orElse( null );

						synchronized( required ) {
							if( type != null && required.remove( type ) ) {
								LOG.info( "Injecting {} from {}", type.getName(), rmtUrl );
								Service proxy = Remotes.get( type, rmtUrl );
								services.forEach( svc -> Service.inject( proxy, svc ) );
								required.notifyAll();
							}
						}
						return required.isEmpty();
					} );
		}

		// now we wait until our dependencies have been satisfied
		while( !required.isEmpty() ) {
			try {
				synchronized( required ) {
					if( !required.isEmpty() ) {
						if( LOG.isInfoEnabled() ) {
							LOG.info( "Awaiting{}", required.stream()
									.map( c -> "\n  " + c.getName() )
									.sorted()
									.collect( joining() ) );
						}
						required.wait();
					}
				}
			}
			catch( InterruptedException e ) {
				e.printStackTrace();
				// Restore interrupted state...
				Thread.currentThread().interrupt();
			}
		}

		LOG.info( "Instance complete" );
		return this;
	}

	/**
	 * @return The port on which this {@link Instance} will accept HTTP requests
	 */
	public int port() {
		return sparkService.port();
	}

	/**
	 * Stops the {@link Instance}
	 */
	public void stop() {
		sparkService.stop();
		sparkService.awaitStop();
	}

	/**
	 * @param b true to have all requests get an error response
	 */
	public void setBroken( boolean b ) {
		broken = b;
	}

	/**
	 * @param b true to have all responses be delayed by 6 seconds
	 */
	public void setDown( boolean b ) {
		down = b;
	}
}
