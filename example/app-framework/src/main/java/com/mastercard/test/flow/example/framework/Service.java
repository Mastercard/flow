package com.mastercard.test.flow.example.framework;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * A discrete bit of business logic
 */
public interface Service {

	/**
	 * Apply this to the public dependency {@link Service} fields in your
	 * {@link Service} implementation. The containing {@link Instance} will inject a
	 * suitable implementation before any requests are processed.
	 */
	@Target(ElementType.FIELD)
	@Retention(RetentionPolicy.RUNTIME)
	public static @interface Dependency {
		// marker only
	}

	/**
	 * @param instance An object
	 * @return All of the {@link Service} interfaces that the object implements
	 */
	@SuppressWarnings("unchecked")
	static Stream<Class<? extends Service>> getServiceTypes( Object instance ) {
		return Stream.of( instance.getClass().getInterfaces() )
				.filter( Service.class::isAssignableFrom )
				.map( i -> (Class<? extends Service>) i );
	}

	/**
	 * @param service A service implementation
	 * @return The unsatisfied dependencies in the service
	 */
	@SuppressWarnings("unchecked")
	static Set<Class<? extends Service>> dependencies( Service service ) {
		Set<Class<? extends Service>> deps = new TreeSet<>( Comparator.comparing( Class::getName ) );
		for( Field f : service.getClass().getFields() ) {
			if( f.isAnnotationPresent( Dependency.class ) ) {
				if( !Service.class.isAssignableFrom( f.getType() ) ) {
					throw new IllegalStateException( f +
							" @Dependency should only be applied to Service fields" );
				}
				try {
					if( f.get( service ) == null ) {
						deps.add( (Class<? extends Service>) f.getType() );
					}
				}
				catch( Exception e ) {
					throw new IllegalStateException( "Failed to check dependency " + f, e );
				}
			}
		}
		return deps;
	}

	/**
	 * @param dependency A service
	 * @param service    The service that relies on the dependency
	 */
	static void inject( Service dependency, Service service ) {
		for( Field f : service.getClass().getFields() ) {
			if( f.isAnnotationPresent( Dependency.class ) && f.getType().isInstance( dependency ) ) {
				try {
					f.set( service, dependency );
				}
				catch( Exception e ) {
					throw new IllegalStateException( "Failed to inject " + dependency + " into " + f, e );
				}
			}
		}
	}
}
