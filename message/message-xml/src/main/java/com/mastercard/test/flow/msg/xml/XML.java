
package com.mastercard.test.flow.msg.xml;

import static java.util.stream.Collectors.toCollection;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;

import javax.xml.XMLConstants;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartDocument;
import javax.xml.stream.events.StartElement;

import com.mastercard.test.flow.msg.AbstractMessage;
import com.mastercard.test.flow.msg.Forest;

/**
 * A message comprising XML data. Paths are specified in simplified xpath-like
 * format:
 * <ol>
 * <li>Always specified from document root <code>/</code></li>
 * <li>XML node names separated by <code>/</code></li>
 * <li>Repeated node names have zero-based suffix ordinal <code>[n]</code></li>
 * <li>Attribute names have <code>@</code> prefix
 * </ol>
 * e.g.:
 *
 * <pre>
 * /root/foo/bar
 * /root/foo[2]/bar
 * /root/foo/@attr
 * </pre>
 *
 * Note that this type only supports really simple XML documents. Mixed content
 * nodes will not be preserved properly, DTDs are ignored, element order is not
 * defined, etc.
 */
public class XML extends AbstractMessage<XML> {

	private static final String VALUE_PATH_ELEMENT = "The element value";
	/**
	 * Use this as the field address to set the XML version attribute in the header
	 */
	public static final String HEADER_VERSION = "xml header version";
	/**
	 * Use this as the field address to set the encoding attribute in the header
	 */
	public static final String HEADER_ENCODING = "xml header encoding";

	private final Supplier<Map<String, Object>> basis;

	private XML( Supplier<Map<String, Object>> basis ) {
		this.basis = basis;
	}

	/**
	 * Constructs a new empty XML document
	 */
	public XML() {
		this( TreeMap::new );
	}

	/**
	 * Parses XML content
	 *
	 * @param bytes XML content bytes
	 */
	public XML( byte[] bytes ) {
		this( () -> {
			if( bytes.length == 0 ) {
				return new TreeMap<>();
			}
			XMLInputFactory xif = XMLInputFactory.newFactory();
			// avoid XXE attacks per
			// https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html#xmlinputfactory-a-stax-parser
			// This disables DTDs entirely for that factory
			xif.setProperty( XMLInputFactory.SUPPORT_DTD, false );
			// This causes XMLStreamException to be thrown if external DTDs are accessed.
			xif.setProperty( XMLConstants.ACCESS_EXTERNAL_DTD, "" );
			// disable external entities
			xif.setProperty( XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false );
			// these latter two calls are suggested by owasp but are difficult to exercise
			// for the purposes of mutation testing. We're prioritising safety over a
			// perfect pitest score by leaving them in.

			try( ByteArrayInputStream bais = new ByteArrayInputStream( bytes ) ) {
				XMLEventReader xer = xif.createXMLEventReader( bais );
				Map<String, Object> roots = new TreeMap<>();

				while( xer.hasNext() ) {
					if( xer.peek().isStartDocument() ) {

						StartDocument sd = (StartDocument) xer.nextEvent();

						Optional.ofNullable( sd.getVersion() )
								.ifPresent( v -> roots.put( HEADER_VERSION, v ) );
						Optional.ofNullable( sd.getCharacterEncodingScheme() )
								.ifPresent( e -> roots.put( HEADER_ENCODING, e ) );
					}
					else if( xer.peek().isStartElement() ) {
						readElement( xer, roots );
					}
					else {
						// XML has a bunch of complicated features that aren't typically used for data
						// exchange, so we're just ignoring that stuff
						xer.nextEvent();
					}
				}

				return roots;
			}
			catch( XMLStreamException | IOException e ) {
				throw new IllegalStateException( "Failed to parse\n"
						+ new String( bytes, StandardCharsets.UTF_8 ) + "\n"
						+ Arrays.toString( bytes ), e );
			}
		} );
	}

	@SuppressWarnings("unchecked")
	private static void readElement( XMLEventReader xer, Map<String, Object> parent )
			throws XMLStreamException {
		Map<String, Object> elementData = new TreeMap<>();

		StartElement start = xer.nextEvent().asStartElement();
		readAttributes( start, elementData );
		readContents( xer, elementData );

		// insert into parent
		parent.compute( start.getName().getLocalPart(), ( n, v ) -> {
			if( v instanceof Map ) {
				// a peer already exists
				return new ArrayList<>( Arrays.asList( v, elementData ) );
			}
			if( v instanceof List ) {
				((List<Object>) v).add( elementData );
				return v;
			}
			return elementData;
		} );
	}

	private static void readAttributes( StartElement start, Map<String, Object> elementData ) {
		Iterator<Attribute> attrs = start.getAttributes();
		while( attrs.hasNext() ) {
			Attribute attr = attrs.next();
			elementData.put( "@" + attr.getName().getLocalPart(), attr.getValue() );
		}
	}

	private static void readContents( XMLEventReader xer, Map<String, Object> elementData )
			throws XMLStreamException {
		StringBuilder value = new StringBuilder();
		boolean ended = false;
		while( !ended && xer.hasNext() ) {
			if( xer.peek().isStartElement() ) {
				readElement( xer, elementData );
			}
			else if( xer.peek().isCharacters() ) {
				value.append( xer.nextEvent().asCharacters().getData() );
			}
			else if( xer.peek().isEndElement() ) {
				// consume it
				xer.nextEvent();
				ended = true;
			}
			else {
				// skip over anything else
				xer.nextEvent();
			}
		}
		elementData.put( VALUE_PATH_ELEMENT, value.toString().trim() );
	}

	@Override
	public XML child() {
		return copyMasksTo( new XML( this::data ) );
	}

	@Override
	public XML peer( byte[] content ) {
		return copyMasksTo( new XML( content ) );
	}

	private Map<String, Object> data() {

		Map<String, Object> data = basis.get();
		for( Update update : updates ) {
			Object value = update.value() != null && update.value() != DELETE
					// flatten types to string - that's all an XML document can store
					? update.value().toString()
					: DELETE;

			traverse( data, update.field(),
					value != DELETE,
					value == DELETE,
					( map, key ) -> {
						if( value == DELETE ) {
							map.remove( key );
						}
						else {
							map.put( key, value );
						}
					},
					( list, idx ) -> {
						if( value == DELETE ) {
							list.remove( idx );
						}
						// values are always in maps, so no need for list set
					} );
		}
		return data;
	}

	@SuppressWarnings("unchecked")
	@Override
	public XML set( String field, Object value ) {

		// we have to flatten maps and lists due to attributes - an element isn't
		// just a name/value pair, it's a name/{data} pair, where {data} contains the
		// attributes and the element value
		if( value instanceof Map ) {
			Map<String, Object> map = (Map<String, Object>) value;
			for( Map.Entry<String, Object> me : map.entrySet() ) {
				set( field + "/" + me.getKey(), me.getValue() );
			}
		}
		else if( value instanceof List ) {
			List<Object> list = (List<Object>) value;
			int idx = 0;
			for( Object le : list ) {
				set( field + "[" + idx + "]", le );
				idx++;
			}
		}
		else {
			super.set( field, value );
		}
		return self();
	}

	@Override
	public Object get( String field ) {
		AtomicReference<Object> result = new AtomicReference<>();
		traverse( data(), field, false, false,
				( map, key ) -> result.set( map.get( key ) ),
				null // values are always in maps, so no need for list fetch
		);
		return result.get();
	}

	private static void traverse( Map<String, Object> data, String field,
			boolean vivify, boolean deletion,
			BiConsumer<Map<String, Object>, String> oa,
			ObjIntConsumer<List<Object>> la ) {
		Deque<String> path = new ArrayDeque<>();
		Collections.addAll( path, field.split( "/" ) );

		while( path.getFirst().isEmpty() ) {
			path.removeFirst();
		}

		if( !deletion
				&& !path.getLast().startsWith( "@" )
				&& !HEADER_VERSION.equals( path.getLast() )
				&& !HEADER_ENCODING.equals( path.getLast() ) ) {
			// it's not a deletion, nor an attribute nor a header field, add the special
			// value path element
			path.add( VALUE_PATH_ELEMENT );
		}

		Forest.traverse( data, path, vivify, oa, la );
	}

	@Override
	public Set<String> fields() {
		Set<String> fields = new TreeSet<>();
		Forest.leaves( "/", data(),
				( path, value ) -> fields.add(
						"/" + path.replace( "/" + VALUE_PATH_ELEMENT, "" ) ) );
		return fields;
	}

	@Override
	public byte[] content() {
		Map<String, Object> data = data();
		Object enc = data.get( HEADER_ENCODING );
		String encoding = enc == null ? null : enc.toString();
		try {
			String doc = writeDocuments( data, false );

			return encoding != null ? doc.getBytes( encoding ) : doc.getBytes();
		}
		catch( UnsupportedEncodingException e ) {
			throw new IllegalArgumentException( "Failed to serialise in '" + encoding + "'", e );
		}
	}

	@Override
	protected String asHuman() {
		return writeDocuments( data(), true );
	}

	private static String writeDocuments( Map<String, Object> roots, boolean indent ) {

		Set<String> treeRoots = roots.keySet().stream()
				.filter( k -> !HEADER_VERSION.equals( k ) )
				.filter( k -> !HEADER_ENCODING.equals( k ) )
				.collect( toCollection( TreeSet::new ) );

		if( treeRoots.size() > 1 ) {
			throw new IllegalStateException( "Multiple root elements found " + treeRoots );
		}

		StringWriter sw = new StringWriter();
		try {
			XMLStreamWriter w = XMLOutputFactory.newInstance().createXMLStreamWriter( sw );

			Object version = roots.get( HEADER_VERSION );
			Object encoding = roots.get( HEADER_ENCODING );

			for( Map.Entry<String, Object> root : roots.entrySet() ) {
				if( !HEADER_VERSION.equals( root.getKey() )
						&& !HEADER_ENCODING.equals( root.getKey() ) ) {
					writeDocument( indent, w, version, encoding, root );
				}
			}
		}
		catch( XMLStreamException | FactoryConfigurationError e ) {
			throw new IllegalStateException( "Failed to serialise " + roots, e );
		}
		return sw.toString().trim();
	}

	private static void writeDocument( boolean indent, XMLStreamWriter w, Object version,
			Object encoding, Map.Entry<String, Object> root ) throws XMLStreamException {

		if( version != null ) {
			w.writeStartDocument(
					encoding != null ? encoding.toString() : null,
					version.toString() );
		}

		writeNode( w, root.getKey(), root.getValue(), indent ? 0 : -1 );
	}

	@SuppressWarnings("unchecked")
	private static void writeNode( XMLStreamWriter w, String name, Object value, int indent )
			throws XMLStreamException {

		if( value instanceof Map ) {
			// it's a single element
			Map<String, Object> elementContents = (Map<String, Object>) value;
			writeElement( w, name, indent, elementContents );
		}
		else if( value instanceof List ) {
			// it's multiple elements
			List<Object> list = (List<Object>) value;
			for( Object child : list ) {
				writeNode( w, name, child,
						indent );
			}
		}
		else if( value == null ) {
			indent( w, indent );
			w.writeStartElement( name );
			w.writeEndElement();
		}
		else {
			throw new IllegalStateException(
					String.format( "Unexpected structure%n%s%n%s", name, value ) );
		}

	}

	private static void writeElement( XMLStreamWriter w, String name, int indent,
			Map<String, Object> elementContents ) throws XMLStreamException {
		indent( w, indent );

		w.writeStartElement( name );

		// attributes
		for( Map.Entry<String, Object> ec : elementContents.entrySet() ) {
			if( ec.getKey().startsWith( "@" ) ) {
				w.writeAttribute( ec.getKey().substring( 1 ), String.valueOf( ec.getValue() ) );
			}
		}

		// text content
		Object text = elementContents.get( VALUE_PATH_ELEMENT );
		if( text != null ) {
			w.writeCharacters( String.valueOf( text ) );
		}

		boolean hasChildren = false;
		// child elements
		for( Map.Entry<String, Object> ec : elementContents.entrySet() ) {
			if( !ec.getKey().startsWith( "@" )
					&& !VALUE_PATH_ELEMENT.equals( ec.getKey() ) ) {
				hasChildren = true;
				writeNode( w, ec.getKey(), ec.getValue(),
						indent == -1 ? -1 : indent + 1 );
			}
		}

		if( hasChildren ) {
			indent( w, indent );
		}
		w.writeEndElement();
	}

	private static void indent( XMLStreamWriter w, int indent ) throws XMLStreamException {
		if( indent != -1 ) {
			w.writeCharacters( "\n" );
			for( int i = 0; i < indent; i++ ) {
				w.writeCharacters( "  " );
			}
		}
	}

}
