/**
 * Copyright (c) 2021 Mastercard. All rights reserved.
 */

package com.mastercard.test.flow.validation.coppice;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.swing.Timer;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.validation.coppice.ui.Animation;
import com.mastercard.test.flow.validation.coppice.ui.GraphTree;
import com.mastercard.test.flow.validation.coppice.ui.GraphView;
import com.mastercard.test.flow.validation.coppice.ui.Progress;
import com.mastercard.test.flow.validation.graph.CachingDiffDistance;
import com.mastercard.test.flow.validation.graph.DiffGraph;

/**
 * This task optimises the entire inheritance tree for the targeted flow:
 * <ol>
 * <li>Remove the existing inheritance links for all flows</li>
 * <li>All of these flows effectively form a fully-connected graph, with the
 * diff distance between flows being the edge cost.</li>
 * <li>Find the minimum spanning tree of the diff graph. The MST is the optimal
 * inheritance structure.</li>
 * </ol>
 */
class OptimiseChildren implements Runnable {

	private final List<Flow> corpus;
	private final Flow txn;
	private final CachingDiffDistance<Flow> diffDistance;
	private final GraphTree display;
	private final Progress progress;

	/**
	 * @param corpus       The list of flows
	 * @param txn          The flow to find start our MST at
	 * @param diffDistance How to calculate inter-flow distance
	 * @param display      Where to display the results
	 * @param progress     How to show processing progress
	 */
	public OptimiseChildren( List<Flow> corpus, Flow txn,
			CachingDiffDistance<Flow> diffDistance, GraphTree display,
			Progress progress ) {
		this.corpus = corpus;
		this.txn = txn;
		this.diffDistance = diffDistance;
		this.display = display;
		this.progress = progress;
	}

	@Override
	public void run() {

		display.graph().forceAnimation( true );
		GraphView.addUIClass( display.getNode( txn ), "optimising" );

		// find compatible flows
		List<Flow> compat = corpus.stream()
				.collect( Collectors.toList() );

		// remove existing edges for those nodes
		AtomicInteger i = new AtomicInteger( 0 );
		compat.forEach( t -> {
			progress.update( "Dissolving", t.meta().description(), i.getAndIncrement(), compat.size() );
			display.removeEdge( t.basis(), t, "basis" );
			Animation.DISSOLVE.event();
		} );
		progress.update( "Dissolving", "complete", 1, 1 );

		// build diffgraph
		DiffGraph<Flow> diffGraph = new DiffGraph<>( diffDistance );
		compat.forEach( diffGraph::add );
		i.set( 0 );
		diffGraph.withMSTListener( ( parent, child ) -> {
			progress.update( "Optimising", child.meta().description(), i.getAndIncrement(),
					compat.size() );
			display.addEdge( parent, child, "basis" );
			Animation.OPTIMISE.event();
		} );

		// find MST. We don't need to look at the result as the graph is updated by the
		// MSTlistener as
		// we go
		diffGraph.minimumSpanningTree( txn );
		progress.update( "Optimising", "complete", 1, 1 );

		display.prepForUsage();

		// give the graph a bit of time to settle
		new Timer( 20000, e -> display.graph().forceAnimation( false ) ).start();

	}
}
