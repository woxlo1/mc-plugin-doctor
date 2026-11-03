# Initial Architecture

## Status and scope

This document records the repository foundation only. It does not claim that any
plugin analysis, command-line, desktop, web, or API functionality exists.
Product versioning remains unset.

## Phase 0 structure

- The root Gradle project owns repository-wide build conventions.
- `:core` is the first Java library boundary. It currently contains no production
  code; its test verifies the minimum Java runtime baseline.
- Further modules are introduced only when their implementation phase begins.
  Analyzer, reporting, CLI, desktop, web, and API modules are planned, not
  implemented.

## Technology decisions

| Area | Decision | Rationale |
| --- | --- | --- |
| Build | Gradle 8.14.3 with Kotlin DSL | Matches the product requirements and keeps build configuration typed. |
| Language | Java 17 toolchain | Uses the available LTS runtime and avoids adding Kotlin runtime or plugin dependencies in the foundation phase. |
| Module boundary | Gradle multi-project, initially `:core` | Establishes a reusable library boundary while keeping this phase free of analyzer or interface features. |
| Tests | JUnit Jupiter 5.12.2 | Provides the basic JVM test infrastructure required by Phase 0. |
| CI | GitHub Actions on pull requests and pushes to `develop` | Runs the same Gradle verification task for the integration branch. |

The JUnit dependency is test-only and is not included in a production artifact.
Its upstream metadata declares Eclipse Public License 2.0 and Eclipse
Distribution License 1.0. No production/runtime dependencies are present in
Phase 0. Third-party notices and license compatibility must be rechecked when
runtime dependencies are proposed.

## Planned boundaries

The following are architectural directions only; none are implemented in this
phase:

- Shared models and execution contracts belong in core.
- Analyzer implementations should be isolated from user interfaces.
- CLI, desktop, web, and API entry points should depend on shared application
  services rather than duplicate analysis logic.
- Reporting and external integrations should be separate from analysis
  implementations.
- Analysis of plugin artifacts must remain local by default and treat inputs as
  untrusted.

## Phase 0 verification plan

Run `gradle --no-daemon check` with Gradle 8.14.3 and Java 17. The task compiles
the module with compiler warnings treated as errors and runs the foundation
runtime-baseline test. GitHub Actions runs this check on pull requests targeting
`develop` and on pushes to `develop`.

## Deferred work

Core analysis contracts, analyzers, reports, CLI and UI behavior, configuration,
security and compatibility analysis, and product versioning are outside Phase 0.
They must not be presented as implemented until their own phase is complete.
