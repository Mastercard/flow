package com.mastercard.test.flow.report.seq;

import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openqa.selenium.support.ui.ExpectedConditions.elementToBeClickable;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

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
		List<WebElement> icons = driver.findElements( By.tagName( "mat-icon" ) )
				.stream()
				.filter( e -> "link_off".equals( e.getText() ) )
				.collect( toList() );

		if( index >= icons.size() ) {
			throw new IllegalStateException( "Expected at least " + (index + 1)
					+ " link_off icons, found only " + icons.size() );
		}

		icons.get( index ).click();

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
		List<WebElement> icons = driver.findElements( By.tagName( "mat-icon" ) )
				.stream()
				.filter( e -> "link".equals( e.getText() ) )
				.collect( toList() );

		if( index >= icons.size() ) {
			throw new IllegalStateException( "Expected at least " + (index + 1)
					+ " link icons, found only " + icons.size() );
		}

		icons.get( index ).click();

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
		Map<String, WebElement> items = driver.findElement( By.id( id ) )
				.findElements( By.tagName( "mat-list-item" ) ).stream()
				.collect( toMap( e -> e.getText().replaceAll( "\\s+", " " ), e -> e ) );

		Assertions.assertTrue( items.containsKey( from ),
				"Expected '" + from + "' in " + items.keySet() );
		Assertions.assertTrue( items.containsKey( to ),
				"Expected '" + to + "' in " + items.keySet() );

		actions.moveToElement( items.get( from ) )
				.clickAndHold()
				.moveByOffset( 0, 5 ) // shrug
				.moveToElement( items.get( to ) )
				.release()
				.pause( 400 )
				.build()
				.perform();

		return this;
	}

	private DiffSequence checkPairList( String parentTag, String... lines ) {
		return checkPairList( driver
				.findElement( By.tagName( parentTag ) ), lines );
	}

	private DiffSequence checkPairList( WebElement parent, String... lines ) {
		String[] fromFlows = parent.findElement( By.id( "left" ) )
				.findElements( By.tagName( "mat-list-item" ) ).stream()
				.map( e -> e.getText().replaceAll( "\\s+", " " ) )
				.toArray( String[]::new );
		String[] toFlows = parent.findElement( By.id( "right" ) )
				.findElements( By.tagName( "mat-list-item" ) ).stream()
				.map( e -> e.getText().replaceAll( "\\s+", " " ) )
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
		Assertions.assertEquals(
				copypasta( lines ),
				copypasta( sb.toString() ) );
		return this;
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
				copypasta( expected ),
				copypasta( driver.findElements( By.tagName( "app-pair-select-item" ) ).stream()
						.map( DiffSequence::flowSelectItemToString ) ) );
		return this;
	}

	private static String flowSelectItemToString( WebElement apse ) {
		StringBuilder sb = new StringBuilder();
		sb.append( apse.findElement( By.className( "description" ) ).getText() );
		apse.findElements( By.tagName( "app-tag" ) )
				.forEach( t -> sb.append( " " ).append( t.getText() ) );
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
		List<WebElement> items = driver.findElements( By.tagName( "app-pair-select-item" ) );
		items.stream()
				.filter( e -> name.equals( flowSelectItemToString( e ) ) )
				.findFirst()
				.orElseThrow( () -> new IllegalStateException(
						"Failed to find expected change item '" + name
								+ "' in:\n  " + items.stream()
										.map( DiffSequence::flowSelectItemToString )
										.collect( joining( "\n  " ) ) ) )
				.click();
		return this;
	}

	private static final Pattern DIFF_LINE = Pattern.compile( "^([0-9]+) ([-+0-9]+) (.*)" );

	/**
	 * Asserts on the displayed diff
	 *
	 * @param expected The diff lines
	 * @return <code>this</code>
	 */
	public DiffSequence hasDiff( String... expected ) {
		trace( "hasDiff", (Object[]) expected );

		// format the line indices so our ascii art comes out nicely
		String raw = driver.findElement( By.tagName( "td-ngx-text-diff" ) )
				.getText();
		int liw = Stream.of( raw.split( "\n" ) )
				.map( DIFF_LINE::matcher )
				.filter( Matcher::matches )
				.flatMap( m -> Stream.of( m.group( 1 ), m.group( 2 ) ) )
				.mapToInt( String::length )
				.max()
				.orElse( 1 );
		String fmt = "%" + liw + "s %" + liw + "s %s";

		String formatted = Stream.of( raw.split( "\n" ) )
				.map( line -> {
					Matcher m = DIFF_LINE.matcher( line );
					if( m.matches() ) {
						return String.format( fmt, m.group( 1 ), m.group( 2 ), m.group( 3 ) );
					}
					return line;
				} )
				.collect( joining( "\n" ) );

		Assertions.assertEquals(
				copypasta( expected ),
				copypasta( formatted ) );

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
				copypasta( expected ),
				copypasta( driver
						.findElement( By.id( "summary" ) )
						.findElements( By.tagName( "mat-expansion-panel-header" ) ).stream()
						.map( e -> e.getText().replace( "\n", " " ) )
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
		WebElement changed = driver.findElement( By.id( "changed" ) );
		changed.findElement( By.tagName( "mat-expansion-panel-header" ) )
				.click();

		// wait for expansion to finish
		new WebDriverWait( driver, ofSeconds( 2 ) )
				.until( dr -> "visibility: visible;"
						.equals( changed.findElement( By.className( "mat-expansion-panel-content" ) )
								.getAttribute( "style" ) ) );

		WebElement changes = changed.findElement( By.id( "changes" ) );

		assertEquals(
				copypasta( expected ),
				copypasta( changes.findElements( By.tagName(
						"mat-expansion-panel-header" ) ).stream()
						.map( e -> e.getText().replace( "\n", " " ) ) ) );

		return this;
	}

	/**
	 * Clicks on an analysis
	 *
	 * @param diff The diff summary
	 * @return <code>this</code>
	 */
	public DiffSequence expandAnalysis( String diff ) {
		trace( "analysisFlows", "diff" );
		WebElement changed = driver.findElement( By.id( "changed" ) );
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
		WebElement changed = driver.findElement( By.id( "changed" ) );
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
