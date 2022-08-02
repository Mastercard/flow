package com.mastercard.test.flow.validation.check;

import static java.util.stream.Collectors.toCollection;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Metadata;
import com.mastercard.test.flow.Model;

/**
 * Exercises {@link MessageSharingCheck}
 */
class MessageSharingCheckTest extends AbstractValidationTest {

	/***/
	MessageSharingCheckTest() {
		super( new MessageSharingCheck(),
				"Message sharing",
				"Ensures that message instances are not shared between distinct interactions" );
	}

	/**
	 * Happy path - no sharing
	 */
	@Test
	void pass() {
		test( mdl( "flow:a,b" ),
				"flow : pass" );
	}

	/**
	 * Violation - same message instance within the same flow
	 */
	@Test
	void intraflow() {
		test( mdl( "flow:a,a" ),
				"  details: Shared message:\n"
						+ "Contents of message 'a'\n"
						+ " expected: null\n"
						+ "   actual: null\n"
						+ "offenders: flow\n"
						+ "trace for flow\n"
						+ "  requester of 'a'->responder of 'a' []" );
	}

	/**
	 * Violation - same message instance in two different flows
	 */
	@Test
	void interflow() {
		test( mdl( "left:a,b", "right:b,c" ),
				"left : pass",
				"  details: Shared message:\n"
						+ "Contents of message 'b'\n"
						+ " expected: null\n"
						+ "   actual: null\n"
						+ "offenders: left\n"
						+ "trace for left\n"
						+ "  requester of 'a'->responder of 'b' []\n"
						+ "right\n"
						+ "trace for right\n"
						+ "  requester of 'b'->responder of 'c' []" );
	}

	private static Model mdl( String... flowMsgs ) {
		Model mdl = Mockito.mock( Model.class );
		Map<String, Message> msgCache = new HashMap<>();
		Mockito.when( mdl.flows() ).thenReturn( Stream.of( flowMsgs )
				.map( f -> flw( f.substring( 0, f.indexOf( ':' ) ), msgCache,
						Stream.of( f.substring( f.indexOf( ":" ) + 1 ).split( "," ) )
								.collect( toCollection( ArrayDeque::new ) ) ) ) );

		return mdl;
	}

	private static Flow flw( String id, Map<String, Message> msgCache, Deque<String> msg ) {
		Metadata meta = Mockito.mock( Metadata.class );
		Mockito.when( meta.id() ).thenReturn( id );
		Mockito.when( meta.trace() ).thenReturn( "trace for " + id );

		Flow flw = Mockito.mock( Flow.class );
		Mockito.when( flw.meta() ).thenReturn( meta );

		Interaction root = ntr( msgCache, msg );
		Mockito.when( flw.root() ).thenReturn( root );

		return flw;
	}

	private static Interaction ntr( Map<String, Message> msgCache, Deque<String> msg ) {
		Interaction ntr = Mockito.mock( Interaction.class );
		{
			String reqContent = msg.removeFirst();
			Actor reqActor = actr( "requester", reqContent );
			Mockito.when( ntr.requester() ).thenReturn( reqActor );
			Message req = msg( msgCache, reqContent );
			Mockito.when( ntr.request() ).thenReturn( req );
		}
		{
			String resContent = msg.removeLast();
			Actor resActor = actr( "responder", resContent );
			Mockito.when( ntr.responder() ).thenReturn( resActor );
			Message res = msg( msgCache, resContent );
			Mockito.when( ntr.response() ).thenReturn( res );
		}
		if( !msg.isEmpty() ) {
			Interaction child = ntr( msgCache, msg );
			Mockito.when( ntr.children() ).thenReturn( Stream.of( child ) );
		}
		else {
			Mockito.when( ntr.children() ).thenReturn( Stream.empty() );
		}

		return ntr;
	}

	private static Actor actr( String name, String msg ) {
		Actor actr = Mockito.mock( Actor.class );
		Mockito.when( actr.name() ).thenReturn( name + " of '" + msg + "'" );
		return actr;
	}

	private static Message msg( Map<String, Message> msgCache, String msg ) {
		return msgCache.computeIfAbsent( msg, id -> {
			Message m = Mockito.mock( Message.class );
			Mockito.when( m.assertable() ).thenReturn( "Contents of message '" + id + "'" );
			return m;
		} );
	}
}
