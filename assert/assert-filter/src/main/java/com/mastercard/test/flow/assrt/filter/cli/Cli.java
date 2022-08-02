package com.mastercard.test.flow.assrt.filter.cli;

import static com.mastercard.test.flow.assrt.filter.cli.Line.fillLine;
import static com.mastercard.test.flow.assrt.filter.cli.Line.wrap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import com.mastercard.test.flow.assrt.filter.FilterOptions;

/**
 * Utility for formatting a command-line interface
 */
public class Cli {

	/**
	 * The suggested minimum width of the interface
	 */
	public static final int MIN_WIDTH = FilterOptions.CLI_MIN_WIDTH.asInt();

	private final AttributedStringBuilder asb = new AttributedStringBuilder();
	private final int width;
	private int lineLength = 0;

	/**
	 * Builds a new instance. It is heavily suggested that you respect
	 * {@link #MIN_WIDTH}
	 *
	 * @param width in characters
	 */
	public Cli( int width ) {
		this.width = width;
	}

	/**
	 * Adds a box to the interface. {@link Line#SINGLE} and {@link Corner#SINGLE} is
	 * assumed
	 *
	 * @param name    The title of the box
	 * @param content The box content
	 * @return <code>this</code>
	 */
	public Cli box( String name, Consumer<Box<Cli>> content ) {
		return box( Line.SINGLE, Corner.SINGLE, name, content );
	}

	/**
	 * Adds a box to the interface.
	 *
	 * @param line    line stroke
	 * @param corner  corner stroke
	 * @param name    The title of the box
	 * @param content The box content
	 * @return <code>this</code>
	 */
	public Cli box( Line line, Corner corner, String name, Consumer<Box<Cli>> content ) {
		Box<Cli> b = new Box<>( line, corner, name, this, width, "", "" );
		content.accept( b );
		return b.close();
	}

	/**
	 * @return The formatted content of the interface
	 */
	public AttributedString content() {
		return asb.toAttributedString();
	}

	/**
	 * Adds text
	 *
	 * @param text The text to add
	 * @return <code>this</code>
	 */
	Cli append( String text ) {
		lineLength += text.length();
		asb.append( text );
		return this;
	}

	/**
	 * Adds repeated text
	 *
	 * @param filler The text to repeat
	 * @param length The desired length
	 * @return <code>this</code>
	 */
	Cli fill( char filler, int length ) {
		StringBuilder sb = new StringBuilder();
		while( sb.length() < length ) {
			sb.append( filler );
		}
		return append( sb.toString() );
	}

	/**
	 * Adds styled text
	 *
	 * @param style The style
	 * @param text  The text
	 * @return <code>this</code>
	 */
	Cli styled( UnaryOperator<AttributedStyle> style, String text ) {
		lineLength += text.length();
		asb.styled( style, text );
		return this;
	}

	/**
	 * Adds styled text that will be trimmed to fit
	 *
	 * @param rightMargin The number of characters of space to leave on the right
	 *                    hand side
	 * @param style       The style
	 * @param text        The text
	 * @return <code>this</code>
	 */
	Cli ellipsised( int rightMargin, UnaryOperator<AttributedStyle> style, String text ) {
		return styled( style, Line.ellipsise( width - rightMargin - lineLength, text ) );
	}

	/**
	 * Fills the rest of the current line and starts a new one
	 *
	 * @param filler The repeated filler text
	 * @param end    The final text
	 * @return <code>this</code>
	 */
	Cli endLine( String filler, String end ) {
		int toFill = width - lineLength - end.length();
		for( int i = 0; i < toFill; i++ ) {
			asb.append( filler );
		}
		asb.append( end ).append( "\n" );
		lineLength = 0;
		return this;
	}

	/**
	 * Content is enclosed in a titled box
	 *
	 * @param <P> parent type
	 */
	public class Box<P> {
		private final Corner crnr;
		private final Line lne;
		private final P parent;
		private final int boxWidth;
		private final String left;
		private final String right;

		/**
		 * @param lne    Line stroke
		 * @param crnr   Corner stroke
		 * @param name   box title
		 * @param parent parent object
		 * @param width  box width
		 * @param left   left outer margin
		 * @param right  right outer margin
		 */
		Box( Line lne, Corner crnr, String name, P parent, int width, String left, String right ) {
			this.lne = lne;
			this.crnr = crnr;
			this.parent = parent;
			this.boxWidth = width - 4;
			this.left = left;
			this.right = right;
			append( left ).append( crnr.tl() ).append( lne.h() );
			if( !name.isEmpty() ) {
				append( " " ).append( Line.ellipsise( width - 6, name ) ).append( " " );
			}
			endLine( lne.h(), crnr.tr + right );
		}

		/**
		 * Inserts a section break
		 *
		 * @param name title
		 * @return <code>this</code>
		 */
		public Box<P> section( String name ) {
			append( left ).append( crnr.lTee() ).append( lne.h() ).append( " " )
					.append( name ).append( " " )
					.endLine( lne.h(), crnr.rTee + right );
			return this;
		}

		/**
		 * Adds a child box
		 *
		 * @param name    box title
		 * @param content box content
		 * @return <code>this</code>
		 */
		public Box<P> box( String name, Consumer<Box<Box<P>>> content ) {
			Box<Box<P>> b = new Box<>( Line.SINGLE, Corner.SINGLE, name, this, boxWidth + 2, left + lne.v,
					lne.v + right );
			content.accept( b );
			return b.close();
		}

		/**
		 * Inserts paragraphs. Content that overflows the line will be wrapped.
		 *
		 * @param text content
		 * @return <code>this</code>
		 */
		public Box<P> paragraph( String text ) {
			Stream.of( text.replace( "\r", "" ).split( "\n" ) )
					.forEach( p -> wrap( boxWidth, 4, p )
							.forEach( line -> append( left ).append( lne.v() ).append( " " )
									.append( line )
									.endLine( " ", " " + lne.v + right ) ) );
			return this;
		}

		/**
		 * Inserts lines. Content that overflows the box will be ellipsised
		 *
		 * @param text content
		 * @return <code>this</code>
		 */
		public Box<P> line( String text ) {
			Stream.of( text.replace( "\r", "" ).split( "\n" ) )
					.forEach( line -> append( left ).append( lne.v() ).append( " " )
							.append( Line.ellipsise( boxWidth, line ) )
							.endLine( " ", " " + lne.v + right ) );
			return this;
		}

		/**
		 * Inserts a collection of words, joined by spaces
		 *
		 * @param style how to style the words
		 * @param words the words
		 * @return <code>this</code>
		 */
		public Box<P> words( UnaryOperator<AttributedStyle> style, Collection<String> words ) {
			wrap( boxWidth, 4, new ArrayDeque<>( words ) )
					.forEach( line -> {
						append( left ).append( lne.v() ).append( " " );
						line.forEach( word -> styled( style, word ).append( " " ) );
						endLine( " ", lne.v + right );
					} );

			return this;
		}

		/**
		 * Inserts a description list
		 *
		 * @param content list content
		 * @return <code>this</code>
		 */
		public Box<P> descriptionList( Consumer<DescriptionList<Box<P>>> content ) {
			DescriptionList<Box<P>> dl = new DescriptionList<>( this, boxWidth + 2,
					left + lne.v, lne.v + right );
			content.accept( dl );
			return dl.close();
		}

		/**
		 * Inserts a list of indexed and tagged items
		 *
		 * @param style   The item style
		 * @param content list content
		 * @return <code>this</code>
		 */
		public Box<P> indexedTaggedList( UnaryOperator<AttributedStyle> style,
				Consumer<IndexedTaggedList<Box<P>>> content ) {
			IndexedTaggedList<Box<P>> itl = new IndexedTaggedList<>(
					this, style, boxWidth + 2,
					left + lne.v, lne.v + right );
			content.accept( itl );
			return itl.close();
		}

		/**
		 * Closes the box
		 *
		 * @return The parent object
		 */
		P close() {
			append( left ).append( crnr.bl() )
					.endLine( lne.h(), crnr.br + right );
			return parent;
		}

	}

	/**
	 * Formats a list of item/description pairs
	 *
	 * @param <P> parent type
	 */
	public class DescriptionList<P> {
		private final P parent;
		private final int dlWidth;
		private final String left;
		private final String right;

		private final Map<String, String> items = new LinkedHashMap<>();

		/**
		 * @param parent parent object
		 * @param width  list width
		 * @param left   margin
		 * @param right  margin
		 */
		DescriptionList( P parent, int width, String left, String right ) {
			this.parent = parent;
			dlWidth = width;
			this.left = left;
			this.right = right;
		}

		/**
		 * Adds an item
		 *
		 * @param term        The described term
		 * @param description The term description
		 * @return <code>this</code>
		 */
		public DescriptionList<P> item( String term, String description ) {
			items.put( term, description );
			return this;
		}

		/**
		 * Completes the list
		 *
		 * @return the parent
		 */
		P close() {
			int itemWidth = items.keySet().stream()
					.mapToInt( String::length )
					.max().orElse( 0 );
			String itemFmt = "  %" + itemWidth + "s";
			String descMargin = String.format( itemFmt, "" );
			items.forEach( ( item, desc ) -> {
				append( left )
						.styled( AttributedStyle::bold, String.format( itemFmt, item ) )
						.append( " : " );
				Deque<String> lines = wrap( dlWidth - itemWidth - 5, 4, desc );
				append( lines.removeFirst() ).endLine( " ", right );
				lines.forEach( line -> append( left )
						.append( descMargin )
						.append( "   " )
						.append( line )
						.endLine( " ", right ) );
			} );
			return parent;
		}
	}

	/**
	 * Formats a list of indexed and tagged items
	 *
	 * @param <P> parent type
	 */
	public class IndexedTaggedList<P> {
		private final P parent;
		private final UnaryOperator<AttributedStyle> style;
		private final int itlWidth;
		private final String left;
		private final String right;

		private final List<IndexedTaggedItem> items = new ArrayList<>();

		/**
		 * @param parent parent object
		 * @param style  item style
		 * @param width  list width
		 * @param left   margin
		 * @param right  margin
		 */
		IndexedTaggedList( P parent, UnaryOperator<AttributedStyle> style, int width, String left,
				String right ) {
			this.parent = parent;
			this.style = style;
			itlWidth = width;
			this.left = left;
			this.right = right;
		}

		/**
		 * Adds an item
		 *
		 * @param index item index
		 * @param item  item
		 * @param tags  item tags
		 * @return <code>this</code>
		 */
		public IndexedTaggedList<P> item( int index, String item, Collection<String> tags ) {
			items.add( new IndexedTaggedItem( index, item, tags ) );
			return this;
		}

		/**
		 * Closes the list
		 *
		 * @return the parent
		 */
		P close() {
			int maxIdx = items.stream().mapToInt( i -> i.index ).max().orElse( 1 );
			int idxWidth = Math.max( 1, (int) Math.ceil( Math.log10( maxIdx ) ) ) + 1;
			String idxFmt = "%" + idxWidth + "s ";
			items.forEach( item -> {
				append( left )
						.append( String.format( idxFmt, item.index ) )
						.ellipsised( right.length() + 1, style, item.item )
						.append( " " );
				Deque<String> tags = new ArrayDeque<>( item.tags );
				fillLine( itlWidth - idxWidth - 4 - item.item.length(), 4, tags )
						.forEach( t -> styled( AttributedStyle::faint, t ).append( " " ) );

				endLine( " ", right );
				wrap( itlWidth - 10, 4, tags ).forEach( line -> {
					append( left ).fill( ' ', 10 );
					line.forEach( t -> styled( AttributedStyle::faint, t ).append( " " ) );
					endLine( " ", right );
				} );
			} );
			return parent;
		}

		private class IndexedTaggedItem {
			public final int index;
			public final String item;
			public final Collection<String> tags;

			IndexedTaggedItem( int index, String item, Collection<String> tags ) {
				this.index = index;
				this.item = item;
				this.tags = tags;
			}
		}
	}

}
