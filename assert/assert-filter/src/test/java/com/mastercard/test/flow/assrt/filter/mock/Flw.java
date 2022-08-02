package com.mastercard.test.flow.assrt.filter.mock;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Metadata;
import com.mastercard.test.flow.Residue;
import com.mastercard.test.flow.Unpredictable;

/**
 * Empty implementation with convenient construction, useful for testing
 */
public class Flw implements Flow {

	private final Metadata meta;

	/**
	 * @param id description and tags
	 */
	public Flw( String id ) {
		meta = new Mtdt( id );
	}

	@Override
	public Metadata meta() {
		return meta;
	}

	@Override
	public Interaction root() {
		return new Ntr();
	}

	@Override
	public Stream<Actor> implicit() {
		return Stream.empty();
	}

	@Override
	public Flow basis() {
		return null;
	}

	@Override
	public Stream<Dependency> dependencies() {
		return Stream.empty();
	}

	@Override
	public Stream<Context> context() {
		return Stream.empty();
	}

	@Override
	public Stream<Residue> residue() {
		return Stream.empty();
	}

	@Override
	public String toString() {
		return meta.id();
	}

	private static class Mtdt implements Metadata {

		private final String desc;
		private final Set<String> tags;
		private static final Pattern ID = Pattern.compile( "^(.*)\\[(.*)\\]$" );

		Mtdt( String id ) {

			Matcher m = ID.matcher( id );
			if( !m.matches() ) {
				throw new IllegalArgumentException( String.format( "'%s' does not match %s", id, ID ) );
			}
			desc = m.group( 1 ).trim();

			tags = Stream.of( m.group( 2 ).split( ", " ) )
					.map( String::trim )
					.filter( s -> !s.isEmpty() )
					.collect( Collectors.toCollection( TreeSet::new ) );
		}

		@Override
		public String description() {
			return desc;
		}

		@Override
		public Set<String> tags() {
			return tags;
		}

		@Override
		public String motivation() {
			return "";
		}

		@Override
		public String trace() {
			return "";
		}
	}

	private static class Ntr implements Interaction {

		Ntr() {
		}

		@Override
		public Actor requester() {
			return () -> "requester";
		}

		@Override
		public Message request() {
			return new Msg();
		}

		@Override
		public Actor responder() {
			return () -> "responder";
		}

		@Override
		public Message response() {
			return new Msg();
		}

		@Override
		public Interaction parent() {
			return null;
		}

		@Override
		public Stream<Interaction> children() {
			return Stream.empty();
		}

		@Override
		public Set<String> tags() {
			return Collections.emptySet();
		}
	}

	private static class Msg implements Message {

		Msg() {
		}

		@Override
		public Message child() {
			return new Msg();
		}

		@Override
		public Message peer( byte[] content ) {
			return new Msg();
		}

		@Override
		public String assertable( Unpredictable... masks ) {
			return "assertable";
		}

		@Override
		public byte[] content() {
			return "content".getBytes( UTF_8 );
		}

		@Override
		public Set<String> fields() {
			return Collections.emptySet();
		}

		@Override
		public Message set( String field, Object value ) {
			return this;
		}

		@Override
		public Object get( String field ) {
			return null;
		}
	}
}
