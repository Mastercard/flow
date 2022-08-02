package com.mastercard.test.flow.example.framework;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link Service} that exists on a different {@link Instance}
 */
public abstract class Remote implements Service {
	private final URL url;
	private final Map<Integer, Method> operations = new HashMap<>();
	private Method training;

	/**
	 * @param url The URL of the {@link Instance} that hosts the {@link Service}
	 */
	protected Remote( URL url ) {
		this.url = url;

		// invoke all operations, recording the IDs that they supply
		Operations.get( this ).forEach( method -> {
			training = method;
			int previousSize = operations.size();
			try {
				method.invoke( this, new Object[method.getParameterCount()] );
			}
			catch( Exception e ) {
				throw new IllegalStateException( "Failed to invoke " + method, e );
			}
			if( operations.size() != previousSize + 1 ) {
				throw new IllegalStateException( method + " failed to call invoke exactly once" );
			}
		} );
		// now do it again to check that they return the same ID, and that IDs are not
		// being reused
		Operations.get( this ).forEach( method -> {
			training = method;
			try {
				method.invoke( this, new Object[method.getParameterCount()] );
			}
			catch( Exception e ) {
				throw new IllegalStateException( "Failed to invoke " + method, e );
			}
		} );

		// exit training mode
		training = null;
	}

	/**
	 * A call to this should be the only thing in the implementation of
	 * {@link Operation} methods in subclasses
	 *
	 * @param id        A unique and unchanging value for the {@link Operation}
	 * @param arguments The arguments supplied to the {@link Operation}
	 * @return The result of the {@link Operation}
	 */
	protected Object invoke( int id, Object... arguments ) {
		if( training != null ) {
			if( operations.containsKey( id )
					&& !training.equals( operations.get( id ) ) ) {
				throw new IllegalStateException(
						"ID " + id + " trained on operations\n" + training + "\nand\n" + operations.get( id ) );
			}
			if( operations.containsValue( training )
					&& !training.equals( operations.get( id ) ) ) {
				throw new IllegalStateException( "Operation " + training + " trained with inconstant ID" );
			}
			operations.put( id, training );
			return null;
		}

		Method m = operations.get( id );
		if( m == null ) {
			throw new IllegalStateException( "No method registered for " + id );
		}

		return Operations.invokeRemote( url, m, arguments );
	}
}
