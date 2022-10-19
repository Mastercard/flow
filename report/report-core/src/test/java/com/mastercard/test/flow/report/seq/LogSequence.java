package com.mastercard.test.flow.report.seq;

import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.joining;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Encapsulates the nuts and bolts of interacting with the logs tab of the flow
 * detail page page so the tests can be more readable
 */
public class LogSequence extends AbstractSequence<LogSequence> {

	/**
	 * @param parent The parent sequence
	 */
	LogSequence( AbstractSequence<?> parent ) {
		super( parent );
	}

	@Override
	protected LogSequence self() {
		return this;
	}

	/**
	 * Navigates to the logs view by URL manipulation
	 *
	 * @param arguments in <code>name=value</code> format
	 * @return this
	 */
	public LogSequence logs( String... arguments ) {
		trace( "logs", (Object[]) arguments );
		String args = "";
		if( arguments.length > 0 ) {
			args = "?" + Stream.of( arguments ).collect( Collectors.joining( "&" ) );
		}

		return get( url + args );
	}

	/**
	 * Sets the log level filters
	 *
	 * @param levels Desired levels
	 * @return <code>this</code>
	 */
	public LogSequence levels( String... levels ) {
		trace( "levels", (Object[]) levels );

		List<WebElement> fields = driver.findElements( By.tagName( "mat-form-field" ) );
		WebElement select = fields.stream()
				.filter( e -> e.getText().endsWith( "Levels" ) )
				.findFirst()
				.orElse( null );

		Assertions.assertNotNull( select, () -> "Failed to find levels select in form fields:\n  "
				+ fields.stream().map( WebElement::getText ).collect( joining( "\n  " ) ) );

		select.click();

		// wait for the popup
		WebDriverWait wait = new WebDriverWait( driver, ofSeconds( 2 ) );
		List<WebElement> options = wait
				.withMessage( "Failed to find non-empty level options, found " +
						driver.findElements( By.tagName( "mat-option" ) ).stream()
								.map( e -> "\n  " + e.getText() )
								.collect( joining() ) )
				.until( dr -> {
					List<WebElement> opts = dr.findElements( By.tagName( "mat-option" ) );
					if( opts.stream().anyMatch( e -> e.getText().isEmpty() ) ) {
						return null;
					}
					return opts;
				} );

		Set<String> desired = new HashSet<>( Arrays.asList( levels ) );
		options.forEach( e -> {
			boolean isSelected = "true".equals( e.getAttribute( "aria-selected" ) );
			boolean wantSelected = desired.contains( e.getText() );
			if( isSelected != wantSelected ) {
				e.click();
			}
		} );

		// dismiss the popup
		actions.sendKeys( Keys.ESCAPE ).build().perform();

		return this;
	}

	/**
	 * Types in the source filter input
	 *
	 * @param filter filter text
	 * @return <code>this</code>
	 */
	public LogSequence source( String filter ) {
		WebElement e = driver.findElement( By.id( "source_filter_input" ) );
		e.click();
		e.sendKeys( filter );
		return this;
	}

	/**
	 * Types in the message filter input
	 *
	 * @param filter filter text
	 * @return <code>this</code>
	 */
	public LogSequence message( String filter ) {
		WebElement e = driver.findElement( By.id( "message_filter_input" ) );
		e.click();
		e.sendKeys( filter );
		return this;
	}

	/**
	 * Asserts on visible log content
	 *
	 * @param lines expected lines
	 * @return <code>this</code>
	 */
	public LogSequence hasMessages( String... lines ) {
		trace( "hasMessages", (Object[]) lines );
		Assertions.assertEquals(
				copypasta( lines ),
				copypasta( driver.findElements( By.className( "mat-row" ) ).stream()
						.map( r -> r.getText().replace( '\n', ' ' ) ) ) );
		return this;
	}
}
