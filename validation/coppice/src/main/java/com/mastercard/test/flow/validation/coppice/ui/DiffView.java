/**
 * Copyright (c) 2021 Mastercard. All rights reserved.
 */

package com.mastercard.test.flow.validation.coppice.ui;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Optional;
import java.util.function.Function;

import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.border.TitledBorder;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.validation.coppice.Coppice;
import com.mastercard.test.flow.validation.coppice.Diff;

/**
 * Displays the difference between two flows
 */
public class DiffView {

	private final Function<Flow, String> flatten;
	private Flow source;
	private Flow destination;

	private final JPanel view = new JPanel( new BorderLayout() );

	private final JButton sourceName = new JButton();
	private final JButton swap = new JButton( "⇄" );
	private final JButton destinationName = new JButton();
	private final JTextPane display = new JTextPane();

	/**
	 * @param coppice   The parent instance
	 * @param selection How flow selection is managed
	 * @param src       The first half of the comparison
	 */
	public DiffView( Coppice coppice, SelectionManager selection, Flow src ) {
		source = src;
		flatten = coppice.diffDistance()::stringify;

		sourceName.addActionListener( e -> {
			if( source != null ) {
				selection.update( source );
				coppice.view();
			}
		} );
		swap.setEnabled( false );
		swap.addActionListener( e -> {
			if( source != null && destination != null ) {
				Flow tmp = destination;
				destination = source;
				source = tmp;
				refresh();
			}
		} );
		destinationName.setEnabled( false );
		destinationName.addActionListener( e -> {
			if( destination != null ) {
				selection.update( destination );
				coppice.view();
			}
		} );

		JPanel north = new JPanel( new GridBagLayout() );
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridy = 0;
		gbc.fill = GridBagConstraints.BOTH;

		gbc.gridx = 0;
		gbc.weightx = 1;
		north.add( sourceName, gbc );
		gbc.gridx++;
		gbc.weightx = 0;
		north.add( swap, gbc );
		gbc.gridx++;
		gbc.weightx = 1;
		north.add( destinationName, gbc );
		north.setBorder( new TitledBorder( "Comparing" ) );

		display.setEditable( false );
		display.setContentType( "text/html" );
		display.putClientProperty( JEditorPane.HONOR_DISPLAY_PROPERTIES, true );

		view.add( north, BorderLayout.NORTH );
		view.add( new JScrollPane( display ), BorderLayout.CENTER );

		refresh();
	}

	/**
	 * Ensures that the diff view is up-to-date
	 */
	protected void refresh() {
		if( sourceName != null ) {
			sourceName.setText( source.meta().description() );
		}
		if( swap != null ) {
			swap.setEnabled( destination != null );
		}
		if( destinationName != null ) {
			destinationName.setEnabled( destination != null );
			destinationName.setText( Optional.ofNullable( destination )
					.map( t -> t.meta().description() )
					.orElse( "Drop flow here" ) );
		}

		if( destination != null ) {
			display.setText( Diff.diffHTML( flatten.apply( source ), flatten.apply( destination ) ) );
		}
		else {
			display.setText( ""
					+ "<html><pre>"
					+ Diff.escapeHTML( flatten.apply( source ) )
					+ "</pre></html>" );
		}

	}

	/**
	 * Sets the second half of the comparison
	 *
	 * @param dst The other {@link Flow}
	 * @return <code>this</code>
	 */
	public DiffView destination( Flow dst ) {
		destination = dst;
		refresh();
		return this;
	}

	/**
	 * @return The diff component
	 */
	public JPanel view() {
		return view;
	}

}
