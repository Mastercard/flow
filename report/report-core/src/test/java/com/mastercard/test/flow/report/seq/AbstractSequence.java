package com.mastercard.test.flow.report.seq;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.mastercard.test.flow.report.Copy;

/**
 * Superclass for dealing with testing report pages
 *
 * @param <S> self type
 */
public abstract class AbstractSequence<S extends AbstractSequence<S>> {

	/**
	 * The base URL
	 */
	protected final String url;
	/**
	 * The browser
	 */
	protected final WebDriver driver;
	/**
	 * Allows non-trivial input actions
	 */
	protected final Actions actions;

	private boolean stepping = false;

	/**
	 * Maps from the name of the icon to what we mean by it
	 */
	private static final Map<String, String> ICON_SEMANTICS;
	static {
		Map<String, String> m = new TreeMap<>();
		m.put( "psychology", "expected" );
		m.put( "visibility", "actual" );
		ICON_SEMANTICS = Collections.unmodifiableMap( m );
	}

	/**
	 * @param driver The browser
	 * @param url    The base URL
	 */
	protected AbstractSequence( WebDriver driver, String url ) {
		this.driver = driver;
		this.url = url;
		actions = new Actions( driver );
	}

	/**
	 * @param parent The parent sequence
	 */
	protected AbstractSequence( AbstractSequence<?> parent ) {
		this( parent.driver, parent.driver.getCurrentUrl() );
		stepping = parent.stepping;
	}

	/**
	 * @param url the base URL
	 */
	protected AbstractSequence( String url ) {
		this( Browser.get(), url );
		Assertions.assertTrue( url.endsWith( "/" ) || url.endsWith( ".html" ),
				"You almost certainly want a trailing '/' or file name on '" + url + "'" );
	}

	/**
	 * @return <code>this</code>
	 */
	protected abstract S self();

	/**
	 * Navigates the browser
	 *
	 * @param destination Where we want to browse to
	 * @return <code>this</code>
	 */
	protected S get( String destination ) {

		// I really hate having to do this, but something seems to have changed on my
		// system such that it's necessary - a single get call usually just leaves you
		// on the `data:` url

		long start = System.currentTimeMillis();
		long limit = start + TimeUnit.SECONDS.toMillis( 20 );
		int attempts = 0;
		do {
			attempts++;
			driver.get( destination );
		}
		while( limit > System.currentTimeMillis() && "data:,".equals( driver.getCurrentUrl() ) );

		Assertions.assertNotEquals( "data:,", driver.getCurrentUrl(),
				String.format( "Failed to achieve destination '%s' after %.2fs and %d attempts",
						destination, (System.currentTimeMillis() - start) / 1000.0, attempts ) );

		return self();
	}

	/**
	 * Hits the back button on the browser
	 *
	 * @return <code>this</code>
	 */
	public S back() {
		driver.navigate().back();
		return self();
	}

	/**
	 * Hits the reload button on the browser
	 *
	 * @return <code>this</code>
	 */
	public S reload() {
		driver.navigate().refresh();
		return self();
	}

	/**
	 * Calling this at the start of your method allows us to log actions and control
	 * execution speed, which can help in debugging your test
	 *
	 * @param operation A name for the operation
	 * @param args      Operation arguments
	 * @return <code>this</code>
	 */
	protected S trace( String operation, Object... args ) {
		if( stepping ) {
			System.out.println( operation + " " + Arrays.toString( args ) );
			System.out.print( "Hit enter to continue" );
			System.out.flush();
			try {
				while( '\n' != System.in.read() ) {
					// wait for newline
				}
			}
			catch( IOException ioe ) {
				ioe.printStackTrace();
			}
		}
		return self();
	}

	/**
	 * Activate stepping mode to require user input for every
	 * {@link #trace(String, Object...)}-ed method to proceed
	 *
	 * @param b <code>true</code> to enter stepping mode, <code>false</code> to run
	 *          normally
	 * @return <code>this</code>
	 */
	public S stepping( boolean b ) {
		if( b && !Browser.SHOW ) {
			throw new IllegalStateException( "Not much point stepping execution if you can't see it.\n"
					+ "Run again with -D" + Browser.SHOW_PRP + "=true" );
		}
		stepping = b;
		return self();
	}

	/**
	 * Asserts on URL arguments
	 *
	 * @param expected in <code>"name=value"</code> format
	 * @return <code>this</code>
	 */
	public S hasUrlArgs( String... expected ) {
		trace( "hasUrlArgs", (Object[]) expected );
		String current = driver.getCurrentUrl();
		Stream<String> actual = Stream.of( current.contains( "?" )
				? current.substring( current.lastIndexOf( "?" ) + 1 )
				: "" )
				.flatMap( a -> Stream.of( a.split( "&" ) ) )
				.sorted();

		assertEquals( copypasta( expected ), copypasta( actual ),
				"arguments on URL " + current );
		return self();
	}

	/**
	 * Asserts page title
	 *
	 * @param title The expected title
	 * @return <code>this</code>
	 */
	public S hasTitle( String title ) {
		trace( "hasTitle", title );
		assertEquals( title, driver.getTitle() );
		return self();
	}

	/**
	 * Asserts content of a single page element
	 *
	 * @param by    How to find the element
	 * @param value The expected text content of the element
	 * @return <code>this</code>
	 */
	public S has( By by, String value ) {
		assertEquals( value,
				driver.findElement( by ).getText(),
				"element " + by );
		return self();
	}

	/**
	 * Clicks on an item in the navigation menu
	 *
	 * @param <T>            The type of the sequence that we're navigating to
	 * @param navItem        The text of the nav menu item
	 * @param seqConstructor How to construct the destinations equence
	 * @return The destination sequence
	 */
	protected <T extends AbstractSequence<T>> T navTo( String navItem,
			Function<AbstractSequence<S>, T> seqConstructor ) {
		driver.findElement( By.id( "menu_trigger" ) ).click();

		Function<WebDriver, Stream<WebElement>> menuItems = dr -> dr
				.findElement( By.className( "mat-menu-panel" ) )
				.findElements( By.className( "mat-menu-item" ) )
				.stream();

		new WebDriverWait( driver, Duration.ofSeconds( 1 ) )
				.withMessage( () -> String.format( "Failed to find '%s' menu item in:\n  %s",
						navItem,
						menuItems.apply( driver )
								.map( WebElement::getText )
								.collect( joining( "\n  " ) ) ) )
				.until( dr -> menuItems.apply( dr )
						.filter( e -> navItem.equals( e.getText() ) )
						.findFirst()
						.orElse( null ) )
				.click();

		return seqConstructor.apply( this );
	}

	/**
	 * Switches to a new tab
	 *
	 * @param <T>            The type of the resulting sequence
	 * @param name           The tab label content
	 * @param seqConstructor How to construct the resulting sequence
	 * @return The resulting sequence
	 */
	public <T extends AbstractSequence<T>> T tab( String name,
			Function<AbstractSequence<S>, T> seqConstructor ) {
		List<WebElement> tabs = driver.findElements( By.className( "mat-tab-label-content" ) );
		tabs.stream()
				.filter( e -> e.getText().startsWith( name ) )
				.findFirst()
				.orElseThrow( () -> new IllegalStateException(
						"Failed to find '" + name + "' tab in:\n  "
								+ tabs.stream()
										.map( WebElement::getText )
										.collect( joining( "\n  " ) ) ) )
				.click();
		waitForTabTransition();
		return seqConstructor.apply( this );
	}

	/**
	 * Switches to a new tab
	 *
	 * @param label The tab label content
	 * @return <code>this</code>
	 */
	public S tab( String label ) {
		return tab( label, dr -> self() );
	}

	/**
	 * Call this after provoking a tab switch, it will block until the tab content
	 * transition is complete
	 */
	protected void waitForTabTransition() {
		// wait for the tab transition to complete
		new WebDriverWait( driver, Duration.ofSeconds( 3 ) )
				.withMessage( () -> "Failed to find a relaxed tab body in:\n  " + driver
						.findElements( By.className( "mat-tab-body-content" ) ).stream()
						.map( e -> e.getAttribute( "style" ) )
						.collect( joining( "\n  " ) ) )
				.until( dr -> dr.findElements( By.className( "mat-tab-body-content" ) ).stream()
						.anyMatch( e -> "transform: none;".equals( e.getAttribute( "style" ) ) ) );
	}

	/**
	 * Extracts a meaningful name from an icon element
	 *
	 * @param icon The icon element
	 * @return What that icon means
	 */
	protected static String iconSemantic( WebElement icon ) {
		String name = icon.findElement( By.tagName( "mat-icon" ) )
				.getAttribute( "svgIcon" );
		return ICON_SEMANTICS.getOrDefault( name, name );
	}

	/**
	 * @param content Some strings
	 * @return A string that can be trivially copy/pasted into java source
	 */
	protected static String copypasta( String... content ) {
		return Copy.pasta( Stream.of( content ) );
	}

	/**
	 * @param content Some strings
	 * @return A string that can be trivially copy/pasted into java source
	 */
	protected static String copypasta( Collection<String> content ) {
		return Copy.pasta( content.stream() );
	}

	/**
	 * @param content Some strings
	 * @return A string that can be trivially copy/pasted into java source
	 */
	protected static String copypasta( Stream<String> content ) {
		return Copy.pasta( content );
	}
}
