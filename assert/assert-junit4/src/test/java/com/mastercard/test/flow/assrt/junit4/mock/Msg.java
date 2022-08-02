package com.mastercard.test.flow.assrt.junit4.mock;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Set;

import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Unpredictable;

/**
 * Empty implementation useful for testing assertion components
 */
public class Msg implements Message {

	private final String content;

	/**
	 * @param content Message content
	 */
	public Msg( String content ) {
		this.content = content;
	}

	@Override
	public Message child() {
		return new Msg( content );
	}

	@Override
	public Message peer( byte[] bytes ) {
		return new Msg( new String( bytes, UTF_8 ) );
	}

	@Override
	public String assertable( Unpredictable... masks ) {
		return content;
	}

	@Override
	public byte[] content() {
		return content.getBytes( UTF_8 );
	}

	@Override
	public Set<String> fields() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Message set( String field, Object value ) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object get( String field ) {
		throw new UnsupportedOperationException();
	}

}
