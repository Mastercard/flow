
<!-- title start -->

# report

Visualising assertion results



 * [../flow](https://github.com/Mastercard/flow) Testing framework
 * [report-core](report-core) Report input/output
 * [report-ng](report-ng) Report webapp

<!-- title end -->

## Concept

We want the report that contains the results of a test run to be both human-readable and machine-readable - goals that can be mutually contradictory.
The approach we've taken is to encode the results of a test run into JSON structures and embed those structures in HTML files.
The HTML is imbued with javascript that renders the data for humans, while it's trivial for machines to extract and parse the JSON from the HTML wrappers.

The report file structure is:

```
root_dir/
├ index.html
├ res/
│ └ <webapp resources>
└ detail/
  ├ <flow_id_hash>.html
  ├ ...
  └ <flow_id_hash>.html
```

Each file in the `detail` subdirectory contains the data for a single flow.
The file name is a hash of the flow's identity (the combination of description and tags).
The index contains a mapping from human-readable flow identities to the filename where full details can be found.

## Functionality

### Index

The index view shows a list of flows in the report.
The list can be filtered on tag inclusion and exclusion, and on regular expression match on flow descriptions.

Tag filters can be added by clicking on a tag in the index, or by typing in the "Include" and "Exclude" fields in the "Filters" expansion panel.
Tags can be dragged between the "Include" and "Exclude" sets.

Clicking on a flow in the list will take you to the relevant detail page.
The model diff tool is accessible via the hamburger menu on the top-left

### Model Diff

The model diff tool shows the differences between two system models. This is useful as an aid to a change review process.

#### Source data

The expansion panel at the top controls the two models that are being controlled. By default the current report is used, but enter a URL or relative path to diff against another model.
The progress bars show how much of a model's flow detail data has been loaded.

#### Pairing

The tool works by comparing pairs of flows, one from each model.

The first tab shows the current set of comparison pairs.
This is automatically populated by those flows that can be unambiguously related - those with the same description and tag sets.
If these pairings are not appropriate, they can be broken by clicking on the unlink button between the two flows.

The second tab shows the set of unpaired flows.
These will be presented as removed and added items in the changes and analysis views.
Related flows that have been missed in the automatic pairing (i.e.: those where the description or tags have been updated) can be paired by dragging them in their respective lists to be in the same position, then clicking the link button between them.

#### Changes

The third tab shows the comparison results.
This is a typical change review interface, I'm sure you're familiar with its ilk.

#### Analysis

The fourth tab shows compiled statistics on the changeset:
 * How many flows have been removed, what tags they bear, what tags have been entirely deleted
 * How many flows have been added, what tags they bear, what tags have been newly introduced
 * A summary of the changes: for each distinct diff a list of flows that underwent that change is compiled. Diffs that affected the same set of flows are presented together.
  It is hoped that this will make it easier to review changes where wide-ranging changes are introduced: an improvement over clicking through multiple pages of the same diff being applied to many similar flows.
 * How many flows have been left unchanged, what tags they bear, what tags have been completely uninvolved in the changes

---

Unfortunately, the model diff tool will not function when browsed via a `file://` url.

### Detail

The flow detail page shows all aspects of a flow and the results of comparing it against the actual system.

#### Sequence

Clicking on the requests and responses will show the message data for that transmission, of which various aspects can be displayed using the icons on the left:
 * The expected message, as defined in the system model
 * The difference between the expected and actual message
 * The actual message, as captured from the system under test
 * The differences between the message in this flow and the same message in the basis flow. This highlights the interesting data in the flow. This feature will not function when browsing on a `file://` url.

For the expected and actual messages, the data can be displayed in a human-readable format, or the bytes can be interpreted as UTF8 or as raw data in a hexdump.

For interactions where we have captured actual data from the system under test the result of comparing that data against expectations, and hence test success, is reflected in the sequence diagram.
For most systems it will not be possible to directly compare captured messages against expectations - there may be unpredictable fields in the messages that have to be masked out before a useful comparison can be made.
The percentage figure presented against messages gives an indication of how heavily messages have been masked before comparison.
 * A low figure indicates that extensive masking has been performed before assertion, so we can have limited confidence that our test data matches reality.
 * A high figure indicates that minimal masking has been applied - our test data is a good represntation of system behaviour.

Obviously, higher figures are better.

Clicking the search icon in the top-right of the sequence view allows message content to be searched - messages that contain the search term will be highlighted in the sequence diagram and occurrences of the term will be highlighted in message content.

#### Context

The context tab, if present, displays the data that represents the context in which the flow is valid.
The obvious example of this would be any non-standard system configuration.

#### Logs

The logs tab shows system logs that were captured while the flow was exercised.
The time column shows the elapsed time since the start of flow processing and the delta from the previous log event. The absolute timestamp is shown in a tooltip.

## Usage

This module is an organisational parent pom, and so does not produce any artifacts.
