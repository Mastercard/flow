/**
 * Copyright (c) 2021 Mastercard. All rights reserved.
 */

package com.mastercard.test.flow.validation.coppice;

import java.util.Collection;
import java.util.function.ToIntBiFunction;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Node;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.validation.coppice.ui.Animation;
import com.mastercard.test.flow.validation.coppice.ui.GraphTree;
import com.mastercard.test.flow.validation.coppice.ui.GraphView;
import com.mastercard.test.flow.validation.coppice.ui.Progress;

/**
 * This task will, for a single flow, find the most similar flow in the corpus.
 * Given that we should try to minimise the number of edits between flows, the
 * identified closest peer would be a good candidate to use as the inheritance
 * basis.
 */
class OptimiseParent implements Runnable {

	private final Collection<Flow> corpus;
	private final Flow txn;
	private final ToIntBiFunction<Flow, Flow> derivationCost;
	private final GraphTree display;
	private final Progress progress;

	private Flow optimalParent = null;
	private int closestDistance = Integer.MAX_VALUE;
	private int testIndex = 0;

	/**
	 * @param corpus         The list of flows
	 * @param txn            The flow to find a parent for
	 * @param derivationCost How to calculate inter-flow distance
	 * @param display        How to show the results
	 * @param progress       How to signal task progress
	 */
	public OptimiseParent( Collection<Flow> corpus, Flow txn,
			ToIntBiFunction<Flow, Flow> derivationCost, GraphTree display,
			Progress progress ) {
		this.corpus = corpus;
		this.txn = txn;
		this.derivationCost = derivationCost;
		this.display = display;
		this.progress = progress;
	}

	@Override
	public void run() {

		display.graph().forceAnimation( true );
		SwingUtilities.invokeLater( () -> {
			GraphView.addUIClass( display.getNode( txn ), "optimising" );
			display.removeEdge( txn.basis(), txn, "basis" );
		} );

		optimalParent = null;
		closestDistance = Integer.MAX_VALUE;
		testIndex = 0;
		Edge candidateEdge = null;

		Node gn = display.getNode( txn );

		for( Flow t : corpus ) {
			if( t != txn ) {

				progress.update( "Optimal parent for " + txn.meta().description(), t.meta().description(),
						testIndex++, corpus.size() );
				Node tn = display.getNode( t );

				int distance = derivationCost.applyAsInt( txn, t );

				Edge testEdge = display.graph().graph().addEdge( "test", tn, gn );

				if( distance < closestDistance ) {
					if( candidateEdge != null ) {
						display.graph().graph().removeEdge( candidateEdge );
					}

					closestDistance = distance;
					optimalParent = t;

					candidateEdge = display.graph().graph().addEdge( "candidate", gn, tn );
					candidateEdge.setAttribute( "ui.class", "candidate" );
					candidateEdge.setAttribute( "ui.label", closestDistance );
				}

				Animation.SEARCH.event();

				display.graph().graph().removeEdge( testEdge );
			}
		}

		if( candidateEdge != null ) {
			candidateEdge.setAttribute( "ui.class", "optimal" );
			progress.update( "Optimal parent for " + txn.meta().description(),
					optimalParent.meta().description(),
					1, 1 );
		}

		display.prepForUsage();

		// give the graph a bit of time to settle
		new Timer( 5000, e -> display.graph().forceAnimation( false ) ).start();
	}
}
