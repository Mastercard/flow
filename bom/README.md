# bom

Bill of Materials

## Overview

This pom-only project offers a mechanism to coordinate the versions of the other flow artifacts that you consume.

## Usage

Import the bom into the `/project/dependencyManagement` section of your pom file:

```xml
<project>
  ...
  <dependencyManagement>
    <dependencies>
      <dependency>
        <!-- controls flow artifact versions -->
        <groupId>com.mastercard.test.flow</groupId>
        <artifactId>bom</artifactId>
        <version>x.y.x</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  ...
<project>
```
The latest release version is [![Maven Central](https://img.shields.io/maven-central/v/com.mastercard.test.flow/parent)](https://search.maven.org/search?q=com.mastercard.test.flow).

The versions of other flow artifacts in your `/project/dependencies` section will then automatically match that of the bom:

```xml
<project>
  ...
  <dependencies>
    <dependency>
      <!-- flow construction. Version controlled by bom import -->
      <groupId>com.mastercard.test.flow</groupId>
      <artifactId>builder</artifactId>
    </dependency>
    <dependency>
      <!-- flow grouping. Version controlled by bom import -->
      <groupId>com.mastercard.test.flow</groupId>
      <artifactId>group</artifactId>
    </dependency>
    <dependency>
      <!-- JSON messages. Version controlled by bom import -->
      <groupId>com.mastercard.test.flow</groupId>
      <artifactId>message-json</artifactId>
    </dependency>
  </dependencies>
  ...
<project>
```

At some point you'll find yourself having to track down the source of a dependency version, a task that is complicated by the use of bom imports.
Running `mvn help:effective-pom -Dverbose=true` will be instructive in such cases.
