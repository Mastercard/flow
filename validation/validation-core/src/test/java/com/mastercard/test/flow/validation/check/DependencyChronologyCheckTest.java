package com.mastercard.test.flow.validation.check;

import static java.util.stream.Collectors.toList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.FieldAddress;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Metadata;
import com.mastercard.test.flow.Model;

/**
 * Exercises {@link DependencyChronologyCheck}
 */
class DependencyChronologyCheckTest extends AbstractValidationTest {

	/***/
	DependencyChronologyCheckTest() {
		super( new DependencyChronologyCheck(),
				"Dependency chronology",
				"Dependencies must copy from past messages" );
	}

	/**
	 * No checks on the empty model
	 */
	@Test
	void empty() {
		test( mdl() );
	}

	/**
	 * Dependencies that go forwards in time
	 */
	@Test
	void valid() {
		test( mdl(
				"f_a 0 1", "f_b 0 2", "f_c 0 3",
				"f_d 1 2", "f_e 1 3",
				"f_f 2 3" ),
				"f_a [] : pass", "f_b [] : pass", "f_c [] : pass",
				"f_d [] : pass", "f_e [] : pass",
				"f_f [] : pass" );
	}

	/**
	 * Dependencies that go backwards in time are invalid
	 */
	@Test
	void invalid() {
		// time travelling within one interaction
		test( mdl( "f_a 3 0" ),
				"  details: Dependency chronology violation: copying data from\n"
						+ "cba\n"
						+ "to\n"
						+ "abc\n"
						+ " expected: null\n"
						+ "   actual: null\n"
						+ "offenders: f_a []\n"
						+ "trace for f_a\n"
						+ "  AVA->BEN []" );

		// A dependency from one message to itself doesn't make any sense at all
		test( mdl( "f_a 1 1" ),
				"  details: Dependency chronology violation: copying data from\n"
						+ "def\n"
						+ "to\n"
						+ "def\n"
						+ " expected: null\n"
						+ "   actual: null\n"
						+ "offenders: f_a []\n"
						+ "trace for f_a\n"
						+ "  BEN->CHE []" );

		// between interactions
		test( mdl( "f_a 2 0" ),
				"  details: Dependency chronology violation: copying data from\n"
						+ "fed\n"
						+ "to\n"
						+ "abc\n"
						+ " expected: null\n"
						+ "   actual: null\n"
						+ "offenders: f_a []\n"
						+ "trace for f_a\n"
						+ "  AVA->BEN []\n"
						+ "  BEN->CHE []" );
	}

	/**
	 * Dependencies that do not relate messages are ignored
	 */
	@Test
	void unmessaged() {
		test( mdl( "f_a 5 -1" ),
				"f_a [] : pass" );
	}

	private static Model mdl( String... flowspec ) {
		List<Flow> flows = Stream.of( flowspec )
				.map( spec -> {
					String[] parts = spec.split( " " );
					return flow( parts[0], Integer.parseInt( parts[1] ), Integer.parseInt( parts[2] ) );
				} )
				.collect( toList() );
		Model mdl = Mockito.mock( Model.class );
		when( mdl.flows() ).thenAnswer( i -> flows.stream() );
		return mdl;
	}

	private static Flow flow( String desc, int srcIdx, int snkIdx ) {
		Actor AVA = () -> "AVA";
		Actor BEN = () -> "BEN";
		Actor CHE = () -> "CHE";

		Message[] msgs = {
				message( "abc" ),
				message( "def" ),
				message( "fed" ),
				message( "cba" ) };

		Interaction root = interaction( AVA, msgs[0], BEN, msgs[3] );
		Interaction child = interaction( BEN, msgs[1], CHE, msgs[2] );

		Interaction[] ntrs = { root, child, child, root };

		when( root.children() ).thenReturn( Stream.of( child ) );

		Flow flw = mock( Flow.class );
		Metadata meta = mock( Metadata.class );
		when( meta.description() ).thenReturn( desc );
		when( meta.tags() ).thenReturn( Collections.emptySet() );
		when( meta.trace() ).thenReturn( "trace for " + desc );
		when( meta.id() ).thenCallRealMethod();

		when( flw.meta() ).thenReturn( meta );
		when( flw.root() ).thenReturn( root );

		FieldAddress src = mock( FieldAddress.class );
		when( src.flow() ).thenReturn( flw );
		if( 0 <= srcIdx && srcIdx < msgs.length ) {
			when( src.getInteraction() ).thenReturn( Optional.of( ntrs[srcIdx] ) );
			when( src.getMessage() ).thenReturn( Optional.of( msgs[srcIdx] ) );
		}
		FieldAddress snk = mock( FieldAddress.class );
		when( snk.flow() ).thenReturn( flw );
		if( 0 <= snkIdx && snkIdx < msgs.length ) {
			when( snk.getInteraction() ).thenReturn( Optional.of( ntrs[snkIdx] ) );
			when( snk.getMessage() ).thenReturn( Optional.of( msgs[snkIdx] ) );
		}

		Dependency dep = mock( Dependency.class );
		when( dep.source() ).thenReturn( src );
		when( dep.sink() ).thenReturn( snk );
		when( flw.dependencies() ).then( i -> Stream.of( dep ) );

		return flw;
	}

	private static final Interaction interaction( Actor from, Message request,
			Actor to, Message response ) {
		Interaction m = mock( Interaction.class );
		when( m.requester() ).thenReturn( from );
		when( m.request() ).thenReturn( request );
		when( m.responder() ).thenReturn( to );
		when( m.response() ).thenReturn( response );
		when( m.tags() ).thenReturn( Collections.emptySet() );
		return m;
	}

	private static final Message message( String name ) {
		Message m = mock( Message.class );
		when( m.assertable() ).thenReturn( name );
		return m;
	}
}
