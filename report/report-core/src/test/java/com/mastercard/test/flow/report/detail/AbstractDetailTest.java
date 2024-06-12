package com.mastercard.test.flow.report.detail;

import static com.mastercard.test.flow.report.Mdl.Actrs.AVA;
import static com.mastercard.test.flow.report.Mdl.Actrs.BEN;
import static com.mastercard.test.flow.report.Mdl.Actrs.CHE;
import static com.mastercard.test.flow.util.Tags.add;
import static com.mastercard.test.flow.util.Tags.remove;
import static com.mastercard.test.flow.util.Tags.set;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toSet;

import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Residue;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.builder.Deriver;
import com.mastercard.test.flow.msg.txt.Text;
import com.mastercard.test.flow.report.ReportTestUtil;
import com.mastercard.test.flow.report.ReportTestUtil.Served;
import com.mastercard.test.flow.report.data.AssertedData;
import com.mastercard.test.flow.report.data.InteractionData;
import com.mastercard.test.flow.report.data.LogEvent;
import com.mastercard.test.flow.report.seq.Browser;
import com.mastercard.test.flow.report.seq.DetailSequence;
import com.mastercard.test.flow.report.seq.IndexSequence;
import com.mastercard.test.flow.util.Tags;

import io.netty.util.internal.shaded.org.jctools.queues.MessagePassingQueue.Consumer;

/**
 * Superclass for tests that exercise the report flow detail page
 */
@ExtendWith(Browser.class)
abstract class AbstractDetailTest {

	/**
	 * The report to test
	 */
	protected static Served report;

	/**
	 * How to interact with and assert on the detail page
	 */
	protected final DetailSequence dseq;

	/**
	 * Create the report, serves it, builds a browser with which to test
	 *
	 * @throws Exception if something fails
	 */
	@BeforeAll
	static void setup() throws Exception {
		Flow basis = Creator.build( flow -> flow
				.meta( data -> data
						.description( "supplied" )
						.tags( set( "cheese", "supply", "fulfilled" ) )
						.motivation( "Normal cheese supply flow" )
						.trace( "basis creation trace" ) )
				.context( new GroupState(
						gs -> gs.ava().hunger( "voracious" ),
						gs -> gs.ben().morals( "flexible" ) ) )
				.call( a -> a
						.from( AVA )
						.to( BEN )
						.request( new Text( "Hi Ben! Do you have any brie?" ) )
						.call( b -> b.to( CHE )
								.request( new Text( "Ava is after brie again, do you have any?" ) )
								.response( new Text( "Yeah sure, here you go" ) ) )
						.response( new Text( "Hi Ava! Here is your brie" ) ) )
				.residue( new GroupState(
						gs -> gs.ava().hunger( "sated" ) ) ) );

		Flow problem = Creator.build( flow -> flow
				.meta( data -> data
						.description( "concerned" )
						.tags( Tags.set( "cheese", "demand", "advert" ) )
						.motivation( "Evidence of a problem" )
						.trace( "problem creation trace" ) )
				.prerequisite( basis )
				.call( a -> a
						.from( AVA )
						.to( CHE )
						.request( new Text( "I just can't stop eating cheese!" ) )
						.response( new Text( "I didn't ask" ) ) ) );

		Flow confirmation = Creator.build( flow -> flow
				.meta( data -> data
						.description( "worried" )
						.tags( Tags.set( "cheese", "pipeline", "query" ) )
						.motivation( "Confirmation of the problem" )
						.trace( "confirmation creation trace" ) )
				.prerequisite( problem )
				.call( a -> a
						.from( CHE )
						.to( BEN )
						.request( new Text( "Do you think Ava has had enough cheese?" ) )
						.response( new Text( "Not at all!" ) ) ) );

		Flow solution = Deriver.build( basis, flow -> flow
				.meta( data -> data
						.description( "denied" )
						.tags( remove( "fulfilled" ), add( "rejected" ) )
						.trace( "child creation trace" )
						.motivation( "Shows what happens when Che can no longer ignore the problem\n\n"
								+ "![cheese addiction](https://i.imgur.com/l39BOrB.gif)" ) )
				.prerequisite( problem )
				.prerequisite( confirmation )
				.context( GroupState.class, gs -> gs.che().guilt( "undeniable" ) )
				.update( i -> i.responder() == CHE, i -> i
						.tags( Tags.add( "denial" ) )
						.response().set( ".+", "No, I'm worried about her dairy consumption.\n"
								+ "I'm cutting you both off" ) )
				.addCall( i -> i.responder() == BEN, 1, a -> a
						.to( CHE ).tags( Tags.add( "confirmation" ) )
						.request( new Text( "She's an adult, she can have cheese if she wants to!" ) )
						.response( new Text( "Feel free to shop elsewhere." ) ) )
				.update( i -> i.responder() == BEN, i -> i.response()
						.set( ".+", "Sorry Ava, no brie today" ) )
				.residue( GroupState.class, gs -> {
					gs.ava().hunger( "unbearable" );
					gs.che().guilt( "absolved" );
				} ) );

		report = ReportTestUtil.serve( "DetailTest", "Model title", w -> w
				.with( basis )
				.with( problem )
				.with( confirmation )
				.with( solution, fd -> {
					String abExpect = "Sorry Ava, no brie today";
					fd.root.response.asserted.expect = abExpect;
					String abActual = abExpect + ", or ever.";
					fd.root.response.asserted.actual = abActual;
					fd.root.response.full.actual = abActual;
					fd.root.response.full.actualBytes = abActual
							.getBytes( UTF_8 );

					InteractionData benChe = fd.root.children.get( 0 );
					String bcExpect = "No, I'm worried about her dairy consumption.\n"
							+ "I'm cutting you both off";
					benChe.response.asserted.expect = bcExpect;
					String bcActual = bcExpect;
					benChe.response.asserted.actual = bcActual;
					benChe.response.full.actual = bcActual;
					benChe.response.full.actualBytes = bcActual.getBytes( UTF_8 );

					fd.residue.stream()
							.filter( rsd -> "Psychological state".equals( rsd.name ) )
							.findFirst()
							.ifPresent( rsd -> {
								rsd.full = new AssertedData( "full expected", "full actual" );
								rsd.masked = new AssertedData( "masked expect", "masked actual" );
							} );

					fd.logs.add( new LogEvent( "2022-02-25T11:03:53.000",
							"WARN", "abc", "message 1" ) );
					fd.logs.add( new LogEvent( "2022-02-25T11:03:53.050",
							"INFO", "def", "message 2" ) );
					fd.logs.add( new LogEvent( "2022-02-25T11:03:54.100",
							"TRACE", "def", "message 3" ) );
					fd.logs.add( new LogEvent( "2022-02-25T11:03:54.300",
							"INFO", "ghi", "message 4" ) );
					fd.logs.add( new LogEvent( "2022-02-25T11:03:54.700",
							"INFO", "def", "message 5" ) );
					fd.logs.add( new LogEvent( "2022-02-25T11:03:55.359",
							"WARN", "abc", "message 6" ) );
				} ) );
	}

	/**
	 * Tears down the server
	 */
	@AfterAll
	static void teardown() {
		report.close();
	}

	/**
	 * Resets the browser to the default detail view
	 */
	@BeforeEach
	void resetView() {
		dseq.stepping( false )
				.detail();
	}

	/**
	 * @param url The report url
	 */
	protected AbstractDetailTest( String url ) {
		dseq = new IndexSequence( url )
				.index()
				.hasFlows(
						"supplied   [cheese, fulfilled, supply]",
						"concerned  [advert, cheese, demand]",
						"worried    [cheese, pipeline, query]",
						"denied     [cheese, rejected, supply]" )
				.detail(
						"denied     [cheese, rejected, supply]" );
	}

	private static class GroupState implements Context, Residue {

		@JsonProperty
		private final PsychState ava;

		@JsonProperty
		private final PsychState ben;

		@JsonProperty
		private final PsychState che;

		@SafeVarargs
		GroupState( Consumer<GroupState>... data ) {
			ava = new PsychState();
			ben = new PsychState();
			che = new PsychState();
			for( Consumer<GroupState> d : data ) {
				d.accept( this );
			}
		}

		private GroupState( GroupState toCopy ) {
			ava = new PsychState( toCopy.ava() );
			ben = new PsychState( toCopy.ben() );
			che = new PsychState( toCopy.che() );
		}

		@Override
		public String name() {
			return "Psychological state";
		}

		@Override
		public Set<Actor> domain() {
			return Stream.of( AVA, BEN, CHE ).collect( toSet() );
		}

		@Override
		public GroupState child() {
			return new GroupState( this );
		}

		PsychState ava() {
			return ava;
		}

		PsychState ben() {
			return ben;
		}

		PsychState che() {
			return che;
		}
	}

	private static class PsychState {

		@JsonProperty
		private String hunger = "normal";

		@JsonProperty
		private String morals = "normal";

		@JsonProperty
		private String guilt = "normal";

		PsychState() {
		}

		PsychState( PsychState toCopy ) {
			hunger( toCopy.hunger() );
			morals( toCopy.morals() );
			guilt( toCopy.guilt() );
		}

		String hunger() {
			return hunger;
		}

		PsychState hunger( String h ) {
			hunger = h;
			return this;
		}

		String morals() {
			return morals;
		}

		PsychState morals( String m ) {
			morals = m;
			return this;
		}

		String guilt() {
			return guilt;
		}

		PsychState guilt( String g ) {
			guilt = g;
			return this;
		}
	}
}
