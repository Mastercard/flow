package com.mastercard.test.flow.example.app.assrt.ctx;

import static java.util.stream.Collectors.toCollection;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.assrt.Applicator;
import com.mastercard.test.flow.example.app.model.ExampleSystem.Actors;
import com.mastercard.test.flow.example.app.model.ctx.Upness;
import com.mastercard.test.flow.example.framework.Instance;

/**
 * Applies {@link Upness} contexts to the system under test
 */
public class UpnessApplicator extends Applicator<Upness> {
	private static final Upness DEFAULT = new Upness();

	private final Map<Actors, Instance> instances = new EnumMap<>( Actors.class );

	/***/
	public UpnessApplicator() {
		super( Upness.class, 2 );
	}

	/**
	 * Adds an instance whose upness can be controlled
	 *
	 * @param system   The {@link Actor} that represents the system in the model
	 * @param instance The system itself
	 * @return <code>this</code>
	 */
	public UpnessApplicator with( Actors system, Instance instance ) {
		instances.put( system, instance );
		return this;
	}

	@Override
	public Comparator<Upness> order() {
		return ( a, b ) -> {
			int d = 0;
			Iterator<Actors> actors = Stream.concat( a.configured(), b.configured() )
					.collect( toCollection( () -> new TreeSet<>( Comparator.comparing( Actor::name ) ) ) )
					.iterator();
			while( actors.hasNext() && d == 0 ) {
				Actors actor = actors.next();
				d = (a.isBroken( actor ) ? 1 : 0) - (b.isBroken( actor ) ? 1 : 0);
				if( d == 0 ) {
					d = (a.isDown( actor ) ? 1 : 0) - (b.isDown( actor ) ? 1 : 0);
				}
			}
			return d;
		};
	}

	@Override
	public void transition( Upness source, Upness destination ) {
		Upness from = Optional.ofNullable( source ).orElse( DEFAULT );
		Upness to = Optional.ofNullable( destination ).orElse( DEFAULT );

		instances.forEach( ( system, instance ) -> {
			if( from.isBroken( system ) != to.isBroken( system ) ) {
				instance.setBroken( to.isBroken( system ) );
			}
			if( from.isDown( system ) != to.isDown( system ) ) {
				instance.setDown( to.isDown( system ) );
			}
		} );
	}

}
