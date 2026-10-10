package com.mastercard.test.flow.report.seq;

import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openqa.selenium.support.ui.ExpectedConditions.elementToBeClickable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.mastercard.test.flow.report.Copy;

/**
 * Encapsulates the details of interacting with the diff tool
 */
public class DiffSequence extends AbstractSequence<DiffSequence> {

	private static int refreshCounter = 0;

	/**
	 * @param parent The parent sequence
	 */
	DiffSequence( AbstractSequence<?> parent ) {
		super( parent );
	}

	/**
	 * Navigates to the diff view by url manipulation
	 *
	 * @param arguments in <code>name=value</code> format
	 * @return <code>this</code>
	 */
	public DiffSequence diff( String... arguments ) {
		trace( "diff", (Object[]) arguments );
		String args = "";
		if( arguments.length > 0 ) {
			args = "?" + Stream.of( arguments ).collect( Collectors.joining( "&" ) );
		}
		// chrome doesn't actually refresh the page if the url doesn't change (and the
		// fragment doesn't count). Hence we're inserting a bogus url parameter to force
		// a page load
		String freshUrl = url.replaceAll( "/(#.*)", "/?refresh=" + refreshCounter++ + "$1" );
		get( freshUrl + args );
		awaitLoad();

		return this;
	}

	/**
	 * @return The current set of URL arguments
	 */
	public String[] getUrlArgs() {
		String current = driver.getCurrentUrl();
		return Stream.of( current.contains( "?" )
				? current.substring( current.lastIndexOf( "?" ) + 1 )
				: "" )
				.flatMap( a -> Stream.of( a.split( "&" ) ) )
				.sorted()
				.toArray( String[]::new );
	}

	/**
	 * Clicks on the source expansion panel header
	 *
	 * @return <code>this</code>
	 */
	public DiffSequence toggleSourceExpansion() {
		trace( "toggleSourceExpansion" );
		driver.findElement( By.id( "sources" ) )
				.findElement( By.tagName( "mat-expansion-panel-header" ) ).click();
		return this;
	}

	/**
	 * Enters the from url in the source panel
	 *
	 * @param from the from url
	 * @return <code>this</code>
	 */
	public DiffSequence from( String from ) {
		trace( "from", from );
		return source( "from_input", from );
	}

	/**
	 * Navigates to the from index
	 *
	 * @param text The expected link text
	 * @return the index sequence
	 */
	public IndexSequence fromIndex( String text ) {
		return srcIndex( "from_link", text );
	}

	/**
	 * Enters the to url in the source panel
	 *
	 * @param to the to url
	 * @return <code>this</code>
	 */
	public DiffSequence to( String to ) {
		trace( "to", to );
		return source( "to_input", to );
	}

	/**
	 * Navigates to the to index
	 *
	 * @param text The expected link text
	 * @return the index sequence
	 */
	public IndexSequence toIndex( String text ) {
		return srcIndex( "to_link", text );
	}

	private IndexSequence srcIndex( String id, String text ) {
		WebElement link = driver.findElement( By.id( id ) );
		Assertions.assertEquals( text, link.getText() );
		link.click();
		return new IndexSequence( this );
	}

	/**
	 * Waits for flow data to be loaded
	 *
	 * @return <code>this</code>
	 */
	public DiffSequence awaitLoad() {
		trace( "awaitLoad" );
		new WebDriverWait( driver, ofSeconds( 5 ) )
				.withMessage( () -> "Incomplete progress bar" )
				.until( dr -> dr.findElements( By.tagName( "mat-progress-bar" ) ).stream()
						.noneMatch( pb -> !"100".equals( pb.getAttribute( "aria-valuenow" ) ) ) );
		return this;
	}

	private DiffSequence source( String id, String srcUrl ) {
		WebElement e = new WebDriverWait( driver, ofSeconds( 2 ) )
				.until( elementToBeClickable( By.id( id ) ) );
		e.click();
		e.clear();
		e.sendKeys( srcUrl );
		return this;
	}

	/**
	 * Navigates to the paired tab
	 *
	 * @return <code>this</code>
	 */
	public DiffSequence paired() {
		trace( "paired" );
		return tab( "Paired" );
	}

	/**
	 * Asserts on the contents of the paired flow lists
	 *
	 * @param lines expected contents
	 * @return <code>this</code>
	 */
	public DiffSequence hasPairs( String... lines ) {
		trace( "hasPairs", (Object[]) lines );
		return checkPairList( "app-paired-flow-list", lines );
	}

	/**
	 * Clicks an unlink button to break an existing flow pairing
	 *
	 * @param index the index of the button in the list
	 * @return <code>this</code>
	 */
	public DiffSequence unpair( int index ) {
		List<WebElement> icons = activeTabPanel().findElements( By.tagName( "mat-icon" ) )
				.stream()
				.filter( e -> "link_off".equals( e.getAttribute( "svgIcon" ) ) )
				.collect( toList() );

		if( index >= icons.size() ) {
			throw new IllegalStateException( "Expected at least " + (index + 1)
					+ " link_off icons, found only " + icons.size() );
		}

		clickElement( icons.get( index ).findElement( By.xpath( ".." ) ) );

		return this;
	}

	/**
	 * Navigates to the unpaired tab
	 *
	 * @return <code>this</code>
	 */
	public DiffSequence unpaired() {
		trace( "unpaired" );
		return tab( "Unpaired" );
	}

	/**
	 * Asserts on the contents of the paired flow lists
	 *
	 * @param lines expected contents
	 * @return <code>this</code>
	 */
	public DiffSequence hasUnpairs( String... lines ) {
		trace( "hasUnpairs", (Object[]) lines );
		return checkPairList( "app-unpaired-flow-list", lines );
	}

	/**
	 * Clicks a link button to pair two flows
	 *
	 * @param index the index of the button in the list
	 * @return <code>this</code>
	 */
	public DiffSequence pair( int index ) {
		List<WebElement> icons = activeTabPanel().findElements( By.tagName( "mat-icon" ) )
				.stream()
				.filter( e -> "link".equals( e.getAttribute( "svgIcon" ) ) )
				.collect( toList() );

		if( index >= icons.size() ) {
			throw new IllegalStateException( "Expected at least " + (index + 1)
					+ " link icons, found only " + icons.size() );
		}

		clickElement( icons.get( index ).findElement( By.xpath( ".." ) ) );

		return this;
	}

	/**
	 * Drags an unpaired flow item up and down in the left-hand list
	 *
	 * @param from The flow to drag
	 * @param to   The flow in the target position
	 * @return <code>this</code>
	 */
	public DiffSequence dragLeft( String from, String to ) {
		trace( "dragLeft", from, to );
		return drag( "left", from, to );
	}

	/**
	 * Drags an unpaired flow item up and down in the right-hand list
	 *
	 * @param from The flow to drag
	 * @param to   The flow in the target position
	 * @return <code>this</code>
	 */
	public DiffSequence dragRight( String from, String to ) {
		trace( "dragRight", from, to );
		return drag( "right", from, to );
	}

	private DiffSequence drag( String id, String from, String to ) {
		Map<String, WebElement> items = activeTabPanel()
				.findElement( By.tagName( "app-unpaired-flow-list" ) )
				.findElement( By.id( id ) )
				.findElements( By.tagName( "app-flow-nav-item" ) ).stream()
				.collect( toMap( DiffSequence::flowItemToString, e -> e ) );

		Assertions.assertTrue( items.containsKey( from ),
				"Expected '" + from + "' in " + items.keySet() );
		Assertions.assertTrue( items.containsKey( to ),
				"Expected '" + to + "' in " + items.keySet() );

		WebElement fromDragBox = items.get( from ).findElement( By.xpath( ".." ) );
		WebElement toDragBox = items.get( to ).findElement( By.xpath( ".." ) );
		((JavascriptExecutor) driver).executeScript(
				"arguments[0].scrollIntoView({block: 'center'});", fromDragBox );
		((JavascriptExecutor) driver).executeAsyncScript( ""
				+ "const from = arguments[0], to = arguments[1], done = arguments[2];"
				+ "const start = from.getBoundingClientRect();"
				+ "const end = to.getBoundingClientRect();"
				+ "const event = (type, x, y, buttons) => new MouseEvent(type, {"
				+ "  bubbles: true, cancelable: true, view: window, button: 0, buttons,"
				+ "  clientX: x, clientY: y"
				+ "});"
				+ "const startX = start.left + start.width / 2;"
				+ "const startY = start.top + start.height / 2;"
				+ "const endX = end.left + end.width / 2;"
				+ "const endY = end.top + 2;"
				+ "from.dispatchEvent(event('mousedown', startX, startY, 1));"
				+ "requestAnimationFrame(() => {"
				+ "  document.dispatchEvent(event('mousemove', startX, startY + 10, 1));"
				+ "  requestAnimationFrame(() => {"
				+ "    document.dispatchEvent(event('mousemove', endX, endY, 1));"
				+ "    requestAnimationFrame(() => {"
				+ "      document.dispatchEvent(event('mouseup', endX, endY, 0));"
				+ "      setTimeout(done, 250);"
				+ "    });"
				+ "  });"
				+ "});", fromDragBox, toDragBox );
		new WebDriverWait( driver, ofSeconds( 2 ) )
				.until( dr -> {
					List<String> order = dragOrder( id );
					return order.indexOf( from ) < order.indexOf( to );
				} );

		return this;
	}

	@SuppressWarnings("unchecked")
	private List<String> dragOrder( String id ) {
		return (List<String>) ((JavascriptExecutor) driver).executeScript( ""
				+ "const tab = document.querySelector('[role=tab][aria-selected=true]');"
				+ "const panel = document.getElementById(tab.getAttribute('aria-controls'));"
				+ "const list = panel.querySelector('app-unpaired-flow-list #' + arguments[0]);"
				+ "return Array.from(list.querySelectorAll('app-flow-nav-item')).map(item => {"
				+ "  const description = item.querySelector('.description').textContent.trim();"
				+ "  const tags = Array.from(item.querySelectorAll('app-tag'))"
				+ "    .map(tag => tag.textContent.trim()).filter(Boolean).join(' ');"
				+ "  return tags ? description + ' ' + tags : description;"
				+ "});", id );
	}

	private DiffSequence checkPairList( String parentTag, String... lines ) {
		return checkPairList( driver
				.findElement( By.tagName( parentTag ) ), lines );
	}

	private DiffSequence checkPairList( WebElement parent, String... lines ) {
		String[][] flows = new WebDriverWait( driver, ofSeconds( 2 ) )
				.until( dr -> {
					try {
						String[] from = parent.findElement( By.id( "left" ) )
								.findElements( By.tagName( "app-flow-nav-item" ) ).stream()
								.map( DiffSequence::flowItemToString )
								.toArray( String[]::new );
						String[] to = parent.findElement( By.id( "right" ) )
								.findElements( By.tagName( "app-flow-nav-item" ) ).stream()
								.map( DiffSequence::flowItemToString )
								.toArray( String[]::new );
						return new String[][] { from, to };
					}
					catch( StaleElementReferenceException e ) {
						return null;
					}
				} );
		String[] fromFlows = flows[0];
		String[] toFlows = flows[1];
		int w = Stream.of( fromFlows, toFlows )
				.flatMap( Stream::of )
				.mapToInt( String::length )
				.max().orElse( 1 );
		String fmt = "%" + w + "s | %-" + w + "s";
		StringBuilder sb = new StringBuilder();
		for( int i = 0; i < fromFlows.length || i < toFlows.length; i++ ) {
			sb.append( String.format( fmt,
					i < fromFlows.length ? fromFlows[i] : "",
					i < toFlows.length ? toFlows[i] : "" ) )
					.append( "\n" );
		}
		Assertions.assertEquals(
				Copy.pasta( lines ),
				Copy.pasta( sb.toString() ) );
		return this;
	}

	private static String flowItemToString( WebElement item ) {
		String description = item.findElement( By.className( "description" ) )
				.getAttribute( "textContent" ).trim();
		String tags = item.findElements( By.tagName( "app-tag" ) ).stream()
				.map( tag -> tag.getAttribute( "textContent" ).trim() )
				.filter( tag -> !tag.isEmpty() )
				.collect( joining( " " ) );
		return tags.isEmpty() ? description : description + " " + tags;
	}

	/**
	 * Navigates to the changes tab
	 *
	 * @return <code>this</code>
	 */
	public DiffSequence changes() {
		trace( "changes" );
		return tab( "Changes" );
	}

	/**
	 * Asserts on the list of changed flows in the chnages tab
	 *
	 * @param expected Expected changes
	 * @return <code>this</code>
	 */
	public DiffSequence hasChangeList( String... expected ) {
		trace( "hasChangeList", (Object[]) expected );
		Assertions.assertEquals(
				Copy.pasta( expected ),
				Copy.pasta( driver.findElements( By.tagName( "app-pair-select-item" ) ).stream()
						.map( DiffSequence::flowSelectItemToString ) ) );
		return this;
	}

	private static String flowSelectItemToString( WebElement apse ) {
		StringBuilder sb = new StringBuilder();
		sb.append( apse.findElement( By.className( "description" ) )
				.getAttribute( "textContent" ).trim() );
		apse.findElements( By.tagName( "app-tag" ) )
				.forEach( t -> sb.append( " " )
						.append( t.getAttribute( "textContent" ).trim() ) );
		return sb.toString();
	}

	/**
	 * Clicks on a change from the list
	 *
	 * @param name The change name
	 * @return <code>this</code>
	 */
	public DiffSequence change( String name ) {
		trace( "change", name );
		List<WebElement> items = activeTabPanel()
				.findElements( By.tagName( "app-pair-select-item" ) );
		WebElement option = items.stream()
				.filter( e -> name.equals( flowSelectItemToString( e ) ) )
				.findFirst()
				.orElseThrow( () -> new IllegalStateException(
						"Failed to find expected change item '" + name
								+ "' in:\n  " + items.stream()
										.map( DiffSequence::flowSelectItemToString )
										.collect( joining( "\n  " ) ) ) )
				.findElement( By.tagName( "mat-list-option" ) );
		clickElement( option );
		return this;
	}

	/**
	 * Clicks the "Less context" button
	 *
	 * @return <code>this</code>
	 */
	public DiffSequence lessContext() {
		return context( "less" );
	}

	/**
	 * Clicks the "More context" button
	 *
	 * @return <code>this</code>
	 */
	public DiffSequence moreContext() {
		return context( "more" );
	}

	private DiffSequence context( String value ) {
		driver.findElement( By.id( "context" ) )
				.findElements( By.tagName( "mat-button-toggle" ) )
				.stream()
				.filter( mbt -> value.equals( mbt.getAttribute( "value" ) ) )
				.findFirst()
				.orElseThrow( () -> new IllegalStateException( "Failed to find context reduce button" ) )
				.click();
		return this;
	}

	/**
	 * Asserts on the displayed diff
	 *
	 * @param expected The diff lines
	 * @return <code>this</code>
	 */
	public DiffSequence hasDiff( String... expected ) {
		trace( "hasDiff", (Object[]) expected );

		List<List<String>> tableCells = new ArrayList<>();
		WebElement diffElement = driver.findElement( By.tagName( "app-text-diff" ) );
		diffElement.findElements( By.tagName( "tr" ) )
				.forEach( row -> tableCells.add( row.findElements( By.tagName( "td" ) ).stream()
						.map( cell -> cell.getAttribute( "textContent" )
								.replace( '\u00A0', ' ' ) )
						.collect( toList() ) ) );

		int[] widths = new int[tableCells.stream()
				.mapToInt( List::size )
				.max()
				.orElse( 0 )];
		Arrays.fill( widths, 1 );
		tableCells.forEach( row -> {
			if( row.size() == widths.length ) {
				for( int i = 0; i < row.size(); i++ ) {
					widths[i] = Math.max( widths[i], row.get( i ).length() );
				}
			}
		} );

		int totalWidth = IntStream.of( widths ).sum() + widths.length - 1;
		String fmt = IntStream.of( widths )
				.limit( widths.length - 1 )
				.mapToObj( i -> "%" + i + "s" )
				.collect( joining( " " ) )
				+ " %-" + widths[widths.length - 1] + "s";

		String formatted = tableCells.stream()
				.map( row -> {
					if( row.size() == widths.length ) {
						return String.format( fmt, row.toArray( new Object[row.size()] ) );
					}
					String s = row.stream().collect( joining( " " ) );
					while( s.length() < totalWidth ) {
						s = " " + s;
						if( s.length() < totalWidth ) {
							s = s + " ";
						}
					}
					return s;
				} )
				.collect( joining( "\n" ) );

		Assertions.assertEquals(
				Copy.pasta( expected ),
				Copy.pasta( formatted ) );

		return this;
	}

	/**
	 * Navigates to the summary tab
	 *
	 * @return <code>this</code>
	 */
	public DiffSequence analysis() {
		trace( "analysis" );
		return tab( "Analysis" );
	}

	/**
	 * Asserts on the analysis summary content
	 *
	 * @param expected expected summary
	 * @return <code>this</code>
	 */
	public DiffSequence hasSummary( String... expected ) {
		trace( "summary", (Object[]) expected );

		Assertions.assertEquals(
				Copy.pasta( expected ),
				Copy.pasta( driver.findElements( By.cssSelector(
						"#summary > mat-expansion-panel > mat-expansion-panel-header" ) ).stream()
						.map( e -> (String) ((JavascriptExecutor) driver).executeScript( ""
								+ "const clone = arguments[0].cloneNode(true);"
								+ "clone.querySelectorAll('app-tag').forEach(tag => "
								+ "  tag.after(document.createTextNode(' ')));"
								+ "return clone.textContent.trim().replace(/\\s+/g, ' ');", e ) )
						.filter( s -> !s.isEmpty() ) ) );

		return this;
	}

	/**
	 * Asserts on the change analysis content
	 *
	 * @param expected expected content
	 * @return this
	 */
	public DiffSequence analysisDiffs( String... expected ) {
		trace( "changeAnalysis", (Object[]) expected );
		WebElement changed = visibleElement( By.id( "changed" ) );
		clickElement( changed.findElement( By.tagName( "mat-expansion-panel-header" ) ) );

		// wait for expansion to finish
		new WebDriverWait( driver, ofSeconds( 2 ) )
				.until( dr -> "visibility: visible;"
						.equals( changed.findElement( By.className( "mat-expansion-panel-content" ) )
								.getAttribute( "style" ) ) );

		WebElement changes = changed.findElement( By.id( "changes" ) );

		assertEquals(
				Copy.pasta( expected ),
				Copy.pasta( changes.findElements( By.tagName(
						"mat-expansion-panel-header" ) ).stream()
						.map( e -> e.getText().replace( "\n", " " ) ) ) );

		return this;
	}

	private WebElement visibleElement( By by ) {
		List<WebElement> elements = activeTabPanel().findElements( by );
		if( !elements.isEmpty() ) {
			return elements.get( 0 );
		}
		return driver.findElements( by ).stream()
				.filter( element -> element.isDisplayed()
						|| element.findElements( By.xpath( "./*" ) ).stream()
								.anyMatch( WebElement::isDisplayed ) )
				.findFirst()
				.orElseThrow( () -> new IllegalStateException( "Failed to find visible element " + by ) );
	}

	private WebElement activeTabPanel() {
		WebElement selectedTab = driver.findElement(
				By.cssSelector( "[role='tab'][aria-selected='true']" ) );
		return driver.findElement( By.id( selectedTab.getAttribute( "aria-controls" ) ) );
	}

	private void clickElement( WebElement element ) {
		((JavascriptExecutor) driver).executeScript(
				"arguments[0].scrollIntoView({block: 'center'});", element );
		new WebDriverWait( driver, ofSeconds( 2 ) )
				.until( elementToBeClickable( element ) )
				.click();
	}

	/**
	 * Clicks on an analysis
	 *
	 * @param diff The diff summary
	 * @return <code>this</code>
	 */
	public DiffSequence expandAnalysis( String diff ) {
		trace( "analysisFlows", "diff" );
		WebElement changed = visibleElement( By.id( "changed" ) );
		WebElement changes = changed.findElement( By.id( "changes" ) );

		WebElement diffPanel = changes.findElements( By.tagName(
				"mat-expansion-panel" ) ).stream()
				.filter( e -> diff.equals( e.findElement( By.tagName( "mat-expansion-panel-header" ) )
						.getText().replace( "\n", " " ) ) )
				.findFirst()
				.orElseThrow( () -> new IllegalStateException( "" ) );
		diffPanel.findElement( By.tagName( "mat-expansion-panel-header" ) ).click();

		// wait for expansion to finish
		new WebDriverWait( driver, ofSeconds( 2 ) )
				.until( dr -> "visibility: visible;"
						.equals( diffPanel.findElement( By.className( "mat-expansion-panel-content" ) )
								.getAttribute( "style" ) ) );

		return this;
	}

	/**
	 * Asserts on the list of flow pairs that have been subjected to a diff
	 *
	 * @param expected expected list
	 * @return <code>this</code>
	 */
	public DiffSequence hasSubjectFlows( String... expected ) {
		trace( "hasSubjectFlows", (Object[]) expected );
		WebElement changed = visibleElement( By.id( "changed" ) );
		WebElement changes = changed.findElement( By.id( "changes" ) );
		checkPairList( changes.findElement( By.className( "mat-expansion-panel-content" ) ),
				expected );
		return this;
	}

	/**
	 * Clicks on the diff icon on an analysis diff
	 *
	 * @param index The position of the flow pair in the subject list
	 * @return <code>this</code>
	 */
	public DiffSequence clickDiff( int index ) {
		trace( "clickDiff", index );
		List<WebElement> triggers = driver.findElements( By.className( "diff_view_trigger" ) );
		Assertions.assertTrue( triggers.size() > index, ""
				+ "Found " + triggers.size() + " diff triggers,"
				+ " but expected at least " + (index + 1) );
		triggers.get( index ).click();

		waitForTabTransition();

		return this;
	}

	@Override
	protected DiffSequence self() {
		return this;
	}

}
