package com.mastercard.test.flow.assrt.junit5;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
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
import com.mastercard.test.flow.assrt.Order;
import com.mastercard.test.flow.assrt.resource.ResourceRequirements;
import com.mastercard.test.flow.assrt.resource.ResourceRules;

/**
 * Self-typed sibling of {@link Flocessor}, supplied by {@link FlowExecution}.
 * All leaves share the core processor, dependency publisher and History.
 */
public final class PreparedFlocessor extends AbstractFlocessor<PreparedFlocessor> {
	private final FlowExecution owner;
	private boolean prepared;
	private boolean declaredResources;
	private final List<Flow> selectedFlows = new ArrayList<>();
	private ResourceRules rules = new ResourceRules();
	private final Map<Flow, ResourceRequirements> requirements = new IdentityHashMap<>();

	/**
	 * Declares an assessed empty resource set using a named bulk rule. The audit
	 * must include the synchronous behaviour, listeners, messages and callbacks,
	 * including hidden state. Matching declares no required physical worker
	 * affinity and no use surviving synchronous callback return. Missing rules
	 * remain UNKNOWN; neither State.LESS nor empty contexts establishes
	 * independence. Rules are resolved once during preparation and never on native
	 * workers.
	 * <p>
	 * Equivalent to a known empty {@link #resources(String, Predicate, String...)}
	 * declaration. It cannot erase restrictions from other matching rules.
	 *
	 * @param rule    Unique nonblank audit name
	 * @param matches Selected flows affirmatively assessed as independent
	 * @return this adapter
	 */
	public PreparedFlocessor independent( String rule, Predicate<Flow> matches ) {
		return resources( rule, matches );
	}

	/**
	 * Declares capacity-one shared state by reusable immutable identity. All
	 * matching rules are unioned; unmatched flows remain global-exclusive UNKNOWN.
	 * These declarations cooperate across prepared serial and parallel runners in
	 * this JVM, not legacy runners or unrelated/background users. Audit complete
	 * synchronous use and cleanup; distinct names alone do not establish isolation
	 * or affinity.
	 *
	 * @param rule    Unique nonblank diagnostic name
	 * @param matches Flows covered by this audit
	 * @param keys    Identities of the actual shared mutable state; empty is
	 *                known-empty
	 * @return this adapter
	 */
	public PreparedFlocessor resources( String rule, Predicate<Flow> matches, String... keys ) {
		beforeConfiguration();
		rules.resources( rule, matches, keys );
		declaredResources = true;
		return this;
	}

	/**
	 * Declares exclusion against all cooperating work, including known-empty work.
	 *
	 * @param rule    Unique nonblank diagnostic name
	 * @param matches Flows requiring exclusive use
	 * @return this adapter
	 */
	public PreparedFlocessor exclusive( String rule, Predicate<Flow> matches ) {
		beforeConfiguration();
		rules.exclusive( rule, matches );
		declaredResources = true;
		return this;
	}

	/**
	 * Retrieves the stored preparation result without reevaluating predicates.
	 *
	 * @param flow A selected or dependency-expanded flow in this live preparation
	 * @return Its resolved resource identities, policy and matching rule names
	 */
	public ResourceRequirements requirements( Flow flow ) {
		return Objects.requireNonNull( requirements.get( flow ),
				"Flow is not prepared or was detached" );
	}

	/**
	 * @param index Preparation-local flow index
	 * @return Its stored requirements
	 */
	ResourceRequirements requirements( int index ) {
		return requirements( selectedFlows.get( index ) );
	}

	/**
	 * @param owner The factory-local lifecycle owner
	 * @param title The test title
	 * @param model The existing system model
	 */
	PreparedFlocessor( FlowExecution owner, String title, Model model ) {
		super( title, model, owner.history() );
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
		if( owner.parallel() || declaredResources ) {
			requireIndependentTracerConfiguration();
		}
		List<DynamicNode> nodes = new ArrayList<>();
		Set<String> identities = new HashSet<>();
		try( Stream<Flow> selected = prepareFlows( this::resolveResources ) ) {
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
			owner.prepareParallel( selectedFlows, nodes,
					selectedFlows.stream().map( requirements::get ).toList() );
		}
		return owner.describe( nodes );
	}

	private void resolveResources( Flow flow ) {
		requirements.put( flow, rules.resolve( flow ) );
		// Explicit serial participation must not pretend that per-flow reservations
		// implement cross-flow context or whole-chain ownership. Legacy serial
		// configuration without these new declarations retains its existing behavior.
		if( declaredResources && !owner.parallel()
				&& (flow.context().findAny().isPresent() || flow.residue().findAny().isPresent()
						|| flow.meta().tags().stream()
								.anyMatch( t -> t.startsWith( Order.CHAIN_TAG_PREFIX ) )) ) {
			throw new IllegalStateException(
					"Flow serial resources do not yet own context, residue or chain: "
							+ flow.meta().id() );
		}
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
		requirements.clear();
		rules = null;
	}

	/**
	 * Processes the selected flow without capturing it in a native description.
	 *
	 * @param index The preparation-local flow index
	 */
	void processSelected( int index ) {
		Flow flow = selectedFlows.get( index );
		Result result = null;
		Throwable failure = null;
		try {
			process( flow );
			result = Result.SUCCESS;
		}
		catch( IncompleteExecutionException e ) {
			result = Result.SKIP;
			failure = e;
			throw e;
		}
		catch( AssertionError e ) {
			result = Result.UNEXPECTED;
			failure = e;
			throw e;
		}
		catch( Exception e ) {
			result = Result.ERROR;
			failure = e;
			throw e;
		}
		catch( Error e ) {
			failure = e;
			throw e;
		}
		finally {
			if( owner.parallel() ) {
				owner.processedParallel( index, result, failure );
			}
			else if( result != null ) {
				history.recordResult( flow, result );
			}
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
