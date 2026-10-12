package com.mastercard.test.flow.report.seq;

import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
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
		new WebDriverWait( driver, ofSeconds( 5 ) )
				.ignoring( StaleElementReferenceException.class )
				.withMessage( "Index page did not render after following " + id )
				.until( dr -> dr.findElements( By.id( "model_title" ) ).stream()
						.anyMatch( WebElement::isDisplayed ) );
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
				.ignoring( StaleElementReferenceException.class )
				.withMessage( () -> "Incomplete progress bar" )
				.until( dr -> {
					List<WebElement> progress = dr.findElements( By.tagName( "mat-progress-bar" ) );
					return progress.size() == 2 && progress.stream()
							.allMatch( pb -> "100".equals( pb.getAttribute( "aria-valuenow" ) ) );
				} );
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
	 * Asserts that the pairing controls are visible.
	 *
	 * @param icon expected control icon
	 * @return <code>this</code>
	 */
	public DiffSequence hasVisiblePairingControls( String icon ) {
		trace( "hasVisiblePairingControls", icon );
		new WebDriverWait( driver, ofSeconds( 5 ) )
				.ignoring( StaleElementReferenceException.class )
				.withMessage( "No visible " + icon + " pairing controls" )
				.until( dr -> {
					List<WebElement> icons = pairingControls( icon );
					return !icons.isEmpty() && icons.stream().allMatch( this::isVisible );
				} );
		return self();
	}

	private List<WebElement> pairingControls( String icon ) {
		WebElement panel = findActiveTabPanel();
		if( panel == null ) {
			return List.of();
		}
		return panel.findElements( By.tagName( "mat-icon" ) ).stream()
				.filter( e -> icon.equals( e.getAttribute( "svgIcon" ) ) )
				.toList();
	}

	private boolean isVisible( WebElement element ) {
		return element.getRect().getWidth() > 0
				&& element.getRect().getHeight() > 0
				&& "visible".equals( ((JavascriptExecutor) driver).executeScript(
						"return getComputedStyle(arguments[0]).visibility", element ) );
	}

	/**
	 * Clicks an unlink button to break an existing flow pairing
	 *
	 * @param index the index of the button in the list
	 * @return <code>this</code>
	 */
	public DiffSequence unpair( int index ) {
		List<WebElement> icons = pairingControls( "link_off" );

		if( index >= icons.size() ) {
			throw new IllegalStateException( "Expected at least " + (index + 1)
					+ " link_off icons, found only " + icons.size() );
		}

		clickPairingControl( icons.get( index ).findElement( By.xpath( ".." ) ) );
		awaitPairingControlRemoval( "link_off", icons.size() );

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
		List<WebElement> icons = pairingControls( "link" );

		if( index >= icons.size() ) {
			throw new IllegalStateException( "Expected at least " + (index + 1)
					+ " link icons, found only " + icons.size() );
		}

		clickPairingControl( icons.get( index ).findElement( By.xpath( ".." ) ) );
		awaitPairingControlRemoval( "link", icons.size() );

		return this;
	}

	private void clickPairingControl( WebElement control ) {
		((JavascriptExecutor) driver).executeScript(
				"arguments[0].scrollIntoView({block: 'center'}); arguments[0].click();", control );
	}

	private void awaitPairingControlRemoval( String icon, int previousCount ) {
		new WebDriverWait( driver, ofSeconds( 5 ) )
				.ignoring( StaleElementReferenceException.class )
				.withMessage( "Pairing action did not remove a " + icon + " control" )
				.until( dr -> pairingControls( icon ).size() < previousCount );
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
		List<String> initialOrder = dragOrder( id );
		boolean movingUp = initialOrder.indexOf( from ) > initialOrder.indexOf( to );
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
		int targetOffset = toDragBox.getRect().getHeight() / 2 - 2;
		actions.moveToElement( fromDragBox )
				.clickAndHold()
				.pause( 100 )
				.moveByOffset( 0, movingUp ? -10 : 10 )
				.pause( 100 )
				.moveToElement( toDragBox, 0, movingUp ? -targetOffset : targetOffset )
				.pause( 250 )
				.release()
				.perform();
		new WebDriverWait( driver, ofSeconds( 5 ) )
				.withMessage( () -> String.format(
						"Failed to move '%s' %s '%s' in %s list; current order: %s",
						from, movingUp ? "before" : "after", to, id, dragOrder( id ) ) )
				.until( dr -> {
					List<String> order = dragOrder( id );
					return movingUp
							? order.indexOf( from ) < order.indexOf( to )
							: order.indexOf( from ) > order.indexOf( to );
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
		String expected = Copy.pasta( lines );
		new WebDriverWait( driver, ofSeconds( 5 ) )
				.ignoring( StaleElementReferenceException.class )
				.withMessage( () -> "Expected pair list:\n" + expected
						+ "\nbut was:\n" + pairList( parentTag ) )
				.until( dr -> expected.equals( pairList( parentTag ) ) );
		return this;
	}

	private DiffSequence checkPairList( WebElement parent, String... lines ) {
		String expected = Copy.pasta( lines );
		new WebDriverWait( driver, ofSeconds( 5 ) )
				.ignoring( StaleElementReferenceException.class )
				.until( dr -> expected.equals( pairList( parent ) ) );
		return this;
	}

	private String pairList( String parentTag ) {
		WebElement panel = findActiveTabPanel();
		if( panel == null ) {
			return "";
		}
		return panel.findElements( By.tagName( parentTag ) ).stream()
				.findFirst()
				.map( this::pairList )
				.orElse( "" );
	}

	private String pairList( WebElement parent ) {
		String[] fromFlows = parent.findElement( By.id( "left" ) )
				.findElements( By.tagName( "app-flow-nav-item" ) ).stream()
				.map( DiffSequence::flowItemToString )
				.toArray( String[]::new );
		String[] toFlows = parent.findElement( By.id( "right" ) )
				.findElements( By.tagName( "app-flow-nav-item" ) ).stream()
				.map( DiffSequence::flowItemToString )
				.toArray( String[]::new );
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
		return Copy.pasta( sb.toString() );
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
		String expectedList = Copy.pasta( expected );
		new WebDriverWait( driver, ofSeconds( 5 ) )
				.withMessage( () -> "Expected changes:\n" + expectedList
						+ "\nbut was:\n" + changeList() )
				.until( dr -> expectedList.equals( changeList() ) );
		return this;
	}

	private String changeList() {
		try {
			WebElement panel = findActiveTabPanel();
			return panel == null ? ""
					: Copy.pasta( panel
							.findElements( By.tagName( "app-pair-select-item" ) ).stream()
							.map( DiffSequence::flowItemToString ) );
		}
		catch( StaleElementReferenceException ignored ) {
			return "";
		}
	}

	/**
	 * Clicks on a change from the list
	 *
	 * @param name The change name
	 * @return <code>this</code>
	 */
	public DiffSequence change( String name ) {
		trace( "change", name );
		WebElement option = new WebDriverWait( driver, ofSeconds( 5 ) )
				.withMessage( "Failed to find expected change item '" + name + "'" )
				.ignoring( StaleElementReferenceException.class )
				.until( dr -> {
					WebElement panel = findActiveTabPanel();
					return panel == null ? null
							: panel
									.findElements( By.tagName( "app-pair-select-item" ) ).stream()
									.filter( e -> name.equals( flowItemToString( e ) ) )
									.findFirst()
									.map( e -> e.findElement( By.tagName( "mat-list-option" ) ) )
									.orElse( null );
				} );
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
		String expectedDiff = Copy.pasta( expected );
		new WebDriverWait( driver, ofSeconds( 5 ) )
				.ignoring( StaleElementReferenceException.class )
				.withMessage( () -> "Expected diff:\n" + expectedDiff
						+ "\nbut was:\n" + diffContent() )
				.until( dr -> expectedDiff.equals( diffContent() ) );
		return this;
	}

	private String diffContent() {
		try {
			WebElement panel = findActiveTabPanel();
			if( panel == null ) {
				return "";
			}
			WebElement diffElement = panel.findElements( By.tagName( "app-text-diff" ) ).stream()
					.filter( WebElement::isDisplayed )
					.findFirst()
					.orElse( null );
			if( diffElement == null ) {
				return "";
			}
			List<List<String>> tableCells = new ArrayList<>();
			diffElement.findElements( By.tagName( "tr" ) )
					.forEach( row -> tableCells.add( row.findElements( By.tagName( "td" ) ).stream()
							.map( cell -> cell.getAttribute( "textContent" )
									.replace( '\u00A0', ' ' ) )
							.collect( toList() ) ) );

			int[] widths = new int[tableCells.stream()
					.mapToInt( List::size )
					.max()
					.orElse( 0 )];
			if( widths.length == 0 ) {
				return "";
			}
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

			return Copy.pasta( formatted );
		}
		catch( StaleElementReferenceException ignored ) {
			return "";
		}
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
		String expectedSummary = Copy.pasta( expected );

		new WebDriverWait( driver, ofSeconds( 3 ) )
				.withMessage( () -> "Expected:\n" + expectedSummary
						+ "\nbut was:\n" + summary() )
				.until( dr -> expectedSummary.equals( summary() ) );

		return this;
	}

	private String summary() {
		try {
			WebElement panel = findActiveTabPanel();
			return panel == null ? ""
					: Copy.pasta( panel.findElements( By.cssSelector(
							"#summary > mat-expansion-panel > mat-expansion-panel-header" ) ).stream()
							.map( e -> (String) ((JavascriptExecutor) driver).executeScript( ""
									+ "const clone = arguments[0].cloneNode(true);"
									+ "clone.querySelectorAll('app-tag').forEach(tag => "
									+ "  tag.after(document.createTextNode(' ')));"
									+ "return clone.textContent.trim().replace(/\\s+/g, ' ');", e ) )
							.filter( s -> !s.isEmpty() ) );
		}
		catch( StaleElementReferenceException ignored ) {
			return "";
		}
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
		WebElement header = changed.findElement( By.tagName( "mat-expansion-panel-header" ) );
		clickElement( header );

		// wait for expansion to finish
		new WebDriverWait( driver, ofSeconds( 2 ) )
				.until( dr -> "true".equals( header.getAttribute( "aria-expanded" ) )
						&& changed.findElement( By.className( "mat-expansion-panel-content" ) )
								.isDisplayed() );

		String expectedDiffs = Copy.pasta( expected );
		new WebDriverWait( driver, ofSeconds( 5 ) )
				.ignoring( StaleElementReferenceException.class )
				.withMessage( () -> "Expected analyses:\n" + expectedDiffs
						+ "\nbut was:\n" + analysisDiffList() )
				.until( dr -> expectedDiffs.equals( analysisDiffList() ) );

		return this;
	}

	private String analysisDiffList() {
		try {
			WebElement changed = visibleElement( By.id( "changed" ) );
			WebElement changes = changed.findElement( By.id( "changes" ) );
			return Copy.pasta( changes.findElements( By.tagName(
					"mat-expansion-panel-header" ) ).stream()
					.map( this::analysisHeaderText ) );
		}
		catch( IllegalStateException | StaleElementReferenceException ignored ) {
			return "";
		}
	}

	private String analysisHeaderText( WebElement header ) {
		return (String) ((JavascriptExecutor) driver).executeScript( ""
				+ "const header = arguments[0];"
				+ "const title = header.querySelector('mat-panel-title').textContent.trim();"
				+ "const details = Array.from(header.querySelectorAll("
				+ "  '.description app-tag, .diff_display span'))"
				+ "  .map(item => item.textContent.trim()).filter(Boolean).join(' ');"
				+ "return (title + ' ' + details).trim().replace(/\\s+/g, ' ');", header );
	}

	private WebElement visibleElement( By by ) {
		return new WebDriverWait( driver, ofSeconds( 5 ) )
				.ignoring( StaleElementReferenceException.class )
				.withMessage( "Failed to find visible element " + by )
				.until( dr -> {
					WebElement panel = findActiveTabPanel();
					if( panel != null ) {
						List<WebElement> elements = panel.findElements( by );
						if( !elements.isEmpty() ) {
							return elements.get( 0 );
						}
					}
					return dr.findElements( by ).stream()
							.filter( element -> element.isDisplayed()
									|| element.findElements( By.xpath( "./*" ) ).stream()
											.anyMatch( WebElement::isDisplayed ) )
							.findFirst()
							.orElse( null );
				} );
	}

	private WebElement activeTabPanel() {
		return new WebDriverWait( driver, ofSeconds( 5 ) )
				.ignoring( StaleElementReferenceException.class )
				.withMessage( "Failed to find active tab panel" )
				.until( dr -> findActiveTabPanel() );
	}

	private WebElement findActiveTabPanel() {
		return driver.findElements( By.cssSelector( "[role='tab'][aria-selected='true']" ) ).stream()
				.map( tab -> tab.getAttribute( "aria-controls" ) )
				.filter( id -> id != null )
				.flatMap( id -> driver.findElements( By.id( id ) ).stream() )
				.filter( WebElement::isDisplayed )
				.findFirst()
				.orElse( null );
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
				.filter( e -> diff.equals( analysisHeaderText(
						e.findElement( By.tagName( "mat-expansion-panel-header" ) ) ) ) )
				.findFirst()
				.orElseThrow( () -> new IllegalStateException( "Failed to find analysis " + diff ) );
		WebElement header = diffPanel.findElement( By.tagName( "mat-expansion-panel-header" ) );
		clickElement( header );

		// wait for expansion to finish
		new WebDriverWait( driver, ofSeconds( 2 ) )
				.until( dr -> "true".equals( header.getAttribute( "aria-expanded" ) )
						&& diffPanel.findElement( By.className( "mat-expansion-panel-content" ) )
								.isDisplayed() );

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
