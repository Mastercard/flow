package com.mastercard.test.flow.assrt.junit5;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.opentest4j.IncompleteExecutionException;
import org.opentest4j.TestAbortedException;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.assrt.AbstractFlocessor;
import com.mastercard.test.flow.assrt.History.Result;

/**
 * Self-typed sibling of {@link Flocessor}, supplied by {@link FlowExecution}.
 * All leaves share the core processor, dependency publisher and History.
 */
public final class PreparedFlocessor extends AbstractFlocessor<PreparedFlocessor> {
	private final FlowExecution owner;
	private boolean prepared;
	private final List<Flow> selectedFlows = new ArrayList<>();
	private final Map<String, Predicate<Flow>> independence = new LinkedHashMap<>();

	/**
	 * Declares an assessed empty resource set using a named bulk rule. The audit
	 * must include the synchronous behaviour, listeners, messages and callbacks,
	 * including hidden state. Matching declares no required physical worker
	 * affinity and no use surviving synchronous callback return. Missing rules
	 * remain UNKNOWN; neither State.LESS nor empty contexts establishes
	 * independence. Rules are resolved once during preparation and never on native
	 * workers.
	 * <p>
	 * Temporary ticket08 tracer only: no chains, basis, contexts, residue, replay,
	 * capture, reports, shared message instances or fan-in; class source URIs and
	 * at most one distinct external predecessor per flow. No general resource
	 * reservation, affinity, cancellation or fairness implementation is implied.
	 *
	 * @param rule    Unique nonblank audit name
	 * @param matches Selected flows affirmatively assessed as independent
	 * @return this adapter
	 */
	public PreparedFlocessor independent( String rule, Predicate<Flow> matches ) {
		beforeConfiguration();
		if( Objects.requireNonNull( rule ).isBlank() || independence.containsKey( rule ) ) {
			throw new IllegalArgumentException( "Independence rule must be named and unique: " + rule );
		}
		independence.put( rule, Objects.requireNonNull( matches ) );
		return this;
	}

	/**
	 * @param owner The factory-local lifecycle owner
	 * @param title The test title
	 * @param model The existing system model
	 */
	PreparedFlocessor( FlowExecution owner, String title, Model model ) {
		super( title, model );
		this.owner = owner;
	}

	/**
	 * Freezes configuration, prepares owned descriptions and audits parallel
	 * admission. No flow bodies execute during preparation.
	 *
	 * @return The native descriptions to return from the owning factory
	 */
	public Stream<DynamicNode> tests() {
		beforeConfiguration();
		prepared = true;
		if( owner.parallel() ) {
			requireIndependentTracerConfiguration();
		}
		List<DynamicNode> nodes = new ArrayList<>();
		Set<String> identities = new HashSet<>();
		try( Stream<Flow> selected = prepareFlows() ) {
			for( Flow flow : selected.collect( Collectors.toList() ) ) {
				int index = nodes.size();
				String id = flow.meta().id();
				if( !identities.add( id ) ) {
					throw new IllegalArgumentException( "Duplicate prepared Flow identity: " + id );
				}
				// Native identity and navigation are values of this preparation, not
				// delayed metadata reads when somebody enumerates the descriptions.
				selectedFlows.add( flow );
				nodes.add( DynamicTest.dynamicTest( id, Flocessor.testSource( flow ),
						owner.invocation( index ) ) );
			}
		}
		if( owner.parallel() ) {
			Map<String, Predicate<Flow>> rules = new LinkedHashMap<>( independence );
			for( Flow flow : selectedFlows ) {
				boolean covered = false;
				for( Predicate<Flow> rule : rules.values() ) {
					covered |= rule.test( flow );
				}
				if( !covered ) {
					throw new IllegalStateException( "Flow parallel tracer UNKNOWN resource audit: "
							+ flow.meta().id() + "; independence rules=" + rules.keySet() );
				}
			}
			owner.prepareParallel( selectedFlows, nodes );
		}
		return owner.describe( nodes );
	}

	@Override
	protected void beforeConfiguration() {
		owner.requireFactory();
		if( prepared ) {
			throw new IllegalStateException(
					"Flow configuration is frozen after first tests() preparation" );
		}
	}

	/** Disposes initialized reporting only after successful owned drainage. */
	void complete() {
		completeProcessing();
	}

	/** Clears the prepared invocation table after owned use has drained. */
	void detach() {
		selectedFlows.clear();
		independence.clear();
	}

	/**
	 * Processes the selected flow without capturing it in a native description.
	 *
	 * @param index The preparation-local flow index
	 */
	void processSelected( int index ) {
		processFlow( selectedFlows.get( index ) );
	}

	private void processFlow( Flow flow ) {
		try {
			process( flow );
			history.recordResult( flow, Result.SUCCESS );
		}
		catch( IncompleteExecutionException e ) {
			history.recordResult( flow, Result.SKIP );
			throw e;
		}
		catch( AssertionError e ) {
			history.recordResult( flow, Result.UNEXPECTED );
			throw e;
		}
		catch( Exception e ) {
			history.recordResult( flow, Result.ERROR );
			throw e;
		}
	}

	@Override
	protected void skip( String reason ) {
		throw new TestAbortedException( reason );
	}

	@Override
	protected void compare( String message, String expected, String actual ) {
		Assertions.assertEquals( expected, actual, message );
	}
}
