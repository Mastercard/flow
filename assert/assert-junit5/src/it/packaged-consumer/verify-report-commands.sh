#!/usr/bin/env bash
# Separate external public-Launcher host; this is not a Surefire compatibility waiver.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
: "${JAVA_HOME:?Set JAVA_HOME to Java 17}"
mkdir -p target/evidence
separator=:
if [[ "$OSTYPE" == msys* || "$OSTYPE" == cygwin* ]]; then separator=';'; fi

for junit in 5.10.0 6.0.3; do
  platform=1.10.0
  if [[ "$junit" == 6.0.3 ]]; then platform=6.0.3; fi
  mvn -B -nsu -Djunit.version="$junit" -Dplatform.version="$platform" \
    -DskipTests=false -Dmaven.test.skip=false -Dmaven.test.failure.ignore=false test-compile \
    org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath \
    -Dmdep.outputFile="target/report-command-$junit.classpath" \
    > "target/evidence/report-command-build-$junit.log" 2>&1
  dependencies=$(tr -d '\r\n' < "target/report-command-$junit.classpath")
  for parallel in false true; do
    for fault in false true; do
      for mixed in false true; do
        point="$junit-$parallel-$fault-$mixed"
        log="target/evidence/report-command-$point.log"
        expected=0
        native='started=3 passed=3 failed=0 aborted=0 containerFailed=0'
        bodies=3
        if [[ "$mixed" == true ]]; then
          expected=1
          native='started=3 passed=1 failed=1 aborted=1 containerFailed=0'
          bodies=2
        fi
        result=0
        "$JAVA_HOME/bin/java" \
          -Dconsumer.parallel="$parallel" -Dconsumer.report.fault="$fault" \
          -Dconsumer.report.mixed="$mixed" -Dflow.parallel="$parallel" \
          -Djunit.platform.launcher.interceptors.enabled="$parallel" \
          -Djunit.jupiter.execution.parallel.enabled=true \
          -Djunit.jupiter.execution.parallel.mode.default=concurrent \
          -Djunit.jupiter.execution.parallel.config.strategy=fixed \
          -Djunit.jupiter.execution.parallel.config.fixed.parallelism=12 \
          -Djunit.jupiter.execution.parallel.config.fixed.max-pool-size=20 \
          -Xlog:class+load=info:file="target/evidence/report-command-$point.classload.log" \
          -cp "target/test-classes$separator$dependencies" consumer.ReportCommand \
          > "$log" 2>&1 || result=$?
        if [[ "$result" != "$expected" ]]; then
          cat "$log"; echo "Expected exit $expected, got $result: $point"; exit 1
        fi
        grep -Fxq "REPORT-NATIVE $native" "$log"
        grep -Fxq "REPORT-CHECK fault=$fault mixed=$mixed bodies=$bodies" "$log"
        trace="target/evidence/report-command-$point.classload.log"
        grep 'org.junit.jupiter.api.TestFactory source:' "$trace" \
          | grep -Fq "/junit-jupiter-api/$junit/junit-jupiter-api-$junit.jar"
        grep 'org.junit.jupiter.engine.JupiterTestEngine source:' "$trace" \
          | grep -Fq "/junit-jupiter-engine/$junit/junit-jupiter-engine-$junit.jar"
        grep 'org.junit.platform.launcher.Launcher source:' "$trace" \
          | grep -Fq "/junit-platform-launcher/$platform/junit-platform-launcher-$platform.jar"
        grep 'org.junit.platform.engine.TestEngine source:' "$trace" \
          | grep -Fq "/junit-platform-engine/$platform/junit-platform-engine-$platform.jar"
        grep 'com.mastercard.test.flow.assrt.junit5.FlowExecution source:' "$trace" \
          | grep -Eq '/com/mastercard/test/flow/assert-junit5/.*\.jar$'
        diagnostics=$(grep -c '^Flow report failed: ' "$log" || true)
        if [[ "$fault" == true ]]; then
          [[ "$diagnostics" == 1 ]]
        else
          [[ "$diagnostics" == 0 ]]
        fi
        if [[ "$mixed" == true ]]; then
          grep -q 'controlled report-command SUT failure' "$log"
        fi
        echo "PASS report command $point exit=$result $native"
      done
    done
  done
done