/**
 * Copyright (c) 2021 Mastercard. All rights reserved.
 */

package com.mastercard.test.flow.validation.coppice;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.TitledBorder;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;

import org.graphstream.ui.graphicGraph.GraphPosLengthUtils;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.validation.InheritanceHealth;
import com.mastercard.test.flow.validation.coppice.ui.Animation;
import com.mastercard.test.flow.validation.coppice.ui.DiffView;
import com.mastercard.test.flow.validation.coppice.ui.FlowList;
import com.mastercard.test.flow.validation.coppice.ui.FlowTransfer;
import com.mastercard.test.flow.validation.coppice.ui.FlowView;
import com.mastercard.test.flow.validation.coppice.ui.GraphTree;
import com.mastercard.test.flow.validation.coppice.ui.Progress;
import com.mastercard.test.flow.validation.coppice.ui.Range;
import com.mastercard.test.flow.validation.coppice.ui.SelectionManager;
import com.mastercard.test.flow.validation.graph.CachingDiffDistance;

/**
 * A GUI tool to examine the internal structure of a system model
 */
public class Coppice {

	private ToIntFunction<
			Flow> creationCost = f -> InheritanceHealth.flatten( f ).split( "\n" ).length;
	private ToIntBiFunction<Flow, Flow> derivationCost = new CachingDiffDistance<>(
			InheritanceHealth::flatten,
			InheritanceHealth::diffDistance );

	private List<Flow> flows = new ArrayList<>();

	/**
	 * Enforces flow selection over all component
	 */
	final SelectionManager selectionManager = new SelectionManager();

	/**
	 * right-click menu
	 */
	final JPopupMenu popupMenu = new JPopupMenu();
	private final Action view = new AbstractAction( "View" ) {

		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed( ActionEvent arg0 ) {
			view();
		}
	};
	private final Action compare = new AbstractAction( "Compare" ) {

		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed( ActionEvent arg0 ) {
			compare();
		}
	};
	private final Action optimiseParent = new AbstractAction( "Optimise parent" ) {

		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed( ActionEvent arg0 ) {
			optimiseParent();
		}
	};
	private final Action optimiseChildren = new AbstractAction( "Optimise children" ) {

		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed( ActionEvent arg0 ) {
			optimiseChildren();
		}
	};
	{
		popupMenu.add( view );
		popupMenu.add( compare );
		popupMenu.add( optimiseParent );
		popupMenu.add( optimiseChildren );
	}

	private final JFrame frame;
	private final JSplitPane split = new JSplitPane( JSplitPane.HORIZONTAL_SPLIT );
	private final JDesktopPane desk = new JDesktopPane();
	private final Progress progress = new Progress();
	/**
	 * The flow list component
	 */
	final FlowList flowList = new FlowList( selectionManager::update );
	private final Range dfc = new Range( "Diff distance filter" );
	private final GraphTree actualHierarchy = new GraphTree( "Actual Hierarchy",
			creationCost, derivationCost, selectionManager::update, popupMenu, dfc );

	/**
	 * Flow detail views
	 */
	final Map<Flow, JInternalFrame> views = new HashMap<>();

	/**
	 * This ensures that the associated flow is selected in all components when the
	 * flow-detail frame is focused
	 */
	private final InternalFrameListener viewListener = new InternalFrameAdapter() {

		@Override
		public void internalFrameActivated( InternalFrameEvent ife ) {
			views.entrySet().stream()
					.filter( e -> e.getValue() == ife.getInternalFrame() )
					.findAny()
					.map( Map.Entry::getKey )
					.ifPresent( selectionManager::update );
		}
	};

	/***/
	public Coppice() {
		view.setEnabled( false );
		compare.setEnabled( false );

		selectionManager.register( flowList );
		selectionManager.register( actualHierarchy );
		selectionManager.register( t -> {
			view.setEnabled( t != null );
			compare.setEnabled( t != null );
			optimiseParent.setEnabled( t != null );
			optimiseChildren.setEnabled( t != null );
		} );

		flowList.withFilterListener( f -> actualHierarchy.filter( "name", f ) );

		frame = new JFrame( "Coppice" );
		frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

		frame.getContentPane().setLayout( new BorderLayout() );

		FlowTransfer.registerSource( flowList.getList(), selectionManager::getSelected );
		FlowTransfer.registerSource( actualHierarchy.tree(), selectionManager::getSelected );

		flowList.getList().addMouseListener( new MouseAdapter() {

			@Override
			public void mouseClicked( MouseEvent e ) {
				flowList.getList().setSelectedIndex( flowList.getList().locationToIndex( e.getPoint() ) );
				if( SwingUtilities.isRightMouseButton( e ) ) {
					popupMenu.show( e.getComponent(), e.getX(), e.getY() );
				}
			}
		} );

		JSlider animScale = new JSlider( SwingConstants.HORIZONTAL, 0, 100, 100 );
		animScale.setBorder( new TitledBorder( "Layout delay" ) );
		animScale.addChangeListener(
				e -> Animation.scale = ((float) animScale.getValue() - animScale.getMinimum())
						/ (animScale.getMaximum() - animScale.getMinimum()) );

		split.setOneTouchExpandable( true );
		split.setContinuousLayout( true );
		frame.getContentPane().add( split, BorderLayout.CENTER );

		JComponent left = new Box( BoxLayout.Y_AXIS );
		left.add( flowList );
		left.add( dfc.controls() );
		left.add( animScale );

		split.add( left, JSplitPane.LEFT );

		JPanel dp = new JPanel( new BorderLayout() );
		desk.setPreferredSize( new Dimension( 800, 600 ) );
		dp.add( desk, BorderLayout.CENTER );
		dp.add( progress, BorderLayout.SOUTH );
		split.add( dp, JSplitPane.RIGHT );

		JInternalFrame actual = new JInternalFrame( "Actual Hierarchy", true, false, true, true );
		actual.getContentPane().setLayout( new BorderLayout() );
		actual.getContentPane().add( actualHierarchy.view(), BorderLayout.CENTER );
		desk.add( actual );
		actual.setSize( 600, 400 );
		actual.setVisible( true );
		try {
			actual.setMaximum( true );
		}
		catch( @SuppressWarnings("unused") PropertyVetoException e ) {
			// meh
		}

		frame.pack();
		frame.setLocationRelativeTo( null );
		frame.setVisible( true );
		frame.toFront();

		progress.update( "Model", "instantiating", -1, 1 );
	}

	/**
	 * Controls how the creation cost of a {@link Flow} is calculated
	 *
	 * @param cc When supplied with a {@link Flow}, calculates the cost of creating
	 *           that {@link Flow} from nothing
	 * @return <code>this</code>
	 */
	public Coppice creationCost( ToIntFunction<Flow> cc ) {
		creationCost = cc;
		return this;
	}

	/**
	 * Controls how the derivation cost of a {@link Flow} is calculated
	 *
	 * @param dc When supplied with a parent and child {@link Flow}s, calculates the
	 *           cost of deriving the child from the parent
	 * @return <code>this</code>
	 */
	public Coppice derivationCost( ToIntBiFunction<Flow, Flow> dc ) {
		derivationCost = dc;
		return this;
	}

	/**
	 * Sets the model to be examined
	 *
	 * @param model the system model
	 * @return <code>this</code>
	 */
	public Coppice examine( Model model ) {
		if( !flows.isEmpty() ) {
			throw new IllegalStateException( "One model at a time please" );
		}

		frame.setTitle( "Coppice : " + model.title() );

		progress.update( "Model", "building", -1, 1 );

		actualHierarchy.graph().forceAnimation( true );
		model.flows().forEach( flow -> {
			flows.add( flow );
			SwingUtilities.invokeLater( () -> {
				flowList.with( flow );
				actualHierarchy.with( flow );
			} );

			progress.update( "Model", flow.meta().description(), -1, 1 );
			Animation.ADDITION.event();
		} );
		progress.update( "Model", "complete", 1, 1 );

		SwingUtilities.invokeLater( () -> {
			flowList.sort();
			actualHierarchy.prepForUsage();
		} );

		// give the graph a bit of time to settle
		new Timer( 5000, e -> actualHierarchy.graph().forceAnimation( false ) ).start();

		return this;
	}

	/**
	 * @return The diffing function
	 */
	public ToIntBiFunction<Flow, Flow> diffDistance() {
		return derivationCost;
	}

	/**
	 * Opens a txn-detail view frame
	 */
	public void view() {
		Flow txn = selectionManager.getSelected();
		if( txn != null ) {
			JInternalFrame flowView = views.computeIfAbsent( txn, t -> {
				FlowView txnView = new FlowView( txn );
				JInternalFrame f = new JInternalFrame( txn.meta().description(), true, true, true, true );
				f.setDefaultCloseOperation( WindowConstants.HIDE_ON_CLOSE );
				f.getContentPane().setLayout( new BorderLayout() );
				f.getContentPane().add( txnView.view(), BorderLayout.CENTER );
				desk.add( f );
				return f;
			} );
			flowView.addInternalFrameListener( viewListener );
			flowView.pack();
			flowView.setVisible( true );
		}
	}

	/**
	 * Opens a txn-compare frame
	 */
	public void compare() {
		Flow txn = selectionManager.getSelected();
		DiffView diffView = new DiffView( this, selectionManager, txn );
		FlowTransfer.registerSink( diffView.view(), diffView::destination );

		JInternalFrame diff = new JInternalFrame( "Diff", true, true, true, true );
		diff.setDefaultCloseOperation( WindowConstants.DISPOSE_ON_CLOSE );
		diff.getContentPane().setLayout( new BorderLayout() );
		diff.getContentPane().add( diffView.view(), BorderLayout.CENTER );
		desk.add( diff );
		diff.pack();
		diff.setSize( diff.getWidth() + 20, 300 );
		diff.setVisible( true );
	}

	/**
	 * Opens a flow-specific hierarchy-optimisation frame
	 */
	public void optimiseParent() {
		Flow txn = selectionManager.getSelected();
		if( txn != null ) {
			GraphTree gt = new GraphTree( "Optimal parent for " + txn.meta().id(),
					creationCost, derivationCost, selectionManager::update, popupMenu, dfc );
			Runnable task = new OptimiseParent( flows, txn, derivationCost, gt, progress );
			runTask( gt, task );
		}
	}

	/**
	 * Opens a global hierarchy-optimisation frame
	 */
	public void optimiseChildren() {
		Flow txn = selectionManager.getSelected();
		if( txn != null ) {
			GraphTree gt = new GraphTree( "Optimal hierarchy from " + txn.meta().id(),
					creationCost, derivationCost, selectionManager::update, popupMenu, dfc );
			Runnable task = new OptimiseChildren( flows, txn, derivationCost, gt, progress );
			runTask( gt, task );
		}
	}

	private void runTask( GraphTree gt, Runnable task ) {
		// build graph and tree
		flows.forEach( gt::with );
		// clone positions from actual
		gt.graph().graph().nodes()
				.forEach( n -> {
					double[] pos = GraphPosLengthUtils.nodePosition(
							actualHierarchy.graph().graphicGraph().getNode( n.getId() ) );
					n.setAttribute( "xyz", pos[0], pos[1], pos[2] );
				} );

		selectionManager.register( gt );
		flowList.withFilterListener( f -> gt.filter( "name", f ) );
		FlowTransfer.registerSource( gt.tree(), selectionManager::getSelected );

		// show in UI
		JInternalFrame taskView = new JInternalFrame( gt.graph().graph().getId(),
				true, true, true, true );
		taskView.setDefaultCloseOperation( WindowConstants.DISPOSE_ON_CLOSE );
		taskView.getContentPane().setLayout( new BorderLayout() );
		taskView.getContentPane().add( gt.view(), BorderLayout.CENTER );
		desk.add( taskView );
		taskView.pack();
		taskView.setVisible( true );
		try {
			taskView.setMaximum( true );
		}
		catch( @SuppressWarnings("unused") PropertyVetoException e ) {
			// meh
		}

		// set the thing running
		Thread t = new Thread( task, taskView.getTitle() );
		t.setDaemon( true );
		t.start();
	}

}
