# Build artifacts

One of the main features of the [flow testing framework](https://github.com/Mastercard/flow) is the production of rich execution reports.
Until such a time as [upload-artifact#14](https://github.com/actions/upload-artifact/issues/14) is addressed, we're reduced to abusing github pages to show these artifacts to best effect.
See [test.yml](https://github.com/Mastercard/flow/blob/main/.github/workflows/test.yml), [mutation.yml](https://github.com/Mastercard/flow/blob/main/.github/workflows/mutation.yml) and [regen_index.pl](https://github.com/Mastercard/flow/blob/pages/regen_index.pl) for the gory details.

## Execution reports

These reports are the result of comparing a unified model of system behaviour against:
 * an instance of the complete system (The "app-itest" report)
 * system components in isolation (everything else)

<!-- start:execution -->
<table>
	<tbody>
		<tr> <th><code>latest</code></th>
			 <th><code>dependabot/github_actions/github/codeql-action-3.25.11</code></th>
			<td><a href="execution/latest/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/latest/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/latest/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/latest/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/latest/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/latest/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/latest/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-01T00:19:01</code></th>
			 <th><code>dependabot/github_actions/github/codeql-action-3.25.11</code></th>
			<td><a href="execution/1719793141/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1719793141/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1719793141/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1719793141/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1719793141/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1719793141/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1719793141/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-06-28T10:02:04</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1719568924/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1719568924/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1719568924/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1719568924/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1719568924/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1719568924/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1719568924/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-06-28T09:46:34</code></th>
			 <th><code>angular_component_structure</code></th>
			<td><a href="execution/1719567994/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1719567994/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1719567994/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1719567994/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1719567994/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1719567994/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1719567994/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-06-28T09:09:47</code></th>
			 <th><code>angular_component_structure</code></th>
			<td><a href="execution/1719565787/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1719565787/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1719565787/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1719565787/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1719565787/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1719565787/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1719565787/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-06-27T15:58:02</code></th>
			 <th><code>angular_component_structure</code></th>
			<td><a href="execution/1719503882/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1719503882/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1719503882/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1719503882/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1719503882/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1719503882/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1719503882/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-06-24T07:48:05</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1719215285/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1719215285/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1719215285/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1719215285/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1719215285/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1719215285/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1719215285/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-06-24T07:24:38</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/multi-e091cc75b0</code></th>
			<td><a href="execution/1719213878/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1719213878/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1719213878/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1719213878/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1719213878/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1719213878/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1719213878/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-06-24T07:22:11</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1719213731/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1719213731/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1719213731/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1719213731/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1719213731/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1719213731/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1719213731/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-06-24T01:31:00</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/types/node-20.14.8</code></th>
			<td><a href="execution/1719192660/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1719192660/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1719192660/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1719192660/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1719192660/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1719192660/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1719192660/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-06-21T10:59:39</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1718967579/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1718967579/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1718967579/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1718967579/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1718967579/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1718967579/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1718967579/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-06-21T10:14:37</code></th>
			 <th><code>doc_tweak</code></th>
			<td><a href="execution/1718964877/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1718964877/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1718964877/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1718964877/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1718964877/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1718964877/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1718964877/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-06-17T07:16:45</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1718608605/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1718608605/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1718608605/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1718608605/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1718608605/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1718608605/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1718608605/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-06-17T07:15:42</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1718608542/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1718608542/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1718608542/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1718608542/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1718608542/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1718608542/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1718608542/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-06-17T07:15:18</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1718608518/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1718608518/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1718608518/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1718608518/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1718608518/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1718608518/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1718608518/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-06-17T07:14:35</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1718608475/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1718608475/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1718608475/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1718608475/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1718608475/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1718608475/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1718608475/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-06-17T01:18:07</code></th>
			 <th><code>dependabot/github_actions/github/codeql-action-3.25.10</code></th>
			<td><a href="execution/1718587087/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1718587087/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1718587087/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1718587087/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1718587087/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1718587087/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1718587087/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-06-17T01:17:47</code></th>
			 <th><code>dependabot/github_actions/actions/checkout-4.1.7</code></th>
			<td><a href="execution/1718587067/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1718587067/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1718587067/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1718587067/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1718587067/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1718587067/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1718587067/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-06-17T00:50:20</code></th>
			 <th><code>dependabot/maven/org.apache.maven.plugins-maven-surefire-plugin-3.3.0</code></th>
			<td><a href="execution/1718585420/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1718585420/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1718585420/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1718585420/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1718585420/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1718585420/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1718585420/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-06-17T00:49:46</code></th>
			 <th><code>dependabot/maven/org.apache.maven.plugins-maven-release-plugin-3.1.0</code></th>
			<td><a href="execution/1718585386/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1718585386/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1718585386/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1718585386/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1718585386/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1718585386/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1718585386/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-06-13T12:16:04</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1718280964/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1718280964/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1718280964/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1718280964/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1718280964/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1718280964/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1718280964/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
	</tbody>
</table>
<!-- end:execution -->

## Mutation testing

Test quality metrics for framework packages.

<!-- start:mutation -->
<table>
	<tbody>
		<tr> <th><code>latest</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/latest/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-06-28T10:08:17</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1719569297/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-06-28T09:52:25</code></th>
			 <th><code>angular_component_structure</code></th>
			<td><a href="mutation/1719568345/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-06-28T09:16:44</code></th>
			 <th><code>angular_component_structure</code></th>
			<td><a href="mutation/1719566204/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-06-27T16:04:11</code></th>
			 <th><code>angular_component_structure</code></th>
			<td><a href="mutation/1719504251/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-06-24T07:54:01</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1719215641/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-06-24T07:32:31</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/multi-e091cc75b0</code></th>
			<td><a href="mutation/1719214351/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-06-24T07:28:02</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1719214082/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-06-24T01:37:27</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/types/node-20.14.8</code></th>
			<td><a href="mutation/1719193047/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-06-21T11:05:15</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1718967915/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-06-21T10:21:08</code></th>
			 <th><code>doc_tweak</code></th>
			<td><a href="mutation/1718965268/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-06-17T07:22:41</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1718608961/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-06-17T07:22:19</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1718608939/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-06-17T07:21:40</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1718608900/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-06-17T07:21:17</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1718608877/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-06-17T01:25:11</code></th>
			 <th><code>dependabot/github_actions/actions/checkout-4.1.7</code></th>
			<td><a href="mutation/1718587511/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-06-17T01:24:50</code></th>
			 <th><code>dependabot/github_actions/github/codeql-action-3.25.10</code></th>
			<td><a href="mutation/1718587490/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-06-17T00:56:10</code></th>
			 <th><code>dependabot/maven/org.apache.maven.plugins-maven-release-plugin-3.1.0</code></th>
			<td><a href="mutation/1718585770/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-06-17T00:55:32</code></th>
			 <th><code>dependabot/maven/org.apache.maven.plugins-maven-surefire-plugin-3.3.0</code></th>
			<td><a href="mutation/1718585732/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-06-13T12:22:38</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1718281358/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-06-13T12:06:45</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1718280405/mutation_report/index.html">mutation</a></td>
		</tr>
	</tbody>
</table>
<!-- end:mutation -->

## Angular coverage

Test coverage for the report application.

<!-- start:ng_coverage -->
<table>
	<tbody>
		<tr> <th><code>latest</code></th>
			 <th><code>dependabot/github_actions/github/codeql-action-3.25.11</code></th>
			<td><a href="ng_coverage/latest/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-01T00:19:01</code></th>
			 <th><code>dependabot/github_actions/github/codeql-action-3.25.11</code></th>
			<td><a href="ng_coverage/1719793141/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-06-28T10:02:04</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1719568924/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-06-28T09:46:34</code></th>
			 <th><code>angular_component_structure</code></th>
			<td><a href="ng_coverage/1719567994/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-06-28T09:09:47</code></th>
			 <th><code>angular_component_structure</code></th>
			<td><a href="ng_coverage/1719565787/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-06-27T15:58:02</code></th>
			 <th><code>angular_component_structure</code></th>
			<td><a href="ng_coverage/1719503882/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-06-24T07:48:05</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1719215285/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-06-24T07:24:38</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/multi-e091cc75b0</code></th>
			<td><a href="ng_coverage/1719213878/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-06-24T07:22:11</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1719213731/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-06-24T01:31:00</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/types/node-20.14.8</code></th>
			<td><a href="ng_coverage/1719192660/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-06-21T10:59:39</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1718967579/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-06-21T10:14:37</code></th>
			 <th><code>doc_tweak</code></th>
			<td><a href="ng_coverage/1718964877/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-06-17T07:16:45</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1718608605/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-06-17T07:15:42</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1718608542/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-06-17T07:15:18</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1718608518/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-06-17T07:14:35</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1718608475/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-06-17T01:18:07</code></th>
			 <th><code>dependabot/github_actions/github/codeql-action-3.25.10</code></th>
			<td><a href="ng_coverage/1718587087/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-06-17T01:17:47</code></th>
			 <th><code>dependabot/github_actions/actions/checkout-4.1.7</code></th>
			<td><a href="ng_coverage/1718587067/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-06-17T00:50:20</code></th>
			 <th><code>dependabot/maven/org.apache.maven.plugins-maven-surefire-plugin-3.3.0</code></th>
			<td><a href="ng_coverage/1718585420/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-06-17T00:49:46</code></th>
			 <th><code>dependabot/maven/org.apache.maven.plugins-maven-release-plugin-3.1.0</code></th>
			<td><a href="ng_coverage/1718585386/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-06-13T12:16:04</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1718280964/report/index.html">ng_coverage</a></td>
		</tr>
	</tbody>
</table>
<!-- end:ng_coverage -->