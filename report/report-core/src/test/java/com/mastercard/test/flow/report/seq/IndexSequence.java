package com.mastercard.test.flow.report.seq;

import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openqa.selenium.support.ui.ExpectedConditions.elementToBeClickable;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Assertions;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.mastercard.test.flow.report.Copy;

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
	 * Hovers the pointer over an index entry
	 *
	 * @param name The text of the index item (same format as asserted by
	 *             {@link #hasFlows(String...)})
	 * @return <code>this</code>
	 */
	public IndexSequence hoverEntry( String name ) {
		trace( "hoverEntry", name );
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
			new Actions( driver )
					.moveToElement( linkItem )
					.build()
					.perform();
		}
		return self();
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
			linkItem.findElement( By.tagName( "a" ) ).click();
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

		assertEquals( Copy.pasta( flows ),
				Copy.pasta( flowItems.stream()
						.map( e -> printFlow( width, e ) ) ) );
		return self();
	}

	/**
	 * Asserts the number of rendered Flow rows.
	 *
	 * @param expected expected row count
	 * @return {@code this}
	 */
	public IndexSequence hasFlowCount( int expected ) {
		trace( "hasFlowCount", expected );
		assertEquals( expected,
				driver.findElements( By.tagName( "app-flow-nav-item" ) ).size(),
				"rendered Flow rows" );
		return self();
	}

	/**
	 * Asserts the visible filtered-result range.
	 *
	 * @param expected range label
	 * @return {@code this}
	 */
	public IndexSequence hasFlowRange( String expected ) {
		trace( "hasFlowRange", expected );
		new WebDriverWait( driver, ofSeconds( 2 ) )
				.withMessage( "Expected paginator range '" + expected + "'" )
				.until(
						d -> d.findElement( By.tagName( "mat-paginator" ) ).getText().contains( expected ) );
		return self();
	}

	/**
	 * Uses one of the Material paginator navigation controls.
	 *
	 * @param action First, Previous, Next, or Last
	 * @return {@code this}
	 */
	public IndexSequence page( String action ) {
		trace( "page", action );
		driver.findElement( By.cssSelector(
				String.format( "button[aria-label='%s page']", action ) ) ).click();
		return self();
	}

	/**
	 * Asserts that no custom direct-page controls are rendered.
	 *
	 * @return {@code this}
	 */
	public IndexSequence hasNoDirectPageControls() {
		trace( "hasNoDirectPageControls" );
		Assertions.assertTrue( driver.findElements( By.id( "page_input" ) ).isEmpty(), "Page input" );
		Assertions.assertTrue( driver.findElements( By.id( "page_go" ) ).isEmpty(), "Go button" );
		return self();
	}

	/**
	 * Asserts that no paginator is rendered for a small result set.
	 *
	 * @return {@code this}
	 */
	public IndexSequence hasNoPaginator() {
		trace( "hasNoPaginator" );
		Assertions.assertTrue( driver.findElements( By.tagName( "mat-paginator" ) ).isEmpty(),
				"paginator" );
		return self();
	}

	/**
	 * Selects a supported Material paginator page size.
	 *
	 * @param size desired rows per page
	 * @return {@code this}
	 */
	public IndexSequence pageSize( int size ) {
		trace( "pageSize", size );
		driver.findElement( By.tagName( "mat-paginator" ) )
				.findElement( By.tagName( "mat-select" ) ).sendKeys( Keys.ENTER );
		new WebDriverWait( driver, ofSeconds( 2 ) )
				.until( d -> {
					try {
						WebElement option = d.findElements( By.tagName( "mat-option" ) ).stream()
								.filter( element -> String.valueOf( size ).equals( element.getText().trim() ) )
								.findFirst().orElse( null );
						if( option == null || !option.isDisplayed() || !option.isEnabled() ) {
							return false;
						}
						option.click();
						return true;
					}
					catch( org.openqa.selenium.StaleElementReferenceException e ) {
						return false;
					}
				} );
		return self();
	}

	/**
	 * Asserts that filtering produced the explicit empty-result message.
	 *
	 * @return {@code this}
	 */
	public IndexSequence hasNoFlows() {
		trace( "hasNoFlows" );
		hasFlowCount( 0 );
		return has( By.id( "no_flows" ), "No flows match the current filters." );
	}

	/**
	 * Asserts that the browser has not requested any Detail pages.
	 *
	 * @return {@code this}
	 */
	public IndexSequence hasNoDetailRequests() {
		trace( "hasNoDetailRequests" );
		assertEquals( 0L, detailRequestCount(), "Detail requests" );
		return self();
	}

	/**
	 * Waits until System diagram activation has requested Detail data.
	 *
	 * @return {@code this}
	 */
	public IndexSequence hasDetailRequests() {
		trace( "hasDetailRequests" );
		new WebDriverWait( driver, ofSeconds( 2 ) ).until( d -> detailRequestCount() > 0 );
		return self();
	}

	private long detailRequestCount() {
		return (Long) ((JavascriptExecutor) driver).executeScript( "return performance"
				+ ".getEntriesByType('resource')"
				+ ".filter(entry => entry.name.includes('/detail/') && entry.name.endsWith('.html'))"
				+ ".length" );
	}

	/**
	 * Checks the user-visible flow-row presentation.
	 *
	 * @return <code>this</code>
	 */
	public IndexSequence hasFlowRowPresentation() {
		trace( "hasFlowRowPresentation" );
		WebElement item = driver.findElements( By.tagName( "app-flow-nav-item" ) ).get( 0 )
				.findElement( By.tagName( "mat-list-item" ) );
		WebElement link = item.findElement( By.className( "flow-link" ) );
		WebElement description = link.findElement( By.className( "description" ) );
		WebElement tags = item.findElement( By.className( "tag-region" ) );

		assertEquals( "none",
				((JavascriptExecutor) driver).executeScript(
						"return getComputedStyle(arguments[0]).textDecorationLine", link ),
				"link decoration" );
		assertEquals( 0, link.findElements( By.tagName( "app-tag" ) ).size(), "linked tags" );
		Assertions.assertFalse( tags.findElements( By.tagName( "app-tag" ) ).isEmpty(),
				"trailing tags" );
		Assertions.assertTrue(
				link.getRect().getX() + link.getRect().getWidth() <= tags.getRect().getX(),
				"tag region should trail the link" );
		Assertions.assertTrue( tags.getRect().getWidth() <= item.getRect().getWidth() * 0.45,
				"tag region should leave room for the description" );

		String longDescription = "A deliberately long flow description that must wrap without losing any text";
		int originalHeight = item.getRect().getHeight();
		((JavascriptExecutor) driver).executeScript(
				"arguments[0].style.width='240px'; arguments[1].textContent=arguments[2]",
				item, description, longDescription );
		assertEquals( longDescription, description.getAttribute( "textContent" ), "description" );
		Assertions.assertTrue( item.getRect().getHeight() > originalHeight, "wrapped row height" );

		WebElement nextItem = driver.findElements( By.tagName( "app-flow-nav-item" ) ).get( 1 )
				.findElement( By.tagName( "mat-list-item" ) );
		((JavascriptExecutor) driver).executeScript(
				"for(let i=0;i<12;i++){"
						+ "const tag=arguments[0].firstElementChild.cloneNode(true);"
						+ "tag.textContent='very-long-domain-tag-'+i;arguments[0].appendChild(tag)}",
				tags );
		double contentBottom = ((Number) ((JavascriptExecutor) driver).executeScript(
				"return Math.max(...[arguments[0],...arguments[1].children]"
						+ ".map(element=>element.getBoundingClientRect().bottom))",
				link, tags )).doubleValue();
		Assertions.assertTrue( item.getRect().getY() + item.getRect().getHeight() >= contentBottom,
				"row should contain wrapped tags" );
		Assertions.assertTrue(
				item.getRect().getY() + item.getRect().getHeight() <= nextItem.getRect().getY(),
				"adjacent rows should not overlap" );

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
				Copy.pasta( expected ),
				Copy.pasta( Stream.of(
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

	/**
	 * Asserts on the displayed interaction summary text.
	 *
	 * @param expected The expected text, or the empty string if we're expecting
	 *                 that element not to be shown at all
	 * @return <code>this</code>
	 */
	public IndexSequence hasInteractionSummary( String expected ) {
		trace( "hasInteractionSummary", expected );
		if( !expected.isEmpty() ) {
			new WebDriverWait( driver, ofSeconds( 2 ) )
					.until(
							d -> expected.equals( d.findElement( By.id( "interaction_summary" ) ).getText() ) );
		}

		assertEquals( expected,
				driver.findElements( By.id( "interaction_summary" ) ).stream()
						.map( WebElement::getText )
						.collect( joining( "\n" ) ),
				"Interaction summary" );
		return self();
	}

	/**
	 * Clicks on the expansion panel to show the interaction diagram.
	 *
	 * @return <code>this</code>
	 */
	public IndexSequence expandInteractions() {
		trace( "expandInteractions" );
		driver.findElement( By.id( "interactions_title" ) )
				.click();
		new WebDriverWait( driver, ofSeconds( 2 ) )
				.until( elementToBeClickable( By.id( "interactions_diagram" ) ) );
		return self();
	}

	/**
	 * Clicks on the "Copy mermaid" button, then asserts on the clipboard contents.
	 * Tries to restore the clipboard state to what it was before the test.
	 *
	 * @param expected Expected mermaid content
	 * @return <code>this</code>
	 */
	public IndexSequence hasMermaidMarkup( String... expected ) {
		trace( "hasMermaidMarkup", (Object[]) expected );

		Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
		Transferable before = cb.getContents( this );

		try {
			driver.findElement( By.id( "copy_mermaid_button" ) )
					.click();

			Transferable after = cb.getContents( this );

			assertEquals(
					Copy.pasta( expected ),
					Copy.pasta( after.getTransferData( DataFlavor.stringFlavor ).toString() ),
					"mermaid markup" );
		}
		catch( Exception e ) {
			throw new IllegalStateException( "failed to extract mermaid", e );
		}
		finally {
			cb.setContents( before, ( clipboard, contents ) -> {
				// we don't care about losing clipboard ownership
			} );
		}

		return self();
	}

	/**
	 * Clicks on the diagram orientation toggles
	 *
	 * @param orientation The new orientation
	 * @return <code>this</code>
	 */
	public IndexSequence diagramOrientation( String orientation ) {
		trace( "diagramOrientation", orientation );

		List<WebElement> toggles = driver.findElement( By.id( "interactions_orientation_group" ) )
				.findElements( By.tagName( "mat-button-toggle" ) );

		toggles.stream()
				.filter( e -> orientation.equals( e.getAttribute( "value" ) ) )
				.findFirst()
				.orElseThrow( () -> new IllegalStateException(
						String.format( "Failed to find diagram orientation value '%s' in %s",
								orientation, toggles.stream()
										.map( e -> e.getAttribute( "value" ) )
										.collect( toSet() ) ) ) )
				.click();

		return self();
	}

	/**
	 * Clicks the "hide filtered actors" toggle
	 *
	 * @return <code>this</code>
	 */
	public IndexSequence toggleFilteredActorHide() {
		trace( "toggleFilteredActorHide" );

		driver.findElement( By.id( "hide_filtered_actors_toggle" ) )
				.click();

		return self();
	}

	/**
	 * Asserts on the displayed interaction diagram.
	 *
	 * @param expected The name of the resource file that contains the expected SVG
	 *                 content
	 * @return <code>this</code>
	 */
	public IndexSequence hasInteractions( String... expected ) {
		trace( "hasInteractions", (Object[]) expected );
		String expectedSummary = Copy.pasta( expected );

		new WebDriverWait( driver, ofSeconds( 2 ) )
				.withMessage( () -> interactionDiagnostics()
						+ "\ndiagram:\n" + interactionDiagram() )
				.until( dr -> {
					try {
						return expectedSummary.equals(
								Copy.pasta( summariseSVG( interactionDiagram() ) ) );
					}
					catch( IllegalStateException e ) {
						return false;
					}
				} );

		String svgSummary = summariseSVG( interactionDiagram() );

		assertEquals(
				expectedSummary,
				Copy.pasta( svgSummary ),
				"interaction diagram structure" );

		return self();
	}

	private String interactionDiagnostics() {
		return (String) ((JavascriptExecutor) driver).executeScript( ""
				+ "return Array.from(document.querySelectorAll('#interactions_diagram path.flowchart-link'))"
				+ ".map(path => {"
				+ "  const style = getComputedStyle(path);"
				+ "  return path.getAttribute('class')"
				+ "    + ' inline=' + path.getAttribute('style')"
				+ "    + ' computed=' + style.strokeWidth + '/' + style.visibility;"
				+ "}).join('\\n');" );
	}

	private String interactionDiagram() {
		return driver
				.findElement( By.id( "interactions_diagram" ) )
				.getAttribute( "innerHTML" );
	}

	/**
	 * Extracts the gist of the interaction diagram.
	 *
	 * @param svg The diagram
	 * @return A string that describes the diagram
	 */
	private static String summariseSVG( String svg ) {
		try {
			Document doc = DocumentBuilderFactory.newInstance()
					.newDocumentBuilder()
					.parse( new InputSource( new StringReader( svg ) ) );
			XPath xpath = XPathFactory.newInstance().newXPath();

			StringBuilder sb = new StringBuilder();

			sb.append( "Nodes:" );
			NodeList nl = (NodeList) xpath.compile( "//span[@class='nodeLabel']" )
					.evaluate( doc, XPathConstants.NODESET );
			for( int i = 0; i < nl.getLength(); i++ ) {
				sb.append( "\n  " )
						.append( nl.item( i ).getTextContent() );
			}

			sb.append( "\nEdges:" );
			nl = (NodeList) xpath.compile( "//path[@class!='arrowMarkerPath']" )
					.evaluate( doc, XPathConstants.NODESET );
			for( int i = 0; i < nl.getLength(); i++ ) {
				Set<String> classes = Stream.of( nl.item( i )
						.getAttributes()
						.getNamedItem( "class" )
						.getTextContent()
						.split( " " ) )
						.collect( toSet() );

				sb.append( "\n " );
				Stream.of( "LS-", "edge-thickness-", "edge-pattern-", "LE-" )
						.forEach( prefix -> {
							sb.append( " " )
									.append( classes.stream()
											.filter( c -> c.startsWith( prefix ) )
											.map( c -> c.substring( prefix.length() ) )
											.findFirst().orElse( prefix + "???" ) );
						} );

				Optional.of( nl.item( i ) )
						.map( Node::getAttributes )
						.map( a -> a.getNamedItem( "style" ) )
						.map( Node::getTextContent )
						.filter( s -> s.contains( "stroke-width: 0" ) )
						.ifPresent( s -> sb.append( " <INVISIBLE>" ) );
			}

			return sb.toString();
		}
		catch( Exception e ) {
			throw new IllegalStateException( "failed to summarise\n" + svg, e );
		}
	}

	private IndexSequence dragChip( String text, String sourceId, String destId ) {
		WebElement source = driver.findElement( By.id( sourceId ) );
		WebElement chip = source.findElements( By.tagName( "mat-chip-row" ) ).stream()
				.filter( c -> text.equals( c.findElement( By.className( "tag_text" ) ).getText() ) )
				.findFirst()
				.orElseThrow( () -> new AssertionError( ""
						+ "Couldn't find chip with text '" + text + "'\n"
						+ "Have you called expandFilters()?" ) );
		WebElement destination = driver
				.findElement( By.id( destId ) )
				.findElement( By.tagName( "mat-chip-grid" ) );
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
