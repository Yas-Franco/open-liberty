# AGENTS.md - Project Context for AI Agents

## Repository Structure

- `dev/` - Main source code directory for the project
  - All source code for Open Liberty
    - Java runtime
    - Unit tests
    - Functional tests (AKA functional acceptance tests or FATs)
  - Build scripts for building and testing
- `cla/` - Contributor License Agreements (corporate and individual)
- `.ci-orchestrator/` - CI/CD infrastructure

## Critical Documents

- `GENAI_GUIDELINES.md` - GenAI usage policy for code contributions
  - Agents MUST adhere to the AI code identification requirements outlined in this file, particularly around commit message requirements for commits that include AI-generated content

## Common Development Tasks

All of the following commands assume that you have cloned the repository and are in the `open-liberty/dev` directory.

### Building the product

These steps will build the Open Liberty runtime and all of its features. This is the recommended way to build the product.

**Prerequisite:** The `JAVA_HOME` environment variable must point to a Java 17 or Java 21 SDK. If setting `JAVA_HOME` to Java 17, you will also need to set `JAVA_21_HOME` to a Java 21 SDK.

```bash
$ ./gradlew cnf:initialize
$ ./gradlew assemble
```

#### Perform a local release

```bash
$ ./gradlew releaseNeeded
```

This task releases all projects to the local releaseRepo. The final openliberty zip can be found in:
> open-liberty/dev/cnf/release/dev/openliberty/<version>/openliberty-xxx.zip

### Building a single project

```bash
$ ./gradlew com.ibm.ws.kernel.boot:build
```

### Running unit tests

```bash
$ ./gradlew com.ibm.ws.kernel.boot:test
```

### Running functional tests

```bash
$ ./gradlew build.example_fat:buildandrun
```
