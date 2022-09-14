
<!-- title start -->

# api

Core type declarations
[![javadoc](https://javadoc.io/badge2/com.mastercard.test.flow/api/javadoc.svg)](https://javadoc.io/doc/com.mastercard.test.flow/api)

 * [../flow](https://github.com/Mastercard/flow) Testing framework

<!-- title end -->

## Overview

This module defines the core types that define the structure of the system model:

 * [Actor][flow.Actor] - elements of the system that can exchange data with each other
 * [Message][flow.Message] - the data that Actors exchange
 * [Interaction][flow.Interaction] - Encapsulates the details of an exchange: The two actors involved and the request/response messages
 * [Flow][flow.Flow] - A contextualised and causally-linked sequence of Interactions
 * [Context][flow.Context] - an aspect of the environment in which a Flow is valid
 * [Residue][flow.Residue] - a persistent change that the flow leaves behind
 * [Metadata][flow.Metadata] - provides human-readable context for a Flow.
 * [Dependency][flow.Dependency] - a relationship between two Flows
 * [Model][flow.Model] - A set of Flows

<!-- code_link_start -->

[flow.Actor]: src/main/java/com/mastercard/test/flow/Actor.java
[flow.Message]: src/main/java/com/mastercard/test/flow/Message.java
[flow.Interaction]: src/main/java/com/mastercard/test/flow/Interaction.java
[flow.Flow]: src/main/java/com/mastercard/test/flow/Flow.java
[flow.Context]: src/main/java/com/mastercard/test/flow/Context.java
[flow.Residue]: src/main/java/com/mastercard/test/flow/Residue.java
[flow.Metadata]: src/main/java/com/mastercard/test/flow/Metadata.java
[flow.Dependency]: src/main/java/com/mastercard/test/flow/Dependency.java
[flow.Model]: src/main/java/com/mastercard/test/flow/Model.java

<!-- code_link_end -->

## Usage

It is unlikely that you'll need to depend directly on this artifact - it will be supplied transitively by [builder](../builder).
