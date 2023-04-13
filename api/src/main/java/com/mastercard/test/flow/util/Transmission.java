package com.mastercard.test.flow.util;

import java.util.Optional;
import java.util.function.Function;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;

/**
 * A single message transmission
 */
public class Transmission {
	/**
	 * Defines {@link Message} role.
	 */
	public enum Type {
		/**
		 * The call that arrives unbidden
		 */
		REQUEST(Interaction::requester, Interaction::responder, Interaction::request),
		/**
		 * The reply that is yearned for
		 */
		RESPONSE(Interaction::responder, Interaction::requester, Interaction::response);

		/**
		 * How to get the transmitter from the {@link Interaction}
		 */
		public final Function<Interaction, Actor> tx;
		/**
		 * How to get the receiver from the {@link Interaction}
		 */
		public final Function<Interaction, Actor> rx;
		/**
		 * How to get the message from the {@link Interaction}
		 */
		public final Function<Interaction, Message> msg;

		Type( Function<Interaction, Actor> tx, Function<Interaction, Actor> rx,
				Function<Interaction, Message> msg ) {
			this.tx = tx;
			this.rx = rx;
			this.msg = msg;
		}

	}

	private final Interaction ntr;
	private final Transmission.Type type;
	private final int depth;

	/**
	 * @param ntr   The interaction that this transmission is a part of
	 * @param type  whether this {@link Transmission} is a request or response
	 * @param depth 0 for the root interaction in a flow, 1 for the children of the
	 *              root, etc
	 */
	Transmission( Interaction ntr, Transmission.Type type, int depth ) {
		this.ntr = ntr;
		this.type = type;
		this.depth = depth;
	}

	/**
	 * Defines whether this {@link Transmission} is a request or response
	 *
	 * @return whether this {@link Transmission} is a request or response
	 */
	public Transmission.Type type() {
		return type;
	}

	/**
	 * Defines the source of the transmission
	 *
	 * @return The {@link Actor} that sends the message
	 */
	public Actor transmitter() {
		return type.tx.apply( ntr );
	}

	/**
	 * Defines the destination of the transmission
	 *
	 * @return The {@link Actor} that receives the message
	 */
	public Actor receiver() {
		return type.rx.apply( ntr );
	}

	/**
	 * Accessor for transmission content
	 *
	 * @return The message content
	 */
	public Message message() {
		return type.msg.apply( source() );
	}

	/**
	 * Accessor for the parent {@link Interaction}
	 *
	 * @return The interaction that this transmission is a part of
	 */
	public Interaction source() {
		return ntr;
	}

	/**
	 * Defines how deep in the {@link Flow}'s interaction structure this
	 * transmission is
	 *
	 * @return 0 for the root interaction in a flow, 1 for the children of the root,
	 *         etc
	 */
	public int depth() {
		return depth;
	}

	/**
	 * Constructs a string that describes the transmission, but with no tag
	 * information
	 *
	 * @return An untagged description
	 */
	public String toUntaggedString() {
		StringBuilder sb = new StringBuilder();
		for( int i = 0; i < depth; i++ ) {
			sb.append( "  " );
		}
		if( type == Type.REQUEST ) {
			sb.append( transmitter().name() ).append( " 🠖 " ).append( receiver().name() );
		}
		else {
			sb.append( receiver().name() ).append( " 🠔 " ).append( transmitter().name() );
		}
		return sb.toString();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append( toUntaggedString() );
		sb.append( " " ).append( ntr.tags() ).append( " " )
				.append( Optional.ofNullable( message() )
						.map( m -> m.getClass().getSimpleName() )
						.orElse( null ) );
		return sb.toString();
	}
}
