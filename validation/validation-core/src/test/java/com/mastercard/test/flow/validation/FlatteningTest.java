package com.mastercard.test.flow.validation;

import static com.mastercard.test.flow.util.Tags.tags;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.UncheckedIOException;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Metadata;
import com.mastercard.test.flow.Residue;

/**
 * Exercises the flow stringifiction behaviour upon which the edit-distance
 * calculations are based
 */
@SuppressWarnings("static-method")
class FlatteningTest {

	/**
	 * Exercises flow stringification
	 */
	@Test
	void flatten() {
		Metadata meta = mock( Metadata.class );
		when( meta.description() ).thenReturn( "description" );
		when( meta.motivation() ).thenReturn( "motivation" );
		when( meta.tags() ).thenReturn( tags( "tags", "are", "handy" ) );

		Interaction root = ntr( "AVA",
				"Hi Ben!", "Hi Ava!",
				"BEN", "greeting" );
		Interaction leaf = ntr( "BEN",
				"I don't want to talk about cheese", "Just play it cool",
				"CHE", "sotto_voce" );
		when( root.children() ).thenAnswer( a -> Stream.of( leaf ) );

		Flow flow = mock( Flow.class );
		when( flow.meta() ).thenReturn( meta );
		when( flow.context() ).thenAnswer( a -> Stream.of( ctx() ) );
		when( flow.residue() ).thenAnswer( a -> Stream.of( rsd() ) );
		when( flow.root() ).thenReturn( root );

		assertEquals( ""
				+ "Identity:\n"
				+ "  description\n"
				+ "  are\n"
				+ "  handy\n"
				+ "  tags\n"
				+ "Motivation:\n"
				+ "  motivation\n"
				+ "Context:\n"
				+ "  Context name:\n"
				+ "  {\n"
				+ "    \"data\" : \"this is context data!\"\n"
				+ "  }\n"
				+ "Residue:\n"
				+ "  Residue name:\n"
				+ "  {\n"
				+ "    \"data\" : \"this is residue data!\"\n"
				+ "  }\n"
				+ "Interactions:\n"
				+ "  ┌REQUEST AVA 🠖 BEN [greeting]\n"
				+ "  │Hi Ben!\n"
				+ "  ╘ Provokes:\n"
				+ "  ┌REQUEST BEN 🠖 CHE [sotto_voce]\n"
				+ "  │I don't want to talk about cheese\n"
				+ "  └\n"
				+ "  ┌RESPONSE BEN 🠔 CHE [sotto_voce]\n"
				+ "  │Just play it cool\n"
				+ "  └\n"
				+ "  ┌RESPONSE AVA 🠔 BEN [greeting]\n"
				+ "  │Hi Ava!\n"
				+ "  └",
				InheritanceHealth.flatten( flow ) );
	}

	/**
	 * Shows what happens when the {@link Context} is not serialisable
	 */
	@Test
	void badContext() {

		Metadata meta = mock( Metadata.class );
		when( meta.description() ).thenReturn( "description" );
		when( meta.motivation() ).thenReturn( "motivation" );
		when( meta.tags() ).thenReturn( tags( "tags", "are", "handy" ) );

		Context ctx = new Context() {
			@SuppressWarnings("unused")
			public final Object nonSerialisable = new Object();

			@Override
			public String name() {
				return null;
			}

			@Override
			public Set<Actor> domain() {
				return null;
			}

			@Override
			public Context child() {
				return null;
			}
		};

		Flow flow = mock( Flow.class );
		when( flow.meta() ).thenReturn( meta );
		when( flow.context() ).thenAnswer( a -> Stream.of( ctx ) );

		assertThrows( UncheckedIOException.class, () -> InheritanceHealth.flatten( flow ) );
	}

	/**
	 * Shows what happens when the {@link Residue} is not serialisable
	 */
	@Test
	void badResidue() {

		Metadata meta = mock( Metadata.class );
		when( meta.description() ).thenReturn( "description" );
		when( meta.motivation() ).thenReturn( "motivation" );
		when( meta.tags() ).thenReturn( tags( "tags", "are", "handy" ) );

		Residue rsd = new Residue() {

			@SuppressWarnings("unused")
			public final Object nonSerialisable = new Object();

			@Override
			public String name() {
				return null;
			}

			@Override
			public Residue child() {
				return null;
			}
		};

		Flow flow = mock( Flow.class );
		when( flow.meta() ).thenReturn( meta );
		when( flow.residue() ).thenAnswer( a -> Stream.of( rsd ) );

		assertThrows( UncheckedIOException.class, () -> InheritanceHealth.flatten( flow ) );
	}

	private static Context ctx() {
		return new Context() {
			public final String data = "this is context data!";

			@Override
			public String name() {
				return "Context name";
			}

			@Override
			public Set<Actor> domain() {
				throw new UnsupportedOperationException( data );
			}

			@Override
			public Context child() {
				throw new UnsupportedOperationException( data );
			}
		};
	}

	private Residue rsd() {
		return new Residue() {
			public final String data = "this is residue data!";

			@Override
			public String name() {
				return "Residue name";
			}

			@Override
			public Residue child() {
				throw new UnsupportedOperationException( data );
			}
		};
	}

	private static Interaction ntr( String from, String req, String res,
			String to, String... tags ) {

		Interaction ntr = mock( Interaction.class );
		when( ntr.requester() ).thenReturn( () -> from );
		Message reqm = msg( req );
		when( ntr.request() ).thenReturn( reqm );
		when( ntr.responder() ).thenReturn( () -> to );
		Message resm = msg( res );
		when( ntr.response() ).thenReturn( resm );
		when( ntr.tags() ).thenReturn( tags( tags ) );
		return ntr;
	}

	private static Message msg( String content ) {
		Message msg = mock( Message.class );
		when( msg.assertable() ).thenReturn( content );
		return msg;
	}
}
