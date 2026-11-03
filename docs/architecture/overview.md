# Architecture Overview

## Status and scope

Phases 0 and 1 establish the repository foundation and reusable core analysis
engine. Phase 2 adds bounded local JAR inspection, inventories, manifest
parsing, and supported plugin descriptor analysis. Phase 3 adds dependency
graphs and cross-artifact dependency, duplicate-class, and plugin-identity
conflict analysis. Phase 4 adds Java/class-file version checks, platform
descriptor compatibility, constant-pool API references, and catalog-driven
deprecated/removed API findings. Phase 5 adds ASM-based class, method, and field
exploration, bytecode reference graphs, and in-memory bytecode reports. No
command-line, desktop, web, or API product entry point exists yet. Product
versioning remains unset.

| Phase | Status |
| --- | --- |
| 0 — Repository Foundation | Implemented |
| 1 — Core Engine | Implemented |
| 2 — Basic JAR Analysis | Implemented |
| 3 — Dependency Analysis | Implemented |
| 4 — Compatibility Analysis | Implemented |
| 5 — Bytecode Analysis | Implemented |

## Modules

- The root Gradle project owns repository-wide build conventions.
- `:core` is a Java library module containing common analysis models, analyzer
  and rule contracts, configuration, logging, error reporting, execution, and
  JAR inspection, dependency analysis, profile-based compatibility analysis,
  and ASM-based bytecode analysis.
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
| Bytecode | ASM 9.7.1 | Provides bounded class, member, and instruction visitation without implementing a bytecode parser. |
| CI | GitHub Actions on pull requests and pushes to `develop` | Runs wrapper validation and Gradle verification for integration changes. |

ASM 9.7.1 declares BSD-3-Clause in its published Maven metadata. JUnit's
upstream metadata declares Eclipse Public License 2.0 and Eclipse
Distribution License 1.0. Jackson Databind and its Jackson Core and Annotations
dependencies, as well as SnakeYAML, declare Apache License 2.0 in their
upstream metadata. They are used for strict JSON and safe YAML descriptor
parsing. Third-party notices and license compatibility must be rechecked when
runtime dependencies are added or changed.

## Deferred boundaries

The current JAR API reads archives without extraction, enforces configurable
entry and uncompressed-size limits, parses Bukkit/Paper/BungeeCord/Velocity
descriptors and the JAR manifest, and returns class/resource inventories with
findings. Dependency analysis operates on a caller-supplied collection of
these reports, resolves declared plugin identities within that collection,
detects missing required dependencies, hard-dependency cycles, duplicate plugin
identities and versions, and classes shared across artifacts. It does not
download dependencies or interpret version constraints because plugin
descriptors do not provide dependency version requirements. Platform
compatibility analysis uses caller-supplied target profiles and API catalogs.
The class-file scanner reads bounded entries without extraction, checks class
versions and preview targets, inventories constant-pool class/member
references, and reports deprecated/removed APIs included in the supplied
catalog. No authoritative built-in platform/API catalog is bundled, and
constant-pool scanning does not inspect bytecode instructions or data flow.
The ASM bytecode API extracts class, method, and field metadata and builds
bounded in-memory reference graphs for inheritance, type use, field access,
method calls, exceptions, and bootstrap handles. The graph is a static
reference inventory, not control-flow or data-flow analysis. Report export is
not implemented. CLI, desktop, web/API, file-backed
configuration, and external analyzer loading are also not implemented. They
must not be described as available until completed and verified in their
respective phases. Analysis of plugin artifacts remains local by default and
treats input files as untrusted.

## Verification

Run `gradlew.bat check` on Windows or `./gradlew check` on Unix-like systems with
JDK 17. The core compiler enables `-Xlint:all -Werror`. Tests cover the runtime
baseline, model validation and immutability, configuration defaults and
validation, dependency/priority ordering, analyzer failures, missing
dependencies, cycles, timeout recovery, severity filtering, rule ordering,
suppression, rule failures, safe archive reading, descriptor validation, basic
report generation, dependency resolution, missing dependencies, cycles, duplicate
classes, plugin identity/version conflicts, class-file parsing, Java/platform
compatibility checks, supplied API catalog matches, ASM class/member metadata,
bytecode reference edges, graph limits, malformed classes, and archive-change
handling.
