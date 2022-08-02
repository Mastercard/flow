package com.mastercard.test.flow.report.seq;

import org.junit.jupiter.api.Assertions;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Encapsulates the nuts and bolts of interacting with the context tab of the
 * flow detail page page so the tests can be more readable
 */
public class ContextSequence extends AbstractSequence<ContextSequence> {

	/**
	 * @param parent The parent sequence
	 */
	ContextSequence( AbstractSequence<?> parent ) {
		super( parent );
	}

	@Override
	protected ContextSequence self() {
		return this;
	}

	/**
	 * Asserts on names of the context panels
	 *
	 * @param names expected names
	 * @return <code>this</code>
	 */
	public ContextSequence hasPanels( String... names ) {
		trace( "hasPanels", (Object[]) names );
		Assertions.assertEquals(
				copypasta( names ),
				copypasta( driver.findElements( By.tagName( "mat-panel-title" ) ).stream()
						.map( WebElement::getText ) ),
				"Context panel names" );

		return this;
	}

	/**
	 * Asserts on the visible content of a single panel
	 *
	 * @param panel Panel name
	 * @param lines expected content
	 * @return <code>this</code>
	 */
	public ContextSequence hasContent( String panel, String... lines ) {
		trace( "hasContent", panel, lines );
		Assertions.assertEquals(
				copypasta( lines ),
				copypasta( driver.findElements( By.tagName( "mat-expansion-panel" ) ).stream()
						.filter( e -> panel.equals(
								e.findElement( By.tagName( "mat-panel-title" ) ).getText() ) )
						.findFirst()
						.orElseThrow( () -> new IllegalStateException(
								"Failed to find expansion panel with title " + panel ) )
						.findElement( By.className( "mat-expansion-panel-body" ) )
						.getText() ),
				"Content of panel " + panel );
		return this;
	}

}
