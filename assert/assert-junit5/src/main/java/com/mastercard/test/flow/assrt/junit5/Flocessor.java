package com.mastercard.test.flow.assrt.junit5;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.opentest4j.IncompleteExecutionException;
import org.opentest4j.TestAbortedException;

import static com.mastercard.test.flow.assrt.Order.CHAIN_TAG_PREFIX;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.assrt.AbstractFlocessor;
import com.mastercard.test.flow.assrt.History.Result;
import com.mastercard.test.flow.util.Tags;

/**
 * Integrates {@link Flow} processing into junit 5. This should be used as the
 * source of test cases in a <a href=
 * "https://junit.org/junit5/docs/current/user-guide/#writing-tests-dynamic-tests">dynamic
 * test</a>, e.g.:
 *
 * <pre>
 * &#64;TestFactory
 * Stream&lt;DynamicNode&gt; flows() {
 * 	return new Flocessor( "My test name", MY_SYSTEM_MODEL )
 * 			.system( State.LESS, MY_ACTORS_UNDER_TEST )
 * 			.behaviour( asrt -&gt; {
 * 				// test behaviour
 * 			} ).tests();
 * }
 * </pre>
 */
public class Flocessor extends AbstractFlocessor<Flocessor> {

	/**
	 * @param title A meaningful name for the test
	 * @param model The system model to exercise
	 */
	public Flocessor( String title, Model model ) {
		super( title, model );
	}

	/**
	 * Return the results of this method from a {@link TestFactory}-annotated method
	 * in your test class
	 *
	 * @return A stream of test cases
	 */
	public Stream<DynamicNode> tests() {
		List<DynamicNode> nodes = new ArrayList<>();
		List<Flow> currentChain = new ArrayList<>();
		String currentChainId = null;

		// Iterate over flows once and separate them into chained and non-chained
		for( Flow flow : flows().collect( Collectors.toList() ) ) {
			Optional<String> chainSuffix = Tags.suffix( flow.meta().tags(), CHAIN_TAG_PREFIX );
			if( chainSuffix.isPresent() ) {
				String chainId = chainSuffix.get();
				if( currentChainId == null || currentChainId.equals( chainId ) ) {
					currentChainId = chainId;
					currentChain.add( flow );
				}
				else {
					// End of the previous chain, add the current chain to nodes
					nodes.add( createDynamicContainer( currentChain ) );
					currentChain.clear();
					currentChainId = chainId;
					currentChain.add( flow );
				}
			}
			else {
				if( currentChainId != null ) {
					// End of the current chain, add the current chain to nodes
					nodes.add( createDynamicContainer( currentChain ) );
					currentChain.clear();
					currentChainId = null;
				}
				nodes.add( dynamicTest(
						flow.meta().id(),
						testSource( flow ),
						() -> processFlow( flow ) ) );
			}
		}

		// If the last flows were part of a chain, add them as well
		if( !currentChain.isEmpty() ) {
			nodes.add( createDynamicContainer( currentChain ) );
		}
		return nodes.stream();
	}

	private void processFlow( Flow flow ) {
		try {
			process( flow );
			history.recordResult( flow, Result.SUCCESS );
		}
		catch( IncompleteExecutionException iee ) {
			// not strictly required to record the skipped outcome in the history, as it
			// does not inform the processing of later flows. That may change in the future
			// though, so for now we're going to live with the mutation testing complaint
			history.recordResult( flow, Result.SKIP );
			throw iee;
		}
		catch( AssertionError ae ) {
			history.recordResult( flow, Result.UNEXPECTED );
			throw ae;
		}
		catch( Exception e ) {
			// not strictly required to record the error outcome in the history, as it
			// does not inform the processing of later flows. That may change in the future
			// though, so for now we're going to live with the mutation testing complaint
			history.recordResult( flow, Result.ERROR );
			throw e;
		}
	}

	private DynamicContainer createDynamicContainer( List<Flow> chain ) {
		List<DynamicTest> tests = chain.stream()
				.map( flow -> dynamicTest(
						flow.meta().id(),
						testSource( flow ),
						() -> processFlow( flow ) ) )
				.collect( Collectors.toList() );

		// Use the chain tag for the display name
		String chainTag = chain.stream()
				.findFirst()
				.flatMap( flow -> Tags.suffix( flow.meta().tags(), CHAIN_TAG_PREFIX ) )
				.orElse( chain.get( 0 ).meta().id() );

		return DynamicContainer.dynamicContainer( "chain:" + chainTag, tests );
	}

	/**
	 * Matching
	 * <code>full.class.name.methodname(simpleclassname.java:linenumber)</code>,
	 * Capturing <code>full.class.name</code> and <code>linenumber</code>
	 */
	private static final Pattern TRACE = Pattern.compile( "(\\S+)\\.[^.]+?\\.java:(\\d+)\\)" );

	private static URI testSource( Flow flow ) {
		URI uri = null;
		try {
			String addendaStripped = flow.meta().trace().replaceAll( " \\[.*?\\]$", "" );

			// If the trace contains something that looks like a stacktraceelement, then
			// convert it into a form that junit will recognise
			Optional<String> urif = Optional.ofNullable( addendaStripped )
					.map( TRACE::matcher )
					.filter( Matcher::find )
					.map( m -> String.format( "class:%s?line=%s", m.group( 1 ), m.group( 2 ) ) );

			if( urif.isPresent() ) {
				uri = new URI( urif.get() );
			}

			// try parsing as a URI anyway, we might be using one of the other schemes
			// supported by junit5:
			// https://junit.org/junit5/docs/current/user-guide/#writing-tests-dynamic-tests-uri-test-source
			uri = new URI( addendaStripped );
		}
		catch( @SuppressWarnings("unused") Exception e ) {
			// this is unfortunate, but the impact is minimal: your IDE will not auto-link
			// you to the flow's source. Test behaviour is unchanged.
		}
		return uri;
	}

	@Override
	protected void skip( String reason ) {
		// we're already in the midst of executing a test when we get here, so
		// TestAbortedException is the correct choice over TestSkippedException
		throw new TestAbortedException( reason );
	}

	@Override
	protected void compare( String message, String expected, String actual ) {
		Assertions.assertEquals( expected, actual, message );
	}

}
