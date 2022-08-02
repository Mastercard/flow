package com.mastercard.test.flow;

import java.util.stream.Stream;

/**
 * Tractable unit of system modelling. A contextualised collection of
 * synchronous {@link Interaction}s that document an operation in the system.
 */
public interface Flow {

	/**
	 * Defines a set of data that provides a human-understandable context for the
	 * flow. This provides the identity for the flow and documentation for why it
	 * exists.
	 *
	 * @return data for human consumption
	 */
	Metadata meta();

	/**
	 * Defines the {@link Flow} upon which this one has been built. {@link Flow}s
	 * can inherit model content from another - this allows the avoidance of
	 * repeated common content and test failures.
	 *
	 * @return The {@link Flow} upon which this one is based
	 */
	Flow basis();

	/**
	 * The system {@link Interaction} that triggers the {@link Flow}'s operation.
	 * This could be a single request/response pair, or that request could trigger a
	 * chain of calls throughout all of the {@link Actor}s in the system
	 *
	 * @return The root interaction of this {@link Flow}
	 */
	Interaction root();

	/**
	 * Defines the {@link Actor}s that are exercised by this {@link Flow}'s
	 * behaviour, but that are not explicitly modelled in the {@link Interaction}
	 * structure. Ideally there would be no undocumented interactions, but it can be
	 * the pragmatic choice.
	 *
	 * @return The {@link Actor}s that are required to be in the system under test
	 *         for this {@link Flow} to be successfully exercised
	 */
	Stream<Actor> implicit();

	/**
	 * Defines the {@link Flow}s upon which this one depends. For example, a
	 * {@link Flow} that documents the retrieval of data will depend on the
	 * {@link Flow} that documents the storage of that data.
	 * <p>
	 * The dependency {@link Flow}s identified by {@link Dependency#source()}
	 * should:
	 * <ul>
	 * <li>Be included in any test run that include this {@link Flow}</li>
	 * <li>Be processed before this {@link Flow}</li>
	 * </ul>
	 *
	 * @return A stream of data dependencies for this {@link Flow}
	 */
	Stream<Dependency> dependencies();

	/**
	 * Defines the environment in which this {@link Flow}'s behaviour is expected to
	 * be valid
	 *
	 * @return A stream of all of the {@link Context} types that exist on this
	 *         {@link Flow}
	 */
	Stream<Context> context();

	/**
	 * Defines the persistent impact of this {@link Flow}s behaviour
	 *
	 * @return A stream of all the {@link Residue} types that exist on the
	 *         {@link Flow}
	 */
	Stream<Residue> residue();
}
