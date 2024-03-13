package com.mastercard.test.flow.report.seq;

import org.junit.jupiter.api.Assertions;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import com.mastercard.test.flow.report.Copy;

/**
 * Encapsulates the nuts and bolts of interacting with the residue tab of the
 * flow detail page page so the tests can be more readable
 */
public class ResidueSequence extends AbstractSequence<ResidueSequence> {

	/**
	 * @param parent The parent sequence
	 */
	ResidueSequence( AbstractSequence<?> parent ) {
		super( parent );
	}

	@Override
	protected ResidueSequence self() {
		return this;
	}

	/**
	 * Asserts on names of the residue panels
	 *
	 * @param names expected names
	 * @return <code>this</code>
	 */
	public ResidueSequence hasPanels( String... names ) {
		trace( "hasPanels", (Object[]) names );
		Assertions.assertEquals(
				Copy.pasta( names ),
				Copy.pasta( driver.findElements( By.tagName( "mat-panel-title" ) ).stream()
						.map( WebElement::getText ) ),
				"Residue panel names" );

		return this;
	}

	/**
	 * Asserts on the visible content of a single panel
	 *
	 * @param panel Panel name
	 * @param lines expected content
	 * @return <code>this</code>
	 */
	public ResidueSequence hasContent( String panel, String... lines ) {
		trace( "hasContent", panel, lines );
		Assertions.assertEquals(
				Copy.pasta( lines ),
				Copy.pasta( driver.findElements( By.tagName( "mat-expansion-panel" ) ).stream()
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
