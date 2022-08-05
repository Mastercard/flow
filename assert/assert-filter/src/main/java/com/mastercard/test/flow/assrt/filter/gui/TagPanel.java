package com.mastercard.test.flow.assrt.filter.gui;

import static com.mastercard.test.flow.assrt.filter.gui.FilterGui.titled;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.TitledBorder;

import com.mastercard.test.flow.assrt.filter.Filter;

/**
 * Provides controls for the tag inclusion and exclusion sets of a
 * {@link Filter}
 */
class TagPanel extends JPanel {

	private static final String SWAP_SELECTED_TAGS = "Swap selected tags";

	private static final long serialVersionUID = 1L;

	private final transient Filter filter;

	private final JList<String> availableTags = new JList<>();
	private final JList<String> includedTags = new JList<>();
	private final JList<String> excludedTags = new JList<>();

	private final JButton avin = new JButton( "⮂" );
	private final JButton avex = new JButton( "⮂" );
	private final JButton inex = new JButton( "⮁" );
	private final JButton reset = new JButton( "Reset" );

	/**
	 * Tags matching this pattern are highlighted in the lists
	 */
	Pattern tagFilter = Pattern.compile( "" );

	/**
	 * Tags matching this predicate are highlighted in the lists
	 */
	transient Predicate<String> tagLimit = t -> true;

	private transient Runnable updateListener = () -> {
		// no-op
	};

	private final transient Comparator<String> order = ( a, b ) -> {
		boolean am = tagFilter.matcher( a ).find() && tagLimit.test( a );
		boolean bm = tagFilter.matcher( b ).find() && tagLimit.test( b );
		if( am && !bm ) {
			return -1;
		}
		if( !am && bm ) {
			return 1;
		}
		return a.compareTo( b );
	};

	/**
	 * @param filter The filter to control
	 */
	TagPanel( Filter filter ) {
		this.filter = filter;
		setLayout( new GridBagLayout() );
		setBorder( new TitledBorder( "Tags" ) );

		avin.setName( "avin_button" );
		avex.setName( "avex_button" );
		inex.setName( "inex_button" );
		reset.setName( "reset_tags_button" );
		Stream.of( avin, avex, inex )
				.forEach( button -> {
					button.setMargin( new Insets( 0, 0, 0, 0 ) );
					button.setEnabled( false );
				} );

		availableTags.setName( "available_tag_list" );
		includedTags.setBackground( new Color( 0.8f, 1, 0.8f ) );
		includedTags.setName( "included_tag_list" );
		excludedTags.setBackground( new Color( 1, 0.8f, 0.8f ) );
		excludedTags.setName( "excluded_tag_list" );
		TagRenderer renderer = new TagRenderer();
		Stream.of( availableTags, includedTags, excludedTags )
				.forEach( list -> {
					list.setPrototypeCellValue( "width assumption" );
					list.setCellRenderer( renderer );
				} );

		// list selection is mutually-exclusive. Buttons activate when an adjacent list
		// has a selection
		AtomicBoolean clearing = new AtomicBoolean( false );
		availableTags.addListSelectionListener( lse -> {
			if( !clearing.get() ) {
				clearing.set( true );
				includedTags.clearSelection();
				excludedTags.clearSelection();
				clearing.set( false );
			}
			avin.setEnabled( !availableTags.isSelectionEmpty() || !includedTags.isSelectionEmpty() );
			avex.setEnabled( !availableTags.isSelectionEmpty() || !excludedTags.isSelectionEmpty() );
		} );
		includedTags.addListSelectionListener( lse -> {
			if( !clearing.get() ) {
				clearing.set( true );
				availableTags.clearSelection();
				excludedTags.clearSelection();
				clearing.set( false );
			}
			avin.setEnabled( !availableTags.isSelectionEmpty() || !includedTags.isSelectionEmpty() );
			inex.setEnabled( !includedTags.isSelectionEmpty() || !excludedTags.isSelectionEmpty() );
		} );
		excludedTags.addListSelectionListener( lse -> {
			if( !clearing.get() ) {
				clearing.set( true );
				availableTags.clearSelection();
				includedTags.clearSelection();
				clearing.set( false );
			}
			avex.setEnabled( !availableTags.isSelectionEmpty() || !excludedTags.isSelectionEmpty() );
			inex.setEnabled( !includedTags.isSelectionEmpty() || !excludedTags.isSelectionEmpty() );
		} );

		avin.setToolTipText( SWAP_SELECTED_TAGS );
		avin.addActionListener( ac -> {
			if( !availableTags.isSelectionEmpty() ) {
				Set<String> s = filter.includedTags();
				s.addAll( availableTags.getSelectedValuesList() );
				filter.includedTags( s );
			}
			if( !includedTags.isSelectionEmpty() ) {
				Set<String> s = filter.includedTags();
				s.removeAll( includedTags.getSelectedValuesList() );
				filter.includedTags( s );
			}
			updateListener.run();
			refresh();
		} );

		avin.setToolTipText( SWAP_SELECTED_TAGS );
		avex.addActionListener( ac -> {
			if( !availableTags.isSelectionEmpty() ) {
				Set<String> s = filter.excludedTags();
				s.addAll( availableTags.getSelectedValuesList() );
				filter.excludedTags( s );
			}
			if( !excludedTags.isSelectionEmpty() ) {
				Set<String> s = filter.excludedTags();
				s.removeAll( excludedTags.getSelectedValuesList() );
				filter.excludedTags( s );
			}
			updateListener.run();
			refresh();
		} );

		avin.setToolTipText( SWAP_SELECTED_TAGS );
		inex.addActionListener( ac -> {
			if( !includedTags.isSelectionEmpty() ) {
				Set<String> in = filter.includedTags();
				Set<String> ex = filter.excludedTags();
				in.removeAll( includedTags.getSelectedValuesList() );
				ex.addAll( includedTags.getSelectedValuesList() );
				filter.includedTags( in )
						.excludedTags( ex );
			}
			if( !excludedTags.isSelectionEmpty() ) {
				Set<String> in = filter.includedTags();
				Set<String> ex = filter.excludedTags();
				ex.removeAll( excludedTags.getSelectedValuesList() );
				in.addAll( excludedTags.getSelectedValuesList() );
				filter.includedTags( in )
						.excludedTags( ex );
			}
			updateListener.run();
			refresh();
		} );

		reset.setToolTipText( "Reset tag selection" );
		reset.addActionListener( ac -> {
			filter.includedTags( emptySet() )
					.excludedTags( emptySet() );
			updateListener.run();
			refresh();
		} );

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.BOTH;
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.gridheight = 3;
		gbc.gridwidth = 1;
		gbc.weightx = 1;
		gbc.weighty = 1;
		add( titled( "Available", new JScrollPane( availableTags ) ), gbc );

		gbc.gridx += gbc.gridwidth;
		gbc.gridheight = 1;
		gbc.weightx = 0;
		gbc.weighty = 0;
		add( avin, gbc );

		gbc.gridx += gbc.gridwidth;
		gbc.weightx = 1;
		gbc.weighty = 1;
		add( titled( "Included", new JScrollPane( includedTags ) ), gbc );

		gbc.gridy += gbc.gridheight;
		gbc.weightx = 0;
		gbc.weighty = 0;
		add( inex, gbc );

		gbc.gridy += gbc.gridheight;
		gbc.weightx = 1;
		gbc.weighty = 1;
		add( titled( "Excluded", new JScrollPane( excludedTags ) ), gbc );

		gbc.gridx -= gbc.gridwidth;
		gbc.weightx = 0;
		gbc.weighty = 0;
		add( avex, gbc );

		gbc.gridy++;
		gbc.gridx--;
		gbc.gridwidth = 3;
		add( reset, gbc );

		refresh();
	}

	/**
	 * @param l executed whenever the filter is updated
	 */
	void withListener( Runnable l ) {
		updateListener = l;
	}

	/**
	 * Filters visible tags
	 *
	 * @param text The filter text
	 */
	void filter( Pattern text ) {
		tagFilter = text;
		refresh();
	}

	/**
	 * Ensures the controls reflect the state of the filter
	 */
	void refresh() {
		Set<String> available = filter.allTags();
		Set<String> included = filter.includedTags();
		Set<String> excluded = filter.excludedTags();

		available.removeAll( included );
		available.removeAll( excluded );

		availableTags.setListData( sorted( available ) );
		includedTags.setListData( sorted( included ) );
		excludedTags.setListData( sorted( excluded ) );

		reset.setEnabled( !included.isEmpty() || !excluded.isEmpty() );
	}

	/**
	 * As with refresh, but also updates the view so that tags that are on enabled
	 * flows will be highlighted
	 */
	void refreshAndLimitTags() {
		Set<String> tagsOnEnabledFlows = filter.flows()
				.flatMap( f -> f.meta().tags().stream() )
				.collect( toSet() );
		tagLimit = tagsOnEnabledFlows::contains;
		refresh();
	}

	private Vector<String> sorted( Collection<String> tags ) {
		Vector<String> v = new Vector<>( tags );
		Collections.sort( v, order );
		return v;
	}

	/**
	 * Handles tag highlighting
	 */
	class TagRenderer extends DefaultListCellRenderer {

		private static final long serialVersionUID = 1L;

		@Override
		public Component getListCellRendererComponent( JList<?> list, Object value, int index,
				boolean isSelected, boolean cellHasFocus ) {
			Component cell = super.getListCellRendererComponent( list, value, index, isSelected,
					cellHasFocus );

			boolean regexMatch = tagFilter.matcher( String.valueOf( value ) ).find();
			boolean tagLimitMatch = tagLimit.test( String.valueOf( value ) );

			if( !regexMatch || !tagLimitMatch ) {
				cell.setFont( cell.getFont().deriveFont( Font.ITALIC ) );
				cell.setForeground( Color.GRAY );
			}

			return cell;
		}
	}
}
