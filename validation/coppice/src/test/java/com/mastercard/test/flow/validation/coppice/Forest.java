package com.mastercard.test.flow.validation.coppice;

import static com.mastercard.test.flow.util.Tags.set;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.builder.Deriver;
import com.mastercard.test.flow.model.EagerModel;
import com.mastercard.test.flow.msg.txt.Text;
import com.mastercard.test.flow.util.TaggedGroup;

/**
 * A large synthetic model for exercising {@link Coppice}
 */
public class Forest extends EagerModel {

	/**
	 * Tags
	 */
	public static final TaggedGroup MODEL_TAGS = new TaggedGroup();

	private enum Actors implements Actor {
		AVA, BEN;
	}

	/***/
	public Forest() {
		super( MODEL_TAGS );

		Random rng = new Random( 12386187923L );
		List<Flow> flows = new ArrayList<>();

		for( int i = 0; i < 400; i++ ) {
			String desc = String.format( "%03d", i );
			if( flows.isEmpty() || rng.nextInt( 25 ) == 0 ) {
				flows.add( Creator.build( flow -> flow
						.meta( data -> data
								.description( desc )
								.tags( set( "root" ) ) )
						.call( a -> a.from( Actors.AVA ).to( Actors.BEN )
								.request( new Text( randomText( rng ) ) )
								.response( new Text( randomText( rng ) ) ) ) ) );
			}
			else {
				Flow basis = flows.get( rng.nextInt( Math.max( 1, flows.size() / 4 ) ) );
				Flow parent = flows.get( rng.nextInt( flows.size() ) );
				flows.add( Deriver.build( basis, flow -> flow
						.meta( data -> data
								.description( desc )
								.tags( set( "mutation", parent.meta().description() ) ) )
						.update( ntr -> true, ntr -> {
							ntr.request( new Text( mutation( parent.root().request().assertable(), rng ) ) );
							ntr.response( new Text( mutation( parent.root().response().assertable(), rng ) ) );
						} ) ) );
			}
		}

		members( flows );
	}

	private static final String ALPHA = "ACGT";

	private static String randomText( Random rng ) {
		StringBuilder sb = new StringBuilder();
		int l = rng.nextInt( 1 ) + 20;
		for( int i = 0; i < l; i++ ) {
			sb.append( ALPHA.charAt( rng.nextInt( ALPHA.length() ) ) )
					.append( "\n" );
		}
		return sb.toString().trim();
	}

	private static String mutation( String gene, Random rng ) {
		char[] nucleobases = gene.replaceAll( "\\s", "" ).toCharArray();
		int mutations = 5;
		for( int i = 0; i < mutations; i++ ) {
			nucleobases[rng.nextInt( nucleobases.length )] = ALPHA
					.charAt( rng.nextInt( ALPHA.length() ) );
		}
		return new String( nucleobases ).replaceAll( "", "\n" ).trim();
	}

}
