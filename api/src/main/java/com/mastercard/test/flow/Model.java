package com.mastercard.test.flow;

import java.util.Set;
import java.util.stream.Stream;

import com.mastercard.test.flow.util.TaggedGroup;
import com.mastercard.test.flow.util.Tags;

/**
 * The collective unit of system modelling. A group of {@link Flow}s that
 * describe the operation of a system.
 */
public interface Model {

	/**
	 * Defines a human-readable name for this model
	 *
	 * @return A human-readable title for this group of {@link Flow}s
	 */
	String title();

	/**
	 * Defines the set of tags that the {@link Flow}s in this {@link Model} carry
	 *
	 * @return The intersection and union of the {@link Metadata#tags()} of the
	 *         {@link Flow}s in this {@link Model}
	 */
	TaggedGroup tags();

	/**
	 * Accessor for the {@link Flow}s in the {@link Model}
	 *
	 * @param include A set of tag values that returned {@link Flow}s must have
	 * @param exclude A set of tag values that returned {@link Flow}s must not have
	 * @return The {@link Flow}s in this {@link Model} that bear all of the tags in
	 *         the include set, but none of the tags in the exclude set
	 */
	Stream<Flow> flows( Set<String> include, Set<String> exclude );

	/**
	 * Accessor for the {@link Flow}s in the {@link Model}
	 *
	 * @return All {@link Flow}s in this {@link Model}
	 */
	default Stream<Flow> flows() {
		return flows( Tags.empty(), Tags.empty() );
	}

	/**
	 * Accessor for constituent models
	 *
	 * @return The constituent sub-models
	 */
	Stream<Model> subModels();

	/**
	 * Adds instrumentation for model construction. Listeners should be propagated
	 * to constituent {@link Model}s.
	 *
	 * @param l The object that should be appraised of the construction of
	 *          sub-models
	 * @return <code>this</code>
	 */
	Model listener( Listener l );

	/**
	 * Instrumentation for {@link Model} construction
	 */
	interface Listener {
		/**
		 * Called when instantiation of a {@link Model} implementation begins
		 *
		 * @param type The {@link Model} type
		 */
		void start( Class<? extends Model> type );

		/**
		 * Called when instantiation of a {@link Model} implementation ends
		 *
		 * @param instance The constructed model instance
		 */
		void end( Model instance );

		/**
		 * Called when a new listener is set, reports on the membership of the
		 * {@link Model}.
		 *
		 * @param model  The {@link Model}
		 * @param models The number of sub-models
		 * @param flows  The number of {@link Flow}s in the {@link Model}
		 */
		void count( Model model, int models, int flows );
	}
}
