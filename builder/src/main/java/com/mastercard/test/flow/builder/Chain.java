package com.mastercard.test.flow.builder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;

/**
 * Convenience class for placing {@link Flow} in an execution chain.
 * {@link Flow}s in a chain will be executed together without other {@link Flow}
 * being interleaved. Supplying an instance of this class when building a
 * {@link Flow} will set a chain ID. Supply the same instance to multiple
 * {@link Flow}s to enchain them together.
 */
@SkipTrace
public class Chain implements Consumer<Builder<?>> {

	/**
	 * Flows that bear the same tag value that starts with this prefix will be
	 * scheduled for execution as a unit, {@link Flow}s that do not bear the same
	 * tag will not be interleaved into that unit
	 */
	public static final String PREFIX = "chain:";

	private final String tag;

	/**
	 * A random-ish chain ID will be generated
	 */
	public Chain() {
		this( stableGeneratedId() );
	}

	/**
	 * The supplied chain ID will be applied
	 *
	 * @param id The id for the chain
	 */
	public Chain( String id ) {
		tag = PREFIX + id;
	}

	@Override
	public void accept( Builder<?> builder ) {
		builder.meta( data -> data.tags( tags -> {
			// remove any existing chain tags
			tags.removeIf( t -> t.startsWith( PREFIX ) );
			// add our own
			tags.add( tag );
		} ) );
	}

	/**
	 * Builds a flow construction step that will remove the flow from a
	 * {@link Chain}
	 *
	 * @return A {@link Flow} construction step that will remove all {@link Chain}
	 *         tags
	 */
	public static Consumer<Builder<?>> unlink() {
		return builder -> builder.meta( data -> data
				.tags( tags -> tags
						.removeIf( tag -> tag
								.startsWith( PREFIX ) ) ) );
	}

	/**
	 * Maps from class names to counts of chain objects constructed in that class
	 */
	private static Map<String, AtomicInteger> classCounts = new HashMap<>();

	/**
	 * Generating chain ids is actually a bit difficult:
	 * <ul>
	 * <li>They must be unique</li>
	 * <li>Tags contribute to flow identity, so we want them to be stable as the
	 * codebase changes</li>
	 * </ul>
	 * Random values are out, a count of chain instances is out (flow construction
	 * order is unknown). We've settled on a per-method chain instance counter.
	 *
	 * @return a chain ID value that we hope will be unique to the instance, but
	 *         that we also hope won't change as {@link Flow}s are added to the
	 *         {@link Model}
	 */
	private static String stableGeneratedId() {
		String className = Trace.trace().replaceAll( ":\\d+", "" );
		AtomicInteger count = classCounts.computeIfAbsent( className, c -> new AtomicInteger() );
		String id = className + count.incrementAndGet();
		return Integer.toHexString( id.hashCode() );
	}

}
