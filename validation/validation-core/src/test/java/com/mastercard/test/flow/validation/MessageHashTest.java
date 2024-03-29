package com.mastercard.test.flow.validation;

import static com.mastercard.test.flow.validation.MessageHash.Include.REQUESTS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.util.Bytes;
import com.mastercard.test.flow.validation.MessageHash.Include;

/**
 * Exercises {@link MessageHash}
 */
@SuppressWarnings("static-method")
class MessageHashTest {

	private static final String EMPTY_MD5 = "D41D8CD98F00B204E9800998ECF8427E";

	private static final Actor AVA = () -> "AVA";
	private static final Actor BEN = () -> "BEN";
	private static final Actor CHE = () -> "CHE";

	private static final MessageHash mh = new MessageHash( Assertions::assertEquals )
			.hashingEverything()
			.hashing( AVA, Include.ALL )
			.hashing( BEN, Include.REQUESTS )
			.hashing( CHE, Include.RESPONSES );

	/**
	 * Exercising fluent API
	 */
	@Test
	void fluency() {
		MessageHash h = new MessageHash( null );
		assertSame( h, h.hashingEverything() );
		assertSame( h, h.hashing( null, null, null, null, null ) );
		assertSame( h, h.hashing( AVA, Include.ALL ) );
	}

	/**
	 * Hashing an empty model
	 */
	@Test
	void emptyModel() {
		// no flows
		mh.expect( model(),
				"ALL MESSAGES",
				"00000000000000000000000000000000 0000 0 B",
				"ALL <-> AVA",
				"00000000000000000000000000000000 0000 0 B",
				"REQUESTS --> BEN",
				"00000000000000000000000000000000 0000 0 B",
				"RESPONSES <-- CHE",
				"00000000000000000000000000000000 0000 0 B" );
	}

	/**
	 * Hashing empty messages
	 */
	@Test
	void emptyMessages() {
		// flows, but no message bytes
		mh.expect( model(
				flow( ntr( AVA, "", BEN, "" ) ),
				flow( ntr( BEN, "", CHE, "" ) ),
				flow( ntr( CHE, "", AVA, "" ) ) ),
				"ALL MESSAGES",
				"00000000000000000000000000000000 0006 0 B",
				"ALL <-> AVA",
				"00000000000000000000000000000000 0002 0 B",
				"REQUESTS --> BEN",
				EMPTY_MD5 + " 0001 0 B",
				"RESPONSES <-- CHE",
				EMPTY_MD5 + " 0001 0 B" );
		// Ben and Che are showing what happens when you md5 zero bytes
		// Ava is showing what happens when you do that twice and XOR the results
		// together
	}

	/**
	 * Hashing requests
	 */
	@Test
	void requests() {
		mh.expect( model(
				flow( ntr( AVA, "data", BEN, "" ) ),
				flow( ntr( BEN, "data", CHE, "" ) ),
				flow( ntr( CHE, "data", AVA, "" ) ) ),
				"ALL MESSAGES",
				"596AF3E1D23D4CCC68DD296FA59864A2 0006 12 B",
				"ALL <-> AVA",
				"596AF3E1D23D4CCC68DD296FA59864A2 0002 4 B",
				"REQUESTS --> BEN",
				"8D777F385D3DFEC8815D20F7496026DC 0001 4 B",
				"RESPONSES <-- CHE",
				EMPTY_MD5 + " 0001 0 B" );
	}

	/**
	 * Hashing responses
	 */
	@Test
	void responses() {
		mh.expect( model(
				flow( ntr( AVA, "", BEN, "data" ) ),
				flow( ntr( BEN, "", CHE, "data" ) ),
				flow( ntr( CHE, "", AVA, "data" ) ) ),
				"ALL MESSAGES",
				"596AF3E1D23D4CCC68DD296FA59864A2 0006 12 B",
				"ALL <-> AVA",
				"596AF3E1D23D4CCC68DD296FA59864A2 0002 4 B",
				"REQUESTS --> BEN",
				"D41D8CD98F00B204E9800998ECF8427E 0001 0 B",
				"RESPONSES <-- CHE",
				"8D777F385D3DFEC8815D20F7496026DC 0001 4 B" );
	}

	/**
	 * Showing that when masking operations are requested, they are applied to a
	 * child of the messages in the model
	 *
	 * @throws NoSuchAlgorithmException This would be surprising
	 */
	@Test
	void masking() throws NoSuchAlgorithmException {

		String expectedHashedContent = "child of 'hash this!' with updates {dynamic field=static value}";
		int expectedLength = expectedHashedContent.length();
		MessageDigest digest = MessageDigest.getInstance( "MD5" );
		String expectedHash = Bytes.toHex( digest.digest( expectedHashedContent.getBytes( UTF_8 ) ) );

		MessageHash masking = new MessageHash( Assertions::assertEquals )
				.hashing( BEN, REQUESTS, m -> m.set( "dynamic field", "static value" ) );

		masking.expect( model(
				flow( ntr( AVA, "hash this!", BEN, "but not this" ) ),
				flow( ntr( BEN, "or this", CHE, "and definitley not this" ) ) ),
				"REQUESTS --> BEN",
				expectedHash + " 0001 " + expectedLength + " B" );
	}

	/**
	 * Demonstrates what happens when you have even numbers of identical messages
	 * being hashed together
	 */
	@Test
	void xor() {
		mh.expect( model(
				flow( ntr( AVA, "this is hashed", BEN, "aaa" ) ),
				flow( ntr( BEN, "bbb", CHE, "this, too, is hashed" ) ),
				flow( ntr( CHE, "both are hashed", AVA, "both are hashed" ) ),
				flow( ntr( AVA, "this is hashed", BEN, "aaa" ) ),
				flow( ntr( BEN, "bbb", CHE, "this, too, is hashed" ) ),
				flow( ntr( CHE, "both are hashed", AVA, "both are hashed" ) ) ),
				"ALL MESSAGES",
				"00000000000000000000000000000000 0012 140 B",
				"ALL <-> AVA",
				"00000000000000000000000000000000 0004 60 B",
				"REQUESTS --> BEN",
				"00000000000000000000000000000000 0002 28 B",
				"RESPONSES <-- CHE",
				"00000000000000000000000000000000 0002 40 B" );
	}

	/**
	 * Demonstrates failure behaviour
	 */
	@Test
	void failure() {
		Model model = model();
		AssertionError ae = assertThrows( AssertionError.class,
				() -> mh.expect( model, "foo" ) );
		assertEquals( ""
				+ "expected: <\"foo\"> but was: <\"ALL MESSAGES\",\n"
				+ "\"00000000000000000000000000000000 0000 0 B\",\n"
				+ "\"ALL <-> AVA\",\n"
				+ "\"00000000000000000000000000000000 0000 0 B\",\n"
				+ "\"REQUESTS --> BEN\",\n"
				+ "\"00000000000000000000000000000000 0000 0 B\",\n"
				+ "\"RESPONSES <-- CHE\",\n"
				+ "\"00000000000000000000000000000000 0000 0 B\">",
				ae.getMessage() );

	}

	/**
	 * You can change the algorithm
	 */
	@Test
	void digest() {
		MessageHash fmh = new MessageHash( Assertions::assertEquals )
				.digest( "SHA-256" )
				.hashingEverything();

		fmh.expect( model(
				flow( ntr( AVA, "request", BEN, "response" ) ) ),
				"ALL MESSAGES",
				"B6AC0AC67176EED20DEDF0D4646C9656E372B18F5111155B130EF345568C85E6 0002 15 B" );

		fmh.digest( "no_such_alg" );
		assertThrows( IllegalArgumentException.class, () -> fmh.expect( null ) );
	}

	/**
	 * Demonstrates changing the format
	 */
	@Test
	void format() {
		MessageHash fmh = new MessageHash( Assertions::assertEquals )
				.format( ( hash, stats ) -> "hash:" + hash + " from " + stats.getSum() + "B" )
				.hashingEverything();

		fmh.expect( model(
				flow( ntr( AVA, "request", BEN, "response" ) ) ),
				"ALL MESSAGES",
				"hash:C1ABB5280BBCDE43A66FD45A541E95B9 from 15B" );
	}

	/**
	 * Exercising {@link MessageHash#humanReadableByteCount(long)}
	 */
	@Test
	void humanReadableByteCount() {
		BiConsumer<Long, String> test = ( in, out ) -> assertEquals(
				out,
				MessageHash.humanReadableByteCount( in ),
				"for " + in );

		test.accept( -1L, "-1 B" );
		test.accept( 0L, "0 B" );
		test.accept( 1L, "1 B" );
		test.accept( 512L, "512 B" );

		long oneKiB = 1024L;
		test.accept( oneKiB - 1, "1023 B" );
		test.accept( oneKiB, "1.0 KiB" );
		test.accept( oneKiB + 51, "1.0 KiB" );
		test.accept( oneKiB + 52, "1.1 KiB" );

		long oneMiB = 1024L * 1024L;
		test.accept( oneMiB - 52, "1023.9 KiB" );
		test.accept( oneMiB - 51, "1.0 MiB" );
		test.accept( oneMiB, "1.0 MiB" );
		test.accept( oneMiB + 53247, "1.0 MiB" );
		test.accept( oneMiB + 53248, "1.1 MiB" );

		long oneGiB = 1024L * 1024L * 1024L;
		test.accept( oneGiB - 52429, "1023.9 MiB" );
		test.accept( oneGiB - 52428, "1.0 GiB" );
		test.accept( oneGiB, "1.0 GiB" );
		test.accept( oneGiB + 54525951, "1.0 GiB" );
		test.accept( oneGiB + 54525952, "1.1 GiB" );

		long accum = 1;
		test.accept( accum, "1 B" );
		accum *= 1024 * 1.5;
		test.accept( accum, "1.5 KiB" );
		accum *= 1024 * 1.5;
		test.accept( accum, "2.3 MiB" );
		accum *= 1024 * 1.5;
		test.accept( accum, "3.4 GiB" );
		accum *= 1024 * 1.5;
		test.accept( accum, "5.1 TiB" );
		accum *= 1024 * 1.5;
		test.accept( accum, "7.6 PiB" );
		accum *= 1024 * 0.75;
		test.accept( accum, "5.7 EiB" );

		test.accept( Long.MAX_VALUE, "8.0 EiB" );
		test.accept( Long.MIN_VALUE, "-8.0 EiB" );
	}

	private static Model model( Flow... flows ) {
		Model model = mock( Model.class );
		when( model.flows() ).then( i -> Stream.of( flows ) );
		return model;
	}

	private static Flow flow( Interaction root ) {
		Flow flow = mock( Flow.class );
		when( flow.root() ).thenReturn( root );
		return flow;
	}

	private static Interaction ntr( Actor requester, String req, Actor responder, String res,
			Interaction... children ) {
		Interaction ntr = mock( Interaction.class );
		when( ntr.requester() ).thenReturn( requester );
		Message reqm = msg( req );
		when( ntr.request() ).thenReturn( reqm );
		when( ntr.responder() ).thenReturn( responder );
		Message resm = msg( res );
		when( ntr.response() ).thenReturn( resm );
		when( ntr.children() ).then( i -> Stream.of( children ) );
		return ntr;
	}

	private static Message msg( String content ) {
		Message msg = mock( Message.class );
		when( msg.content() ).thenReturn( content.getBytes( UTF_8 ) );

		Map<String, String> childUpdates = new TreeMap<>();
		Message child = mock( Message.class );

		doAnswer( inv -> {
			childUpdates.put( inv.getArgument( 0 ), inv.getArgument( 1 ) );
			return null;
		} )
				.when( child )
				.set( any(), any() );

		when( child.content() )
				.thenAnswer( inv -> {
					String s = String.format(
							"child of '%s' with updates %s",
							content, childUpdates );
					return s.getBytes( UTF_8 );
				} );

		when( msg.child() ).thenReturn( child );

		return msg;
	}
}
