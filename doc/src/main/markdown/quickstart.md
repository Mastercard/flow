# Quickstart guide

This guide will take you from an empty project to a full, if basic, flow-tested system.

 * [Project root](../../../../)

## Dependency management

This library is published as a set of tightly-scoped and loosely-coupled jars.
Each of the artifacts you consume should have the same version number, so it's best to define the library version in a property and reuse that in all dependency declarations:

```xml
<properties>
  <flow.version>x.y.z</flow.version>
</properties>
```

Bear in mind that the artifacts provided by this project are intended for usage in testing only.
Take care to scope the dependencies appropriately for your project structure.

## Define the system

For the purposes of this guide we're going to start with the problem statement: we've developed a system for someone named Ben. The system will generate responses to greetings that Ben receives. The system is trivially simple right now, but we'd like to add a test suite before it gets any more complicated and unpredictable.

The system looks like this:

<!-- snippet start -->

<!-- quick.BenSys:system -->

```java
public static String getGreetingResponse( String input ) {
	// Ben's default position is to be polite...
	String output = "I am well, thanks for asking.";
	if( input.contains( "despise" ) ) {
		// ... but he strongly defends his boundaries
		output = "The feeling is mutual!";
	}
	return output;
}
```
[Snippet context](../../test/java/com/mastercard/test/flow/doc/quick/BenSys.java#L15-L23,15-23)

<!-- snippet end -->

## Defining the system actors

The first step to modelling the system is to define its constituent components. These are represented in the framework by the [`Actor` interface][flow.Actor].
Add a dependency:

<!-- snippet start -->

<!-- doc/pom.xml:build -->

```xml
<dependency>
	<!-- flow construction -->
	<groupId>com.mastercard.test.flow</groupId>
	<artifactId>builder</artifactId>
	<version>${flow.version}</version>
</dependency>
```
[Snippet context](../../../pom.xml#L21-L26,21-26)

<!-- snippet end -->

This allows us to define our system actors:

<!-- snippet start -->

<!-- quick.Actors:actors -->

```java
enum Actors implements Actor {
	AVA, BEN,
}
```
[Snippet context](../../test/java/com/mastercard/test/flow/doc/quick/Actors.java#L10-L12,10-12)

<!-- snippet end -->

Note that the `Actor` interface is actually provided by the `api` artifact. Since we're about to need the `builder` jar that transitively provides `api` _anyway_, we've avoided a redundant dependency and gone straight to adding `builder`.

<!-- code_link_start -->

[flow.Actor]: ../../../../api/src/main/java/com/mastercard/test/flow/Actor.java

<!-- code_link_end -->

## Creating a flow

The basic unit of this framework is the [`Flow` type][flow.Flow].
This represent a set of linked [interactions][flow.Interaction] between agents in the system that is being modelled. The data that is exchanged in these interactions is modelled by the [`Message` interface][flow.Message].

`Message` implementations provide a human-readable view of a given data format, along with mechanisms to get and set individual message fields.

For our purposes the [`Text` message type][txt.Text] is appropriate, so we add another dependency:

<!-- snippet start -->

<!-- doc/pom.xml:message -->

```xml
<dependency>
	<!-- simple text message type (other types packaged separately) -->
	<groupId>com.mastercard.test.flow</groupId>
	<artifactId>message-text</artifactId>
	<version>${flow.version}</version>
</dependency>
```
[Snippet context](../../../pom.xml#L30-L35,30-35)

<!-- snippet end -->

Now we can create a `Flow` object using the [`Creator` class][Creator] by:
 * Defining the flow identity with tags and a short description
 * Documenting why the flow exists with the motivation text
 * Defining the interaction structure

<!-- snippet start -->

<!-- Greetings:first_flow -->

```java
private Flow polite = Creator.build( flow -> flow
		.meta( data -> data
				.description( "Happy Ava" )
				.tags( Tags.set( "polite", "greeting" ) )
				.motivation( "Shows what happens when Ava meets Ben while she's in a _good_ mood" ) )
		.call( hello -> hello
				.from( AVA ).to( BEN )
				.request( new Text( "Hello Ben, how are you today?" ) )
				.response( new Text( "I am well, thanks for asking." ) ) ) );
```
[Snippet context](../../test/java/com/mastercard/test/flow/doc/quick/Greetings.java#L21-L29,21-29)

<!-- snippet end -->

<!-- code_link_start -->

[flow.Flow]: ../../../../api/src/main/java/com/mastercard/test/flow/Flow.java
[flow.Interaction]: ../../../../api/src/main/java/com/mastercard/test/flow/Interaction.java
[flow.Message]: ../../../../api/src/main/java/com/mastercard/test/flow/Message.java
[txt.Text]: ../../../../message/message-text/src/main/java/com/mastercard/test/flow/msg/txt/Text.java
[Creator]: ../../../../builder/src/main/java/com/mastercard/test/flow/builder/Creator.java

<!-- code_link_end -->

## Deriving a flow

Flows can also be built by [derivation][Deriver], starting with an existing flow and then:
 * Overwriting the description
 * Changing the tags and motivation
 * Updating the interactions: each call to `update()` specifies a set of changes that are applied to interactions that match the predicate in the first argument. The arguments to `set()` are specific to the message type. In this case the `Text` message type is altered by passing regular expressions.

<!-- snippet start -->

<!-- Greetings:derived_flow -->

```java
private Flow rude = Deriver.build( polite, flow -> flow
		.meta( data -> data
				.description( "Grumpy Ava" )
				.tags( Tags.remove( "polite" ), Tags.add( "rude" ) )
				.motivation( m -> m.replaceAll( "good", "bad" ) ) )
		.update( i -> i.responder() == BEN,
				i -> i.request().set( ", .*", ", I profoundly despise you!" ),
				i -> i.response().set( ".+", "The feeling is mutual!" ) ) );
```
[Snippet context](../../test/java/com/mastercard/test/flow/doc/quick/Greetings.java#L33-L40,33-40)

<!-- snippet end -->

These example flows are trivial enough that we're not really saving any effort by deriving flows over creating them from scratch, but the savings become sharply apparent as flow complexity increases.

<!-- code_link_start -->

[Deriver]: ../../../../builder/src/main/java/com/mastercard/test/flow/builder/Deriver.java

<!-- code_link_end -->

## Grouping flows into a system model

To package flows together we'll need:

<!-- snippet start -->

<!-- doc/pom.xml:model -->

```xml
<dependency>
	<!-- flow grouping -->
	<groupId>com.mastercard.test.flow</groupId>
	<artifactId>model</artifactId>
	<version>${flow.version}</version>
</dependency>
```
[Snippet context](../../../pom.xml#L51-L56,51-56)

<!-- snippet end -->

This will allow us to create an instance of the [`Model` interface][flow.Model], in this case by extending the [`EagerModel` class][EagerModel]. In the constructor of our subclass we define the intersection and union of tags on our flows and then supply the flows that make up the membership of the model:

<!-- snippet start -->

<!-- Greetings:group_construction -->

```java
public Greetings() {
	super( new TaggedGroup( "greeting" )
			.union( "polite", "rude" ) );
	members( flatten( polite, rude ) );
}
```
[Snippet context](../../test/java/com/mastercard/test/flow/doc/quick/Greetings.java#L44-L48,44-48)

<!-- snippet end -->

Structuring the flows into a hierarchy of models allows efficient construction - it becomes possible to build subsets of flows based on tag values. Thus we don't suffer the cost of building _every_ flow when we only want to exercise one of them.

<!-- code_link_start -->

[flow.Model]: ../../../../api/src/main/java/com/mastercard/test/flow/Model.java
[EagerModel]: ../../../../model/src/main/java/com/mastercard/test/flow/model/EagerModel.java

<!-- code_link_end -->

## Validating the system model

The framework provides a number of validation checks that help to ensure the system model is well-formed.
To use these, add a dependency:

<!-- snippet start -->

<!-- doc/pom.xml:validation -->

```xml
<dependency>
	<!-- system model validation -->
	<groupId>com.mastercard.test.flow</groupId>
	<artifactId>validation-junit5</artifactId>
	<version>${flow.version}</version>
</dependency>
```
[Snippet context](../../../pom.xml#L72-L77,72-77)

<!-- snippet end -->

This allows us to add a test like so:

<!-- snippet start -->

<!-- quick.ValidationTest:validation -->

```java
@TestFactory
Stream<DynamicNode> checks() {
	return new Validator()
			.checking( new Greetings() )
			.with( AbstractValidator.defaultChecks() )
			.tests();
}
```
[Snippet context](../../test/java/com/mastercard/test/flow/doc/quick/ValidationTest.java#L21-L27,21-27)

<!-- snippet end -->

Run the test - it should pass.

If you'd prefer to use jUnit4, then [`validation-junit4` is also available][junit4.Validator].

<!-- code_link_start -->

[junit4.Validator]: ../../../../validation/validation-junit4/src/main/java/com/mastercard/test/flow/validation/junit4/Validator.java

<!-- code_link_end -->

## Asserting the model against the system

Now that we've got a validated system model we can use the assertion components to compare it against the actual system.

Add a dependency:

<!-- snippet start -->

<!-- doc/pom.xml:assertion -->

```xml
<dependency>
	<!-- system assertion -->
	<groupId>com.mastercard.test.flow</groupId>
	<artifactId>assert-junit5</artifactId>
	<version>${flow.version}</version>
</dependency>
```
[Snippet context](../../../pom.xml#L99-L104,99-104)

<!-- snippet end -->

This allows us to write a test like so:

<!-- snippet start -->

<!-- quick.AssertionTest:assertion -->

```java
@TestFactory
Stream<DynamicNode> tests() {
	return new Flocessor( "Ben behaviour", new Greetings() )
			.system( State.LESS, BEN )
			.behaviour( asrt -> {
				String input = new String( asrt.expected().request().content(), UTF_8 );
				String output = BenSys.getGreetingResponse( input );
				asrt.actual()
						.request( input.getBytes( UTF_8 ) )
						.response( output.getBytes( UTF_8 ) );
			} )
			.tests();
}
```
[Snippet context](../../test/java/com/mastercard/test/flow/doc/quick/AssertionTest.java#L24-L36,24-36)

<!-- snippet end -->

The `Flocessor` constructor arguments define a human-readable name for the test and the system model that we will be exercising.
The `system()` call defines characteristics of the system under test - what actors are included in the system and whether system behaviour is affected by previous tests.
The `behaviour()` is where you define the test actions. The behaviour defined here is supplied with the interaction data appropriate for the characteristics of the system under test - that data is piped into the system and then the results are populated back into the assertion object.

Run the test - it should pass.
Try changing the message values in the flows or the system behaviour to provoke a failure.

If you'd prefer to use jUnit4, then [`assert-junit4` is also available][junit4.Flocessor].
<!-- code_link_start -->

[junit4.Flocessor]: ../../../../assert/assert-junit4/src/main/java/com/mastercard/test/flow/assrt/junit4/Flocessor.java

<!-- code_link_end -->

## Conclusion

You now have a thin vertical slice of a flow-based testing system:
 * A system that generates Ben's responses to greetings
 * A flow-based model of that system
 * A test that checks the structural validity of that model
 * A test that checks the accuracy of the model against the actual system

[This document](further.md) describes some of the more advanced features that you'll find useful as your system grows in complexity.
