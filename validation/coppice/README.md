
<!-- title start -->

# coppice

Model optimisation tool
---
[![javadoc](https://javadoc.io/badge2/com.mastercard.test.flow/coppice/javadoc.svg)](https://javadoc.io/doc/com.mastercard.test.flow/coppice)

 * [../validation](..) Checking model consistency

<!-- title end -->

## Overview

This module provides a GUI tool to examine and optimise the inheritance structure of a system model.

## Motivation

The basic idea of flow-based system model is very simple: it's just a bunch of flows, where flows consist of a related set of messages. The naive implementation of that concept (just having a massive repository of messages stored in files) is difficult to work with - wide-ranging changes to message content are tedious to implement and worse to review. Every bit of message content defined in files or in source code should be regarded as technical debt that should be minimised.

This framework tries to solve this problem by compressing the data via an inheritance model - flows are typically defined by a reference to another flow (the inheritance basis) and a set of updates that are applied to that basis. This means that the parts of messages that are common to all flows are only defined once, at the inheritance root. Hence changes to those parts are easy to implement and review.

However, the inheritance model gives us a new problem - we now have to choose which flow to inherit from. Making a bad choice means we have to update a lot of fields and, as noted above, every message update we add to the flow is technical debt that might weigh down future development.

It's also quite easy to end up making redundant message updates - setting field values that the parent flow already has. Again, such updates are technical debt.

This tool offers a convenient way to combat these issues when working on an system model.

## Implementation

Simply construct a new `Coppice` instance and call examining() to supply the system model to examine, as in [CoppiceTest][CoppiceTest] and [CoppiceMain][CoppiceMain].

<!-- code_link_start -->

[CoppiceTest]: src/test/java/com/mastercard/test/flow/validation/coppice/CoppiceTest.java
[CoppiceMain]: ../../example/app-model/src/test/java/com/mastercard/test/flow/example/app/model/CoppiceMain.java

<!-- code_link_end -->

## Interface

The coppice interface will display a list of flows on the left (filterable by the text field above it) and a visualisation of the current inheritance structure in the main pane.

Right-clicking on a flow in any of the views will present 4 operations:

 * **View** - opens a pane to display the flow's content.
 * **Compare** - opens a pane to compare two flows' content. Drag-and-drop a flow from the list on the left or from a hierarchy's tree component to set the comparison partner.
 * **Optimise parent** - runs a search over the whole model to find the closest pair to the selected flow.
 * **Optimise children** - Runs a global-optimisation routine on the model: all existing hierarchy links are dissolved and then the minimum spanning tree of the complete diff graph is computed.

The bottom of hierarchy visualisations shows metrics on the health of the visible structure:
 * The `roots` figure is the total number of content lines of all of the hierarchy root flows.
 * The `diff` figure is the total number of content lines that change in each inheritance relationship
 * The histogram along the bottom shows the distribution of diff weights

Lower numbers are, in general, better for these metrics.

The "Diff distance filter" sliders allow you to highlight areas of particularly high and low diff weight.

The "Layout delay" slider controls the speed at which hierarchy graph visualisations are updated. Lower values will complete faster but you might end up with a messy layout.

## Usage

Coppice can be used:
 * To identify redundant field updates - use the **Compare** view to see the true differences between a parent and child, then compare those diffs against the updates being applied during flow derivation.
 * To identify a parent candidate for a new flow.
 * To motivate model refactors via the hierarchy health metrics.
 
## Caveats

Coppice computes the theoretically-optimal hierarchy, but it shouldn't be followed slavishly - considerations of code legibility and flexibility should take precedence.

For example, the optimal structures suggested by Coppice do not take code organisation into consideration, so you might find that logically-grouped flows are scattered throughout the hierarchy with different parents. We'd probably be better off paying the cost of sub-optimal inheritance in order to gain a less confusing code structure.
