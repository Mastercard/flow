package com.mastercard.test.flow.report.seq;

import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.joining;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Encapsulates the nuts and bolts of interacting with the detail page so the
 * tests can be more readable
 */
public class DetailSequence extends AbstractSequence<DetailSequence> {

	private static int refreshCounter = 0;

	/**
	 * @param parent The parent sequence
	 */
	DetailSequence( AbstractSequence<?> parent ) {
		super( parent );
	}

	/**
	 * Navigates to the detail page by URL manipulation
	 *
	 * @param arguments in <code>name=value</code> format
	 * @return this
	 */
	public DetailSequence detail( String... arguments ) {
		trace( "detail", (Object[]) arguments );
		String args = "";
		if( arguments.length > 0 ) {
			args = "?" + Stream.of( arguments ).collect( Collectors.joining( "&" ) );
		}

		return get( url.replaceAll( "^(.*\\.html).*$", "$1" )
				+ "?refresh=" + refreshCounter++
				+ "#" + args );
	}

	/**
	 * @param header The expected header content
	 * @return <code>this</code>
	 */
	public DetailSequence hasHeader( String... header ) {
		trace( "hasHeader", (Object[]) header );
		WebElement h = driver.findElement( By.tagName( "mat-toolbar" ) );
		Assertions.assertEquals(
				copypasta( header ),
				copypasta( h.getText() ),
				"Header content" );
		return this;
	}

	/**
	 * @param motive The expected motivation text
	 * @return <code>this</code>
	 */
	public DetailSequence hasMotivation( String motive ) {
		trace( "hasMotivation", motive );
		return has( By.id( "motivation" ), motive );
	}

	/**
	 * @param tag        The HTML tag name of an element expected in the motivation
	 *                   div
	 * @param attributes name/pair attributes expected on that element
	 * @return <code>this</code>
	 */
	public DetailSequence hasMotivationElement( String tag, String... attributes ) {
		trace( "hasMotivationElement " + tag, (Object[]) attributes );
		WebElement e = driver
				.findElement( By.id( "motivation" ) )
				.findElement( By.tagName( tag ) );
		Assertions.assertEquals( 0, attributes.length % 2,
				"Expecting name/value pairs, but got odd number of args " + attributes.length );
		for( int i = 0; i < attributes.length; i += 2 ) {
			Assertions.assertEquals(
					attributes[i + 1],
					e.getAttribute( attributes[i] ),
					"Attribute " + attributes[i] );
		}
		return this;
	}

	/**
	 * @param trace The expected creation trace
	 * @return <code>this</code>
	 */
	public DetailSequence hasTrace( String trace ) {
		trace( "hasTrace", trace );
		return has( By.id( "trace" ), trace );
	}

	/**
	 * Navigates to the basis flow
	 *
	 * @return A new {@link DetailSequence}
	 */
	public DetailSequence basis() {
		trace( "basis" );
		driver.findElement( By.id( "basis_link" ) ).click();
		return new DetailSequence( this );
	}

	/**
	 * Navigates to the sole dependency flow
	 *
	 * @return A new {@link DetailSequence}
	 */
	public DetailSequence dependency() {
		trace( "dependency" );
		WebElement link = driver.findElement( By.id( "dependency_link" ) );
		Assertions.assertEquals( "task_alt\nDependency", link.getText() );
		link.click();
		return new DetailSequence( this );
	}

	/**
	 * Navigates to one of the multiple dependency flows
	 *
	 * @param linkText The expected link text
	 * @return A new {@link DetailSequence}
	 */
	public DetailSequence dependency( String linkText ) {
		trace( "dependency", linkText );
		WebElement menu = driver.findElement( By.id( "dependencies_item" ) );
		Assertions.assertEquals( "task_alt Dependencies", menu.getText() );
		menu.click();

		// The menu take a little while to fully appear
		WebDriverWait wait = new WebDriverWait( driver, ofSeconds( 2 ) );
		WebElement link = wait
				.withMessage( () -> String.format(
						"Failed to find link with text '%s', found%s", linkText,
						driver.findElements( By.className( "dependency_menu_link" ) ).stream()
								.map( e -> "\n" + e.getText() )
								.collect( joining() ) ) )
				.until( dr -> dr.findElements( By.className( "dependency_menu_link" ) ).stream()
						.filter( e -> e.getText().equals( linkText ) )
						.findFirst()
						.orElse( null ) );

		link.click();
		return new DetailSequence( this );
	}

	/**
	 * Clicks the Peers link
	 *
	 * @return The resulting {@link IndexSequence}
	 */
	public IndexSequence peers() {
		trace( "peers" );
		driver.findElement( By.id( "peer_link" ) ).click();
		return new IndexSequence( this );
	}

	/**
	 * Navigates to the logs tab
	 *
	 * @return The means to interact with and assert on the log tab
	 */
	public LogSequence logs() {
		trace( "logs" );
		return tab( "Logs", LogSequence::new );
	}

	/**
	 * Navigates to the flow sequence tab
	 *
	 * @return The means to interact with and assert on the flow tab
	 */
	public FlowSequence flow() {
		trace( "flow" );
		return tab( "Flow", FlowSequence::new );
	}

	/**
	 * Navigates to the context tab
	 *
	 * @return The means to interact with and assert on the context data
	 */
	public ContextSequence context() {
		trace( "context" );
		return tab( "Context", ContextSequence::new );
	}

	/**
	 * Navigates to the residue tab
	 *
	 * @return The means to interact with and assert on the residue data
	 */
	public ResidueSequence residue() {
		trace( "residue" );
		return tab( "Residue", ResidueSequence::new );
	}

	@Override
	protected DetailSequence self() {
		return this;
	}

}
