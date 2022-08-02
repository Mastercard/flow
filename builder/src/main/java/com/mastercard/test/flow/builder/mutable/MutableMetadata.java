package com.mastercard.test.flow.builder.mutable;

import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Metadata;
import com.mastercard.test.flow.builder.SkipTrace;
import com.mastercard.test.flow.builder.Trace;
import com.mastercard.test.flow.builder.concrete.ConcreteMetadata;

/**
 * Supports the fluid construction of {@link Metadata}
 */
@SkipTrace
public class MutableMetadata {

	private String description = "";
	private final Set<String> tags = new TreeSet<>();
	private String motivation = "";
	private String primaryTrace = null;
	private Set<String> traceAddendum = new TreeSet<>();

	/**
	 * Initially empty
	 */
	MutableMetadata() {
	}

	/**
	 * Note that the {@link Metadata#trace()} value is not copied over.
	 *
	 * @param src The starting point of construction
	 */
	MutableMetadata( Metadata src ) {
		description = src.description();
		src.tags().forEach( tags::add );
		motivation = src.motivation();
	}

	/**
	 * Sets the description
	 *
	 * @param desc The new {@link Metadata#description()} value
	 * @return <code>this</code>
	 */
	public MutableMetadata description( String desc ) {
		description = desc;
		return this;
	}

	/**
	 * Updates tags
	 *
	 * @param updates How to update the {@link Flow} tags
	 * @return <code>this</code>
	 */
	@SafeVarargs
	public final MutableMetadata tags( Consumer<Set<String>>... updates ) {
		for( Consumer<Set<String>> update : updates ) {
			update.accept( tags );
		}
		return this;
	}

	/**
	 * Sets the motivation
	 *
	 * @param mot The new {@link Metadata#motivation()} value
	 * @return <code>this</code>
	 */
	public MutableMetadata motivation( String mot ) {
		return motivation( e -> mot );
	}

	/**
	 * Updates the motivation
	 *
	 * @param change How to update the motivation text
	 * @return <code>this</code>
	 */
	public MutableMetadata motivation( UnaryOperator<String> change ) {
		motivation = change.apply( motivation );
		return this;
	}

	/**
	 * Sets the trace. Please consider carefully before overriding the default
	 * behaviour of walking the call stack. Perhaps you can disambiguate your flow
	 * traces by using {@link #trace(Consumer)} addenda fields?
	 *
	 * @param t The new {@link Metadata#trace()} value, or <code>null</code> to work
	 *          it out automatically from the stack trace
	 * @return <code>this</code>
	 */
	public MutableMetadata trace( String t ) {
		primaryTrace = t;
		return this;
	}

	/**
	 * Updates the trace addendum fields. These values are appended to the primary
	 * trace string, and offer a way to disambiguate the traces of distinct
	 * {@link Flow}s that originate from the same code location.
	 *
	 * @param addenda How to update the trace addendum fields
	 * @return <code>this</code>
	 */
	public MutableMetadata trace( Consumer<Set<String>> addenda ) {
		addenda.accept( traceAddendum );
		return this;
	}

	/**
	 * @return An immutable {@link Metadata} instance
	 */
	public ConcreteMetadata build() {
		return new ConcreteMetadata(
				description,
				tags,
				motivation,
				""
						+ (primaryTrace != null ? primaryTrace : Trace.trace())
						+ (traceAddendum.isEmpty() ? "" : " " + traceAddendum) );
	}

}
