package com.mastercard.test.flow.example.framework;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastercard.test.flow.example.framework.Operation.Body;
import com.mastercard.test.flow.example.framework.Operation.Header;
import com.mastercard.test.flow.example.framework.Operation.Path;
import com.mastercard.test.flow.example.framework.Operation.Query;

/**
 * Methods for working with {@link Operation} methods
 */
public class Operations {
	private static final Logger LOG = LoggerFactory.getLogger( Operations.class );
	private static final ObjectMapper JSON = new ObjectMapper();

	private Operations() {
		// no instances
	}

	/**
	 * Extracts {@link Operation}s from an object
	 *
	 * @param o an object
	 * @return The {@link Operation}s supported by the object
	 */
	@SuppressWarnings("unchecked")
	public static Stream<Method> get( Object o ) {
		List<Method> operations = new ArrayList<>();
		for( Class<?> itf : o.getClass().getInterfaces() ) {
			if( Service.class.isAssignableFrom( itf ) ) {
				operations( (Class<? extends Service>) itf ).forEach( operations::add );
			}
		}
		if( operations.isEmpty() ) {
			throw new IllegalStateException( "No operations in " + o.getClass() );
		}
		return operations.stream();
	}

	/**
	 * Extracts {@link Operations} from an interface
	 *
	 * @param itf The interface
	 * @return A stream of {@link Operation} methods
	 */
	public static Stream<Method> operations( Class<? extends Service> itf ) {
		List<Method> operations = new ArrayList<>();
		for( Method m : itf.getMethods() ) {
			if( m.isAnnotationPresent( Operation.class ) ) {
				for( Parameter param : m.getParameters() ) {
					if( !param.isAnnotationPresent( Path.class )
							&& !param.isAnnotationPresent( Query.class )
							&& !param.isAnnotationPresent( Header.class )
							&& !param.isAnnotationPresent( Body.class ) ) {
						throw new IllegalStateException(
								param + " is none of Query, Path, Header or Body parameter in " + m );
					}
				}
				operations.add( m );
			}
		}
		return operations.stream();
	}

	/**
	 * Invokes an {@link Operation} on a local {@link Service} using parameters from
	 * an HTTP request
	 *
	 * @param service          The service to invoke
	 * @param operation        The operation method
	 * @param pathParameters   How to get request path parameters
	 * @param queryParameters  How to get request query parameters
	 * @param headerParameters How to get request headers
	 * @param body             Request body
	 * @return Response body
	 */
	public static String invokeLocal( Object service, Method operation,
			UnaryOperator<String> pathParameters,
			UnaryOperator<String> queryParameters,
			UnaryOperator<String> headerParameters,
			Supplier<String> body ) {
		try {
			LOG.debug( "invoking {}", operation );
			Object[] args = new Object[operation.getParameterCount()];
			for( int i = 0; i < args.length; i++ ) {
				Parameter param = operation.getParameters()[i];
				LOG.debug( "Parsing for {}", param );
				if( param.isAnnotationPresent( Path.class ) ) {
					args[i] = parse( pathParameters.apply( param.getAnnotation( Path.class ).value() ),
							param.getType() );
				}
				else if( param.isAnnotationPresent( Query.class ) ) {
					args[i] = parse( queryParameters.apply( param.getAnnotation( Query.class ).value() ),
							param.getType() );
				}
				else if( param.isAnnotationPresent( Header.class ) ) {
					args[i] = parse( headerParameters.apply( param.getAnnotation( Header.class ).value() ),
							param.getType() );
				}
				else if( param.isAnnotationPresent( Body.class ) ) {
					args[i] = parse( body.get(), param.getType() );
				}
				else {
					throw new IllegalStateException(
							param + " is none of Query, Path, Header or Body parameter" );
				}
			}
			if( LOG.isDebugEnabled() ) {
				LOG.debug( "Invoking {} with args {}", service, Arrays.deepToString( args ) );
			}

			Object result = operation.invoke( service, args );
			LOG.debug( "Result {}", result );
			return serialise( result );
		}
		catch( Exception e ) {
			throw new IllegalArgumentException( String.format(
					"Failed to invoke %s on %s with\npath\n%s\nquery\n%s\nbody\n%s",
					operation, service,
					pathParameters, queryParameters, body ),
					e );
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static Object parse( String value, Class<?> type ) throws IOException {
		LOG.debug( "Parsing {} as {}", value, type );
		if( type.equals( String.class ) ) {
			return value;
		}
		if( type.equals( Class.class ) ) {
			try {
				return Class.forName( value );
			}
			catch( ClassNotFoundException cnfe ) {
				throw new IllegalStateException( cnfe );
			}
		}
		if( type.isEnum() ) {
			return Enum.valueOf( (Class<Enum>) type, value );
		}
		return JSON.readValue( value, type );
	}

	private static String serialise( Object value ) throws IOException {
		if( value instanceof String ) {
			return (String) value;
		}
		if( value instanceof Class<?> ) {
			return ((Class<?>) value).getName();
		}
		if( value instanceof Enum ) {
			return ((Enum<?>) value).name();
		}
		return JSON.writeValueAsString( value );
	}

	/**
	 * Invokes an {@link Operation} on a remote {@link Instance}
	 *
	 * @param url       The url of the {@link Instance}
	 * @param m         The {@link Operation} method
	 * @param arguments The {@link Operation} arguments
	 * @return The {@link Operation} result
	 */
	public static Object invokeRemote( URL url, Method m, Object... arguments ) {
		LOG.debug( "Operations.invokeRemote( {}, {}, {} ) ",
				url, m.getName(), Arrays.toString( arguments ) );
		try {
			Map<String, String> pathArgs = new HashMap<>();
			Map<String, String> queryArgs = new HashMap<>();
			Map<String, String> headerArgs = new HashMap<>();
			String reqBody = null;
			for( int i = 0; i < arguments.length; i++ ) {
				String value = serialise( arguments[i] );
				Parameter param = m.getParameters()[i];
				if( param.isAnnotationPresent( Path.class ) ) {
					pathArgs.put( param.getAnnotation( Path.class ).value(), value );
				}
				else if( param.isAnnotationPresent( Query.class ) ) {
					queryArgs.put( param.getAnnotation( Query.class ).value(), value );
				}
				else if( param.isAnnotationPresent( Header.class ) ) {
					headerArgs.put( param.getAnnotation( Header.class ).value(), value );
				}
				else if( param.isAnnotationPresent( Body.class ) ) {
					reqBody = value;
				}
				else {
					throw new IllegalStateException(
							param + " is none of Query, Path, Header or Body parameter" );
				}
			}
			Operation op = m.getAnnotation( Operation.class );
			String path = Stream.of( op.path().split( "/" ) )
					.map( e -> e.startsWith( ":" ) ? pathArgs.remove( e.substring( 1 ) ) : e )
					.collect( joining( "/" ) );
			String query = queryArgs.isEmpty()
					? ""
					: queryArgs.entrySet().stream()
							.map( e -> e.getKey() + "=" + e.getValue() )
							.collect( Collectors.joining( "&", "?", "" ) );

			HttpURLConnection connection = (HttpURLConnection) new URL( url + path + query )
					.openConnection();
			connection.setRequestMethod( op.method() );
			connection.setDoOutput( reqBody != null );
			connection.addRequestProperty( "Content-Type", op.reqContentType() );
			connection.addRequestProperty( "Accept", op.resContentType() );
			connection.setConnectTimeout( 2000 );
			connection.setReadTimeout( 2000 );
			headerArgs.forEach( connection::addRequestProperty );

			if( LOG.isDebugEnabled() ) {
				LOG.debug( "Requesting {} {} {} {}",
						connection.getRequestMethod(), connection.getURL(),
						connection.getRequestProperties(), reqBody );
			}

			if( reqBody != null ) {
				try( OutputStream out = connection.getOutputStream() ) {
					out.write( reqBody.getBytes( UTF_8 ) );
				}
			}

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buff = new byte[1024];
			int read = 0;
			try( InputStream in = connection.getInputStream() ) {
				while( (read = in.read( buff )) != -1 ) {
					baos.write( buff, 0, read );
				}
			}

			String resBody = new String( baos.toByteArray(), UTF_8 );
			Object result = null;
			if( !m.getReturnType().equals( void.class ) ) {
				result = parse( resBody, m.getReturnType() );
			}

			if( LOG.isDebugEnabled() ) {
				LOG.debug( "Response {} {} {} {}",
						connection.getResponseCode(), connection.getResponseMessage(),
						connection.getHeaderFields(), resBody );
			}

			if( connection.getResponseCode() != 200 ) {
				throw new IOException( "Bad response " + connection.getResponseCode() + "\n" + resBody );
			}

			return result;
		}
		catch( IOException ioe ) {
			throw new UncheckedIOException( ioe );
		}
	}
}
