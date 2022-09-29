
package com.mastercard.test.flow.assrt.filter.gui;

import static com.mastercard.test.flow.assrt.filter.gui.FilterGui.titled;
import static java.awt.GridBagConstraints.BOTH;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.TitledBorder;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.filter.Filter;

/**
 * Interface widgets for the final flow selction step
 */
class FlowPanel extends JPanel {

	private static final String RESET = "Reset";
	private static final String RESET_FLOW_SELECTION = "Reset flow selection";

	private static final long serialVersionUID = 1L;

	private final transient Filter filter;

	/**
	 * Map from list content to flow
	 */
	final Map<String, IndexedFlow> listedFlows = new HashMap<>();
	private final JList<String> disabledFlows = new JList<>();
	private final JList<String> enabledFlows = new JList<>();

	private final JButton include = new JButton( "✔" );
	private final JButton exclude = new JButton( "✖" );
	private final JButton reset = new JButton( RESET );

	/**
	 * Flows whose descriptions match this are highlighted in the lists
	 */
	Pattern flowFilter = Pattern.compile( "" );

	private final transient Comparator<String> order = ( a, b ) -> {
		IndexedFlow af = listedFlows.get( a );
		IndexedFlow bf = listedFlows.get( b );
		boolean am = flowFilter.matcher( af.flow.meta().description() ).find();
		boolean bm = flowFilter.matcher( bf.flow.meta().description() ).find();
		int d = 0;
		if( am && !bm ) {
			d = -1;
		}
		if( d == 0 && !am && bm ) {
			d = 1;
		}
		if( d == 0 ) {
			d = af.flow.meta().id().compareTo( bf.flow.meta().id() );
		}
		if( d == 0 ) {
			d = af.index - bf.index;
		}
		return d;
	};
	private transient Runnable updateListener = () -> {
		// no-op
	};

	/**
	 * @param filter The filter to manipulate
	 */
	FlowPanel( Filter filter ) {
		this.filter = filter;
		setLayout( new GridBagLayout() );
		setBorder( new TitledBorder( "Flows" ) );

		enabledFlows.setBackground( new Color( 0.8f, 1, 0.8f ) );
		enabledFlows.setName( "enabled_flow_list" );
		disabledFlows.setBackground( new Color( 1, 0.8f, 0.8f ) );
		disabledFlows.setName( "disabled_flow_list" );
		FlowRenderer renderer = new FlowRenderer();
		Stream.of( disabledFlows, enabledFlows )
				.forEach( list -> {
					list.setPrototypeCellValue( "typical flow width" );
					list.setCellRenderer( renderer );
				} );

		include.setName( "include_button" );
		exclude.setName( "exclude_button" );
		reset.setName( "reset_flows_button" );
		Stream.of( include, exclude )
				.forEach( button -> {
					button.setMargin( new Insets( 0, 0, 0, 0 ) );
					button.setEnabled( false );
				} );

		// list selection is mutually-exclusive. Buttons activate when an adjacent list
		// has a selection
		AtomicBoolean clearing = new AtomicBoolean( false );
		enabledFlows.addListSelectionListener( lse -> {
			if( !clearing.get() ) {
				clearing.set( true );
				disabledFlows.clearSelection();
				clearing.set( false );
			}
			include.setEnabled( !enabledFlows.isSelectionEmpty() || !disabledFlows.isSelectionEmpty() );
			exclude.setEnabled( !enabledFlows.isSelectionEmpty() );
		} );
		disabledFlows.addListSelectionListener( lse -> {
			if( !clearing.get() ) {
				clearing.set( true );
				enabledFlows.clearSelection();
				clearing.set( false );
			}
			include.setEnabled( !enabledFlows.isSelectionEmpty() || !disabledFlows.isSelectionEmpty() );
			exclude.setEnabled( !enabledFlows.isSelectionEmpty() );
		} );

		include.setToolTipText( "Enable selected flows" );
		include.addActionListener( ac -> {
			if( !enabledFlows.isSelectionEmpty() ) {
				filter.indices( enabledFlows.getSelectedValuesList().stream()
						.map( listedFlows::get )
						.map( iti -> iti.index )
						.collect( toSet() ) );
			}
			if( !disabledFlows.isSelectionEmpty() ) {
				Set<Integer> s = filter.indices();
				disabledFlows.getSelectedValuesList().stream()
						.map( listedFlows::get )
						.forEach( iti -> s.add( iti.index ) );
				filter.indices( s );
			}
			updateListener.run();
			refresh();
		} );

		exclude.setToolTipText( "Disable selected flows" );
		exclude.addActionListener( ac -> {
			if( !enabledFlows.isSelectionEmpty() ) {
				Set<Integer> s = filter.indices();
				if( s.isEmpty() ) {
					IntStream.range( 0, (int) filter.flows().count() )
							.forEach( s::add );
				}

				enabledFlows.getSelectedValuesList().stream()
						.map( listedFlows::get )
						.forEach( iflow -> s.remove( iflow.index ) );

				filter.indices( s );
			}
			updateListener.run();
			refresh();
		} );

		reset.setToolTipText( RESET_FLOW_SELECTION );
		reset.addActionListener( ac -> {
			if( filter.indices().isEmpty() && Filter.historicReport() != null ) {
				filter.loadFailureIndices();
			}
			else {
				filter.indices( Collections.emptySet() );
			}
			updateListener.run();
			refresh();
		} );

		add( titled( "Disabled", new JScrollPane( disabledFlows ) ),
				new GBCB()
						.fill( BOTH )
						.gridx( 0 ).gridy( 0 )
						.gridwidth( 1 ).gridheight( 2 )
						.weightx( 1 ).weighty( 1 )
						.get() );

		add( include,
				new GBCB()
						.fill( BOTH )
						.gridx( 1 ).gridy( 0 )
						.gridwidth( 1 ).gridheight( 1 )
						.weightx( 0 ).weighty( 1 )
						.get() );

		add( exclude,
				new GBCB()
						.fill( BOTH )
						.gridx( 1 ).gridy( 1 )
						.gridwidth( 1 ).gridheight( 1 )
						.weightx( 0 ).weighty( 1 )
						.get() );

		add( titled( "Enabled", new JScrollPane( enabledFlows ) ),
				new GBCB()
						.fill( BOTH )
						.gridx( 2 ).gridy( 0 )
						.gridwidth( 1 ).gridheight( 2 )
						.weightx( 1 ).weighty( 1 )
						.get() );

		add( reset, new GBCB()
				.fill( BOTH )
				.gridx( 0 ).gridy( 2 )
				.gridwidth( 3 ).gridheight( 1 )
				.weightx( 1 ).weighty( 0 )
				.get() );

	}

	/**
	 * @param l Called whenever the flow selection is changed
	 */
	void withListener( Runnable l ) {
		updateListener = l;
	}

	/**
	 * Updates the flow highlighting
	 *
	 * @param text The new highlight pattern
	 */
	void filter( Pattern text ) {
		flowFilter = text;
		refresh();
	}

	/**
	 * Ensures the controls reflect the state of the filter
	 */
	void refresh() {
		List<Flow> all = filter.taggedFlows();
		Set<Integer> indices = filter.indices();
		Set<String> includedTags = filter.includedTags();

		Vector<String> enabledItems = new Vector<>();
		Vector<String> disabledItems = new Vector<>();

		int index = 0;
		for( Flow flow : all ) {
			String rendered = String.format(
					"<html>%s <span style=\"color:gray\">%s</span></html>",
					flow.meta().description(),
					flow.meta().tags().stream()
							.filter( t -> !includedTags.contains( t ) )
							.collect( joining( " " ) ) )
					// We're using swing's html support to render tags in gray, but the renderer
					// will also try
					// to wrap lines. We want our items to only take one line, so...
					.replace( " ", "&nbsp;" );
			listedFlows.put( rendered, new IndexedFlow( index, flow ) );

			if( indices.isEmpty() || indices.contains( new IndexedFlow( index, flow ).index ) ) {
				enabledItems.add( rendered );
			}
			else {
				disabledItems.add( rendered );
			}

			index++;
		}

		Collections.sort( enabledItems, order );
		Collections.sort( disabledItems, order );

		enabledFlows.setListData( enabledItems );
		disabledFlows.setListData( disabledItems );

		if( indices.isEmpty() ) {
			if( Filter.historicReport() != null ) {
				reset.setEnabled( true );
				reset.setText( "Fails" );
				reset.setToolTipText( "Select historic failures" );
			}
			else {
				reset.setEnabled( false );
				reset.setText( RESET );
				reset.setToolTipText( RESET_FLOW_SELECTION );
			}
		}
		else {
			reset.setEnabled( true );
			reset.setText( RESET );
			reset.setToolTipText( RESET_FLOW_SELECTION );
		}

		updateListener.run();
	}

	private class IndexedFlow {

		public final int index;
		public final Flow flow;

		IndexedFlow( int index, Flow flow ) {
			this.index = index;
			this.flow = flow;
		}
	}

	/**
	 * Handles flow highlighting
	 */
	class FlowRenderer extends DefaultListCellRenderer {

		private static final long serialVersionUID = 1L;

		@Override
		public Component getListCellRendererComponent( JList<?> list, Object value, int index,
				boolean isSelected, boolean cellHasFocus ) {
			Component cell = super.getListCellRendererComponent( list, value, index, isSelected,
					cellHasFocus );

			IndexedFlow f = listedFlows.get( value );
			if( f != null && !flowFilter.matcher( f.flow.meta().description() ).find() ) {
				cell.setFont( cell.getFont().deriveFont( Font.ITALIC ) );
				cell.setForeground( Color.GRAY );
			}

			return cell;
		}
	}
}
