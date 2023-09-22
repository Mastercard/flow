
<!-- title start -->

# message-web

Browser interaction messages

[![javadoc](https://javadoc.io/badge2/com.mastercard.test.flow/message-web/javadoc.svg)](https://javadoc.io/doc/com.mastercard.test.flow/message-web)

 * [../message](..) Implementations of the Message interface

<!-- title end -->

## Usage

After [importing the `bom`](../../bom):

```xml
<dependency>
  <!-- browser interaction message type -->
  <groupId>com.mastercard.test.flow</groupId>
  <artifactId>message-web</artifactId>
</dependency>
```

Defining a form submission sequence:

<!-- snippet start -->

<!-- Messages:form_submission -->

```java
/**
 * Builds an interaction to provoke a histogram via the web ui
 *
 * @return A web sequence that visits the web UI's main page and requests a
 *         histogram analysis
 */
public static WebSequence directHistogram() {
	return new WebSequence()
			.set( "web_ui_url", "http://determinedatruntime.com/web" )
			.set( "subject", "" )
			.set( "characters", "" )
			.operation( "populate and submit",
					( driver, params ) -> {
						driver.navigate()
								.to( params.get( "web_ui_url" ) );
						driver.findElement( By.id( "subject_input" ) )
								.sendKeys( params.get( "subject" ) );
						driver.findElement( By.id( "characters_input" ) )
								.sendKeys( params.get( "characters" ) );
						driver.findElement( By.id( "submit_button" ) )
								.click();
					} )
			.masking( RNG, m -> m
					.replace( "web_ui_url", "not asserted" ) );
}
```
[Snippet context](../../example/app-model/src/main/java/com/mastercard/test/flow/example/app/model/Messages.java#L348-L372,348-372)

<!-- snippet end -->

Defining result extraction sequence:

<!-- snippet start -->

<!-- Messages:result_extraction -->

```java
/**
 * Builds an interaction to extract the interesting bits from the web UI's
 * results page
 *
 * @return A web sequence that extracts result fields from the web UI
 */
public static WebSequence results() {
	return new WebSequence()
			.set( "subject", "" )
			.set( "characters", "" )
			.set( "results", "" )
			.set( "page_source", "" )
			.operation( "extract results", ( driver, params ) -> {
				params.put( "subject", driver.findElement( By.id( "subject_output" ) ).getText() );
				params.put( "characters", driver.findElement( By.id( "characters_output" ) ).getText() );
				params.put( "results", driver.findElement( By.id( "results_output" ) ).getText() );
				params.put( "page_source", driver.getPageSource() );
			} )
			.masking( BORING, m -> m
					.replace( "page_source", "not asserted" ) );
}
```
[Snippet context](../../example/app-model/src/main/java/com/mastercard/test/flow/example/app/model/Messages.java#L376-L396,376-396)

<!-- snippet end -->

These message definitions are included in the flow called `empty` in [Web][model.Web].
The parameter maps are updated in the `hello` flow, like so:

<!-- snippet start -->

<!-- model.Web:parameter_update -->

```java
.update( WEB_UI,
		rq( "subject", "Hello web!",
				"characters", "aeiou" ),
		rs( "subject", "Hello web!",
				"characters", "aeiou",
				"results", "  e = 2\n  o = 1" ) )
```
[Snippet context](../../example/app-model/src/main/java/com/mastercard/test/flow/example/app/model/Web.java#L54-L59,54-59)

<!-- snippet end -->

<!-- code_link_start -->

[model.Web]: ../../example/app-model/src/main/java/com/mastercard/test/flow/example/app/model/Web.java

<!-- code_link_end -->

The messages are processed like so:

<!-- snippet start -->

<!-- IntegrationTest:browser_invocation -->

```java
if( assrt.expected().request() instanceof WebSequence
		&& assrt.expected().response() instanceof WebSequence ) {
	WebSequence actions = (WebSequence) assrt.expected().request().child();
	actions.set( "web_ui_url",
			"http://localhost:" + clusterManager.getWebUiPort() + "/web" );
	WebDriver driver = Browser.get();
	byte[] actionResults = actions.process( driver );
	assrt.actual().request( actionResults );

	WebSequence results = (WebSequence) assrt.expected().response();
	response = results.process( driver );
}
```
[Snippet context](../../example/app-itest/src/test/java/com/mastercard/test/flow/example/app/itest/IntegrationTest.java#L155-L166,155-166)

<!-- snippet end -->

Note how:
 * The actual URL for the page is populated at runtime on a `child()` of the request sequence. This means that the runtime-sourced data will be highlighted in the full diff view of the execution report.
 * We're grabbing the full page source in the extracted results, but we're also masking it as not interesting. This allows users to see the page source in the execution report, but doesn't condemn us to exhaustively tracking every non-functional change to the HTML in the test suite.
