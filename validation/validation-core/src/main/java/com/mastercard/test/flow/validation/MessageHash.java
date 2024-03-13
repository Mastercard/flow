package com.mastercard.test.flow.validation;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.util.Bytes;
import com.mastercard.test.flow.util.Flows;

/**
 * Provides a mechanism by which changes to message content can be detected. If
 * you add a test for your model that checks the hashes against known-good
 * values then you can:
 * <ul>
 * <li>Detect unexpected updates to test behaviour. E.g.: if you've added a flow
 * that doesn't <i>appear</i> to involve one of your actors, but the test
 * reveals that the message content for that actor has changed, then you know
 * you've got some explaining to do in your code review.</li>
 * <li>Refactor the model with confidence: if the hashes don't change, then the
 * messages are the same and the test coverage has not been impacted by the
 * refactor.</li>
 * </ul>
 * <p>
 * <b>N.B.</b> This class operates by hashing messages in isolation and then
 * combining the hash values with <code>XOR</code>. <code>XOR</code> is
 * commutative so the result is insensitive to the order in which messages are
 * processed. This is invaluable when refactoring a system model. The downside
 * is that the result of <code>XOR</code>-ing a value with itself is zero, so if
 * you have pairs of identical messages then the final hash value will contain
 * no contribution from them.
 * </p>
 * <p>
 * You should be prepared to explain this to change reviewers who notice that
 * the hash values <i>haven't</i> changed when the message and byte counts
 * <i>have</i>.
 * </p>
 */
public class MessageHash {

	/**
	 * Convenient access to {@link Interaction} {@link Message}s
	 */
	public enum Include implements Function<Interaction, Stream<Message>> {
		/**
		 * {@link Interaction#request()} {@link Message}s are included in the hash
		 */
		REQUESTS("-->", ntr -> Stream.of( ntr.request() )),
		/**
		 * {@link Interaction#response()} {@link Message}s are included in the hash
		 */
		RESPONSES("<--", ntr -> Stream.of( ntr.response() )),
		/**
		 * {@link Interaction#request()} and {@link Interaction#response()}
		 * {@link Message}s are included in the hash
		 */
		ALL("<->", ntr -> Stream.of( ntr.request(), ntr.response() ));

		/**
		 * A string that can be put after the requester name and before the responder
		 * name to make it clearer what messages are included
		 */
		public final String actorInfix;
		private final Function<Interaction, Stream<Message>> access;

		Include( String actorPrefix, Function<Interaction, Stream<Message>> access ) {
			actorInfix = actorPrefix;
			this.access = access;
		}

		@Override
		public Stream<Message> apply( Interaction t ) {
			return access.apply( t );
		}
	}

	private final BiConsumer<String, String> assertion;
	private final List<Hash> hashes = new ArrayList<>();
	private String digestName = "MD5";
	private BiFunction<String, IntSummaryStatistics,
			String> format = ( hash, stats ) -> String.format(
					"%s %04d %s",
					hash, stats.getCount(), humanReadableByteCount( stats.getSum() ) );

	/**
	 * @param assertion How to check the actual hashes against the expectation. This
	 *                  will be supplied with:
	 *                  <ol>
	 *                  <li>The expected hash values, as supplied to
	 *                  {@link #expect(Model, String...)}
	 *                  <li>The actual hash values, as computed form the model
	 *                  passed to {@link #expect(Model, String...)}
	 *                  </ol>
	 *                  The consumer should raise an objection if the two values are
	 *                  different
	 */
	public MessageHash( BiConsumer<String, String> assertion ) {
		this.assertion = assertion;
	}

	/**
	 * Sets the hashing algorithm name. Defaults to <code>MD5</code>.
	 *
	 * @param name The hashing algorithm to use on message content
	 * @return <code>this</code>
	 */
	public MessageHash digest( String name ) {
		digestName = name;
		return this;
	}

	/**
	 * Defines how the digest output is formatted.
	 *
	 * @param fmt How to produce the output line when supplied with the message
	 *            content hash and statistics about message sizes (in bytes)
	 * @return <code>this</code>
	 * @see #humanReadableByteCount(long)
	 */
	public MessageHash format( BiFunction<String, IntSummaryStatistics, String> fmt ) {
		format = fmt;
		return this;
	}

	/**
	 * Adds a comparison to hash some subset of the messages in the model
	 *
	 * @param name        A name for this comparison
	 * @param flows       How to select flows to include in the hash
	 * @param interaction How to select interaction to include in the hash
	 * @param messages    How to select messages to include in the has
	 * @param content     How to extract hashable content from messages
	 * @return <code>this</code>
	 */
	public MessageHash hashing( String name,
			Predicate<Flow> flows,
			Predicate<Interaction> interaction,
			Function<Interaction, Stream<Message>> messages,
			Function<Message, byte[]> content ) {
		hashes.add( new Hash( name, flows, interaction, messages, content ) );
		return this;
	}

	/**
	 * Adds a comparison that hashes all message content
	 *
	 * @return <code>this</code>
	 */
	public MessageHash hashingEverything() {
		return hashing( "ALL MESSAGES", f -> true, i -> true, Include.ALL, Message::content );
	}

	/**
	 * Adds a comparison to hash requests to and/or responses from a particular
	 * {@link Actor}
	 *
	 * @param responder The actor
	 * @param messages  The message type of interest
	 * @return <code>this</code>
	 */
	public MessageHash hashing( Actor responder, Include messages ) {
		return hashing( messages.name() + " " + messages.actorInfix + " " + responder.name(),
				f -> true,
				i -> i.responder() == responder,
				messages,
				Message::content );
	}

	/**
	 * Adds a comparison to hash requests to and/or responses from a particular
	 * {@link Actor}, while masking out dynamic fields that should not be included
	 * in the hash.
	 *
	 * @param responder The actor
	 * @param messages  The message type of interest
	 * @param mask      How to mask out fields that should not be included in the
	 *                  hash
	 * @return <code>this</code>
	 */
	public MessageHash hashing( Actor responder, Include messages, Consumer<Message> mask ) {
		return hashing( messages.name() + " " + messages.actorInfix + " " + responder.name(),
				f -> true,
				i -> i.responder() == responder,
				messages,
				m -> {
					Message child = m.child();
					mask.accept( child );
					return child.content();
				} );
	}

	/**
	 * Computes the actual hashes and compares them against the supplied
	 * expectations
	 *
	 * @param model    The {@link Model} that contains the messages to hash
	 * @param expected The expected hash names and values
	 */
	public void expect( Model model, String... expected ) {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance( digestName );
		}
		catch( NoSuchAlgorithmException e ) {
			throw new IllegalArgumentException( "Failed to build digest " + digestName, e );
		}

		List<String> lines = new ArrayList<>();

		for( Hash hash : hashes ) {
			hash.compute( model, digest, format, lines::add );
		}

		assertion.accept(
				copypasta( Stream.of( expected ) ),
				copypasta( lines.stream() ) );
	}

	/**
	 * @param content Lines of content
	 * @return A string that can be trivially copypasta'd into a java source file
	 */
	static String copypasta( Stream<String> content ) {
		return content
				.map( s -> s.replace( "\r", "" ) )
				.flatMap( s -> Stream.of( s.split( "\n" ) ) )
				.map( s -> s.replace( "\"", "\\\"" ) )
				.collect( Collectors.joining( "\",\n\"", "\"", "\"" ) );
	}

	private static class Hash {

		public final String name;
		public final Predicate<Flow> flows;
		public final Predicate<Interaction> interaction;
		public final Function<Interaction, Stream<Message>> messages;
		public final Function<Message, byte[]> content;

		public Hash( String name, Predicate<Flow> flows, Predicate<Interaction> interaction,
				Function<Interaction, Stream<Message>> messages, Function<Message, byte[]> content ) {
			this.name = name;
			this.flows = flows;
			this.interaction = interaction;
			this.messages = messages;
			this.content = content;
		}

		public void compute( Model model,
				MessageDigest digest,
				BiFunction<String, IntSummaryStatistics, String> format,
				Consumer<String> lines ) {
			lines.accept( name );

			byte[] accumulator = new byte[digest.getDigestLength()];

			IntSummaryStatistics stats = model.flows()
					.filter( flows )
					.flatMap( Flows::interactions )
					.filter( interaction )
					.flatMap( messages )
					.map( content )
					.mapToInt( b -> {
						byte[] h = digest.digest( b );
						for( int i = 0; i < h.length; i++ ) {
							accumulator[i] ^= h[i];
						}
						return b.length;
					} )
					.summaryStatistics();

			lines.accept( format.apply( Bytes.toHex( accumulator ), stats ) );
		}
	}

	/**
	 * Formats a byte count in a human-friendly way. From
	 * https://stackoverflow.com/a/3758880/494747
	 *
	 * @param bytes A byte count
	 * @return A human-readable approximation of that byte count
	 */
	public static String humanReadableByteCount( long bytes ) {
		long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs( bytes );
		if( absB < 1024 ) {
			return bytes + " B";
		}
		long value = absB;
		CharacterIterator ci = new StringCharacterIterator( "KMGTPE" );
		for( int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10 ) {
			value >>= 10;
			ci.next();
		}
		if( Long.signum( bytes ) == -1 ) {
			value = -value;
		}
		return String.format( "%.1f %ciB", value / 1024.0, ci.current() );
	}

}
