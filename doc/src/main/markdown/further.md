# Further reading

This guide explores some features that will be useful as the system model grows in complexity.

 * [Project root](https://github.com/Mastercard/flow)

## Execution report

Adding a call to [`.reporting()`][AbstractFlocessor.reporting(Reporting)] to the construction chain of the `Flocessor` instance controls whether a HTML report of the test run is produced. The report will detail both the expected and observed system behaviour and the results of comparing the two.
The [`Reporting` enum value][Reporting] that you supply controls whether the report is generated and under what circumstances it is automatically opened in a browser.

By default the report will be saved to a timestamped directory under `target/mctf`, but the `mctf.report.name` system property offers control over the destination directory.

<!-- code_link_start -->

[AbstractFlocessor.reporting(Reporting)]: ../../../../assert/assert-core/src/main/java/com/mastercard/test/flow/assrt/AbstractFlocessor.java#L190-L197,190-197
[Reporting]: ../../../../assert/assert-core/src/main/java/com/mastercard/test/flow/assrt/Reporting.java

<!-- code_link_end -->

## Report replay

Given that an execution report contains the observed behaviour of the system under test it can be used as a substitute for that system when checking the accuracy of the model.

If you find yourself in the following circumstances:
 * You have an execution report where the behaviour of the system has diverged from that described in the system model, i.e.: it contains an assertion failure
 * You are confident that the system is correct and you want to update the model to match it
 * Reading data from a file will be faster or more convenient than exercising the actual system

then you can use the report replay feature to quickly iterate changes to your flows until the system model is accurate.

Run the same test that produced the report, but set system property `mctf.report.replay` to activate replay mode. The property can be set to the path to the report to read data from, or to `latest` to replay from the most recent report in `target/mctf`.

You can use [`Replay.isActive()`][Replay.isActive()] in your assertion components to avoid setup and teardown activities that serve no purpose when the source of data is a report rather than the actual system.

<!-- code_link_start -->

[Replay.isActive()]: ../../../../assert/assert-core/src/main/java/com/mastercard/test/flow/assrt/Replay.java#L41-L48,41-48

<!-- code_link_end -->

## Selective execution

By default the assertion components will compare every appropriate flow against the system under test. Flows are processed as standard jUnit test cases, so you can use the facilities of your IDE to exercise a particular flow in isolation.

In some circumstances that is not appropriate, for example:
 * Testing outside of the IDE
 * IDE interface doesn't allow selection of the desired flow subset
 * You want to avoid building flows that you're not going to exercise

The assertion components offer three mechanisms to run a subset of flows:

### System properties

The following system properties will control which flows are built and processed:

| property              | value                               | effect |
| --------------------- | ----------------------------------- | ------ |
| `mctf.filter.include` | comma-separated list of tag values  | Only flows that have all of these tags will be processed |
| `mctf.filter.exclude` | comma-separated list of tag values  | Only flows that don't have any of these tags will be processed |
| `mctf.filter.indices` | comma-separated list of flow indices| Of the list of flows that pass the include and exclude tag filters, only those indexed here will be processed |

### Selection interface

Setting system property `mctf.filter.update=true` will cause an environment-appropriate interface to be presented. This lets you interactively edit the `include`, `exclude` and `indices` filters.

Setting system property `mctf.filter.repeat=true` will cause the `include`, `exclude` and `indices` filters that were in effect in the previous test execution to be repeated. Supplying it along with `update` is a convenient way to iterate on a given set of flows: you configure the filters in the first iteration and then in subsequent runs you can just hit enter twice to use the same values again.

The [assert-filter documentation](../../../../assert/assert-filter) contains more detail on the selection interfaces.

### API filtering

The `Flocessor` API offers two methods to control which flows are exercised:
 * [`.filtering()`][AbstractFlocessor.filtering(Consumer)]: offers control of the same tag and index-based filtering as the system property and selection interfaces.
 * [`.exercising()`][AbstractFlocessor.exercising(Predicate,Consumer)]: offers finer-grained control - all of a flow's data is available to make a decision on whether or not to exercise that flow.

Note that only the tag/index-based filtering can be used to avoid flow construction costs.

<!-- code_link_start -->

[AbstractFlocessor.filtering(Consumer)]: ../../../../assert/assert-core/src/main/java/com/mastercard/test/flow/assrt/AbstractFlocessor.java#L306-L314,306-314
[AbstractFlocessor.exercising(Predicate,Consumer)]: ../../../../assert/assert-core/src/main/java/com/mastercard/test/flow/assrt/AbstractFlocessor.java#L319-L344,319-344

<!-- code_link_end -->

## Model structure

One of the benefits of selective execution (not having to build every flow in order to exercise a subset of them) is realised by implementing flow construction into a structure of `Model` types.

The two main `Model` implementations provided in this framework are:
 * [`EagerModel`][EagerModel]: Builds flows instances in the constructor.
 * [`LazyModel`][LazyModel]: A collection of `EagerModel` instances, which are constructed as flows are requested.

If a flow in one `EagerModel` type is derived from a flow in another `EagerModel`, then you can simply declare the dependency model as a constructor parameter.
The `LazyModel` that both models are registered to will handle satisfying the dependency.

You can see this behaviour in action in the example system model:
 * The flows in [`Implicit`][Implicit] are derived from flows in [`Deferred`][Deferred]
 * The `Implicit` constructor declares a parameter of `Deferred`
 * Both models are registered to the [same `LazyModel` instance][ExampleSystem]

<!-- code_link_start -->

[EagerModel]: ../../../../model/src/main/java/com/mastercard/test/flow/model/EagerModel.java
[LazyModel]: ../../../../model/src/main/java/com/mastercard/test/flow/model/LazyModel.java
[Implicit]: ../../../../example/app-model/src/main/java/com/mastercard/test/flow/example/app/model/Implicit.java
[Deferred]: ../../../../example/app-model/src/main/java/com/mastercard/test/flow/example/app/model/Deferred.java
[ExampleSystem]: ../../../../example/app-model/src/main/java/com/mastercard/test/flow/example/app/model/ExampleSystem.java

<!-- code_link_end -->

## Log capture

Diagnosing unexpected system behaviour is made vastly easier if system logs are available. It's possible to include log content in the test execution report by providing an implementation of the [`LogCapture`][LogCapture] interface when you build the `Flocessor` object, via the [`logs()`][AbstractFlocessor.logs(LogCapture)] method.

Note that the assertion components will not make any assumptions about the format of the `LogEvent.time` field - it is up to the `LogCapture` implementation to put events in chronological order. It is recommended that the `time` values are compatible with [Date.parse](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/parse) - the html report is then able to enhance the log view with an elapsed time display.

 * The [`Tail`][Tail] class captures events from a single file
 * The [`Merge`][Merge] class allows `LogCapture` instances to be multiplexed.
 
<!-- code_link_start -->

[LogCapture]: ../../../../assert/assert-core/src/main/java/com/mastercard/test/flow/assrt/LogCapture.java
[Tail]: ../../../../assert/assert-core/src/main/java/com/mastercard/test/flow/assrt/log/Tail.java
[Merge]: ../../../../assert/assert-core/src/main/java/com/mastercard/test/flow/assrt/log/Merge.java
[AbstractFlocessor.logs(LogCapture)]: ../../../../assert/assert-core/src/main/java/com/mastercard/test/flow/assrt/AbstractFlocessor.java#L274-L281,274-281

<!-- code_link_end -->

## Interaction structure

The system in the [quickstart guide](quickstart.md) was extremely simple, and hence the flows that model it also had the simplest possible structure: two actors and a single request/response pair between them.
More interesting systems will have many more actors, and a request to one of them might provoke a chain of requests and responses through the rest of the system.

Interaction structures such as these can be modelled by nesting a call to the [`.call()`][Response.call(Function)] method between defining the request and response messages.

For example, here is the definition of a flow that models a call to the example system's `UI` actor. That request is processed by propagating the data through the `CORE` service to the `HISTOGRAM` service, and then the responses chain back to the user. The structure of the flow definition code mirrors the interaction structure of the flow.

<!-- snippet start -->

<!-- Direct:nested -->

```java
empty = Creator.build( flow -> flow
		.meta( data -> data
				.description( "empty" )
				.tags( add( "direct", "histogram" ) )
				.motivation( "Counting zero characters while-you-wait" ) )
		.call( a -> a
				.from( Actors.USER )
				.to( Actors.UI )
				.request( textReq( "POST", "/histogram", "" ) )
				.call( b -> b
						.to( Actors.CORE )
						.request( coreReq( "POST", "/process", "text", "" ) )
						.call( c -> c
								.to( Actors.HISTOGRAM )
								.request( textReq( "POST", "/count/all", "" ) )
								.response( jsonRes() ) )
						.response( coreRes() ) )
				.response( jsonRes() ) ) );
```
[Snippet context](../../../../example/app-model/src/main/java/com/mastercard/test/flow/example/app/model/Direct.java#L56-L73,56-73)

<!-- snippet end -->

Interaction structure can be changed when deriving a flow via the following methods:

 * [`addCall()`][Builder.addCall(Predicate,int,Function)]
 * [`removeCall()`][Builder.removeCall(Predicate)]
 * [`subset()`][Builder.subset(Predicate)]
 * [`superset()`][Builder.superset(Actor, Message, Message)]

<!-- code_link_start -->

[Response.call(Function)]: ../../../../builder/src/main/java/com/mastercard/test/flow/builder/steps/Response.java#L30-L36,30-36
[Builder.addCall(Predicate,int,Function)]: ../../../../builder/src/main/java/com/mastercard/test/flow/builder/Builder.java#L106-L117,106-117
[Builder.removeCall(Predicate)]: ../../../../builder/src/main/java/com/mastercard/test/flow/builder/Builder.java#L121-L127,121-127
[Builder.subset(Predicate)]: ../../../../builder/src/main/java/com/mastercard/test/flow/builder/Builder.java#L131-L139,131-139
[Builder.superset(Actor, Message, Message)]: ../../../../builder/src/main/java/com/mastercard/test/flow/builder/Builder.java#L149-L158,149-158

<!-- code_link_end -->

## Downstream assertion

One of the implications of a more complicated interaction structure is that each actor in the system has more outputs in need of assertion: when an actor is provoked with a request it will produce a response, but it will _also_ produce requests to other actors that should be checked against the system model.

A convenient way to perform this assertion is to capture the downstream requests in a [`Consequests`][Consequests] instance, then provide the captured data to the assertion component by calling [`Assertion.assertConsequests()`][Assertion!.assertConsequests(Consequests)].

You can see this happening in [`BenTest`][BenTest].

<!-- code_link_start -->

[Consequests]: ../../../../assert/assert-core/src/main/java/com/mastercard/test/flow/assrt/Consequests.java
[Assertion!.assertConsequests(Consequests)]: ../../../../assert/assert-core/src/main/java/com/mastercard/test/flow/assrt/Assertion.java#L107-L113,107-113

<!-- code_link_end -->

## Integration and isolation testing

Once the system model documents the behaviour and interaction of more than one system component, we can add assertion components that exercise those components in isolation as well as the components working together. All of these tests are driven from the same system model.

Look at the [example system](../../../../example) to see this in action:
 * The [`IntegrationTest`][IntegrationTest] exercises the complete system. The test launches an instance of each of the services, waits for them to start up fully, and then hits the system entrypoints with requests.
 * Each subsystem has its own test that exercises it in isolation, e.g.: [`CoreTest`][CoreTest], a subclass of [`AbstractServiceTest`][AbstractServiceTest]. Here a single service is started up and hit with requests. We've introduced a `MockService` to take the part of the other services - its responses are harvested from the system model data.

You can find the execution reports that are produced by these tests listed [here](https://mastercard.github.io/flow/).
Note how all tests present the same set of expected data, but each exercises a different subset of it according to the system under test.

The ability to drive multiple levels of testing from a single system model is the key strength of this framework. If the isolation tests are all passing, then you can have good confidence that the integration test will also pass.

<!-- code_link_start -->

[IntegrationTest]: ../../../../example/app-itest/src/test/java/com/mastercard/test/flow/example/app/itest/IntegrationTest.java
[CoreTest]: ../../../../example/app-core/src/test/java/com/mastercard/test/flow/example/app/core/CoreTest.java
[AbstractServiceTest]: ../../../../example/app-assert/src/main/java/com/mastercard/test/flow/example/app/assrt/AbstractServiceTest.java

<!-- code_link_end -->

## Field masking

Inevitably your system will exhibit behaviours that are undesirable to accurately model in a test suite. When these behaviours affect the message content in the system model you'll get assertion failures due to the unpredictable content.
Such failures can be avoided by masking out the unpredictable message fields.

Field masking is achieved in three stages:

 1. Define the sources of unpredictable data by creating an implementation of the [`Unpredictable` interface][flow.Unpredictable].
 2. When building message instances, [define masking operations][AbstractMessage.masking(Unpredictable,UnaryOperator)] for the effects of that unpredictablity
 3. In assertion components, [define which causes of unpredictable data are included in the system under test][AbstractFlocessor.masking(Unpredictable...)]

The assertion component is thus able to apply the appropriate masking operations to the expected and observed message data before comparing the two.

Consider the following worked example:
 * We're written a system that allows Ben to respond to requests for random numbers. It's split into two subsystems:
    * [BenSys][mask.BenSys] parses the requests and forms responses
    * [DieSys][mask.DieSys] does the actual random number generation
 * In the system model, we've created an [Unpredictable instance][mask.Unpredictables] to represent the random number generator in the system
 * When we [build a message][Rolling?d\+] we include a call to [`.masking()`][AbstractMessage.masking(Unpredictable,UnaryOperator)] to define what should be masked out when that source of unpredictablity is in the system under test.
   There are [many masking operations available][msg.Mask] (and you can [define your own][msg.Mask.andThen(Consumer)])
 * Note that masking operations are inherited - we don't need to specify them again in derived flows.
 * We can now define two assertion components:
    * [One to exercise the complete system][BenDiceTest?masking]. Here the unpredictable `DieSys` is part of the system under test, so we call [.masking( RNG )][AbstractFlocessor.masking(Unpredictable...)] in the flocessor construction chain. This will cause the masking operations we defined against the `RNG` unpredictable value to be applied, so we'll be asserting messages that look like `Those summed to ?`
    * [One to exercise BenSys in isolation][BenTest]. Here the system does not include `DieSys` - we replace it with a mock behaviour that is driven by the test data in the system model. Hence there is no need for any masking so the asserted messages will be of the form `Those summed to 4`

<!-- code_link_start -->

[flow.Unpredictable]: ../../../../api/src/main/java/com/mastercard/test/flow/Unpredictable.java
[AbstractMessage.masking(Unpredictable,UnaryOperator)]: ../../../../message/message-core/src/main/java/com/mastercard/test/flow/msg/AbstractMessage.java#L50-L57,50-57
[AbstractFlocessor.masking(Unpredictable...)]: ../../../../assert/assert-core/src/main/java/com/mastercard/test/flow/assrt/AbstractFlocessor.java#L202-L209,202-209
[mask.BenSys]: ../../test/java/com/mastercard/test/flow/doc/mask/BenSys.java
[mask.DieSys]: ../../test/java/com/mastercard/test/flow/doc/mask/DieSys.java
[mask.Unpredictables]: ../../test/java/com/mastercard/test/flow/doc/mask/Unpredictables.java
[AbstractMessage.masking(Unpredictable,UnaryOperator)]: ../../../../message/message-core/src/main/java/com/mastercard/test/flow/msg/AbstractMessage.java#L50-L57,50-57
[Rolling?d\+]: ../../test/java/com/mastercard/test/flow/doc/mask/Rolling.java#L30,30
[msg.Mask]: ../../../../message/message-core/src/main/java/com/mastercard/test/flow/msg/Mask.java
[msg.Mask.andThen(Consumer)]: ../../../../message/message-core/src/main/java/com/mastercard/test/flow/msg/Mask.java#L290-L292,290-292
[BenDiceTest?masking]: ../../test/java/com/mastercard/test/flow/doc/mask/BenDiceTest.java#L31,31
[BenTest]: ../../test/java/com/mastercard/test/flow/doc/mask/BenTest.java
[AbstractFlocessor.masking(Unpredictable...)]: ../../../../assert/assert-core/src/main/java/com/mastercard/test/flow/assrt/AbstractFlocessor.java#L202-L209,202-209

<!-- code_link_end -->

## Flow context

The behaviour of a non-trivial system is unlikely to depend on input alone - the response is likely to also depend on state such as system configuration or the residue of previous requests.
Such factors are included in the system model by adding [`Context`][flow.Context] objects to flows via the [`.context()`][Builder.context(Context)] method.

The data held in the model's context objects is applied to the system under test via [`Applicator`][assrt.Applicator] implementations, which are registered with the flocessor via [`applicators()`][AbstractFlocessor.applicators(Applicator...)].

You can see usage of these types in the example system:
 * [`QueueProcessing`][model.ctx.QueueProcessing] : system model type that defines assumptions about the state of the task queue
 * [`QueueProcessingApplicator`][QueueProcessingApplicator] : assertion component that ensures that the modelled assertions are true in the running system

<!-- code_link_start -->

[flow.Context]: ../../../../api/src/main/java/com/mastercard/test/flow/Context.java
[Builder.context(Context)]: ../../../../builder/src/main/java/com/mastercard/test/flow/builder/Builder.java#L225-L232,225-232
[assrt.Applicator]: ../../../../assert/assert-core/src/main/java/com/mastercard/test/flow/assrt/Applicator.java
[AbstractFlocessor.applicators(Applicator...)]: ../../../../assert/assert-core/src/main/java/com/mastercard/test/flow/assrt/AbstractFlocessor.java#L248-L254,248-254
[model.ctx.QueueProcessing]: ../../../../example/app-model/src/main/java/com/mastercard/test/flow/example/app/model/ctx/QueueProcessing.java
[QueueProcessingApplicator]: ../../../../example/app-assert/src/main/java/com/mastercard/test/flow/example/app/assrt/ctx/QueueProcessingApplicator.java

<!-- code_link_end -->

## Flow residue

It is rare that a system is free of side effects, so it can be important to model and assert on the persistent impacts of data processing.
This is possible via the [`Residue`][flow.Residue] type.

Define types that implement the `Residue` interface and attach them to your `Flow` instances to model the side effects of the flow's behaviour.

This model data can be asserted on by adding [`Checker`][assrt.Checker] implementations to the flocessor via [`checkers`][AbstractFlocessor.checkers(Checker...)].

You can see usage of these types in the example system:
 * [`DBItems`][model.rsd.DBItems] : system model type that documents the expected changes to database contents as a result of a flow's behaviour
 * [`DBItemsChecker`][DBItemsChecker] : assertion component that checks that the database has changed as expected

<!-- code_link_start -->

[flow.Residue]: ../../../../api/src/main/java/com/mastercard/test/flow/Residue.java
[assrt.Checker]: ../../../../assert/assert-core/src/main/java/com/mastercard/test/flow/assrt/Checker.java
[AbstractFlocessor.checkers(Checker...)]: ../../../../assert/assert-core/src/main/java/com/mastercard/test/flow/assrt/AbstractFlocessor.java#L261-L267,261-267
[model.rsd.DBItems]: ../../../../example/app-model/src/main/java/com/mastercard/test/flow/example/app/model/rsd/DBItems.java
[DBItemsChecker]: ../../../../example/app-assert/src/main/java/com/mastercard/test/flow/example/app/assrt/rsd/DBItemsChecker.java

<!-- code_link_end -->
## Change management

As discussed in the [motivation document](motivation/index.md), the entire point of a test suite is to allow effective review of changes to a system:
if the changes to the system source code are too subtle to predict, then the accompanying changes to the test suite should make the impact clear.

The flow framework provides two features to aid in change review:

### Message hashing

The [`MessageHash`][MessageHash] class computes digests of user-defined subsets of the message data in the system model. If these hash values are committed to source control along with the system model then they can be useful during change review: if a particular hash value has not changed, then the reviewer can have confidence that the underlying message data has not changed either. Provided that the subsets of messages that are hashed together are chosen carefully, this can be a powerful tool for effective review.

For an example of this usage, see the [`ExampleSystemTest.hashes()`][ExampleSystemTest] test case. Here we're producing hashes of:
 * All message content
 * For each actor in the system:
    * Requests to the actor
    * Responses from the actor

This test will fail if the expected hash values are not updated as the system model is changed, so the change reviewer can look at the diff on this test to see at a glance which parts of the system have been impacted by a change.

### Report diff tool

The flow framework uses an inheritance mechanism (deriving a new flow from an existing one) to effectively compress the set of test data that we use to exercise a tested system. This compression makes it easier to make sweeping changes to test data with small edits to the flow construction code, but by the same token it can be difficult to understand the impact of those small changes.

The model-diff tooling provided in every execution report seeks to mitigate this difficulty.
When supplied with the URLs of two execution reports the tool will download all flow data and display and summarise the changes between the two system models.

Full instructions for using the diff tool are provided [here](../../../../report/README.md).

<!-- code_link_start -->

[MessageHash]: ../../../../validation/validation-core/src/main/java/com/mastercard/test/flow/validation/MessageHash.java
[ExampleSystemTest]: ../../../../example/app-model/src/test/java/com/mastercard/test/flow/example/app/model/ExampleSystemTest.java

<!-- code_link_end -->

## Dependencies

A single flow only captures a single request to the modelled system, but many system behaviours will span multiple requests. It is possible to share data between flows by defining [dependencies][flow.Dependency] in the system model. During system assertion the dependencies are honoured by copying data from observed messages. Dependencies are added via the [`Builder.dependency()`][Builder.dependency(Flow,Function)] method.

For example:
 * We've written [another system for Ben][dep.BenSys], this time to deal with all the requests he gets to remember things for other people.
 * The [model for this system][dep.Storage] has two flows:
   * `put`: takes a value and returns a storage key
   * `get`: takes a storage key and returns the value.
 * The `get` flow declares a dependency on `put` that extracts the key value from the storage response and inserts it into the retrieval request.

Assertion components will satisfy dependencies as observed data is extracted from the system under test. They will also take flow dependencies into account when you use the selective execution facilities - if you ask to process a single flow all of its dependencies will automatically be added to the execution.

<!-- code_link_start -->

[flow.Dependency]: ../../../../api/src/main/java/com/mastercard/test/flow/Dependency.java
[Builder.dependency(Flow,Function)]: ../../../../builder/src/main/java/com/mastercard/test/flow/builder/Builder.java#L204-L214,204-214
[dep.BenSys]: ../../test/java/com/mastercard/test/flow/doc/dep/BenSys.java
[dep.Storage]: ../../test/java/com/mastercard/test/flow/doc/dep/Storage.java

<!-- code_link_end -->

## Execution order

The assertion components will calculate an execution schedule for flows that tries to satisfy the following constraints:
 1. Dependency flows are processed before their dependents
 1. Basis flows are processed before those that are derived from them
 1. Avoids expensive context changes
 1. Lexicographic ordering on flow ID values.

The constraints are listed here in order of preference, e.g.: the resulting execution will break lexicographic ordering in order to satisfy a dependency constraint.

Empty dependencies can be added to the system model via the [`Builder.prerequisite()`][Builder.prerequisite(Flow)] method. These introduce an ordering constraint without data transfer between the two flows.

### Chaining

While a dependency on flow `A` from flow `B` will guarantee that `A` will be processed before `B`, it does not give any stronger assurances than that. for example, it is perfectly possible that other flows will be interleaved between `A` and `B`. If this is a problem then you can use flow chaining to gain greater control over the execution order.

If you add a tag with the prefix of `chain:` to a group of flows, they will be scheduled as a unit in the overall execution order - flows that do not bear the same chain tag will not be interleaved into that unit. The order of flows within a chain is still determined by the standard constraints described above. A flow should only belong to a single chain.

The [`Chain`][builder.Chain] class offers a convenient way to add the chain tag to flows. An example of its usage can be seen in [`Deferred`][Deferred] in the example system model.

<!-- code_link_start -->

[Builder.prerequisite(Flow)]: ../../../../builder/src/main/java/com/mastercard/test/flow/builder/Builder.java#L191-L197,191-197
[builder.Chain]: ../../../../builder/src/main/java/com/mastercard/test/flow/builder/Chain.java
[Deferred]: ../../../../example/app-model/src/main/java/com/mastercard/test/flow/example/app/model/Deferred.java

<!-- code_link_end -->

## Execution control

The assertion components supplied in this framework will exhibit some default behaviours when processing flows that can be controlled via system properties:

 * Processing of a given flow will halt when the first assertion error is encountered. It might be the case that you really want to provoke the system with the _second_ interaction in a flow without having to fix the assertion errors in the _first_. In such cases setting `mctf.suppress.assertion=true` will allow all interactions in a flow to be processed.
 * Flows will be skipped during processing if they declare an implicit dependency on an Actor that is not part of the system under test. This behaviour can be suppressed by setting `mctf.suppress.system=true`
 * Flows will be skipped during processing if their basis flow has suffered an assertion failure. This behaviour is based on the assumption that the child flow is likely to suffer the same assertion failure as its parent, and there's little point in spamming the test results with duplicates of the same failure. This check can be suppressed by setting `mctf.suppress.basis=true`
 * Flows will be skipped if their dependency flows suffered an error during processing. The assumption here is that the dependent flow has no hope of success if the dependency failed. This check can be suppressed by setting `mctf.suppress.dependency=true`

## Inheritance health

This framework offers an inheritance mechanism to compress message data - instead of each flow carrying a complete copy of every message, they will typically hold a reference to an existing flow (the basis) and a set of updates that distinguish the new flow from the basis.
At runtime a flow's data is constructed on request by taking a copy of the basis and applying the updates.

This mechanism reduces runtime memory usage and reduces the effort required for widely-scoped changes to shared message fields, but it introduces a new risk - choosing the wrong inheritance basis.
If an inappropriate basis flow is chosen then many message updates must be made to get to the intended goal. Every message update is technical debt that should be avoided where possible.

The buildup of this debt can be monitored by adding a test that uses the [`InheritanceHealth`][InheritanceHealth] class, [for example][ExampleSystemTest].
Such a test computes metrics and a cost histogram about the actual inheritance hierarchy and a theoretical optimal hierarchy.
Every flow that is added will cause these metrics to increase, but if the actual metric increases more than the optimal then that's an indicator that a better inheritance basis exists for the new flow.

The [coppice tool](../../../../validation/coppice) can visualise this process and offers a way to find the best choice of inheritance basis.

Bear in mind that the inheritance health metrics are extremely rough guides - human considerations of code organisation should take precedence.

<!-- code_link_start -->

[InheritanceHealth]: ../../../../validation/validation-core/src/main/java/com/mastercard/test/flow/validation/InheritanceHealth.java
[ExampleSystemTest]: ../../../../example/app-model/src/test/java/com/mastercard/test/flow/example/app/model/ExampleSystemTest.java

<!-- code_link_end -->

## Beyond testing

As the system model exists in its own right independent of any test assertion mechanism, it can be used for more than just testing.
For example, the [example system diagram](../../../../example/README.md) is [generated][SystemDiagramTest] from the system model.

<!-- code_link_start -->

[SystemDiagramTest]: ../../../../example/app-model/src/test/java/com/mastercard/test/flow/example/app/model/SystemDiagramTest.java

<!-- code_link_end -->
