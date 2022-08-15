package com.mastercard.test.flow.msg.txt;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mastercard.test.flow.msg.AbstractMessage;

/**
 * Simple textual-content message.
 * <ul>
 * <li>Content updates are addressed by regular expression substitutions.</li>
 * <li>Content retrieval is addressed by regular expression capture. If the
 * regex contains capture groups then the first one is returned, otherwise the
 * whole match is returned.</li>
 * </ul>
 */
public class Text extends AbstractMessage<Text> {

	private final Supplier<String> base;

	/**
	 * @param content Initial message content
	 */
	public Text( String content ) {
		base = () -> content;
	}

	/**
	 * @param bytes Initial message content, assumed to be UTF_8
	 */
	public Text( byte[] bytes ) {
		this( new String( bytes, UTF_8 ) );
	}

	private Text( Text parent ) {
		base = parent::build;
	}

	@Override
	public Text child() {
		return copyMasksTo( new Text( this ) );
	}

	@Override
	public Text peer( byte[] content ) {
		return copyMasksTo( new Text( content ) );
	}

	@Override
	public byte[] content() {
		return build().getBytes( UTF_8 );
	}

	@Override
	public Set<String> fields() {
		// Not supported: how do you list possible regexes?
		return Collections.emptySet();
	}

	/**
	 * Applies the updates to build the final value
	 *
	 * @return The message content
	 */
	protected String build() {
		String text = base.get();
		for( Update update : updates ) {
			text = text.replaceAll( update.field(),
					update.value() == DELETE ? "" : String.valueOf( update.value() ) );
		}
		return text;
	}

	@Override
	protected String asHuman() {
		return build();
	}

	@Override
	public Object get( String field ) {
		Matcher m = Pattern.compile( field ).matcher( build() );
		if( m.find() ) {
			return m.groupCount() == 0 ? m.group( 0 ) : m.group( 1 );
		}
		return null;
	}

}
