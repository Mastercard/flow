package com.mastercard.test.flow.builder;

import java.util.function.Consumer;
import java.util.function.Function;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.builder.mutable.MutableFlow;
import com.mastercard.test.flow.builder.mutable.MutableRootInteraction;
import com.mastercard.test.flow.builder.steps.From;

/**
 * Convenience class for defining a {@link Flow} from scratch
 */
@SkipTrace
public class Creator extends Builder<Creator> {

	private Creator() {
		super( new MutableFlow() );
	}

	/**
	 * Constructs a new {@link Flow}
	 *
	 * @param steps How to build the {@link Flow}
	 * @return The resulting {@link Flow}
	 */
	@SafeVarargs
	public static Flow build( Consumer<? super Creator>... steps ) {
		Creator b = new Creator();
		for( Consumer<? super Creator> step : steps ) {
			step.accept( b );
		}
		return b.build();
	}

	/**
	 * Defines the {@link Flow}'s {@link Interaction} structure
	 *
	 * @param call The path from the root actor through the rest of the system and
	 *             back
	 * @return <code>this</code>
	 */
	public Creator call( Function<From<Creator>, Creator> call ) {
		MutableRootInteraction ntr = new MutableRootInteraction();
		From<Creator> c = new From<>( this, ntr );
		Creator ret = call.apply( c );
		if( ret != this ) {
			throw new IllegalStateException( "Failed to return to origin" );
		}
		flow.root( ntr );
		return ret;
	}

	private Flow build() {
		return flow.build();
	}
}
