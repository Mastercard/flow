package com.mastercard.test.flow.example.app.model.ctx;

import static java.util.stream.Collectors.toSet;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.example.app.model.ExampleSystem.Actors;

/**
 * Controls whether the services are up and running or not
 */
public class Upness implements Context {
	private static final Set<Actor> DOMAIN = Stream.of( Actors.values() ).collect( toSet() );

	@JsonProperty("down")
	private final Set<Actors> down = EnumSet.noneOf( Actors.class );

	@JsonProperty("broken")
	private final Set<Actors> broken = EnumSet.noneOf( Actors.class );

	/**
	 * Default state is all system working
	 */
	public Upness() {
		// leave the sets empty
	}

	private Upness( Upness parent ) {
		down.addAll( parent.down );
		broken.addAll( parent.broken );
	}

	/**
	 * Adds systems to the working set
	 *
	 * @param working The systems that should be up and running
	 * @return <code>this</code>
	 */
	public Upness up( Actors... working ) {
		for( Actors a : working ) {
			down.remove( a );
			broken.remove( a );
		}
		return this;
	}

	/**
	 * Adds systems to the unresponsive set
	 *
	 * @param downed The systems that should not respond to requests
	 * @return <code>this</code>
	 */
	public Upness down( Actors... downed ) {
		for( Actors a : downed ) {
			down.add( a );
			broken.remove( a );
		}
		return this;
	}

	/**
	 * Adds systems to the broken set
	 *
	 * @param busted The systems that should return error responses to all requests
	 * @return <code>this</code>
	 */
	public Upness broken( Actors... busted ) {
		for( Actors a : busted ) {
			down.remove( a );
			broken.add( a );
		}
		return this;
	}

	/**
	 * Queries system status
	 *
	 * @param system The system to check
	 * @return <code>true</code> of the supplied system should be unresponsive
	 */
	public boolean isDown( Actors system ) {
		return down.contains( system );
	}

	/**
	 * Queries system status
	 *
	 * @param system The system to check
	 * @return <code>true</code> of the supplied system should return errors
	 */
	public boolean isBroken( Actors system ) {
		return broken.contains( system );
	}

	/**
	 * @return A stream of all the {@link Actor}s that have been configured for
	 *         non-standard status
	 */
	public Stream<Actors> configured() {
		return Stream.of( down, broken )
				.flatMap( Set::stream );
	}

	@Override
	public String name() {
		return "Upness";
	}

	@Override
	public Set<Actor> domain() {
		return DOMAIN;
	}

	@Override
	public Context child() {
		return new Upness( this );
	}

}
