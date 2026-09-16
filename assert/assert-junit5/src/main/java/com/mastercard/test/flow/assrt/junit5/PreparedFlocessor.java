package com.mastercard.test.flow.assrt.junit5;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
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
import com.mastercard.test.flow.assrt.ContextDomain;
import com.mastercard.test.flow.assrt.History.Result;
import com.mastercard.test.flow.assrt.resource.ChainPlan;
import com.mastercard.test.flow.assrt.resource.ResourceRequirements;
import com.mastercard.test.flow.assrt.resource.ResourceReservations.Grant;
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
	private final Map<Flow, Integer> selectedIndices = new IdentityHashMap<>();
	private ChainPlan chains;
	private ContextDomain domain;
	private BiConsumer<Flow, ContextDomain.Receipt> ownership;

	/**
	 * Binds all selected flows to the existing fixture's complete physical domain,
	 * including possible prior-context removal for flows with no context. Declare
	 * before owner lifecycle actions and tests(); it does not create or reset
	 * state, classify UNKNOWN flows, or authorize reporting/capture/replay.
	 *
	 * @param domain Shared by every cooperating wrapper for this fixture's lifetime
	 * @return this adapter
	 */
	public PreparedFlocessor contextDomain( ContextDomain domain ) {
		beforeConfiguration();
		Objects.requireNonNull( domain ).checkMode( owner.parallel() );
		if( this.domain != null && this.domain != domain )
			throw new IllegalStateException( "A runner cannot replace its actual fixture domain" );
		this.domain = domain;
		useContextDomain( domain );
		declaredResources = true;
		return this;
	}

	/**
	 * Also supplies exact owner-evidence receipts before each native invocation is
	 * emitted, outside all bookkeeping locks. The callback must be short and
	 * nonthrowing; it must not perform fixture actions. Owners correlate the
	 * supplied flow/receipt explicitly with their work, not by thread or a
	 * current-user guess. Receipt uncertainty covers pre-body work and outer
	 * cleanup, including evidence delivered by another thread before native
	 * completion. It retains the existing whole-chain grant and stops this run;
	 * ordinary safe cleanup throws do neither. A callback failure before native
	 * handoff stops admission and returns only proven-unused ownership, never a
	 * grant already marked uncertain or still used by another chain member.
	 *
	 * @param domain    Shared physical fixture domain
	 * @param ownership Receives each selected Flow and its exact admitted receipt
	 * @return this adapter
	 */
	public PreparedFlocessor contextDomain( ContextDomain domain,
			BiConsumer<Flow, ContextDomain.Receipt> ownership ) {
		contextDomain( domain );
		this.ownership = Objects.requireNonNull( ownership );
		return this;
	}

	/**
	 * @param index Admitted member, not yet emitted to native execution
	 * @param grant Existing whole reservation, never reacquired on a native worker
	 */
	void admitted( int index, Grant grant ) {
		if( ownership != null )
			ownership.accept( selectedFlows.get( index ), domain.receipt( grant ) );
	}

	/**
	 * Permits outside overlap only after auditing each entire named chain's state,
	 * callbacks and cleanup. Member resource declarations remain necessary; an
	 * UNKNOWN or exclusive member still makes the whole chain global-exclusive.
	 * Membership comes from existing chain tags, never from partial predicates.
	 *
	 * @param rule  Unique nonblank whole-chain audit name
	 * @param names Existing chain tag suffixes, without the chain: prefix
	 * @return this adapter
	 */
	public PreparedFlocessor isolatedChains( String rule, String... names ) {
		beforeConfiguration();
		rules.isolatedChains( rule, names );
		declaredResources = true;
		return this;
	}

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
	 * Retrieves the frozen whole-unit reservation without reevaluating predicates.
	 * Retain the immutable value for diagnostics after this preparation detaches.
	 *
	 * @param flow A selected or dependency-expanded flow in this live preparation
	 * @return Effective resource union, UNKNOWN/exclusive policy and isolation
	 *         audits
	 */
	public ResourceRequirements reservation( Flow flow ) {
		int index = Objects.requireNonNull( selectedIndices.get( flow ),
				"Flow is not prepared or was detached" );
		return reservation( index );
	}

	/**
	 * @param index Preparation-local flow index
	 * @return Its stored requirements
	 */
	ResourceRequirements requirements( int index ) {
		return requirements( selectedFlows.get( index ) );
	}

	/**
	 * @param index Selected member index
	 * @return Complete whole-unit requirements
	 */
	ResourceRequirements reservation( int index ) {
		return chains.requirements( index );
	}

	/**
	 * @param index Next selected index
	 * @return Whether it continues the previous unit
	 */
	boolean continuesChain( int index ) {
		return index > 0 && chains.first( index ) == chains.first( index - 1 );
	}

	/**
	 * @param owner The factory-local lifecycle owner
	 * @param title The test title
	 * @param model The existing system model
	 */
	PreparedFlocessor( FlowExecution owner, String title, Model model ) {
		super( title, model, owner.history() );
		this.owner = owner;
		finalOnlyReporting();
	}

	/**
	 * Freezes configuration, prepares owned descriptions and audits parallel
	 * admission. No flow bodies execute during preparation.
	 *
	 * @return The native descriptions to return from the owning factory
	 */
	public Stream<DynamicNode> tests() {
		beforeConfiguration();
		owner.preparing();
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
				selectedIndices.put( flow, index );
				nodes.add( DynamicTest.dynamicTest( id, Flocessor.testSource( flow ),
						owner.invocation( index ) ) );
			}
		}
		chains = rules.chains( selectedFlows,
				selectedFlows.stream().map( requirements::get ).toList() );
		if( owner.parallel() )
			owner.prepareParallel( selectedFlows, nodes, chains );
		return owner.describe( nodes );
	}

	private void resolveResources( Flow flow ) {
		ResourceRequirements resolved = rules.resolve( flow );
		requirements.put( flow, domain == null ? resolved : resolved.plus( domain.requirements() ) );
		// Labels alone cannot identify applied state. Existing undeclared serial
		// configuration retains its legacy processor-local behavior.
		if( domain == null && (declaredResources || owner.parallel())
				&& (flow.context().findAny().isPresent() || flow.residue().findAny().isPresent()) ) {
			throw new IllegalStateException(
					"Flow resources require an actual fixture domain for context or residue: "
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

	/** Claims enabled report output after returned descriptions are validated. */
	void initialize() {
		initializeReporting();
	}

	/** Clears the prepared invocation table after owned use has drained. */
	void detach() {
		detachProcessing();
		selectedFlows.clear();
		selectedIndices.clear();
		requirements.clear();
		chains = null;
		rules = null;
		ownership = null;
		useContextDomain( null );
		domain = null;
	}

	/**
	 * Processes the selected flow without capturing it in a native description.
	 *
	 * @param index The preparation-local flow index
	 * @param grant The existing whole native reservation, retained through outer
	 *              cleanup
	 */
	void processSelected( int index, Grant grant ) {
		Flow flow = selectedFlows.get( index );
		Result result = null;
		Throwable failure = null;
		try( ContextDomain.Use use = domain == null ? null : domain.enter( grant ) ) {
			// Lease entry/exit are infrastructure, not Flow processing. In particular,
			// late owner evidence may reject entry after the adapter's pre-use gate.
			try {
				process( flow );
				result = Result.SUCCESS;
			}
			catch( IncompleteExecutionException e ) {
				result = Result.SKIP;
				throw e;
			}
			catch( AssertionError e ) {
				result = Result.UNEXPECTED;
				throw e;
			}
			catch( Exception e ) {
				result = Result.ERROR;
				throw e;
			}
		}
		catch( Throwable e ) {
			failure = e;
			throw e;
		}
		finally {
			try {
				if( owner.parallel() ) {
					owner.processedParallel( index, result, failure );
				}
				else if( result != null ) {
					history.recordResult( flow, result );
				}
				else
					owner.incompleteSerial( failure );
			}
			catch( Throwable cleanup ) {
				if( failure == null )
					throw cleanup;
				if( failure != cleanup )
					failure.addSuppressed( cleanup );
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
