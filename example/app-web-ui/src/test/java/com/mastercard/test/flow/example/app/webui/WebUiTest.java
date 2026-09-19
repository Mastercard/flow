package com.mastercard.test.flow.example.app.webui;

import static com.mastercard.test.flow.util.Tags.tags;

import java.util.function.Consumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mastercard.test.flow.assrt.Assertion;
import com.mastercard.test.flow.assrt.Replay;
import com.mastercard.test.flow.assrt.junit5.Flocessor;
import com.mastercard.test.flow.example.app.WebUi;
import com.mastercard.test.flow.example.app.assrt.AbstractServiceTest;
import com.mastercard.test.flow.example.app.assrt.Browser;
import com.mastercard.test.flow.example.app.assrt.MockInstance;
import com.mastercard.test.flow.example.app.model.ExampleSystem.Actors;
import com.mastercard.test.flow.example.framework.Instance;
import com.mastercard.test.flow.msg.web.WebSequence;

/**
 * Test that exercises the {@link WebUi} service in isolation by standing up an
 * instance of it and hitting it with requests. We use a {@link MockInstance} to
 * take the part of the rest of the application.
 */
@ExtendWith(Browser.class)
class WebUiTest extends AbstractServiceTest {
	private static final Logger LOG = LoggerFactory.getLogger( WebUiTest.class );

	private static Instance service;

	/**
	 * Starts the service
	 */
	@BeforeAll
	public static void startService() {
		if( !Replay.isActive() ) {
			service = new Main().build().start();
		}
	}

	/**
	 * Stops the service
	 */
	@AfterAll
	public static void stopService() {
		if( !Replay.isActive() ) {
			service.stop();
		}
	}

	/***/
	public WebUiTest() {
		super( service, Actors.WEB_UI, LOG );
	}

	@Override
	protected Consumer<Flocessor> custom() {
		// The flows that hit the web UI are tagged as such, so we can avoid having to
		// build all the other flows that will never be exercised by this test
		return flocessor -> flocessor
				.filtering( config -> config
						.includedTags( tags( "web" ) ) );
	}

	@Override
	protected byte[] getResponse( Assertion assrt ) {
		WebDriver driver = Browser.get();

		WebSequence actions = (WebSequence) assrt.expected().request().child();
		actions.set( "web_ui_url", "http://localhost:" + service.port() + "/web" );

		byte[] actionResults = actions.process( driver );
		assrt.actual().request( actionResults );

		WebSequence results = (WebSequence) assrt.expected().response();
		return results.process( driver );
	}
}
