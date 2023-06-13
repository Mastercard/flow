package com.mastercard.test.flow.report.seq;

import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openqa.selenium.support.ui.ExpectedConditions.elementToBeClickable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Encapsulates the nuts and bolts of interacting with the index page so the
 * tests can be more readable
 */
public class IndexSequence extends AbstractSequence<IndexSequence> {

	/**
	 * @param indexUrl The URL of the index
	 */
	public IndexSequence( String indexUrl ) {
		super( indexUrl );
	}

	/**
	 * @param parent The parent sequence
	 */
	IndexSequence( AbstractSequence<?> parent ) {
		super( parent );
	}

	@Override
	protected IndexSequence self() {
		return this;
	}

	/**
	 * Navigates to the index by URL manipulation
	 *
	 * @param arguments in <code>name=value</code> format
	 * @return this
	 */
	public IndexSequence index( String... arguments ) {
		trace( "index", (Object[]) arguments );
		String args = "";
		if( arguments.length > 0 ) {
			args = "#?" + Stream.of( arguments ).collect( Collectors.joining( "&" ) );
		}
		return get( url + args );
	}

	/**
	 * Navigates to a flow detail page by clicking on an index item
	 *
	 * @param name The text of the index item (same format as asserted by
	 *             {@link #hasFlows(String...)})
	 * @return The detail page sequence
	 */
	public DetailSequence detail( String name ) {
		trace( "detail", name );
		List<WebElement> flowItems = driver.findElements( By.tagName( "app-flow-nav-item" ) );
		int width = flowItems.stream()
				.map( IndexSequence::flowDescription )
				.mapToInt( String::length )
				.max()
				.orElse( 0 );
		WebElement linkItem = flowItems.stream()
				.filter( e -> name.equals( printFlow( width, e ) ) )
				.findFirst()
				.orElse( null );
		if( linkItem == null ) {
			Assertions.fail( String.format(
					"Failed to find detail link '%s' in%s",
					name,
					flowItems.stream()
							.map( e -> "\n" + printFlow( width, e ) )
							.collect( joining() ) ) );
		}
		else {
			linkItem.click();
		}
		return new DetailSequence( this );
	}

	/**
	 * Navigates to the model diff tool
	 *
	 * @return the diff sequence
	 */
	public DiffSequence diff() {
		trace( "diff" );
		return navTo( "Model diff", DiffSequence::new );
	}

	/**
	 * Asserts on the page header contents
	 *
	 * @param model     expected model title
	 * @param test      expected test title
	 * @param timestamp expected timestamp
	 * @return <code>this</code>
	 */
	public IndexSequence hasHeader( String model, String test, String timestamp ) {
		trace( "hasHeader", model, test, timestamp );
		return hasTitle( model + " | " + test + " @ " + timestamp )
				.has( By.id( "model_title" ), model )
				.has( By.id( "test_title" ), test )
				.has( By.id( "timestamp" ), timestamp );
	}

	/**
	 * Asserts on the current list of visible flows in the index
	 *
	 * @param flows expected flow listing
	 * @return <code>this</code>
	 */
	public IndexSequence hasFlows( String... flows ) {
		trace( "hasFlows", (Object[]) flows );
		List<WebElement> flowItems = driver.findElements( By.tagName( "app-flow-nav-item" ) );
		int width = flowItems.stream()
				.map( IndexSequence::flowDescription )
				.mapToInt( String::length )
				.max()
				.orElse( 0 );

		assertEquals( copypasta( flows ),
				copypasta( flowItems.stream()
						.map( e -> printFlow( width, e ) ) ) );
		return self();
	}

	/**
	 * Asserts on the active filter display
	 *
	 * @param description Active description filter
	 * @param include     tag inclusion filters
	 * @param exclude     tag exclusion filters
	 * @return <code>this</code>
	 */
	public IndexSequence hasFilters( String description, String include, String exclude ) {
		trace( "hasFilters", description, include, exclude );
		WebElement header = driver.findElement( By.tagName( "mat-panel-description" ) );

		assertEquals( description,
				header.findElements( By.className( "description_match_out" ) ).stream()
						.map( WebElement::getText )
						.findFirst()
						.orElse( "" ),
				"description" );

		assertEquals( include,
				header.findElements( By.className( "include" ) ).stream()
						.map( WebElement::getText )
						.collect( joining( ", " ) ),
				"included tags" );

		assertEquals( exclude,
				header.findElements( By.className( "exclude" ) ).stream()
						.map( WebElement::getText )
						.collect( joining( ", " ) ),
				"excluded tags" );

		return self();
	}

	/**
	 * Clicks on the tag header to expand the tag summary
	 *
	 * @return <code>this</code>
	 */
	public IndexSequence expandTags() {
		trace( "expandTags" );
		driver.findElement( By.id( "tag_summary" ) )
				.findElement( By.tagName( "mat-expansion-panel-header" ) )
				.click();
		new WebDriverWait( driver, ofSeconds( 2 ) )
				.until( elementToBeClickable( By.id( "tag_sort_toggle" ) ) );
		return self();
	}

	/**
	 * Checks on the tag summary
	 *
	 * @param expected The expected tag summary content
	 * @return <code>this</code>
	 */
	public IndexSequence hasTags( String... expected ) {
		trace( "hasTags", (Object[]) expected );
		WebElement tagSummary = driver.findElement( By.id( "tag_summary" ) );
		assertEquals(
				copypasta( expected ),
				copypasta( Stream.of(
						tagSummary.findElement( By.tagName( "mat-panel-description" ) )
								.findElements( By.tagName( "span" ) ).stream()
								.map( IndexSequence::spanIconText )
								.collect( joining( " " ) )
								.replace( "check_circle_outline", "_PASS_" )
								.replace( "error_outline", "_FAIL_" )
								.replace( "help_outline", "_SKIP_" )
								.replace( "new_releases", "_ERROR_" ),
						tagSummary
								.findElement( By.id( "tags" ) ).getText() ) ) );
		return this;
	}

	private static String spanIconText( WebElement e ) {
		List<String> content = new ArrayList<>();
		e.findElements( By.tagName( "mat-icon" ) ).stream()
				.map( i -> i.getAttribute( "svgIcon" ) )
				.forEach( content::add );
		content.add( e.getText() );
		return content.stream().collect( joining( " " ) );
	}

	/**
	 * Clicks the tag sort toggle
	 *
	 * @return <code>this</code>
	 */
	public IndexSequence toggleTagSort() {
		trace( "toggleTagSort" );
		driver.findElement( By.id( "tag_sort_toggle" ) )
				.click();
		return this;
	}

	/**
	 * Clicks on the filter header to expand the controls
	 *
	 * @return <code>this</code>
	 */
	public IndexSequence expandFilters() {
		trace( "expandFilters" );
		driver.findElement( By.id( "filters" ) )
				.findElement( By.tagName( "mat-expansion-panel-header" ) )
				.click();
		new WebDriverWait( driver, ofSeconds( 2 ) )
				.until( elementToBeClickable( By.id( "desc_filter_input" ) ) );
		return self();
	}

	/**
	 * Adds text to the description filter
	 *
	 * @param text The filter text
	 * @return <code>this</code>
	 */
	public IndexSequence descriptionFilter( String text ) {
		trace( "descriptionFilter", text );
		WebElement e = driver.findElement( By.id( "desc_filter_input" ) );
		e.click();
		e.sendKeys( text );
		return self();
	}

	/**
	 * Clicks tags in the index list, populating the include filter
	 *
	 * @param tag The tag to click
	 * @return <code>this</code>
	 */
	public IndexSequence clickTag( String tag ) {
		trace( "clickTag", tag );

		driver.findElement( By.tagName( "app-flow-nav-list" ) )
				.findElements( By.tagName( "app-tag" ) ).stream()
				.filter( te -> tag.equals( te.getText() ) )
				.findFirst()
				.orElseThrow( () -> new AssertionError( "Couldn't find tag " + tag ) )
				.click();

		return self();
	}

	/**
	 * Drags a tag from the include filter control to the exclude filter control
	 *
	 * @param tag The tag text
	 * @return <code>this</code>
	 */
	public IndexSequence dragToExclude( String tag ) {
		trace( "dragToExclude", tag );
		return dragChip( tag, "tag_include", "tag_exclude" );
	}

	/**
	 * Drags a tag from the exclude filter control to the include filter control
	 *
	 * @param tag The tag text
	 * @return <code>this</code>
	 */
	public IndexSequence dragToInclude( String tag ) {
		trace( "dragToInclude", tag );
		return dragChip( tag, "tag_exclude", "tag_include" );
	}

	private IndexSequence dragChip( String text, String sourceId, String destId ) {
		WebElement source = driver.findElement( By.id( sourceId ) );
		WebElement chip = source.findElements( By.tagName( "mat-chip" ) ).stream()
				.filter( c -> text.equals( c.findElement( By.className( "tag_text" ) ).getText() ) )
				.findFirst()
				.orElseThrow( () -> new AssertionError( ""
						+ "Couldn't find chip with text '" + text + "'\n"
						+ "Have you called expandFilters()?" ) );
		WebElement destination = driver
				.findElement( By.id( destId ) )
				.findElement( By.tagName( "mat-chip-list" ) );
		actions.moveToElement( chip )
				.clickAndHold( chip )
				.moveToElement( destination )
				// apparently crucial: a wee shoogle to activate the drop site
				.moveByOffset( 1, 0 )
				.release()
				.build()
				.perform();
		return self();
	}

	private static String flowDescription( WebElement e ) {
		return e.findElement( By.className( "description" ) ).getText().trim();
	}

	private static List<String> flowTags( WebElement e ) {
		return e.findElements( By.tagName( "app-tag" ) ).stream()
				.map( t -> t.getText().trim() )
				.filter( s -> !s.isEmpty() )
				.collect( toList() );
	}

	private static String printFlow( int descWidth, WebElement e ) {
		String fmt = "%-" + descWidth + "s  %s";
		return String.format( fmt, flowDescription( e ), flowTags( e ) );
	}
}
