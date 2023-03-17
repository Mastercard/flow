package com.mastercard.test.flow.report.seq;

import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;
import static org.openqa.selenium.chrome.ChromeDriverService.CHROME_DRIVER_SILENT_OUTPUT_PROPERTY;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxBinary;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;

import io.github.bonigarcia.wdm.WebDriverManager;

/**
 * Handles the lifecycle of the chrome instance we use to test the report. Add
 * this class as an {@link ExtendWith} annotation on your test class that uses
 * the browser
 */
public class Browser implements
		BeforeAllCallback,
		AfterAllCallback,
		ExtensionContext.Store.CloseableResource {

	/**
	 * The system property name that controls browser visibility
	 */
	public static final String SHOW_PRP = "browser.show";

	/**
	 * <code>true</code> if the browser is visible
	 */
	public static final boolean SHOW = Boolean.getBoolean( SHOW_PRP );

	/**
	 * Causes a single browser instance to be used for all tests. This is obviously
	 * faster but it seems to cause maddeningly opaque and tedious-to-reproduce test
	 * failures.
	 */
	private static final boolean SHARED = Boolean.getBoolean( "browser.share" );

	/**
	 * The system property name that causes browser tests to be skipped
	 */
	private static final String SKIP_PRP = "browser.skip";

	/**
	 * The system property that controls which driver we use
	 */
	private static final String DRIVER_PRP = "browser.driver";

	/**
	 * Causes all tests that use the browser to be skipped
	 */
	private static final boolean SKIP = Boolean.getBoolean( SKIP_PRP );

	private static WebDriver driver;
	private static boolean shutdownHooked = false;

	/**
	 * @return The browser
	 */
	public static WebDriver get() {
		if( driver == null ) {
			if( !shutdownHooked ) {
				throw new IllegalStateException( ""
						+ "You've asked for the browser before the shutdown hook has been set.\n"
						+ "Only call Browser.get() in test classes with @ExtendWith(Browser.class) annotation" );
			}
			Driver type = Driver.CHROME;

			for( Driver d : Driver.values() ) {
				if( d.name().equalsIgnoreCase( System.getProperty( DRIVER_PRP ) ) ) {
					type = d;
					break;
				}
			}

			driver = type.get();
		}
		return driver;
	}

	/**
	 * Called before the first test method on classes where this is registered as an
	 * extension
	 */
	@Override
	public void beforeAll( ExtensionContext context ) throws Exception {
		Assumptions.assumeFalse( SKIP, "Skipping due to system property '" + SKIP_PRP + "'" );
		Assumptions.assumeFalse( "none".equals( System.getProperty( "node" ) ),
				"The report webapp hasn't been compiled (due to system property node=none)" );

		if( !shutdownHooked ) {
			context.getRoot()
					.getStore( GLOBAL )
					.put( Browser.class.getName(), this );
			shutdownHooked = true;
		}
	}

	/**
	 * Called after the last test method on classes where this is registered as an
	 * extension
	 */
	@Override
	public void afterAll( ExtensionContext context ) throws Exception {
		if( !SHARED ) {
			quitBrowser();
		}
	}

	/**
	 * Called when all tests in all classes are complete. This works as advertised
	 * when invoked by maven, but eclipse seems to call it prematurely
	 */
	@Override
	public void close() throws Throwable {
		quitBrowser();
		shutdownHooked = false;
	}

	private static void quitBrowser() {
		if( driver != null ) {
			// chromedriver seems to have an impossible-to-suppress stderr line on startup,
			// so it's only fair that we mirror that on shutdown
			System.err.println( "Shutting down browser" );
			driver.quit();
			driver = null;
		}
	}

	private enum Driver {
		CHROME {
			@Override
			public WebDriver get() {
				WebDriverManager.chromedriver().setup();
				ChromeOptions options = new ChromeOptions();
				if( !SHOW ) {
					options.addArguments( "--headless=new" );
				}
				// some oddness around browser locale changing with headful/less mode, possibly
				// related to https://bugs.chromium.org/p/chromium/issues/detail?id=755338
				// I'm seeing the opposite effect: headless mode used the correct locale,
				// headful mode did not. In any case, let's set it explicitly
				options.addArguments( "--lang=en_GB" );
				options.addArguments( "--disable-gpu" );
				options.addArguments( "--window-size=1400,800" );
				// https://github.com/SeleniumHQ/selenium/issues/11750
				options.addArguments( "--remote-allow-origins=*" );

				// suppress most stdout noise. We still get a "ChromeDriver was started
				// successfully." line for some reason
				System.setProperty( CHROME_DRIVER_SILENT_OUTPUT_PROPERTY, "true" );
				Logger.getLogger( "org.openqa.selenium" ).setLevel( Level.OFF );

				return new ChromeDriver( options );
			}
		},
		FIREFOX {
			@Override
			public WebDriver get() {
				WebDriverManager.firefoxdriver().setup();

				FirefoxOptions options = new FirefoxOptions();

				if( !SHOW ) {
					FirefoxBinary binary = new FirefoxBinary();
					binary.addCommandLineOptions( "--headless" );
					options.setBinary( binary );
				}

				FirefoxProfile profile = new FirefoxProfile();
				profile.setPreference( "intl.accept_languages", "en-GB" );
				options.setProfile( profile );

				options.addArguments( "--width=1400", "--height=800" );

				return new FirefoxDriver( options );
			}
		};

		public abstract WebDriver get();
	}
}
