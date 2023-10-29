package com.mastercard.test.flow.report.seq;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import com.mastercard.test.flow.report.Copy;

/**
 * Encapsulates the nuts and bolts of interacting with the flow sequence diagram
 * so the tests can be more readable
 */
public class FlowSequence extends AbstractSequence<FlowSequence> {

	/**
	 * @param parent The parent sequence
	 */
	FlowSequence( AbstractSequence<?> parent ) {
		super( parent );
	}

	/**
	 * Asserts on the flow diagram headers
	 *
	 * @param actors Expected actors
	 * @return <code>this</code>
	 */
	public FlowSequence hasActors( String... actors ) {
		trace( "hasActors", (Object[]) actors );
		WebElement seq = driver.findElement( By.tagName( "app-flow-sequence" ) );
		Assertions.assertEquals(
				Copy.pasta( actors ),
				copypasta( seq.findElements( By.className( "entity" ) ).stream()
						.map( WebElement::getText ) ),
				"Sequence diagram actors" );
		return this;
	}

	/**
	 * Asserts on the flow diagram actions
	 *
	 * @param tx expected actions
	 * @return <code>this</code>
	 */
	public FlowSequence hasTransmissions( String... tx ) {
		trace( "hasTransmissions", (Object[]) tx );
		WebElement seq = driver.findElement( By.tagName( "app-flow-sequence" ) );
		int actors = seq.findElements( By.className( "entity" ) ).size();
		Assertions.assertEquals(
				Copy.pasta( tx ),
				copypasta( seq.findElements( By.tagName( "app-seq-action" ) ).stream()
						.map( asa -> actionText( actors, asa ) ) ),
				"Sequence diagram transmissions" );
		return this;
	}

	/**
	 * Clicks on a transmission in the sequence
	 *
	 * @param label The transmission label
	 * @return <code>this</code>
	 */
	public FlowSequence onTransmission( String label ) {
		trace( "onTransmission", label );
		driver.findElement( By.tagName( "app-flow-sequence" ) )
				.findElements( By.tagName( "app-seq-action" ) ).stream()
				.filter( e -> label.equals( e.findElement( By.tagName( "span" ) ).getText() ) )
				.findFirst()
				.orElseThrow( () -> new IllegalStateException(
						"Failed to find transmission with label '" + label + "'" ) )
				.findElement( By.className( "mat-ripple" ) )
				.click();
		return this;
	}

	/**
	 * Clicks on the expected view button
	 *
	 * @return <code>this</code>
	 */
	public FlowSequence onExpected() {
		trace( "onExpected" );
		driver.findElement( By.id( "Expected" ) ).click();
		return this;
	}

	/**
	 * Clicks on the actual view button
	 *
	 * @return <code>this</code>
	 */
	public FlowSequence onActual() {
		trace( "onActual" );
		driver.findElement( By.id( "Actual" ) ).click();
		return this;
	}

	/**
	 * Clicks on the diff view button
	 *
	 * @return <code>this</code>
	 */
	public FlowSequence onDiff() {
		trace( "onDiff" );
		driver.findElement( By.id( "Diff" ) ).click();
		return this;
	}

	/**
	 * Clicks on the basis diff fiew button
	 *
	 * @return <code>this</code>
	 */
	public FlowSequence onBasis() {
		trace( "onBasis" );
		driver.findElement( By.id( "Basis" ) ).click();
		return this;
	}

	/**
	 * Asserts on the displayed message content. Search hits will be enclosed in
	 * square brackets.
	 *
	 * @param lines Expected content lines
	 * @return <code>this</code>
	 */
	public FlowSequence hasMessage( String... lines ) {
		trace( "hasMessage", (Object[]) lines );

		WebElement ate = driver
				.findElement( By.tagName( "app-transmission" ) );
		String actual;
		if( ate.findElements( By.tagName( "mark" ) ).isEmpty() ) {
			// no search hits, just dump the text
			actual = ate.getText();
		}
		else {
			// search hits are in there! we need to iterate through the chunks to highlight
			// them in our extracted text
			actual = ate
					.findElements( By.className( "hl_chunk" ) ).stream()
					.map( c -> String.format(
							"mark".equals( c.getTagName() ) ? "[%s]" : "%s",
							c.getText() ) )
					.collect( joining() );
		}

		Assertions.assertEquals(
				Copy.pasta( lines ),
				Copy.pasta( actual ) );
		return this;
	}

	/**
	 * Clicks on the search toggle button
	 *
	 * @return <code>this</code>
	 */
	public FlowSequence toggleSearch() {
		trace( "toggleSearch" );
		driver.findElement( By.id( "search_toggle" ) )
				.findElement( By.className( "mat-icon" ) )
				.click();
		return this;
	}

	/**
	 * Enters a search term
	 *
	 * @param term The term to search for
	 * @return <code>this</code>
	 */
	public FlowSequence search( String term ) {
		trace( "search", term );
		WebElement input = driver.findElement( By.id( "search_input" ) );
		input.click();
		input.sendKeys( term );
		return this;
	}

	/**
	 * Asserts on the contents of the search box
	 *
	 * @param term The expected search term
	 * @return <code>this</code>
	 */
	public FlowSequence hasSearch( String term ) {
		trace( "hasSearch", term );
		WebElement input = driver.findElement( By.id( "search_input" ) );
		assertEquals( term, input.getAttribute( "value" ) );
		return this;
	}

	/**
	 * Asserts on which messages are showing search hits
	 *
	 * @param expected The expected search hits
	 * @return <code>this</code>
	 */
	public FlowSequence hasSearchHits( String... expected ) {
		trace( "expectSearchHits", (Object[]) expected );
		List<String> actual = driver
				.findElements( By.tagName( "app-seq-action" ) )
				.stream()
				.filter( e -> !e.findElements( By.tagName( "mark" ) ).isEmpty() )
				.map( e -> String.format( "%s : %s",
						e.findElement( By.className( "label" ) ).getText(),
						e.findElements( By.tagName( "mark" ) ).stream()
								.map( AbstractSequence::iconSemantic )
								.collect( joining( " " ) ) ) )
				.collect( Collectors.toList() );

		assertEquals(
				Copy.pasta( expected ),
				Copy.pasta( actual ),
				"Message content search hits" );
		return this;
	}

	private static final Map<String, String> ACTION_ICONS = new LinkedHashMap<>();
	static {
		ACTION_ICONS.put( "psychology", "e" );
		ACTION_ICONS.put( "foundation", "b" );
		ACTION_ICONS.put( "visibility", "a" );
		ACTION_ICONS.put( "check_circle_outline", "p" );
		ACTION_ICONS.put( "error_outline", "f" );
	}
	private static final Pattern MARGIN_LEFT = Pattern.compile( "margin-left: (\\d+\\.?\\d*)%" );

	private static String actionText( int actors, WebElement afs ) {
		StringBuilder sb = new StringBuilder();

		String style = afs.findElement( By.className( "action" ) ).getAttribute( "style" );
		Matcher m = MARGIN_LEFT.matcher( style );
		if( !m.find() ) {
			throw new IllegalStateException( "Failed to find margin-left in '" + style + "'" );
		}
		int pad = (int) (Float.parseFloat( m.group( 1 ) ) / 100 * actors * 2);

		for( int i = 0; i < pad; i++ ) {
			sb.append( " " );
		}

		sb.append( String.format( "%-12s", afs.findElement( By.tagName( "span" ) ).getText() ) );

		for( int i = 0; i < actors * 2 - pad; i++ ) {
			sb.append( " " );
		}

		Set<String> icons = afs.findElements( By.tagName( "mat-icon" ) ).stream()
				.map( e -> e.getAttribute( "svgIcon" ) )
				.collect( toSet() );

		sb.append( " [" )
				.append( ACTION_ICONS.entrySet().stream()
						.map( e -> icons.contains( e.getKey() ) ? e.getValue() : " " )
						.collect( joining() ) )
				.append( "]" );

		afs.findElements( By.className( "coverage" ) )
				.forEach( ce -> sb.append( " " ).append( ce.getText() ) );

		return sb.toString();
	}

	@Override
	protected FlowSequence self() {
		return this;
	}

}
