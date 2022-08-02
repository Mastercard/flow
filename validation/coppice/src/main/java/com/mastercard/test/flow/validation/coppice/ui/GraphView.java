/**
 * Copyright (c) 2021 Mastercard. All rights reserved.
 */

package com.mastercard.test.flow.validation.coppice.ui;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.awt.event.MouseEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graphstream.graph.Element;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.ui.geom.Point2;
import org.graphstream.ui.geom.Point3;
import org.graphstream.ui.graphicGraph.GraphicGraph;
import org.graphstream.ui.layout.Layout;
import org.graphstream.ui.layout.springbox.implementations.SpringBox;
import org.graphstream.ui.swing_viewer.DefaultView;
import org.graphstream.ui.swing_viewer.SwingViewer;
import org.graphstream.ui.swing_viewer.util.DefaultMouseManager;
import org.graphstream.ui.view.Viewer;
import org.graphstream.ui.view.camera.Camera;
import org.graphstream.ui.view.util.InteractiveElement;

/**
 * Provides a swing component that displays a graph, dropping the default
 * graphstream mouse interaction behaviour in favour of zoom and pan controls
 */
public class GraphView {

	private final Graph graph;
	private final GraphicGraph graphicGraph;
	private final DefaultView view;
	private final Layout layout = new SpringBox();

	private final List<SelectionListener> listeners = new ArrayList<>();
	private Node selected = null;

	/**
	 * @param name graph name
	 */
	public GraphView( String name ) {
		graph = new MultiGraph( name );
		graph.setAttribute( "ui.quality" );
		graph.setAttribute( "ui.antialias" );
		graph.setAttribute( "ui.stylesheet", stylesheet( "style.css" ) );

		SwingViewer viewer = new SwingViewer( graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD );
		viewer.enableAutoLayout( layout );

		graphicGraph = viewer.getGraphicGraph();

		view = (DefaultView) viewer.addDefaultView( false );
		view.setMouseManager( new PanDragManager( this::setSelected ) );

		// mousewheeel zooming
		Camera cam = view.getCamera();
		cam.setViewPercent( 1 );
		view.addMouseWheelListener( e -> {
			int i = e.getWheelRotation();
			double factor = Math.pow( 1.25, i );
			double zoom = cam.getViewPercent() * factor;
			Point2 pxCenter = cam.transformGuToPx( cam.getViewCenter().x, cam.getViewCenter().y, 0 );
			Point3 guClicked = cam.transformPxToGu( e.getX(), e.getY() );
			double newRatioPx2Gu = cam.getMetrics().ratioPx2Gu / factor;
			double x = guClicked.x + (pxCenter.x - e.getX()) / newRatioPx2Gu;
			double y = guClicked.y - (pxCenter.y - e.getY()) / newRatioPx2Gu;
			cam.setViewCenter( x, y, 0 );
			cam.setViewPercent( zoom );
			if( zoom > 1 ) {
				cam.resetView();
			}
		} );
	}

	/**
	 * @param b <code>true</code> to force the graph to animate regardless of its
	 *          comfort, <code>false</code> to allow it to rest when comfortable
	 *          enough
	 */
	public void forceAnimation( boolean b ) {
		layout.setStabilizationLimit( b ? 0 : 0.9 );
	}

	/**
	 * @return The viewable graph
	 */
	public GraphicGraph graphicGraph() {
		return graphicGraph;
	}

	/**
	 * @param l An object interested in node selection
	 * @return this
	 */
	public GraphView withSelectionListener( SelectionListener l ) {
		listeners.add( l );
		return this;
	}

	/**
	 * Forces node selection
	 *
	 * @param id node id
	 * @return <code>this</code>
	 */
	public GraphView setSelected( String id ) {

		if( selected != null ) {
			removeUIClass( selected, "selected" );
			selected.edges().forEach( e -> removeUIClass( e, "selected" ) );
		}

		selected = graph().getNode( id );

		if( selected != null ) {
			addUIClass( selected, "selected" );
			selected.edges().forEach( e -> addUIClass( e, "selected" ) );
			listeners.forEach( l -> l.selected( selected.getId() ) );
		}
		else {
			listeners.forEach( l -> l.selected( null ) );
		}

		return this;
	}

	/**
	 * Adds a UI class to an element
	 *
	 * @param e        The element to add to
	 * @param cssClass The class
	 * @return The new set of UI classes on the element
	 */
	public static String addUIClass( Element e, String cssClass ) {
		return updateUIClass( e, c -> {
			while( c.contains( cssClass ) ) {
				c.remove( cssClass );
			}
			c.addFirst( cssClass );
		} );
	}

	/**
	 * Removes a UI class from an element
	 *
	 * @param e        The element to remove from
	 * @param cssClass The class
	 * @return The new set of UI classes on the element
	 */
	public static String removeUIClass( Element e, String cssClass ) {
		return updateUIClass( e, c -> {
			while( c.contains( cssClass ) ) {
				c.remove( cssClass );
			}
		} );
	}

	private static String updateUIClass( Element e, Consumer<Deque<String>> op ) {
		Deque<String> classes = Optional.ofNullable( e.getAttribute( "ui.class" ) )
				.map( String::valueOf )
				.map( c -> Stream.of( c.split( "," ) )
						.map( String::trim )
						.collect( Collectors.toCollection( ArrayDeque::new ) ) )
				.orElseGet( ArrayDeque::new );
		op.accept( classes );
		String nc = classes.stream().collect( Collectors.joining( "," ) );
		e.setAttribute( "ui.class", nc );
		return nc;
	}

	/**
	 * @return The graph that we're viewing
	 */
	public Graph graph() {
		return graph;
	}

	/**
	 * @return The view component
	 */
	public DefaultView view() {
		return view;
	}

	/**
	 * Interface for those interested in node selection
	 */
	public interface SelectionListener {

		/**
		 * Called when the selected node changes
		 *
		 * @param id the ID of the selected node, or <code>null</code> if nothing is
		 *           selected
		 */
		void selected( String id );
	}

	/**
	 * Ditches the selection bits of {@link DefaultMouseManager} in favour of
	 * drag-panning
	 */
	private static class PanDragManager extends DefaultMouseManager {

		private final Consumer<String> selection;
		private float x0;
		private float y0;

		PanDragManager( Consumer<String> selection ) {
			this.selection = selection;
		}

		@Override
		public void mousePressed( MouseEvent event ) {
			curElement = view.findGraphicElementAt( EnumSet.of( InteractiveElement.NODE ),
					event.getX(), event.getY() );

			if( curElement != null ) {
				view.freezeElement( curElement, true );
			}

			x0 = x1 = event.getX();
			y0 = y1 = event.getY();

		}

		@Override
		public void mouseDragged( MouseEvent event ) {
			if( curElement != null ) {
				elementMoving( curElement, event );
			}
			else {
				// continue pan
				Camera cam = view.getCamera();
				Point3 panStart = cam.transformPxToGu( x1, y1 );
				Point3 panEnd = cam.transformPxToGu( event.getX(), event.getY() );
				Point3 current = cam.getViewCenter();
				cam.setViewCenter(
						current.x - (panEnd.x - panStart.x),
						current.y - (panEnd.y - panStart.y),
						current.z );
				x1 = event.getX();
				y1 = event.getY();
			}
		}

		@Override
		public void mouseReleased( MouseEvent event ) {
			if( curElement != null ) {
				view.freezeElement( curElement, false );

				if( Math.abs( x0 - event.getX() ) < 10 && Math.abs( y0 - event.getY() ) < 10 ) {
					selection.accept( curElement.getId() );
				}

				curElement = null;
			}
			else if( Math.abs( x0 - event.getX() ) < 10 && Math.abs( y0 - event.getY() ) < 10 ) {
				selection.accept( null );
			}
		}
	}

	private static String stylesheet( String name ) {
		try(
				ByteArrayOutputStream data = new ByteArrayOutputStream();
				InputStream resource = GraphView.class.getClassLoader()
						.getResourceAsStream( name ); ) {
			byte[] buff = new byte[1024];
			int read = 0;
			while( (read = resource.read( buff )) >= 0 ) {
				data.write( buff, 0, read );
			}
			return new String( data.toByteArray(), UTF_8 );
		}
		catch( IOException ioe ) {
			throw new UncheckedIOException( ioe );
		}
	}
}
