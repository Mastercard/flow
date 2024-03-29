
<!-- title start -->

# example

Service constellation to exercise the flow framework



 * [../flow](https://github.com/Mastercard/flow) Testing framework

 Application libraries:
 
 * [app-framework](app-framework) Library providing a simple microservice framework
 * [app-api](app-api) Library providing interfaces that define the REST API for each service in the example application

Services:

 * [app-web-ui](app-web-ui) Front-end service that presents an HTML interface to the system
 * [app-ui](app-ui) Front-end service that accepts external requests
 * [app-core](app-core) Service that orchestrates functionality between services in the example application
 * [app-histogram](app-histogram) Service that counts characters in a given set of text
 * [app-queue](app-queue) Service that handles deferred processing
 * [app-store](app-store) Service that provides data storage

Test modules:

 * [app-model](app-model) Library providing system description flows for creating tests with the Flows framework
 * [app-assert](app-assert) Test library providing shared assertion components
 * [app-itest](app-itest) System integration test suite for exercising app instances

<!-- title end -->

## Example Application Overview

These services present operations that can produce a histogram of specific
characters within a target string. This simple functionality is decomposed
into several services so that the Flow framework can be used to test and illustrate
the data flows between them.

The diagram below illustrates the services and their interdependencies.

<!-- system_diagram_start -->

```mermaid
graph TD
    OPS(OPS<br>Provokes queue) -- POST --> QUEUE[QUEUE<br>Stores and processes<br>deferred operations]
    USER([USER<br>Needs characters counted]) -- GET/POST --> UI[UI<br>HTTP interface]
    USER -- browser --> WEB_UI[WEB_UI<br>Browser interface]
    subgraph example system
    CORE[CORE<br>Orchestrates processing] -- POST --> HISTOGRAM[HISTOGRAM<br>Counts characters]
    CORE -- GET/POST --> QUEUE
    QUEUE -- POST --> CORE
    QUEUE -- DELETE/GET/PUT --> STORE[STORE<br>Key/Value store]
    STORE -- SQL --> DB[(DB<br>)]
    UI -- GET/POST --> CORE
    WEB_UI -- POST --> UI
    end
```

<!-- system_diagram_end -->

<details>
<summary>Artifact dependency structure</summary>
    Solid lines are <code>compile</code>-scope dependencies, dotted are <code>test</code>-scope.

<!-- start_module_diagram:example -->

```mermaid
graph LR
  subgraph core
    builder[<a href='https://github.com/Mastercard/flow/tree/main/builder'>builder</a>]
    model[<a href='https://github.com/Mastercard/flow/tree/main/model'>model</a>]
  end
  subgraph message
    message-http[<a href='https://github.com/Mastercard/flow/tree/main/message/message-http'>message-http</a>]
    message-json[<a href='https://github.com/Mastercard/flow/tree/main/message/message-json'>message-json</a>]
    message-sql[<a href='https://github.com/Mastercard/flow/tree/main/message/message-sql'>message-sql</a>]
    message-text[<a href='https://github.com/Mastercard/flow/tree/main/message/message-text'>message-text</a>]
    message-web[<a href='https://github.com/Mastercard/flow/tree/main/message/message-web'>message-web</a>]
  end
  subgraph validation
    validation-junit5[<a href='https://github.com/Mastercard/flow/tree/main/validation/validation-junit5'>validation-junit5</a>]
    coppice[<a href='https://github.com/Mastercard/flow/tree/main/validation/coppice'>coppice</a>]
  end
  subgraph assert
    assert-junit5[<a href='https://github.com/Mastercard/flow/tree/main/assert/assert-junit5'>assert-junit5</a>]
    duct[<a href='https://github.com/Mastercard/flow/tree/main/report/duct'>duct</a>]
  end
  subgraph example
    app-api[<a href='https://github.com/Mastercard/flow/tree/main/example/app-api'>app-api</a>]
    app-assert[<a href='https://github.com/Mastercard/flow/tree/main/example/app-assert'>app-assert</a>]
    app-core[<a href='https://github.com/Mastercard/flow/tree/main/example/app-core'>app-core</a>]
    app-framework[<a href='https://github.com/Mastercard/flow/tree/main/example/app-framework'>app-framework</a>]
    app-histogram[<a href='https://github.com/Mastercard/flow/tree/main/example/app-histogram'>app-histogram</a>]
    app-itest[<a href='https://github.com/Mastercard/flow/tree/main/example/app-itest'>app-itest</a>]
    app-model[<a href='https://github.com/Mastercard/flow/tree/main/example/app-model'>app-model</a>]
    app-queue[<a href='https://github.com/Mastercard/flow/tree/main/example/app-queue'>app-queue</a>]
    app-store[<a href='https://github.com/Mastercard/flow/tree/main/example/app-store'>app-store</a>]
    app-ui[<a href='https://github.com/Mastercard/flow/tree/main/example/app-ui'>app-ui</a>]
    app-web-ui[<a href='https://github.com/Mastercard/flow/tree/main/example/app-web-ui'>app-web-ui</a>]
  end
  app-api --> app-web-ui
  app-api --> app-ui
  app-api --> app-core
  app-api --> app-histogram
  app-api --> app-queue
  app-api --> app-store
  app-api --> app-model
  app-assert -.-> app-web-ui
  app-assert -.-> app-ui
  app-assert -.-> app-core
  app-assert -.-> app-histogram
  app-assert -.-> app-queue
  app-assert -.-> app-store
  app-assert -.-> app-itest
  app-core --> app-itest
  app-framework --> app-api
  app-histogram --> app-itest
  app-model --> app-assert
  app-queue --> app-itest
  app-store --> app-itest
  app-ui --> app-itest
  app-web-ui --> app-itest
  assert-junit5 --> app-assert
  builder --> app-model
  coppice -.-> app-model
  duct --> app-assert
  message-http --> app-model
  message-json --> app-model
  message-sql --> app-model
  message-text --> app-model
  message-web --> app-model
  model --> app-model
  validation-junit5 -.-> app-model

  %% keeps subgraphs positioned correctly
  core ~~~ message
  message ~~~ validation
  validation ~~~ assert
```

<!-- end_module_diagram -->
</details>

## Starting the services

To exercise this application, start each of the individual services.
For example, each service can be started in Maven in a separate terminal.
The commands below are executed from the main "example" directory.

For example:

```shell
mvn exec:java -pl app-core
```

To start the service listening on a specific port, use
the `--port` option during invocation like so:

```shell
mvn exec:java -pl app-ui -Dexec.args="--port 8080"
```

This is particularly helpful with the `app-ui` service so it always
starts on a consistent port.

**Alternatively** all services can be started using the `Main` class
in `app-itest`:

```shell
mvn exec:java -pl app-itest
```

When the cluster is started, this will output a line indicating which
port the UI service is listening on:

```
...
[com.mastercard.test.flow.example.app.itest.Main.main()] INFO com.mastercard.test.flow.example.app.itest.Main - Cluster started with UI on port 60783
```


## Exercising the services

Once the services are up and running, you can send requests to the `app-ui` service.

### Examples

#### A simple histogram

```shell
$ curl -X POST -H "Content-Type: text/plain" --data "the text" http://localhost:8080/histogram
{" ":1,"e":2,"h":1,"t":3,"x":1}
```

#### A deferred histogram

```shell
# submit the data and get back an ID
$ curl -X POST -H "Content-Type: text/plain" --data "the text" http://localhost:8080/histogram/deferred
9e8d6cc8-b66f-48ff-80aa-e51225f1c77b

# query the status of the request
$ curl http://localhost:8080/histogram/deferred/9e8d6cc8-b66f-48ff-80aa-e51225f1c77b
COMPLETE

# Retrieve the results
$ curl -X POST http://localhost:8080/histogram/deferred/9e8d6cc8-b66f-48ff-80aa-e51225f1c77b
{" ":1,"t":3,"e":2,"h":1,"x":1}
```

## Testing the application using Flows

Each service includes its own set of tests based on Flows. Running the tests
will generate flow diagrams in `target/mctf` that show the scenarios under
test and what messaging was asserted.

There is also self-contained functional integration tests in the `app-itest`
module that starts all services and tests the flows.

All of these tests are based on the same 
[set of Flows in the app-model module](app-model).
This demonstrates how a single change to the Model can be used to update service
tests and system integration tests.
