package com.mastercard.test.flow.assrt.junit5;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.TestIdentifier;
import org.opentest4j.TestAbortedException;

import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.assrt.Order;
import com.mastercard.test.flow.util.Flows;

/**
 * Temporary checked ticket08 frontier: explicitly independent synchronous work,
 * at most one external predecessor, no chain/context/basis/residue or message
 * aliasing. Not a resource scheduler. Exactly one factory iterator may wait;
 * native workers execute original bodies with no offload or idle estimates.
 * Native FAILED is evidence separate from the processor's History.
 */
final class FlowParallelOwner implements FlowNativeCall.Observer {
	private final Object lock = new Object();
	private final FlowExecution owner;
	private FlowNativeProfile profile;
	private final String factoryId;
	private Thread factoryThread;
	private Stream<?> originalStream;
	private FlowNativeCall.Attachment attachment;
	private final List<Node> nodes = new ArrayList<>();
	private final Map<String, Node> names = new HashMap<>();
	private final Map<String, Node> nativeIds = new HashMap<>();
	private final PriorityQueue<Integer> ready = new PriorityQueue<>();
	private Throwable stopped;
	private boolean prepared;
	private boolean streamClosed;
	private boolean factoryTerminal;
	private boolean released;
	private int issued;
	private int terminal;
	private int active;

	/**
	 * Captures the factory identity, thread and checked native execution profile.
	 *
	 * @param owner   The detachable factory-local handle
	 * @param context The genuine factory context
	 */
	FlowParallelOwner( FlowExecution owner, ExtensionContext context ) {
		this.owner = owner;
		profile = FlowNativeProfile.enter( context );
		factoryId = context.getUniqueId();
		factoryThread = Thread.currentThread();
	}

	/**
	 * Connects this observer to the early call-local Launcher receiver.
	 *
	 * @param context The owning factory context used for the handshake
	 */
	void attach( ExtensionContext context ) {
		attachment = FlowNativeCall.attach( context, this );
	}

	/**
	 * Rejects a changed native profile or invalid Launcher attachment.
	 *
	 * @param context The factory context being revalidated
	 */
	void checkFactory( ExtensionContext context ) {
		profile.check( context );
		checkAttachment();
	}

	/**
	 * Audits supported model surfaces and builds readiness before live admission.
	 *
	 * @param flows        Selected flows in canonical prerequisite order
	 * @param descriptions Corresponding owned dynamic tests
	 */
	void prepare( List<Flow> flows, List<DynamicNode> descriptions ) {
		Map<Flow, Integer> indices = new IdentityHashMap<>();
		Map<Message, Flow> messages = new IdentityHashMap<>();
		for( int i = 0; i < flows.size(); i++ ) {
			Flow flow = flows.get( i );
			indices.put( flow, i );
			if( flow.basis() != null || flow.context().findAny().isPresent()
					|| flow.residue().findAny().isPresent()
					|| flow.meta().tags().stream().anyMatch( t -> t.startsWith( Order.CHAIN_TAG_PREFIX ) ) ) {
				throw unsupported( flow, "basis, context, residue or chain" );
			}
			Flows.interactions( flow ).forEach( interaction -> {
				for( Message message : new Message[] { interaction.request(), interaction.response() } ) {
					if( message != null && messages.putIfAbsent( message, flow ) != null ) {
						throw unsupported( flow, "shared message instance" );
					}
				}
			} );
			DynamicTest description = (DynamicTest) descriptions.get( i );
			URI uri = description.getTestSourceUri().orElseThrow(
					() -> unsupported( flow, "missing class source URI" ) );
			if( !"class".equals( uri.getScheme() ) ) {
				throw unsupported( flow, "non-class source URI" );
			}
			Node node = new Node( description, ClassSource.from( uri ) );
			nodes.add( node );
			names.put( description.getDisplayName(), node );
		}
		for( int i = 0; i < flows.size(); i++ ) {
			Flow flow = flows.get( i );
			Integer predecessor = null;
			for( Dependency dependency : flow.dependencies().toList() ) {
				Integer source = indices.get( dependency.source().flow() );
				if( source == null || source >= i ) {
					throw unsupported( flow, "absent, self or noncanonical prerequisite" );
				}
				if( predecessor != null && !predecessor.equals( source ) ) {
					throw unsupported( flow, "fan-in publication" );
				}
				if( dependency.sink().flow() != null && dependency.sink().flow() != flow ) {
					throw unsupported( flow, "foreign binding destination" );
				}
				predecessor = source;
			}
			if( predecessor == null ) {
				ready.add( i );
			}
			else {
				nodes.get( predecessor ).successors.add( i );
			}
		}
		prepared = true;
	}

	private static IllegalStateException unsupported( Flow flow, String surface ) {
		return new IllegalStateException( "Flow parallel tracer unchecked " + surface + ": "
				+ flow.meta().id() );
	}

	/**
	 * Creates unsplittable readiness-driven enumeration with original-stream
	 * cleanup. Stream closure alone does not finalize the factory.
	 *
	 * @param original The validated factory return stream
	 * @return The native admission stream
	 */
	Stream<DynamicNode> consume( Stream<?> original ) {
		originalStream = original;
		Spliterator<DynamicNode> source = new Spliterators.AbstractSpliterator<>( Long.MAX_VALUE,
				Spliterator.ORDERED | Spliterator.NONNULL ) {
			@Override
			public Spliterator<DynamicNode> trySplit() {
				return null;
			}

			@Override
			public boolean tryAdvance( Consumer<? super DynamicNode> action ) {
				try {
					Node node = admit();
					if( node == null ) {
						return false;
					}
					// Admission precedes native registration, including inline callbacks.
					action.accept( node.description );
					return true;
				}
				catch( RuntimeException | Error failure ) {
					stop( failure );
					throw failure;
				}
			}
		};
		return StreamSupport.stream( source, false ).onClose( () -> {
			try( Stream<?> cleanup = originalStream ) {
				synchronized( lock ) {
					if( issued != nodes.size() || stopped != null ) {
						throw new IllegalStateException( "Incomplete Flow parallel enumeration", stopped );
					}
				}
			}
			catch( RuntimeException | Error failure ) {
				stop( failure );
				throw failure;
			}
			finally {
				synchronized( lock ) {
					streamClosed = true;
					originalStream = null;
				}
			}
		} );
	}

	private Node admit() {
		if( Thread.currentThread() != factoryThread ) {
			throw new IllegalStateException( "Only the actual Flow factory may await readiness" );
		}
		for( ;; ) {
			profile.checkAdmission();
			checkAttachment();
			synchronized( lock ) {
				checkActive();
				if( issued == nodes.size() ) {
					return null;
				}
				if( !ready.isEmpty() ) {
					Node node = nodes.get( ready.remove() );
					node.admitted = true; // Empty, explicitly audited resource grant.
					issued++;
					return node;
				}
				try {
					lock.wait();
				}
				catch( InterruptedException failure ) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException( "Flow readiness interrupted", failure );
				}
			}
		}
	}

	/**
	 * Validates the exact native binding, runs the selected body and records
	 * drainage.
	 *
	 * @param index   The preparation-local flow index
	 * @param context The intercepted native leaf context
	 */
	void process( int index, ExtensionContext context ) {
		profile.checkAdmission();
		checkAttachment();
		Node node;
		synchronized( lock ) {
			if( stopped != null ) {
				throw new TestAbortedException( "Flow parallel admission stopped", stopped );
			}
			checkActive();
			node = nodes.get( index );
			if( context == null || nativeIds.get( context.getUniqueId() ) != node
					|| FlowExtension.invocationExecutable() != node.description.getExecutable()
					|| context.getExecutionMode() != org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT
					|| !node.admitted || !node.started || node.entered || node.result != null ) {
				throw new IllegalStateException( "Flow executable differs from its owned native binding" );
			}
			node.entered = true;
			active++;
		}
		try {
			owner.processParallel( index );
		}
		catch( Error failure ) {
			if( !(failure instanceof AssertionError) ) {
				stop( failure );
			}
			throw failure;
		}
		finally {
			synchronized( lock ) {
				node.safe = true;
				active--;
				lock.notifyAll();
			}
		}
	}

	private void checkAttachment() {
		try {
			if( attachment == null ) {
				throw new IllegalStateException( "Missing early Flow native attachment" );
			}
			attachment.check();
		}
		catch( RuntimeException | Error failure ) {
			stop( failure );
			throw failure;
		}
	}

	private void checkActive() {
		if( !prepared || released || stopped != null ) {
			throw new IllegalStateException( "Flow parallel tracer is not active", stopped );
		}
	}

	/**
	 * Retains the first failure, clears readiness and wakes the factory waiter.
	 * Existing ownership is not released.
	 *
	 * @param failure The cause preventing safe admission
	 */
	void stop( Throwable failure ) {
		synchronized( lock ) {
			if( stopped == null ) {
				stopped = failure;
			}
			ready.clear();
			lock.notifyAll();
		}
	}

	@Override
	public void registered( TestIdentifier id ) {
		try {
			synchronized( lock ) {
				Node node = names.get( id.getDisplayName() );
				if( node == null || !node.admitted || !id.isTest() || id.isContainer()
						|| !id.getParentId().filter( factoryId::equals ).isPresent()
						|| !id.getSource().filter( node.source::equals ).isPresent()
						|| node.id != null && !node.id.equals( id.getUniqueId() )
						|| nativeIds.containsKey( id.getUniqueId() )
								&& nativeIds.get( id.getUniqueId() ) != node ) {
					throw new IllegalStateException( "Unexpected native Flow registration: " + id );
				}
				node.id = id.getUniqueId();
				nativeIds.put( node.id, node );
			}
		}
		catch( RuntimeException | Error failure ) {
			stop( failure );
			throw failure;
		}
	}

	@Override
	public void started( TestIdentifier id ) {
		synchronized( lock ) {
			bound( id ).started = true;
		}
	}

	private Node bound( TestIdentifier id ) {
		Node node = nativeIds.get( id.getUniqueId() );
		if( node == null ) {
			IllegalStateException failure = new IllegalStateException( "Unbound native Flow: " + id );
			stop( failure );
			throw failure;
		}
		return node;
	}

	@Override
	public void finished( TestIdentifier id, TestExecutionResult result ) {
		synchronized( lock ) {
			Node node = bound( id );
			if( node.result != null ) {
				if( node.result.getStatus() != result.getStatus()
						|| !node.result.getThrowable().equals( result.getThrowable() ) ) {
					stop( new IllegalStateException( "Conflicting native Flow terminal: " + id ) );
				}
				return;
			}
			node.result = result;
			terminal++;
			if( !node.entered || !node.safe ) {
				stop(
						new IllegalStateException( "Native terminal without drained Flow processing: " + id ) );
			}
			else if( stopped == null ) {
				ready.addAll( node.successors );
			}
			lock.notifyAll();
		}
	}

	@Override
	public void skipped( TestIdentifier id, String reason ) {
		stop(
				new IllegalStateException( "Unchecked native Flow subtree skip: " + id + ": " + reason ) );
	}

	@Override
	public void factoryFinished( TestExecutionResult result ) {
		boolean complete;
		synchronized( lock ) {
			factoryTerminal = true;
			complete = prepared && streamClosed && issued == nodes.size() && terminal == issued
					&& active == 0 && stopped == null
					&& result.getStatus() == TestExecutionResult.Status.SUCCESSFUL;
			if( !complete ) {
				stop( new IllegalStateException( "Incomplete native Flow factory", result.getThrowable()
						.orElse( null ) ) );
			}
		}
		if( complete ) {
			checkAttachment();
			attachment.release();
			owner.completeParallel();
			synchronized( lock ) {
				released = true;
				nodes.clear();
				names.clear();
				nativeIds.clear();
				profile = null;
				factoryThread = null;
				attachment = null;
			}
		}
	}

	/**
	 * Stops admission at the exceptional class backstop without forcing release.
	 *
	 * @throws IllegalStateException If native completion has not released this
	 *                               owner
	 */
	void close() {
		synchronized( lock ) {
			if( released ) {
				return;
			}
			stop( new IllegalStateException( "Flow parallel class backstop reached" ) );
			throw new IllegalStateException( "Incomplete Flow parallel tracer: prepared=" + prepared
					+ ", issued=" + issued + ", terminal=" + terminal + ", active=" + active
					+ ", factoryTerminal=" + factoryTerminal + "; no forced release or finalization",
					stopped );
		}
	}

	private static final class Node {
		/** Original description and executable offered to Jupiter. */
		final DynamicTest description;
		/** Expected native registration source. */
		final ClassSource source;
		/** Canonical indices made ready by this node's drained native terminal. */
		final List<Integer> successors = new ArrayList<>();
		/** Native unique ID assigned by validated registration. */
		String id;
		/** Whether readiness granted this node to native enumeration. */
		boolean admitted;
		/** Whether the bound native start callback has arrived. */
		boolean started;
		/** Whether the guarded original body has entered. */
		boolean entered;
		/** Whether entered processing has synchronously drained. */
		boolean safe;
		/** Native terminal evidence, independent of processor History. */
		TestExecutionResult result;

		/**
		 * Captures immutable description identity before admission.
		 *
		 * @param description The original native test
		 * @param source      The expected class source
		 */
		Node( DynamicTest description, ClassSource source ) {
			this.description = description;
			this.source = source;
		}
	}
}
