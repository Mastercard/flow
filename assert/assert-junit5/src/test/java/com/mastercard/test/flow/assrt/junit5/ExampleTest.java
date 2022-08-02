package com.mastercard.test.flow.assrt.junit5;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.junit5.mock.Actrs;
import com.mastercard.test.flow.assrt.junit5.mock.Mdl;

/**
 * Illustrates how to consume a {@link Model} in a junit 5 test class. This test
 * will fail, so you don't want to run it normally. It is instead invoked by
 * {@link MetaTest}.
 */
@SuppressWarnings("static-method")
class ExampleTest {

	/**
	 * Set this to true if you want the test to do anything
	 */
	public static boolean active = false;

	/**
	 * Stops this fail-prone test from stinking up normal test runs
	 */
	@BeforeAll
	public static void activation() {
		Assumptions.assumeTrue( active, "Test activation" );
	}

	/**
	 * @return Test instances to exercise the {@link Flow}s in {@link Mdl}
	 */
	@TestFactory
	Stream<DynamicNode> flows() {
		return new Flocessor( "junit 5 example test", new Mdl() )
				.system( State.FUL, Actrs.BEN )
				.behaviour( asrt -> {
					if( asrt.flow().meta().id().contains( "success" ) ) {
						asrt.actual().response( asrt.expected().response().content() );
					}
					else if( asrt.flow().meta().id().contains( "failure" ) ) {
						asrt.actual().response( "unexpected content!".getBytes( UTF_8 ) );
					}
					else if( asrt.flow().meta().id().contains( "error" ) ) {
						throw new IllegalArgumentException( "no thanks!" );
					}
					else {
						asrt.actual().response( "unsupported behaviour".getBytes( UTF_8 ) );
					}
				} ).tests();
	}

}
