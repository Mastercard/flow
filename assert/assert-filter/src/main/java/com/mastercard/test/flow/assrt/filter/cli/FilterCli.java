package com.mastercard.test.flow.assrt.filter.cli;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Supplier;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp.Capability;

import com.mastercard.test.flow.assrt.filter.Filter;
import com.mastercard.test.flow.assrt.filter.FilterOptions;

/**
 * Shows an interface on the console to allow the filters to be updated before
 * we run the tests. Crucially, we get the chance to choose tags before flows
 * are built - this can improve performance by avoiding building flows that will
 * not be exercised.
 */
public class FilterCli {
	/**
	 * @return <code>true</code> if the cli should be invoked
	 */
	public static boolean requested() {
		return "cli".equals( FilterOptions.FILTER_UPDATE.value() )
				|| FilterOptions.FILTER_UPDATE.isTrue()
						&& GraphicsEnvironment.isHeadless();
	}

	private final Filter filter;

	private Supplier<Terminal> termBuilder = () -> {
		try {
			return TerminalBuilder.builder()
					.dumb( true )
					.build();
		}
		catch( IOException e ) {
			throw new UncheckedIOException( e );
		}
	};

	/**
	 * @param filter The filter to manipulate
	 */
	public FilterCli( Filter filter ) {
		this.filter = filter;
	}

	/**
	 * Useful for testing, not so much for real life
	 *
	 * @param t The terminal in which to run the interface
	 */
	public void in( Terminal t ) {
		termBuilder = () -> t;
	}

	/**
	 * Shows the interface. This method will block until the user has configured the
	 * {@link Filter} to their liking
	 */
	public void blockForInput() {
		try( Terminal term = termBuilder.get() ) {
			String line = null;

			UiPhase uiPhase = new TagPhase( filter );
			while( uiPhase != null ) {
				term.puts( Capability.clear_screen );
				term.flush();

				LineReader lr = LineReaderBuilder.builder()
						.terminal( term )
						.completer( uiPhase.completer() )
						.build();

				Cli cli = new Cli( Math.max( Cli.MIN_WIDTH, term.getWidth() ) );
				uiPhase.render( cli );
				lr.printAbove( cli.content() );
				line = lr.readLine( "> " );
				uiPhase = uiPhase.next( line );
			}
		}
		catch( IOException e ) {
			throw new UncheckedIOException( "Failed to close terminal", e );
		}
	}

}
