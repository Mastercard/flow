package com.mastercard.test.flow.report.data;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mastercard.test.flow.Flow;

/**
 * Represents a single log event that occurred when a {@link Flow} was exercised
 */
public class LogEvent {

	/**
	 * When the event occurred
	 */
	@JsonProperty("time")
	public final String time;
	/**
	 * The severity of the event
	 */
	@JsonProperty("level")
	public final String level;
	/**
	 * The source of the event
	 */
	@JsonProperty("source")
	public final String source;
	/**
	 * The event message
	 */
	@JsonProperty("message")
	public final String message;

	/**
	 * @param time    When the event occurred
	 * @param level   The severity of the event
	 * @param source  The source of the event
	 * @param message The event message
	 */
	public LogEvent(
			@JsonProperty("time") String time,
			@JsonProperty("level") String level,
			@JsonProperty("source") String source,
			@JsonProperty("message") String message ) {
		this.time = time;
		this.level = level;
		this.source = source;
		this.message = message;
	}

	/**
	 * @param time    When the event occurred
	 * @param level   The severity of the event
	 * @param source  The source of the event
	 * @param message The event message
	 */
	public LogEvent( Instant time,
			String level,
			String source,
			String message ) {
		this( time.toString(), level, source, message );
	}

	@Override
	public String toString() {
		return String.format( "%s %s %s %s", time, level, source, message );
	}

	/**
	 * Dumps the stacktrace to a string
	 *
	 * @param th The problem
	 * @return A string describing the source of the problem
	 */
	public static String stackTrace( Throwable th ) {
		try( StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter( sw ); ) {
			th.printStackTrace( pw );
			return sw.toString();
		}
		catch( Exception e ) {
			throw new IllegalStateException( "Failed to capture stacktrace", e );
		}
	}
}
