package com.mastercard.test.flow.assrt.junit5;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.builder.Chain;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.builder.Deriver;
import com.mastercard.test.flow.util.TaggedGroup;

@SuppressWarnings("static-method")
class FlocessorTest {

	/**
	 * Validates the {@link Flocessor} class for DynamicContainer creation of
	 * chained flows
	 */
	@Test
	void tests() {

		Flocessor flocessor = new Flocessor( "Test Flocessor", new ChainedMdl() );
		List<DynamicNode> nodes = flocessor.tests().collect( Collectors.toList() );
		assertEquals( 5, nodes.size() );

		// assert first test is a DynamicTest
		assertInstanceOf( DynamicTest.class, nodes.get( 0 ) );
		assertEquals( "a []", nodes.get( 0 ).getDisplayName() );

		// assert second test is a DynamicContainer
		assertInstanceOf( DynamicContainer.class, nodes.get( 1 ) );
		List<DynamicNode> chain1 = ((DynamicContainer) nodes.get( 1 )).getChildren()
				.collect( Collectors.toList() );

		// assert all children are present in the correct order
		assertEquals( "b1 [chain:b]", chain1.get( 0 ).getDisplayName() );
		assertEquals( "b2 [chain:b]", chain1.get( 1 ).getDisplayName() );
		assertEquals( "d [chain:b]", chain1.get( 2 ).getDisplayName() );

		// assert test after a chain is a DynamicTest
		assertInstanceOf( DynamicTest.class, nodes.get( 2 ) );
		assertEquals( "c []", nodes.get( 2 ).getDisplayName() );

		List<DynamicNode> chain2 = ((DynamicContainer) nodes.get( 3 )).getChildren()
				.collect( Collectors.toList() );

		assertEquals( "d1 [chain:d]", chain2.get( 0 ).getDisplayName() );
		assertEquals( "d2 [chain:d]", chain2.get( 1 ).getDisplayName() );

		// assert last test is a DynamicContainer
		List<DynamicNode> chain3 = ((DynamicContainer) nodes.get( 4 )).getChildren()
				.collect( Collectors.toList() );

		assertEquals( "e1 [chain:e]", chain3.get( 0 ).getDisplayName() );
		assertEquals( "e2 [chain:e]", chain3.get( 1 ).getDisplayName() );

	}

	public static class ChainedMdl implements Model {

		private Flow success = Creator.build( flow -> flow
				.meta( data -> data
						.description( "a" ) ) );
		private Chain chain1 = new Chain( "b" );

		private Flow chainedChain1 = Creator.build( chain1, flow -> flow
				.meta( data -> data
						.description( "b1" ) ) );
		private Flow derivedChain1 = Deriver.build( chainedChain1, chain1, flow -> flow
				.meta( data -> data
						.description( "b2" ) ) );

		private Flow successChild = Creator.build( flow -> flow
				.meta( data -> data
						.description( "c" ) ) );

		private Flow chainedChain2 = Creator.build( new Chain( "d" ), flow -> flow
				.meta( data -> data
						.description( "d1" ) ) );
		private Flow derivedChain2 = Deriver.build( chainedChain2, new Chain( "d" ), flow -> flow
				.meta( data -> data
						.description( "d2" ) ) );

		private Flow derivedChildChain1 = Deriver.build( derivedChain1, chain1, flow -> flow
				.meta( data -> data
						.description( "d" ) ) );

		private Chain chain3 = new Chain( "e" );

		private Flow chainedChain3 = Creator.build( chain3, flow -> flow
				.meta( data -> data
						.description( "e1" ) ) );
		private Flow derivedChain3 = Deriver.build( chainedChain3, chain3, flow -> flow
				.meta( data -> data
						.description( "e2" ) ) );

		@Override
		public Stream<Flow> flows( Set<String> include, Set<String> exclude ) {
			return Stream.of( success, chainedChain1, derivedChain1, chainedChain2, derivedChain2,
					successChild, derivedChildChain1,
					chainedChain3, derivedChain3 );
		}

		@Override
		public Model listener( Listener l ) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String title() {
			return "Mdl";
		}

		@Override
		public TaggedGroup tags() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Stream<Model> subModels() {
			return Stream.empty();
		}

	}
}
