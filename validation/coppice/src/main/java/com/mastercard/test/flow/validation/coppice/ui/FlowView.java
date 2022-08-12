/**
 * Copyright (c) 2021 Mastercard. All rights reserved.
 */

package com.mastercard.test.flow.validation.coppice.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.Collection;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.util.Flows;
import com.mastercard.test.flow.util.Transmission;

/**
 * Displays the details of a flow
 */
public class FlowView {

	/**
	 * The viewed flow
	 */
	protected Flow flow;
	/**
	 * Transmissions of the current flow
	 */
	protected List<Transmission> transmissions;

	private final JSlider slider;

	private final JPanel panel = new JPanel( new BorderLayout() );

	private final JList<String> tagList = new JList<>();

	/**
	 * @param flow The flow to view
	 */
	public FlowView( Flow flow ) {
		this( flow, true );
	}

	/**
	 * @param flow      The flow to view
	 * @param showTrace whether to show the flow trace
	 */
	protected FlowView( Flow flow, boolean showTrace ) {
		this.flow = flow;
		slider = new JSlider( SwingConstants.VERTICAL, 0, 1, 0 );
		slider.setInverted( true );
		slider.setPaintTrack( false );
		slider.setPaintTicks( true );
		slider.setMajorTickSpacing( 1 );

		JScrollPane tagPanel = new JScrollPane( tagList );
		tagPanel.setBorder( new TitledBorder( "Tags" ) );

		JSplitPane split = new JSplitPane( JSplitPane.HORIZONTAL_SPLIT );
		split.setOneTouchExpandable( true );
		split.setContinuousLayout( true );
		split.add( tagPanel, JSplitPane.LEFT );
		split.add( messages(), JSplitPane.RIGHT );
		split.setDividerLocation( 0.2 );

		if( showTrace ) {
			JTextField ct = new JTextField( flow.meta().trace() );
			ct.setEditable( false );
			ct.setHorizontalAlignment( SwingConstants.CENTER );
			panel.add( ct, BorderLayout.NORTH );
		}

		panel.add( split, BorderLayout.CENTER );

		refresh();
	}

	/**
	 * @return The viewed flow
	 */
	public Flow flow() {
		return flow;
	}

	/**
	 * @return The view component
	 */
	public JComponent view() {
		return panel;
	}

	private final void refreshTags() {
		Vector<String> tags = new Vector<>( getTags() );
		tagList.setListData( tags );
	}

	/**
	 * @return flow tags
	 */
	protected Collection<String> getTags() {
		return flow.meta().tags();
	}

	/**
	 * Ensures that the view is up-to-date
	 */
	protected void refresh() {
		refreshTags();
		transmissions = Flows.transmissions( flow );

		slider.setMaximum( transmissions.size() - 1 );
		Stream.of( slider.getChangeListeners() )
				.forEach( l -> l.stateChanged( new ChangeEvent( slider ) ) );
	}

	private final JComponent messages() {

		JLabel name = new JLabel();
		JLabel tags = new JLabel();
		tags.setForeground( Color.GRAY );

		JTextPane display = new JTextPane();
		display.setEditable( false );
		display.setContentType( "text/html" );

		slider.addChangeListener( e -> {
			Transmission transmission = transmissions.get( slider.getValue() );
			name.setText( transmission.toUntaggedString() );
			tags.setText( transmission.source().tags().stream()
					.collect( Collectors.joining( " " ) ) );
			display.setText( messageContent( slider.getValue() ) );
			display.setCaretPosition( 0 ); // scroll to top
		} );

		JPanel title = new JPanel( new FlowLayout( FlowLayout.LEFT ) );
		title.add( name );
		title.add( tags );

		JPanel msg = new JPanel( new BorderLayout() );
		msg.add( title, BorderLayout.NORTH );
		msg.add( new JScrollPane( display ), BorderLayout.CENTER );

		JPanel main = new JPanel( new BorderLayout() );
		main.setBorder( new TitledBorder( "Messages" ) );
		main.add( slider, BorderLayout.WEST );
		main.add( msg, BorderLayout.CENTER );
		return main;
	}

	private String messageContent( int transmissionIndex ) {
		return String.format( "<html><body><pre>%s</pre></body></html>",
				transmissions.get( transmissionIndex ).message().assertable() );
	}

}
