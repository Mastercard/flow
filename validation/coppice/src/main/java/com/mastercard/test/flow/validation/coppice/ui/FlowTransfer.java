/**
 * Copyright (c) 2021 Mastercard. All rights reserved.
 */

package com.mastercard.test.flow.validation.coppice.ui;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JTree;
import javax.swing.TransferHandler;

import com.mastercard.test.flow.Flow;

/**
 * Manages dragging of {@link Flow}s from one component to another
 */
public class FlowTransfer {

	private FlowTransfer() {
		// no instances
	}

	/**
	 * The things that can be dragged from
	 */
	static final Map<JComponent, Supplier<Flow>> sources = new HashMap<>();

	/**
	 * The things that can be dragged to
	 */
	static final Map<JComponent, Consumer<Flow>> sinks = new HashMap<>();

	/**
	 * Registers a drag source of flows
	 *
	 * @param list The list that holds flows
	 * @param get  how to get the dragged flow
	 */
	public static void registerSource( JList<?> list, Supplier<Flow> get ) {
		sources.put( list, get );
		list.setDragEnabled( true );
		list.setTransferHandler( EXPORT );
	}

	/**
	 * Registers a drag source of flows
	 *
	 * @param tree The tree that holds flows
	 * @param get  how to get the dragged flow
	 */
	public static void registerSource( JTree tree, Supplier<Flow> get ) {
		sources.put( tree, get );
		tree.setDragEnabled( true );
		tree.setTransferHandler( EXPORT );
	}

	private static final TransferHandler EXPORT = new TransferHandler( "flow" ) {

		private static final long serialVersionUID = 1L;

		@Override
		public int getSourceActions( JComponent c ) {
			return TransferHandler.LINK;
		}

		@Override
		protected Transferable createTransferable( JComponent c ) {
			Flow txn = sources.get( c ).get();
			DataFlavor flava = flavour();

			return new Transferable() {

				@Override
				public boolean isDataFlavorSupported( DataFlavor df ) {
					return df.equals( flava );
				}

				@Override
				public DataFlavor[] getTransferDataFlavors() {
					return new DataFlavor[] { flava };
				}

				@Override
				public Object getTransferData( DataFlavor df )
						throws UnsupportedFlavorException, IOException {
					return txn;
				}
			};
		}
	};

	/**
	 * Registers a drop target for dragged flows
	 *
	 * @param c   The component
	 * @param set What to do with dropped flows
	 */
	public static void registerSink( JComponent c,
			Consumer<Flow> set ) {
		sinks.put( c, set );
		DataFlavor flava = flavour();
		c.setTransferHandler( new TransferHandler( "flow" ) {

			private static final long serialVersionUID = 1L;

			@Override
			public boolean canImport( JComponent cmp, DataFlavor[] dfs ) {
				return Stream.of( dfs ).anyMatch( df -> df.equals( flava ) );
			}

			@Override
			public boolean importData( JComponent cmp, Transferable t ) {
				try {
					sinks.get( cmp ).accept( (Flow) t.getTransferData( flava ) );
				}
				catch( Exception e ) {
					throw new IllegalStateException( e );
				}
				return true;
			}
		} );
	}

	/**
	 * @return The dnd-flavour of flows
	 */
	static DataFlavor flavour() {
		try {
			return new DataFlavor(
					DataFlavor.javaJVMLocalObjectMimeType + ";class=" + Flow.class.getName() );
		}
		catch( ClassNotFoundException e ) {
			throw new IllegalStateException( e );
		}
	}

}
