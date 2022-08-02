package com.mastercard.test.flow.builder.mock;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Unpredictable;

/**
 * Empty implementation useful for tracking instances
 */
public class Msg implements Message {

	private final String id;
	private final Map<String, Object> updates = new TreeMap<>();

	private static final Pattern CHILD_ID = Pattern.compile( "Child\\^(\\d+) of '(.*)'" );

	/**
	 * @param id A human-readable ID for this message
	 */
	public Msg( String id ) {
		this.id = id;
	}

	@Override
	public Message child() {
		int childLevel = 1;
		String content = id;

		Matcher m = CHILD_ID.matcher( id );
		if( m.matches() ) {
			childLevel = Integer.parseInt( m.group( 1 ) ) + 1;
			content = m.group( 2 );
		}

		return new Msg( String.format( "Child^%s of '%s'", childLevel, content ) );
	}

	@Override
	public Message peer( byte[] content ) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String assertable( Unpredictable... masks ) {
		return String.format( "Msg[%s%s]", id, updates.isEmpty() ? "" : " " + updates );
	}

	@Override
	public byte[] content() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Set<String> fields() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Message set( String field, Object value ) {
		updates.put( field, value );
		return this;
	}

	@Override
	public Object get( String field ) {
		return "retrived value of '" + field + "'";
	}

}
