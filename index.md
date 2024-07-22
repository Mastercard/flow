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
			 <th><code>dependabot/npm_and_yarn/report/report-ng/babel/core-7.24.9</code></th>
			<td><a href="execution/latest/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/latest/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/latest/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/latest/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/latest/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/latest/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/latest/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-22T01:01:05</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/babel/core-7.24.9</code></th>
			<td><a href="execution/1721610065/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1721610065/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1721610065/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1721610065/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1721610065/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1721610065/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1721610065/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-22T01:00:43</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/zone.js-0.14.8</code></th>
			<td><a href="execution/1721610043/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1721610043/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1721610043/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1721610043/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1721610043/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1721610043/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1721610043/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-22T00:59:42</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/types/node-20.14.11</code></th>
			<td><a href="execution/1721609982/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1721609982/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1721609982/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1721609982/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1721609982/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1721609982/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1721609982/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-22T00:59:10</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/jasmine-core-5.2.0</code></th>
			<td><a href="execution/1721609950/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1721609950/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1721609950/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1721609950/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1721609950/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1721609950/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1721609950/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-22T00:56:47</code></th>
			 <th><code>dependabot/github_actions/github/codeql-action-3.25.13</code></th>
			<td><a href="execution/1721609807/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1721609807/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1721609807/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1721609807/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1721609807/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1721609807/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1721609807/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-22T00:19:30</code></th>
			 <th><code>dependabot/maven/org.apache.maven.plugins-maven-javadoc-plugin-3.8.0</code></th>
			<td><a href="execution/1721607570/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1721607570/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1721607570/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1721607570/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1721607570/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1721607570/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1721607570/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-15T07:54:36</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1721030076/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1721030076/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1721030076/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1721030076/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1721030076/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1721030076/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1721030076/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-15T07:37:33</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1721029053/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1721029053/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1721029053/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1721029053/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1721029053/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1721029053/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1721029053/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-15T07:35:42</code></th>
			 <th><code>dependabot/github_actions/actions/setup-node-4.0.3</code></th>
			<td><a href="execution/1721028942/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1721028942/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1721028942/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1721028942/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1721028942/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1721028942/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1721028942/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-15T07:34:21</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1721028861/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1721028861/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1721028861/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1721028861/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1721028861/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1721028861/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1721028861/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-15T07:34:01</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1721028841/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1721028841/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1721028841/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1721028841/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1721028841/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1721028841/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1721028841/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-15T07:32:16</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1721028736/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1721028736/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1721028736/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1721028736/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1721028736/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1721028736/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1721028736/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-15T01:08:35</code></th>
			 <th><code>dependabot/github_actions/github/codeql-action-3.25.12</code></th>
			<td><a href="execution/1721005715/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1721005715/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1721005715/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1721005715/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1721005715/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1721005715/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1721005715/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-15T01:08:12</code></th>
			 <th><code>dependabot/maven/org.apache.maven.plugins-maven-release-plugin-3.1.1</code></th>
			<td><a href="execution/1721005692/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1721005692/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1721005692/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1721005692/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1721005692/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1721005692/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1721005692/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-15T01:07:52</code></th>
			 <th><code>dependabot/maven/org.apache.maven.plugins-maven-surefire-plugin-3.3.1</code></th>
			<td><a href="execution/1721005672/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1721005672/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1721005672/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1721005672/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1721005672/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1721005672/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1721005672/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-15T00:14:55</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/babel/core-7.24.8</code></th>
			<td><a href="execution/1721002495/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1721002495/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1721002495/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1721002495/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1721002495/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1721002495/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1721002495/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-12T07:31:49</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1720769509/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1720769509/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1720769509/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1720769509/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1720769509/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1720769509/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1720769509/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-09T07:25:11</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1720509911/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1720509911/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1720509911/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1720509911/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1720509911/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1720509911/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1720509911/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-09T07:24:29</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1720509869/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1720509869/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1720509869/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1720509869/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1720509869/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1720509869/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1720509869/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-09T07:18:21</code></th>
			 <th><code>dependabot/github_actions/actions/download-artifact-4.1.8</code></th>
			<td><a href="execution/1720509501/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1720509501/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1720509501/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1720509501/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1720509501/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1720509501/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1720509501/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
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
			 <th><code>dependabot/npm_and_yarn/report/report-ng/jasmine-core-5.2.0</code></th>
			<td><a href="mutation/latest/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-22T01:04:45</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/jasmine-core-5.2.0</code></th>
			<td><a href="mutation/1721610285/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-22T01:03:23</code></th>
			 <th><code>dependabot/github_actions/github/codeql-action-3.25.13</code></th>
			<td><a href="mutation/1721610203/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-22T00:25:42</code></th>
			 <th><code>dependabot/maven/org.apache.maven.plugins-maven-javadoc-plugin-3.8.0</code></th>
			<td><a href="mutation/1721607942/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-15T07:59:59</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1721030399/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-15T07:43:31</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1721029411/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-15T07:41:24</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1721029284/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-15T07:39:47</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1721029187/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-15T07:37:51</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1721029071/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-15T07:29:22</code></th>
			 <th><code>dependabot/github_actions/github/codeql-action-3.25.12</code></th>
			<td><a href="mutation/1721028562/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-15T01:14:22</code></th>
			 <th><code>dependabot/maven/org.apache.maven.plugins-maven-release-plugin-3.1.1</code></th>
			<td><a href="mutation/1721006062/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-15T01:14:03</code></th>
			 <th><code>dependabot/github_actions/actions/setup-node-4.0.3</code></th>
			<td><a href="mutation/1721006043/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-15T01:13:36</code></th>
			 <th><code>dependabot/maven/org.apache.maven.plugins-maven-surefire-plugin-3.3.1</code></th>
			<td><a href="mutation/1721006016/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-15T00:21:17</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/babel/core-7.24.8</code></th>
			<td><a href="mutation/1721002877/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-12T07:37:32</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1720769852/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-09T07:31:02</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1720510262/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-09T07:30:28</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1720510228/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-08T09:02:15</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1720429335/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-08T01:00:11</code></th>
			 <th><code>dependabot/github_actions/actions/upload-artifact-4.3.4</code></th>
			<td><a href="mutation/1720400411/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-08T00:59:54</code></th>
			 <th><code>dependabot/maven/com.fasterxml.jackson.core-jackson-databind-2.17.2</code></th>
			<td><a href="mutation/1720400394/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-08T00:59:34</code></th>
			 <th><code>dependabot/github_actions/actions/download-artifact-4.1.8</code></th>
			<td><a href="mutation/1720400374/mutation_report/index.html">mutation</a></td>
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
			 <th><code>dependabot/npm_and_yarn/report/report-ng/babel/core-7.24.9</code></th>
			<td><a href="ng_coverage/latest/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-22T01:01:05</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/babel/core-7.24.9</code></th>
			<td><a href="ng_coverage/1721610065/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-22T01:00:43</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/zone.js-0.14.8</code></th>
			<td><a href="ng_coverage/1721610043/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-22T00:59:42</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/types/node-20.14.11</code></th>
			<td><a href="ng_coverage/1721609982/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-22T00:59:10</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/jasmine-core-5.2.0</code></th>
			<td><a href="ng_coverage/1721609950/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-22T00:56:47</code></th>
			 <th><code>dependabot/github_actions/github/codeql-action-3.25.13</code></th>
			<td><a href="ng_coverage/1721609807/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-22T00:19:30</code></th>
			 <th><code>dependabot/maven/org.apache.maven.plugins-maven-javadoc-plugin-3.8.0</code></th>
			<td><a href="ng_coverage/1721607570/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-15T07:54:36</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1721030076/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-15T07:37:33</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1721029053/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-15T07:35:42</code></th>
			 <th><code>dependabot/github_actions/actions/setup-node-4.0.3</code></th>
			<td><a href="ng_coverage/1721028942/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-15T07:34:21</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1721028861/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-15T07:34:01</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1721028841/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-15T07:32:16</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1721028736/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-15T01:08:35</code></th>
			 <th><code>dependabot/github_actions/github/codeql-action-3.25.12</code></th>
			<td><a href="ng_coverage/1721005715/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-15T01:08:12</code></th>
			 <th><code>dependabot/maven/org.apache.maven.plugins-maven-release-plugin-3.1.1</code></th>
			<td><a href="ng_coverage/1721005692/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-15T01:07:52</code></th>
			 <th><code>dependabot/maven/org.apache.maven.plugins-maven-surefire-plugin-3.3.1</code></th>
			<td><a href="ng_coverage/1721005672/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-15T00:14:55</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/babel/core-7.24.8</code></th>
			<td><a href="ng_coverage/1721002495/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-12T07:31:49</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1720769509/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-09T07:25:11</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1720509911/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-09T07:24:29</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1720509869/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-09T07:18:21</code></th>
			 <th><code>dependabot/github_actions/actions/download-artifact-4.1.8</code></th>
			<td><a href="ng_coverage/1720509501/report/index.html">ng_coverage</a></td>
		</tr>
	</tbody>
</table>
<!-- end:ng_coverage -->