# Contributing to Flow

Please take a moment to review this document in order to make the contribution
process easy and effective for everyone involved.

Following these guidelines helps to communicate that you respect the time of
the developers managing and developing this open source project. In return,
they should reciprocate that respect in addressing your issue, assessing
changes, and helping you finalise your pull requests.

## Using the issue tracker

The issue tracker is the preferred channel for [bug reports](#bug-reports), 
[features requests](#feature-requests) and [submitting pull requests](#pull-requests), 
but please respect the following restrictions:

* Please **do not** use the issue tracker for personal support requests.

* Please **do not** derail or troll issues. Keep the discussion on topic and
  respect the opinions of others.


## Bug reports

A bug is a _demonstrable problem_ that is caused by the code in the repository.
Good bug reports are extremely helpful - thank you!

Guidelines for bug reports:

1. **Use the GitHub issue search** &mdash; check if the issue has already been
   reported.

2. **Check if the issue has been fixed** &mdash; try to reproduce it using the
   latest `master` or `next` branch in the repository.

3. **Isolate the problem** &mdash; ideally create a reduced test case.

A good bug report shouldn't leave others needing to chase you up for more
information. Please try to be as detailed as possible in your report. What is
your environment? What steps will reproduce the issue? What OS experiences the
problem? What would you expect to be the outcome? All these details will help
people to fix any potential bugs.

Example:

> Short and descriptive example bug report title
>
> A summary of the issue and the browser/OS environment in which it occurs. If
> suitable, include the steps required to reproduce the bug.
>
> 1. This is the first step
> 2. This is the second step
> 3. Further steps, etc.
>
> `<url>` - a link to the reduced test case
>
> Any other information you want to share that is relevant to the issue being
> reported. This might include the lines of code that you have identified as
> causing the bug, and potential solutions (and your opinions on their
> merits).


## Feature requests

Feature requests are welcome. But take a moment to find out whether your idea
fits with the scope and aims of the project. It's up to *you* to make a strong
case to convince the project's developers of the merits of this feature. Please
provide as much detail and context as possible.


## Pull requests

Good pull requests - patches, improvements, new features - are a fantastic
help. They should remain focused in scope and avoid containing unrelated
commits.

**Please ask first** before embarking on any significant pull request that is speculative in nature (e.g.
implementing new features, refactoring code), otherwise you risk spending a lot of
time working on something that the project's developers might not want to merge
into the project.
There's no need to ask for permission for things like fixing issues - the project has already expressed a desire for that work.

### For new Contributors

If you have never created a pull request before, welcome :tada: :smile: [Here is a great course](https://egghead.io/courses/how-to-contribute-to-an-open-source-project-on-github)
on how to create a pull request..

1. [Fork](http://help.github.com/fork-a-repo/) the project, clone your fork,
   and configure the remotes:

   ```bash
   # Clone your fork of the repo into the current directory
   git clone https://github.com/<your-username>/<repo-name>
   # Navigate to the newly cloned directory
   cd <repo-name>
   # Assign the original repo to a remote called "upstream"
   git remote add upstream https://github.com/hoodiehq/<repo-name>
   ```

2. If you cloned a while ago, get the latest changes from upstream:

   ```bash
   git checkout master
   git pull upstream master
   ```

3. Create a new topic branch (off the main project development branch) to
   contain your feature, change, or fix:

   ```bash
   git checkout -b <topic-branch-name>
   ```

4. Make sure to update, or add to the tests when appropriate. Patches and
   features will not be accepted without tests. Run `mvn test` to check that
   all tests pass after you've made changes. Look for a `Testing` section in
   the project’s README for more information.

5. If you added or changed a feature, make sure to document it accordingly in
   the `README.md` file.

6. Push your topic branch up to your fork:

   ```bash
   git push origin <topic-branch-name>
   ```

8. [Open a Pull Request](https://help.github.com/articles/using-pull-requests/)
    with a clear title and description.


**IMPORTANT**: By submitting a patch, you agree to license your work under the
same license as that used by the project.

## Conventions

### Code formatting

Code format is enforced using the eclipse code formatter - builds will fail if non-compliant code is found.
The format is controlled by [src/main/eclipse/mctf_format.xml](src/main/eclipse/mctf_format.xml).
If you're using eclipse you can import that format directly.
If you're using Intellij then [this plugin](https://plugins.jetbrains.com/plugin/6546-eclipse-code-formatter) should help.

The format can be applied with:

```
mvn formatter:format
```

POM files should be formatted with

```
mvn sortpom:sort
```

### Test coverage

We use [pitest](https://pitest.org/) to measure test coverage and quality.
You can run it for every project with:

```
mvn test org.pitest:pitest-maven:mutationCoverage
```

It takes a while to run, so if you're iterating to improve the coverage of a single module you'll probably be happier running it for just that module.
This can be achieved by putting a file named 'mutate' in that module's target directory and then compiling the tests, i.e.: from the root module:

```
touch <submodule>/target/mutate
mvn test-compile
```

Test results will be generated at
```
<submodule>/target/pit-reports/index.html
```

### Documentation

Correctly-formed javadoc should exist on all non-private elements.

