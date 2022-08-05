package com.mastercard.test.flow.assrt.mock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.FieldAddress;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Metadata;
import com.mastercard.test.flow.Residue;

/**
 * Empty implementation with convenient construction, useful for testing
 * processing order
 */
public class Flw implements Flow {

	private final Metadata meta;
	private final List<Dependency> deps = new ArrayList<>();
	private final Collection<Context> contexts = new ArrayList<>();
	private final Collection<Residue> residue = new ArrayList<>();
	private Flow basis = null;

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
		throw new UnsupportedOperationException();
	}

	@Override
	public Stream<Actor> implicit() {
		throw new UnsupportedOperationException();
	}

	/**
	 * @param b The new basis flow
	 * @return <code>this</code>
	 */
	public Flw basis( Flow b ) {
		basis = b;
		return this;
	}

	@Override
	public Flow basis() {
		return basis;
	}

	/**
	 * @param source The flow upon which this one depends
	 * @return <code>this</code>
	 */
	public Flw depedency( Flow source ) {
		deps.add( new Dep( source ) );
		return this;
	}

	@Override
	public Stream<Dependency> dependencies() {
		return deps.stream();
	}

	/**
	 * @param ctx The context to add
	 * @return <code>this</code>
	 */
	public Flw context( Context ctx ) {
		if( ctx != null ) {
			contexts.add( ctx );
		}
		return this;
	}

	@Override
	public Stream<Context> context() {
		return contexts.stream();
	}

	/**
	 * @param rsd The residue to add
	 * @return <code>this</code>
	 */
	public Flw residue( Residue rsd ) {
		residue.add( rsd );
		return this;
	}

	@Override
	public Stream<Residue> residue() {
		return residue.stream();
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
			throw new UnsupportedOperationException();
		}

		@Override
		public String trace() {
			throw new UnsupportedOperationException();
		}
	}

	private static class Dep implements Dependency {
		private final FieldAddress source;

		Dep( Flow source ) {
			this.source = new FA( source );
		}

		@Override
		public FieldAddress source() {
			return source;
		}

		@Override
		public UnaryOperator<Object> mutation() {
			throw new UnsupportedOperationException();
		}

		@Override
		public FieldAddress sink() {
			throw new UnsupportedOperationException();
		}
	}

	private static class FA implements FieldAddress {
		private final Flow flow;

		FA( Flow flow ) {
			this.flow = flow;
		}

		@Override
		public Flow flow() {
			return flow;
		}

		@Override
		public Function<Flow, Interaction> interaction() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Function<Interaction, Message> message() {
			throw new UnsupportedOperationException();
		}

		@Override
		public String field() {
			throw new UnsupportedOperationException();
		}
	}
}
