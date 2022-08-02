package com.mastercard.test.flow.example.framework;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * A factory for {@link Remote} implementations
 */
public class Remotes {

	private Remotes() {
		// no instances
	}

	private static Map<Class<? extends Service>,
			Function<URL, Service>> constructors = new HashMap<>();

	/**
	 * Adds a new {@link Remote} implementation, a proxy that can be built when a
	 * {@link Service} is discovered on another {@link Instance}
	 *
	 * @param <T>         The {@link Service} interface type
	 * @param type        The {@link Service} interface type
	 * @param constructor How to build a {@link Remote} for that service
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Service> void register( Class<T> type,
			Function<URL, T> constructor ) {
		constructors.put( type, (Function<URL, Service>) constructor );
	}

	/**
	 * @param <T>  The {@link Service} interface type
	 * @param type The {@link Service} interface type
	 * @param url  URL of {@link Instance} that hosts that {@link Service}
	 * @return A {@link Remote} proxy for that {@link Service}
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Service> T get( Class<T> type, URL url ) {

		Function<URL, Service> constructor = constructors.get( type );
		if( constructor == null ) {
			throw new IllegalStateException( "No Remote for " + type + " has been registered" );
		}

		return (T) constructor.apply( url );
	}
}
