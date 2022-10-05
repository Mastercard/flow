/**
 * Copyright (c) 2021 Mastercard. All rights reserved.
 */

package com.mastercard.test.flow.validation.coppice.ui;

import static java.util.stream.Collectors.toList;
import static org.graphstream.ui.view.util.InteractiveElement.NODE;

import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.AbstractElement;
import org.graphstream.ui.graphicGraph.GraphicElement;

import com.mastercard.test.flow.Flow;

/**
 * Provides a linked tree and graph view of a flow hierarchy
 */
public class GraphTree implements SelectionManager.Client {

	private final ToIntBiFunction<Flow, Flow> derivationCost;
	private final JSplitPane component;

	/**
	 * The graph component
	 */
	final GraphView graph;
	/**
	 * The graph nodes
	 */
	final Map<String, Flow> graphNodes = new HashMap<>();

	private DefaultMutableTreeNode root = new DefaultMutableTreeNode( "Flows" );
	/**
	 * The tree component
	 */
	FlowTree tree = new FlowTree( root );
	private final Map<Flow, DefaultMutableTreeNode> treeNodes = new HashMap<>();

	private final Map<Flow, Integer> basisDistance = new HashMap<>();

	private final Consumer<Flow> selectionListener;

	private final Range diffWeightFilter;
	private int minDistance = Integer.MAX_VALUE;
	private int maxDistance = 0;

	private final Map<String, Predicate<Flow>> filters = new HashMap<>();

	/**
	 * @param name              The name of the view
	 * @param creationCost      How to calculate the construction cost of a flow
	 * @param derivationCost    How to calculate the derivation cost of a flow
	 * @param selectionListener How items are selected
	 * @param popupMenu         What to display when an item is clicked
	 * @param diffWeightFilter  Which diffs to display
	 */
	public GraphTree( String name,
			ToIntFunction<Flow> creationCost,
			ToIntBiFunction<Flow, Flow> derivationCost,
			Consumer<Flow> selectionListener,
			JPopupMenu popupMenu,
			Range diffWeightFilter ) {
		this.derivationCost = derivationCost;
		this.selectionListener = selectionListener;
		this.diffWeightFilter = diffWeightFilter;

		tree.setRootVisible( false );
		tree.setShowsRootHandles( true );
		tree.addTreeSelectionListener( e -> selectionListener.accept(
				Optional.ofNullable( tree.getLastSelectedPathComponent() )
						.map( o -> ((DefaultMutableTreeNode) o).getUserObject() )
						.filter( o -> o instanceof Flow )
						.map( o -> (Flow) o )
						.orElse( null ) ) );
		tree.addMouseListener( new MouseAdapter() {

			@Override
			public void mouseClicked( MouseEvent e ) {
				if( SwingUtilities.isRightMouseButton( e ) ) {
					int row = tree.getClosestRowForLocation( e.getX(), e.getY() );
					tree.setSelectionRow( row ); // this will provoke the treeselectionlistener above
					popupMenu.show( e.getComponent(), e.getX(), e.getY() );
				}
			}
		} );

		graph = new GraphView( name );
		graph.withSelectionListener( id -> selectionListener.accept( graphNodes.get( id ) ) );
		graph.view().addMouseListener( new MouseAdapter() {

			@Override
			public void mouseClicked( MouseEvent e ) {
				if( SwingUtilities.isRightMouseButton( e ) ) {
					Flow flow = Optional
							.ofNullable( graph.view().findGraphicElementAt(
									EnumSet.of( NODE ), e.getX(), e.getY() ) )
							.map( AbstractElement::getId )
							.map( graphNodes::get )
							.orElse( null );
					selectionListener.accept( flow );
					popupMenu.show( e.getComponent(), e.getX(), e.getY() );
				}
			}
		} );

		component = new JSplitPane( JSplitPane.HORIZONTAL_SPLIT );
		component.setOneTouchExpandable( true );
		component.setContinuousLayout( true );
		component.setDividerLocation( 0 );

		component.add( new JScrollPane( tree,
				ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER ), JSplitPane.LEFT );
		component.add( graph.view(), JSplitPane.RIGHT );

		// draw a histogram of visible edge distances
		graph.view().setForeLayoutRenderer(
				( graphics, gg, px2Gu, widthPx, heightPx, minXGu, minYGu, maxXGu, maxYGu ) -> {
					Set<Node> visibleNodes = graph.view().allGraphicElementsIn(
							EnumSet.of( NODE ),
							0, 0, widthPx, heightPx )
							.stream()
							.filter( e -> !String.valueOf( e.getAttribute( "ui.class" ) )
									.contains( "filtered_out" ) )
							.map( GraphicElement::getId )
							.map( graph.graph()::getNode )
							.collect( Collectors.toSet() );

					// map from distance to incidence count
					TreeMap<Integer, Integer> distances = new TreeMap<>();
					int rootWeight = 0;
					int total = 0;
					for( Node node : visibleNodes ) {
						Flow flow = graphNodes.get( node.getId() );
						if( basisDistance.containsKey( flow ) ) {
							int d = basisDistance.get( flow );
							total += d;
							distances.compute( d,
									( k, v ) -> v != null ? v + 1 : 1 );
						}
						else {
							rootWeight += creationCost.applyAsInt( flow );
						}
					}

					int histHeight = 50;
					graphics.setColor( Color.gray );
					graphics.drawString( "roots " + rootWeight, 5, heightPx - histHeight + 15 );

					// quantise those values into a smaller number of buckets
					if( !distances.isEmpty() ) {
						int min = distances.firstKey();
						int max = distances.lastKey();
						int[] quanta = new int[Math.min( 10, distances.size() )];
						int quantumSize = (int) Math
								.ceil( (float) (max - min) / quanta.length );
						int maxCount = 0;
						for( int i1 = 0; i1 < quanta.length; i1++ ) {
							int from = min + i1 * quantumSize;
							int to = min + (i1 + 1) * quantumSize;
							quanta[i1] = distances.tailMap( from ).headMap( to ).values().stream()
									.mapToInt( Integer::intValue ).sum();
							if( quanta[i1] > maxCount ) {
								maxCount = quanta[i1];
							}
						}

						// draw the buckets
						graphics.drawString( "diffs " + total, 5, heightPx - histHeight + 30 );
						graphics.drawString( String.valueOf( min ), 5, heightPx - 5 );
						String ms = String.valueOf( max );
						graphics.drawString( ms, widthPx - 5 - graphics.getFontMetrics().stringWidth( ms ),
								heightPx - 5 );

						if( maxCount != 0 ) {
							int segWidth = widthPx / quanta.length;
							for( int i2 = 0; i2 < quanta.length; i2++ ) {
								int x = i2 * segWidth;
								int h = quanta[i2] * histHeight / maxCount;
								graphics.setColor( new Color(
										(float) i2 / quanta.length, 1.0f - (float) i2 / quanta.length,
										0, 0.5f ) );
								graphics.fillRect( x, heightPx - h, segWidth, h );
							}
						}
					}
				} );

		diffWeightFilter.withListener( this::applyWeightFilter );
	}

	private void applyWeightFilter( Range range ) {
		int dr = maxDistance - minDistance;
		int min = (int) (minDistance + range.minimum() * dr);
		int max = (int) (minDistance + range.maximum() * dr);
		filter( "basis edge weight", flow -> {
			Integer bd = basisDistance.get( flow );
			if( bd != null ) {
				return min <= bd.intValue() && bd.intValue() <= max;
			}
			return true;
		} );
	}

	/**
	 * Adds an item filter
	 *
	 * @param source The source of the filter
	 * @param filter filter behaviour
	 */
	public void filter( String source, Predicate<Flow> filter ) {
		filters.put( source, filter );

		Predicate<Flow> combined = filters.values().stream().reduce( f -> true, Predicate::and );

		tree.filter( combined );
		graphNodes.entrySet().forEach( e -> {
			if( combined.test( e.getValue() ) ) {
				GraphView.removeUIClass( graph.graph().getNode( e.getKey() ), "filtered_out" );
			}
			else {
				GraphView.addUIClass( graph.graph().getNode( e.getKey() ), "filtered_out" );
			}
		} );
	}

	/**
	 * @return The combined component
	 */
	public JComponent view() {
		return component;
	}

	/**
	 * @return the graph component
	 */
	public GraphView graph() {
		return graph;
	}

	/**
	 * @return the tree component
	 */
	public JTree tree() {
		return tree;
	}

	/**
	 * Adds or updates nodes with a basis relationship to this tree
	 *
	 * @param flow The flow to add
	 * @return <code>this</code>
	 */
	public GraphTree with( Flow flow ) {
		// update the tree
		DefaultMutableTreeNode cn = treeNodes.get( flow );
		if( cn != null ) {
			((DefaultTreeModel) tree.getModel()).removeNodeFromParent( cn );
		}
		else {
			cn = new DefaultMutableTreeNode( flow );
			treeNodes.put( flow, cn );
		}

		DefaultMutableTreeNode pn = Optional.ofNullable( flow.basis() )
				.map( treeNodes::get )
				.orElse( root );
		pn.add( cn );
		((DefaultTreeModel) tree.getModel()).insertNodeInto( cn, pn, pn.getChildCount() - 1 );
		tree.expandPath( new TreePath( pn.getPath() ) );

		// update the graph
		String id = String.valueOf( flow.hashCode() );
		Node n = graph.graph().getNode( id );
		if( n != null ) {
			// already exists!
			n.enteringEdges()
					.filter( e -> "basis".equals( e.getAttribute( "ui.class" ) ) )
					.forEach( graph.graph()::removeEdge );
		}
		else {
			n = graph.graph().addNode( id );
			n.setAttribute( "ui.label", flow.meta().id() );
			graphNodes.put( n.getId(), flow );
		}
		GraphView.addUIClass( n, "floe" );

		addEdge( flow.basis(), flow, "basis" );

		// we can't guarantee that parent flows are added before the children, so
		// we might have to rejuggle things a bit
		for( Map.Entry<Flow, DefaultMutableTreeNode> e : treeNodes.entrySet().stream()
				.filter( e -> e.getKey().basis() == flow )
				.collect( toList() ) ) {
			e.getValue().removeFromParent();
			cn.add( e.getValue() );
			addEdge( flow, e.getKey(), "basis" );
		}

		return this;
	}

	/**
	 * Call this when you want the UI to open for business. The txn tree will be
	 * sorted and displayed
	 */
	public void prepForUsage() {
		sort( root );
		((DefaultTreeModel) tree.getModel()).reload();
		component.setDividerLocation( 0.2f );
	}

	private void sort( DefaultMutableTreeNode node ) {
		List<DefaultMutableTreeNode> children = new ArrayList<>();
		for( int i = 0; i < node.getChildCount(); i++ ) {
			children.add( (DefaultMutableTreeNode) node.getChildAt( i ) );
			sort( (DefaultMutableTreeNode) node.getChildAt( i ) );
		}
		node.removeAllChildren();

		Collections.sort( children, ( a, b ) -> {
			// we want to encourage deep trees, so sort nodes with many children higher so
			// they get more
			// attention
			int diff = b.getChildCount() - a.getChildCount();

			// fall back to alphabetical
			if( diff == 0
					&& a.getUserObject() instanceof Flow
					&& b.getUserObject() instanceof Flow ) {
				diff = ((Flow) a.getUserObject()).meta().id()
						.compareTo( ((Flow) b.getUserObject()).meta().id() );
			}
			return diff;
		} );

		children.forEach( node::add );
	}

	/**
	 * @param f A flow
	 * @return The graph node for that flow
	 */
	public Node getNode( Flow f ) {
		return graph.graph().getNode( String.valueOf( f.hashCode() ) );
	}

	/**
	 * Adds an edge in the graph between two flows
	 *
	 * @param from    The source flow
	 * @param to      The destination flow
	 * @param uiClass The edge type
	 * @return the new edge
	 */
	public Edge addEdge( Flow from, Flow to, String uiClass ) {
		if( from != null && to != null ) {
			String f = String.valueOf( from.hashCode() );
			String t = String.valueOf( to.hashCode() );
			if( graph.graph().getNode( f ) != null && graph.graph().getNode( t ) != null ) {
				String en = f + "-" + t;
				Edge e = graph.graph().getEdge( en );
				if( e == null ) {
					e = graph.graph().addEdge( en, f, t, true );
					e.setAttribute( "ui.class", uiClass );
				}
				else {
					GraphView.addUIClass( e, uiClass );
				}

				if( "basis".equals( uiClass ) ) {
					// also update the tree
					DefaultMutableTreeNode p = treeNodes.get( from );
					DefaultMutableTreeNode n = treeNodes.get( to );
					n.removeFromParent();
					p.add( n );

					Integer dd = derivationCost.applyAsInt( from, to );
					basisDistance.put( to, dd );
					minDistance = Math.min( minDistance, dd );
					maxDistance = Math.max( maxDistance, dd );
					applyWeightFilter( diffWeightFilter );

					e.setAttribute( "ui.label", dd );
				}
				return e;
			}
		}
		return null;
	}

	/**
	 * Removes an edge from the graph
	 *
	 * @param from    The source flow
	 * @param to      The destination flow
	 * @param uiClass The edge type
	 */
	public void removeEdge( Flow from, Flow to, String uiClass ) {
		if( from != null && to != null ) {
			String f = String.valueOf( from.hashCode() );
			String t = String.valueOf( to.hashCode() );
			if( graph.graph().getNode( f ) != null && graph.graph().getNode( t ) != null ) {
				String en = f + "-" + t;
				Edge e = graph.graph().getEdge( en );
				if( e != null ) {
					String c = GraphView.removeUIClass( e, uiClass );
					if( c.isEmpty() || "selected".equals( c ) ) {
						graph.graph().removeEdge( e );
					}
					if( "basis".equals( uiClass ) ) {
						// also update the tree
						DefaultMutableTreeNode n = treeNodes.get( to );
						if( n != null ) {
							n.removeFromParent();
							root.add( n );
						}

						basisDistance.remove( to );
						IntSummaryStatistics iss = basisDistance.values().stream()
								.mapToInt( Integer::intValue )
								.summaryStatistics();
						minDistance = iss.getMin();
						maxDistance = iss.getMax();
						applyWeightFilter( diffWeightFilter );
					}
				}
			}
		}
	}

	private static class FlowTree extends JTree {

		private static final long serialVersionUID = 1L;

		private Predicate<Flow> filter = t -> true;

		public FlowTree( DefaultMutableTreeNode root ) {
			super( root, false );
			getSelectionModel().setSelectionMode( TreeSelectionModel.SINGLE_TREE_SELECTION );
		}

		public void filter( Predicate<Flow> f ) {
			filter = f;
			repaint();
		}

		@Override
		public String convertValueToText( Object value, boolean selected, boolean expanded,
				boolean leaf, int row, boolean hasFocus ) {
			return Optional.ofNullable( value )
					.map( node -> ((DefaultMutableTreeNode) node).getUserObject() )
					.filter( Flow.class::isInstance )
					.map( t -> (Flow) t )
					.map( t -> {
						boolean passesFilter = filter.test( t );
						String style = passesFilter
								? "div.name { font-weight: bold; } div.tags { color: gray; }"
								: "div.name { font-style: italic; color: #DCDCDC; } div.tags { color: #F5F5F5; }";
						return String.format( ""
								+ "<html>\n"
								+ "  <head>\n"
								+ "    <style>\n"
								+ "%s"
								+ "    </style>\n"
								+ "  </head>\n"
								+ "  <body>\n"
								+ "    <div class=\"name\">%s</div>\n"
								+ "    <div class=\"tags\">%s</div>\n"
								+ "  </body>\n"
								+ "</html>",
								style,
								t.meta().description(),
								t.meta().tags().stream()
										.sorted()
										.collect( Collectors.joining( " " ) ) );
					} )
					.orElseGet(
							() -> super.convertValueToText( value, selected, expanded, leaf, row, hasFocus ) );
		}
	}

	@Override
	public void force( Flow txn ) {
		selectionListener.accept( txn );
		if( txn != null ) {
			TreePath treePath = new TreePath( treeNodes.get( txn ).getPath() );
			tree.setSelectionPath( treePath );
			tree.scrollPathToVisible( treePath );
			// we've hidden the horizontal scrollbar, so keep the viewport stuck to the left
			// of the tree
			((JScrollPane) tree.getParent().getParent()).getHorizontalScrollBar().setValue( 0 );
			graph.setSelected( String.valueOf( txn.hashCode() ) );
		}
		else {
			tree.clearSelection();
			graph.setSelected( null );
		}
	}

}
