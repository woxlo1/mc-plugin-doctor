# Architecture Overview

## Status and scope

Phases 0 and 1 establish the repository foundation and reusable core analysis
engine. No plugin-specific analysis, command-line, desktop, web, or API product
entry point exists yet. Product versioning remains unset.

| Phase | Status |
| --- | --- |
| 0 — Repository Foundation | Implemented |
| 1 — Core Engine | Implemented |
| 2 — Basic JAR Analysis | Planned |
| 3 — Dependency Analysis | Planned |
| 4 — Compatibility Analysis | Planned |
| 5 — Bytecode Analysis | Planned |

## Modules

- The root Gradle project owns repository-wide build conventions.
- `:core` is a Java library module containing common analysis models, analyzer
  and rule contracts, configuration, logging, error reporting, and execution.
- Future analyzer implementations will consume the core contracts. Reporting
  and user interfaces should remain separate modules and reuse the core engine.

## Core contracts

- `AnalysisRequest` identifies a target path and immutable request attributes.
  The core currently passes the request through; it does not read the target.
- `Finding` records rule and analyzer identifiers, severity, category,
  description, evidence, optional locations and suggestions, and a confidence
  value constrained to the inclusive range 0–1.
- `PluginAnalyzer` declares immutable metadata and returns findings through the
  shared execution context. Its initialize/analyze/close lifecycle runs in the
  same timed worker for each execution; close is attempted even when
  initialization or analysis fails.
- `AnalyzerRegistry` rejects duplicate analyzer identifiers.
- `AnalysisConfiguration` provides analyzer enablement, options, timeout
  overrides, a default timeout, minimum result severity, and failure policy.
- `AnalysisEngine` orders analyzers by dependency and then priority. It reports
  missing dependencies and cycles, skips dependents of failed analyzers, applies
  per-analyzer timeouts, validates finding ownership, and continues independent
  analyzers when configured to do so.
- Analyzer timeout enforcement interrupts a worker thread. Implementations that
  ignore interruption may continue in a daemon thread; analyzers must cooperate
  with interruption. The current engine does not provide a process sandbox.
- `AnalysisRule` evaluates findings in registration order. Rules can keep,
  suppress, or change severity; rule failures are reported without discarding
  the finding or preventing later rules from running.
- `AnalysisLogger` abstracts diagnostics; `JdkAnalysisLogger` adapts it to
  `java.util.logging`.

The public Java API currently supports an in-process caller. There is no
configuration file loader, analyzer classpath discovery, or external plugin
loading. Analyzer implementations are trusted code and must not be loaded from
untrusted artifacts.

## Technology decisions

| Area | Decision | Rationale |
| --- | --- | --- |
| Build | Gradle 8.14.3 with Kotlin DSL | Matches the product requirements and keeps build configuration typed. |
| Language | Java 17 toolchain | Uses the available LTS runtime and avoids adding an unnecessary Kotlin runtime dependency. |
| Module boundary | Gradle multi-project, currently `:core` | Establishes a reusable library boundary and adds modules only when implemented. |
| Tests | JUnit Jupiter 5.12.2 | Provides JVM unit tests; dependencies are test-only. |
| Logging | JDK `java.util.logging` adapter behind a core interface | Avoids introducing a runtime logging framework dependency. |
| CI | GitHub Actions on pull requests and pushes to `develop` | Runs wrapper validation and Gradle verification for integration changes. |

JUnit's upstream metadata declares Eclipse Public License 2.0 and Eclipse
Distribution License 1.0. No production/runtime dependencies are present.
Third-party notices and license compatibility must be rechecked when runtime
dependencies are proposed.

## Deferred boundaries

JAR reading and plugin descriptors, dependency graph analysis, compatibility
and class-file analysis, bytecode inspection, reports, CLI, desktop, web/API,
file-backed configuration, and external analyzer loading are not implemented.
They must not be described as available until completed and verified in their
respective phases. Analysis of plugin artifacts must remain local by default
and treat input files as untrusted.

## Verification

Run `gradlew.bat check` on Windows or `./gradlew check` on Unix-like systems with
JDK 17. The core compiler enables `-Xlint:all -Werror`. Tests cover the runtime
baseline, model validation and immutability, configuration defaults and
validation, dependency/priority ordering, analyzer failures, missing
dependencies, cycles, timeout recovery, severity filtering, rule ordering,
suppression, and rule failures.
