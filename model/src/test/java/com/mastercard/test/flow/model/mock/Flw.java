package com.mastercard.test.flow.model.mock;

import static java.util.stream.Collectors.toSet;

import java.util.Set;
import java.util.stream.Stream;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Metadata;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.Residue;

/**
 * Empty implementation useful for testing {@link Model}s
 */
public class Flw implements Flow {

	private final Metadata meta;

	/**
	 * @param desc identifying description
	 * @param tags identifying tags
	 */
	public Flw( String desc, String... tags ) {
		meta = new Mtdt( desc, tags );
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

	@Override
	public Flow basis() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Stream<Dependency> dependencies() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Stream<Context> context() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Stream<Residue> residue() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String toString() {
		return meta.id();
	}

	private static class Mtdt implements Metadata {

		private final String id;
		private final Set<String> tags;

		Mtdt( String desc, String... tags ) {
			id = desc;
			this.tags = Stream.of( tags ).collect( toSet() );
		}

		@Override
		public String description() {
			return id;
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
}
