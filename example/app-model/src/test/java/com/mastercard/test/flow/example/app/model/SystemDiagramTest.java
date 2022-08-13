package com.mastercard.test.flow.example.app.model;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.example.app.model.ExampleSystem.Actors;
import com.mastercard.test.flow.msg.http.HttpReq;
import com.mastercard.test.flow.msg.sql.Query;
import com.mastercard.test.flow.msg.web.WebSequence;
import com.mastercard.test.flow.util.Flows;

/**
 * Ensures that the diagram in the main example readme accurately reflects the
 * system model that drives testing
 */
@SuppressWarnings("static-method")
class SystemDiagramTest {

	/**
	 * Extra data about system actors for the purposes of the diagram
	 */
	private enum Meta {
		USER(false, "([", "])", "Needs characters counted"),
		WEB_UI(true, "[", "]", "Browser interface"),
		UI(true, "[", "]", "HTTP interface"),
		CORE(true, "[", "]", "Orchestrates processing"),
		HISTOGRAM(true, "[", "]", "Counts characters"),
		OPS(false, "(", ")", "Provokes queue"),
		QUEUE(true, "[", "]", "Stores and processes<br>deferred operations"),
		STORE(true, "[", "]", "Key/Value store"),
		DB(true, "[(", ")]", ""),
		;

		public final boolean internal;
		public final String left;
		public final String right;
		public final String description;

		Meta( boolean internal, String left, String right, String description ) {
			this.internal = internal;
			this.left = left;
			this.right = right;
			this.description = description;
		}
	}

	/**
	 * Generates mermaid markup from the system model and inserts it into the main
	 * example system readme. The test will fail if that insertion made any changes
	 *
	 * @throws Exception If the insertion fails
	 */
	@Test
	void regenerate() throws Exception {
		// map from requester to responder to a set of call characterisations
		Map<Actor,
				Map<Actor, Set<String>>> calls = new TreeMap<>( Comparator.comparing( Actor::name ) );

		ExampleSystem.MODEL.flows()
				.flatMap( Flows::interactions )
				.forEach( ntr -> calls
						.computeIfAbsent( ntr.requester(),
								q -> new TreeMap<>( Comparator.comparing( Actor::name ) ) )
						.computeIfAbsent( ntr.responder(),
								s -> new TreeSet<>() )
						.add( characterise( ntr.request() ) ) );

		StringBuilder mm = new StringBuilder( "```mermaid\ngraph TD\n" );
		Set<Actor> defined = new HashSet<>();

		// Start with external actors
		Stream.of( Meta.values() )
				.filter( m -> !m.internal )
				.sorted( Comparator.comparing( Meta::name ) )
				.map( m -> Actors.valueOf( m.name() ) )
				.forEach( ext -> {
					Map<Actor, Set<String>> cts = calls.remove( ext );
					for( Map.Entry<Actor, Set<String>> ct : cts.entrySet() ) {
						mm.append( link( ext, ct.getKey(), defined, ct.getValue() ) );
					}
				} );

		// then the rest
		mm.append( "    subgraph example system\n" );
		for( Map.Entry<Actor, Map<Actor, Set<String>>> e : calls.entrySet() ) {
			for( Map.Entry<Actor, Set<String>> rs : e.getValue().entrySet() ) {
				mm.append( link( e.getKey(), rs.getKey(), defined, rs.getValue() ) );
			}
		}
		mm.append( "    end\n```" );

		insert( Paths.get( "../README.md" ),
				"<!-- system_diagram_start -->",
				mm.toString(),
				"<!-- system_diagram_end -->" );
	}

	private static String link( Actor from, Actor to, Set<Actor> defined,
			Set<String> characterisations ) {
		return String.format( "    %s -- %s --> %s\n",
				node( from, defined ),
				characterisations.stream().collect( joining( "/" ) ),
				node( to, defined ) );
	}

	private static String node( Actor a, Set<Actor> defined ) {
		if( defined.contains( a ) ) {
			return a.name();
		}
		defined.add( a );
		Meta m = Meta.valueOf( a.name() );
		return String.format( "%s%s%s<br>%s%s",
				a, m.left, a, m.description, m.right );
	}

	private static String characterise( Message request ) {
		if( request instanceof HttpReq ) {
			HttpReq hr = (HttpReq) request;
			return hr.method();
		}
		if( request instanceof WebSequence ) {
			return "browser";
		}
		if( request instanceof Query ) {
			return "SQL";
		}
		return request.getClass().getSimpleName();
	}

	/**
	 * Regenerates a section of a file and throws a failed comparison test if the
	 * file was altered by that act.
	 *
	 * @param path    The file to operate on
	 * @param start   The line at which to insert the content
	 * @param content The content to insert
	 * @param end     The line at which the content ends
	 * @throws Exception IO failure
	 */
	private static void insert( Path path, String start, String content, String end )
			throws Exception {

		String existing = "";
		if( Files.exists( path ) ) {
			existing = new String( Files.readAllBytes( path ), UTF_8 );
		}
		List<String> regenerated = new ArrayList<>();

		Iterator<String> exi = Arrays.asList( existing.split( "\n", -1 ) ).iterator();
		while( exi.hasNext() ) {
			boolean found = false;
			while( exi.hasNext() && !found ) {
				String line = exi.next();
				if( line.trim().equals( start ) ) {
					found = true;
					break;
				}
				regenerated.add( line );
			}
			if( found ) {

				boolean endFound = false;
				while( exi.hasNext() && !endFound ) {
					String line = exi.next();
					if( line.trim().equals( end ) ) {
						endFound = true;
					}
				}

				// add the new content
				regenerated.add( start );
				regenerated.add( "" );
				regenerated.add( content );
				regenerated.add( "" );
				regenerated.add( end );
			}
		}

		// write the file
		String newContent = regenerated.stream().collect( Collectors.joining( "\n" ) );
		Files.write( path, newContent.getBytes( UTF_8 ) );

		Assertions.assertEquals( existing, newContent,
				path + " has been updated and needs to be committed to git" );
	}
}
