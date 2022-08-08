package com.mastercard.test.flow.example.app.model;

import static com.mastercard.test.flow.example.app.model.ExampleSystem.Unpredictables.BORING;
import static com.mastercard.test.flow.example.app.model.ExampleSystem.Unpredictables.RNG;
import static com.mastercard.test.flow.msg.http.HttpMsg.header;
import static com.mastercard.test.flow.msg.http.HttpReq.path;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import org.openqa.selenium.By;

import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.builder.mutable.MutableInteraction;
import com.mastercard.test.flow.example.app.model.ExampleSystem.Unpredictables;
import com.mastercard.test.flow.msg.http.HttpMsg;
import com.mastercard.test.flow.msg.http.HttpReq;
import com.mastercard.test.flow.msg.http.HttpRes;
import com.mastercard.test.flow.msg.json.Json;
import com.mastercard.test.flow.msg.sql.Query;
import com.mastercard.test.flow.msg.txt.Text;
import com.mastercard.test.flow.msg.web.WebSequence;

/**
 * Utilities for building message content
 */
public class Messages {

	/**
	 * The element of HTTP request paths where item ID is populated
	 */
	public static final String PATH_ID = path( "id" );
	private static final String UUID_RGX = "[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}";

	private Messages() {
		// no instances
	}

	/**
	 * @param method request method
	 * @param path   request path
	 * @param body   request body
	 * @return A {@link HttpReq} message
	 */
	public static HttpReq httpReq( String method, String path, Message body ) {
		return new HttpReq()
				.set( HttpReq.METHOD, method )
				.set( HttpReq.PATH, path )
				.set( HttpMsg.VERSION, "HTTP/1.1" )
				.set( HttpMsg.header( "Content-Type" ), "application/json" )
				.set( HttpMsg.header( "Accept" ), "application/json" )
				.set( HttpMsg.header( "Connection" ), "keep-alive" )
				.set( HttpMsg.BODY, body )
				.masking( Unpredictables.HOST, m -> m
						.delete( HttpMsg.header( "Host" ) )
						.delete( HttpMsg.header( "User-Agent" ) ) )
				.masking( Unpredictables.BORING, m -> m
						.delete( HttpMsg.header( "Content-Length" ) ) )
				.masking( Unpredictables.RNG, m -> m
						.replace( PATH_ID, "_uuid_" ) );
	}

	/**
	 * @param body response body
	 * @return A {@link HttpRes} message
	 */
	public static HttpRes httpRes( Message body ) {
		return new HttpRes()
				.set( HttpMsg.VERSION, "HTTP/1.1" )
				.set( HttpRes.STATUS, "200" )
				.set( HttpRes.STATUS_TEXT, "OK" )
				.set( HttpMsg.header( "Content-Type" ), "application/json" )
				.set( HttpMsg.header( "Date" ), "Sun, 06 Jun 2021 19:50:29 GMT" )
				.set( HttpMsg.header( "Server" ), "Jetty(9.4.48.v20220622)" )
				.set( HttpMsg.header( "Transfer-Encoding" ), "chunked" )
				.set( HttpMsg.BODY, body )
				.masking( Unpredictables.CLOCK, m -> m
						.delete( HttpMsg.header( "Date" ) ) )
				.masking( Unpredictables.RNG, m -> m
						.replace( UUID_RGX, "_uuid_" ) );
	}

	/**
	 * @return A 404 response
	 */
	public static HttpRes notFound() {
		return httpRes( new Text( "<html><body><h2>404 Not found</h2></body></html>" ) )
				.set( HttpRes.STATUS, "404" )
				.set( HttpRes.STATUS_TEXT, "Not Found" );
	}

	/**
	 * @return A 500 response
	 */
	public static HttpRes error() {
		return httpRes( new Text( "The service has been configured to be broken!" ) )
				.set( HttpRes.STATUS, "500" )
				.set( HttpRes.STATUS_TEXT, "Server Error" )
				.set( header( "Content-Type" ), "text/html;charset=utf-8" );
	}

	/**
	 * Builds a HTTP request with a textual body
	 *
	 * @param method HTTP request method
	 * @param path   HTTP request path
	 * @param body   Request body content
	 * @return a HTTP request with a textual body
	 */
	public static HttpReq textReq( String method, String path, String body ) {
		return httpReq( method, path, new Text( body ) );
	}

	/**
	 * Builds a HTTP response with a textual body
	 *
	 * @param body Request body content
	 * @return a HTTP request with a textual body
	 */
	public static HttpRes textRes( String body ) {
		return httpRes( new Text( body ) );
	}

	/**
	 * Builds a HTTP request assuming the request structure for the core service
	 *
	 * @param method request method
	 * @param path   request path
	 * @param nvp    name/value pairs for the json body
	 * @return A request for the core service
	 */
	public static HttpReq coreReq( String method, String path, Object... nvp ) {
		Json body = new Json()
				.set( "characters", null )
				.set( "defer", false )
				.set( "text", "" );
		for( int i = 0; i < nvp.length; i += 2 ) {
			body.set( String.valueOf( nvp[i] ), nvp[i + 1] );
		}
		return httpReq( method, path, body );
	}

	/**
	 * Builds a HTTP response assuming the response structure for the core service
	 *
	 * @param nvp name/value pairs for the json body
	 * @return A response from the core service
	 */
	public static HttpRes coreRes( Object... nvp ) {
		return coreRes( Arrays.asList( nvp ) );
	}

	/**
	 * Builds a HTTP response assuming the response structure for the core service
	 *
	 * @param nvp name/value pairs for the json body
	 * @return A response from the core service
	 */
	public static HttpRes coreRes( List<Object> nvp ) {
		if( nvp.size() % 2 != 0 ) {
			throw new IllegalArgumentException( "nvp mismatch" );
		}
		Json body = new Json()
				.set( "deferredID", null )
				.set( "deferredStatus", null )
				.set( "result", Json.EMPTY_MAP )
				.masking( RNG, m -> m
						.replace( "deferredID", "_uuid_" ) );
		for( int i = 0; i < nvp.size(); i += 2 ) {
			body.set( String.valueOf( nvp.get( i ) ), nvp.get( i + 1 ) );
		}
		return httpRes( body );
	}

	/**
	 * Builds a HTTP response with a json body (initially empty)
	 *
	 * @param nvp name/value pairs for the json body
	 * @return A http response
	 */
	public static HttpRes jsonRes( Object... nvp ) {
		return jsonRes( Arrays.asList( nvp ) );
	}

	/**
	 * Builds a HTTP response with a json body (initially empty)
	 *
	 * @param nvp name/value pairs for the json body
	 * @return A http response
	 */
	public static HttpRes jsonRes( List<Object> nvp ) {
		if( nvp.size() % 2 != 0 ) {
			throw new IllegalArgumentException( "nvp mismatch" );
		}
		Json body = new Json();
		for( int i = 0; i < nvp.size(); i += 2 ) {
			body.set( String.valueOf( nvp.get( i ) ), nvp.get( i + 1 ) );
		}
		return httpRes( body );
	}

	/**
	 * Builds the DB query message for the insert operation
	 *
	 * @param key   Item key
	 * @param value Item value
	 * @return The insert query message
	 */
	public static Query dbInsert( String key, String value ) {
		return new Query(
				"INSERT INTO item (id, data) VALUES (?, ?)"
						+ " ON DUPLICATE KEY UPDATE  data = ?" )
								.set( "1", key )
								.set( "2", value )
								.set( "3", value );
	}

	/**
	 * Builds the DB select query message
	 *
	 * @param key Item key
	 * @return The select query message
	 */
	public static Query dbSelect( String key ) {
		return new Query( "SELECT data FROM item WHERE id = ?" )
				.set( "1", key );
	}

	/**
	 * Builds the DB deletion query message
	 *
	 * @param key Item key
	 * @return The delete query message
	 */
	public static Query dbDelete( String key ) {
		return new Query( "DELETE FROM item WHERE id = ?" )
				.set( "1", key );
	}

	/**
	 * @param nvp name/value pairs
	 * @return An operation to update the request message
	 */
	public static Consumer<MutableInteraction> rq( Object... nvp ) {
		if( nvp.length % 2 != 0 ) {
			throw new IllegalArgumentException( "nvp mismatch" );
		}

		return i -> {
			Message m = i.request();
			for( int idx = 0; idx < nvp.length - 1; idx += 2 ) {
				m.set( String.valueOf( nvp[idx] ), nvp[idx + 1] );
			}
		};
	}

	/**
	 * @param nvp name/value pairs
	 * @return An operation to update the response message
	 */
	public static Consumer<MutableInteraction> rs( Object... nvp ) {
		if( nvp.length % 2 != 0 ) {
			throw new IllegalArgumentException( "nvp mismatch" );
		}

		return i -> {
			Message m = i.response();
			for( int idx = 0; idx < nvp.length - 1; idx += 2 ) {
				m.set( String.valueOf( nvp[idx] ), nvp[idx + 1] );
			}
		};
	}

	/**
	 * @param nvp name/value pairs
	 * @return An operation to update the response message
	 */
	public static Consumer<MutableInteraction> rs( List<Object> nvp ) {
		return rs( nvp.toArray( new Object[nvp.size()] ) );
	}

	/**
	 * Updates the names in a list of name/value pairs
	 *
	 * @param path How to update the paths
	 * @param nvp  The path/value pairs
	 * @return A new list with updated paths
	 */
	public static List<Object> pathUpdate( UnaryOperator<String> path, List<Object> nvp ) {
		if( nvp.size() % 2 != 0 ) {
			throw new IllegalArgumentException( "nvp mismatch" );
		}
		List<Object> res = new ArrayList<>();
		for( int i = 0; i < nvp.size(); i += 2 ) {
			res.add( path.apply( String.valueOf( nvp.get( i ) ) ) );
			res.add( nvp.get( i + 1 ) );
		}
		return res;
	}

	/**
	 * Builds a name/value pair list from a single value for multiple paths
	 *
	 * @param value The value for all paths
	 * @param paths The paths
	 * @return A list of path/value pairs
	 */
	public static List<Object> valueFill( Object value, String... paths ) {
		List<Object> nvp = new ArrayList<>();
		for( String path : paths ) {
			nvp.add( path );
			nvp.add( value );
		}
		return nvp;
	}

	// snippet-start:form_submission
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
	// snippet-end:form_submission

	// snippet-start:result_extraction
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
	// snippet-end:result_extraction
}
