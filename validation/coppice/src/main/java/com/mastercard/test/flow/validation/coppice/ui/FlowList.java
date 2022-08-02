/**
 * Copyright (c) 2019 Mastercard. All rights reserved.
 */

package com.mastercard.test.flow.validation.coppice.ui;

import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;

/**
 * Holds the list of {@link Flow}s from the {@link Model}, displaying it with a
 * filter control
 */
public class FlowList extends JPanel implements SelectionManager.Client {

	private static final long serialVersionUID = 1L;

	private final JTextField filter = new JTextField();
	/**
	 * A list of things that are interested in filter changes
	 */
	final List<Consumer<Predicate<Flow>>> filterListeners = new ArrayList<>();
	private final List<Flow> allTransactions = new ArrayList<>();
	private final JList<Flow> list = new JList<>();

	/**
	 * @param opener What to do when a transaction is clicked
	 */
	public FlowList( Consumer<Flow> opener ) {
		filter.setName( "txn_filter" );
		filter.setHorizontalAlignment( SwingConstants.CENTER );
		filter.addKeyListener( new KeyAdapter() {

			@Override
			public void keyReleased( KeyEvent e ) {
				Predicate<Flow> pred = refresh();
				filterListeners.forEach( l -> l.accept( pred ) );
			}
		} );

		list.setName( "txn_list" );
		list.setCellRenderer( new TaggedItemRenderer<>(
				f -> f.meta().description(),
				f -> f.meta().tags() ) );
		list.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
		list.addListSelectionListener( e -> opener.accept( list.getSelectedValue() ) );

		setName( "basis_panel" );
		setBorder( new TitledBorder( "Flows" ) );
		setLayout( new BorderLayout() );
		add( filter, BorderLayout.NORTH );
		add( new JScrollPane( list,
				ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER ),
				BorderLayout.CENTER );
	}

	/**
	 * @param flw a flow
	 * @return this
	 */
	public FlowList with( Flow flw ) {
		SwingUtilities.invokeLater( () -> {
			allTransactions.add( flw );
			refresh();
		} );
		return this;
	}

	/**
	 * @param l a listener
	 * @return <code>this</code>
	 */
	public FlowList withFilterListener( Consumer<Predicate<Flow>> l ) {
		filterListeners.add( l );
		return this;
	}

	/**
	 * Sorts the flow list on ID
	 */
	public void sort() {
		SwingUtilities.invokeLater( () -> {
			allTransactions.sort( Comparator.comparing( f -> f.meta().id() ) );
			refresh();
		} );
	}

	/**
	 * @return The list component
	 */
	public JList<Flow> getList() {
		return list;
	}

	/**
	 * Applies the name filter to the list
	 *
	 * @return The filter behaviour
	 */
	Predicate<Flow> refresh() {
		Set<String> includes = new TreeSet<>();
		Set<String> excludes = new TreeSet<>();

		for( String s : filter.getText().split( " " ) ) {
			if( s.startsWith( "-" ) ) {
				if( s.length() > 1 ) {
					excludes.add( s.substring( 1 ).toLowerCase() );
				}
			}
			else {
				includes.add( s.toLowerCase() );
			}
		}
		Predicate<Flow> pred = flow -> includes.stream()
				.allMatch( flow.meta().id().toLowerCase()::contains )
				&& excludes.stream()
						.noneMatch( flow.meta().id().toLowerCase()::contains );
		Vector<Flow> filtered = allTransactions.stream()
				.filter( pred )
				.collect( Collectors.toCollection( Vector::new ) );
		list.setListData( filtered );
		return pred;
	}

	@Override
	public void force( Flow txn ) {
		if( txn != null ) {
			list.setSelectedValue( txn, true );
		}
		else {
			list.clearSelection();
		}
	}
}
