/**
 * Copyright (c) 2020 Mastercard. All rights reserved.
 */

package com.mastercard.test.flow.validation.coppice.ui;

import java.awt.Color;
import java.awt.Component;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

/**
 * Renders things that have a description and tags in lists
 *
 * @param <T> item type
 */
class TaggedItemRenderer<T> extends JPanel implements ListCellRenderer<T> {

	private static final long serialVersionUID = 1L;
	private JLabel description = new JLabel();
	private JLabel tags = new JLabel();
	private final Function<T, String> describer;
	private final Function<T, Set<String>> tagger;

	/**
	 * @param describer How to get a description from the item
	 * @param tagger    How to get tags from the item
	 */
	public TaggedItemRenderer( Function<T, String> describer, Function<T, Set<String>> tagger ) {
		this.describer = describer;
		this.tagger = tagger;
		setLayout( new BoxLayout( this, BoxLayout.X_AXIS ) );
		add( description );
		add( new JLabel( " " ) );
		add( tags );
	}

	@Override
	public Component getListCellRendererComponent( JList<? extends T> list, T value, int index,
			boolean selected, boolean focussed ) {
		description.setText( describer.apply( value ) );
		tags.setText( tagger.apply( value ).stream()
				.sorted()
				.collect( Collectors.joining( " " ) ) );
		Stream.of( this, description, tags ).forEach( c -> {
			if( selected ) {
				c.setBackground( list.getSelectionBackground() );
				c.setForeground( list.getSelectionForeground() );
			}
			else {
				c.setBackground( list.getBackground() );
				c.setForeground( list.getForeground() );
			}
			c.setEnabled( list.isEnabled() );
			c.setFont( list.getFont() );
			c.setOpaque( true );
		} );
		tags.setForeground( Color.gray );
		return this;
	}

	/**
	 * @return Text of the display
	 */
	public String getText() {
		return description.getText() + " " + tags.getText();
	}

}
