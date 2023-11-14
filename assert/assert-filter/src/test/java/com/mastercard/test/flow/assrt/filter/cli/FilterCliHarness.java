package com.mastercard.test.flow.assrt.filter.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jline.terminal.Terminal.TYPE_DUMB;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Deque;

import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.Assertions;

import com.mastercard.test.flow.assrt.filter.Filter;
import com.mastercard.test.flow.assrt.filter.Util;
import com.mastercard.test.flow.assrt.filter.mock.Mdl;

/**
 * Utility for exercising {@link FilterCli}
 */
class FilterCliHarness {

	private final PipedOutputStream input = new PipedOutputStream();
	private final ByteArrayOutputStream output = new ByteArrayOutputStream();

	private Deque<TerminalInteraction> interactions = new ArrayDeque<>();

	private String resultingFlows = "cli has not finished!";

	/**
	 * Adds a content expectation on the cli
	 *
	 * @param explanation A message to present on failing assertions
	 * @param content     The cli content to expect
	 * @param prompt      Marks the end of the expected content
	 * @return <code>this</code>
	 */
	public FilterCliHarness expect( String explanation, String content, String prompt ) {
		interactions.add( new Expectation( explanation, content, prompt ) );
		return this;
	}

	/**
	 * Adds an input to the cli
	 *
	 * @param content The user to provide to the cli
	 * @return <code>this</code>
	 */
	public FilterCliHarness input( String content ) {
		interactions.add( new Input( content ) );
		return this;
	}

	/**
	 * Add s a check on whether flows have been accessed yet
	 *
	 * @param explanation TODO
	 * @param b           <code>true</code> to expect flow access,
	 *                    <code>false</code> to expect no access
	 * @return <code>this</code>
	 */
	public FilterCliHarness expectFlowConstruction( String explanation, boolean b ) {
		interactions.add( new FlowConstructionCheck( b ) );
		return this;
	}

	/**
	 * Checks on the flows that pass the filter
	 *
	 * @param flows The expected flows
	 */
	public void expectResults( String... flows ) {
		assertEquals( Util.copypasta( flows ), resultingFlows );
	}

	/**
	 * Drives a cli
	 *
	 * @param model The model to filter
	 * @return <code>this</code>
	 */
	public FilterCliHarness on( Mdl model ) {
		try(
				PipedInputStream pis = new PipedInputStream( input );
				Terminal t = new DumbTerminal( TYPE_DUMB, TYPE_DUMB, pis, output, UTF_8 ) ) {
			Filter filter = new Filter( model );
			FilterCli cli = new FilterCli( filter );
			cli.in( t );

			// run the cli in another thread
			Thread clith = new Thread( () -> {
				cli.blockForInput();
			}, "cli runner" );
			clith.setDaemon( true );
			clith.start();

			while( !interactions.isEmpty() ) {
				interactions.poll().perform( input, output, model );
			}

			try {
				clith.join( 5000 );
			}
			catch( InterruptedException e ) {
				throw new IllegalStateException( "unexpected", e );
			}

			Assertions.assertFalse( clith.isAlive(), "cli thread should have stopped!" );

			resultingFlows = Util.copypasta( filter.flows()
					.map( f -> f.meta().id() ) );
		}
		catch( IOException e ) {
			throw new UncheckedIOException( e );
		}
		return this;
	}

	/**
	 * Superclass for cli interactions
	 */
	static abstract class TerminalInteraction {

		/**
		 * Implement this to do the interaction
		 *
		 * @param input  Where to put user input
		 * @param output Where to get terminal output
		 * @param model  The model that is being filtered
		 * @throws IOException if something goes wrong
		 */
		abstract void perform( PipedOutputStream input, ByteArrayOutputStream output, Mdl model )
				throws IOException;
	}

	private static class Expectation extends FilterCliHarness.TerminalInteraction {
		private final String explanation;
		private final String content;
		private final String end;

		Expectation( String explanation, String content, String end ) {
			this.explanation = explanation;
			this.content = content;
			this.end = end;
		}

		@Override
		public void perform( PipedOutputStream input, ByteArrayOutputStream output, Mdl model )
				throws IOException {
			String actual = "";
			do {
				actual = new String( output.toByteArray(), UTF_8 );
			}
			while( !actual.endsWith( end ) );
			output.reset();
			actual = actual.substring( 0, actual.length() - end.length() ).trim();

			Assertions.assertEquals( content, actual, explanation );
		}
	}

	private static class Input extends FilterCliHarness.TerminalInteraction {
		private final String content;

		Input( String content ) {
			this.content = content;
		}

		@Override
		public void perform( PipedOutputStream input, ByteArrayOutputStream output, Mdl model )
				throws IOException {
			input.write( content.getBytes( UTF_8 ) );
		}
	}

	private static class FlowConstructionCheck extends TerminalInteraction {
		private final boolean expected;

		FlowConstructionCheck( boolean expected ) {
			this.expected = expected;
		}

		@Override
		void perform( PipedOutputStream input, ByteArrayOutputStream output, Mdl model )
				throws IOException {
			assertEquals( expected, model.flowAccess() );
		}
	}
}
