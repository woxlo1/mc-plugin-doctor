# Minecraft Plugin Doctor

[English](#english) | [日本語](#日本語)

## English

### Project Overview

Minecraft Plugin Doctor is a planned developer tool for inspecting and
diagnosing Minecraft server plugins. The repository is in early development;
Phases 0 and 1 establish the repository foundation and reusable core analysis
engine, Phase 2 provides bounded, local JAR and plugin descriptor analysis, and
Phase 3 analyzes dependencies and conflicts across supplied JAR reports.

### Features

- **Implemented:** Gradle foundation, Java 17 toolchain, immutable analysis
  findings and configuration, analyzer registry and execution engine, ordered
  rule processing, logging/error reporting, bounded JAR inventory, manifest and
  Bukkit/Paper/BungeeCord/Velocity descriptor parsing, dependency graphs,
  missing required dependency and dependency-cycle findings, duplicate plugin
  and class detection, and GitHub Actions verification.
- **Planned:** Platform compatibility and bytecode analysis; exported reports
  and product interfaces. No user-facing application is available yet.

Feature statuses used in project documentation are **Planned**, **In Progress**,
**Implemented**, **Experimental**, and **Unsupported**.

### Supported Platforms

Bukkit, Spigot, Paper, Folia, Purpur, Velocity, and BungeeCord are planned
analysis targets. Platform support has not been implemented or verified.

### Requirements

- JDK 17

### Installation

There is no installable product yet. To build the repository, use the wrapper
commands in [Development Setup](#development-setup).

### Getting Started

The core analyzer API and engine are implemented. The Java API can inspect
local JARs without extraction, inventory classes and resources, parse manifests
and supported plugin descriptors, and analyze
dependencies and duplicate classes across a supplied set of JAR reports. There
is no user-facing plugin analysis workflow yet.

### CLI Usage

**Planned.** No CLI executable or commands are available.

### Desktop Usage

**Planned.** No desktop application is available.

### Configuration

Core analysis configuration is implemented as an in-memory Java API. Loading or
saving product configuration files is **Planned**.

### AI Configuration

**Planned and optional.** No AI integration or provider configuration is
implemented.

### Report Formats

**Planned.** The JAR analysis API returns an in-memory report; export formats
and report generation are not implemented.

### GitHub Actions

The CI workflow runs Gradle verification on pull requests targeting `develop`
and pushes to `develop`. It does not analyze plugin artifacts.

### Architecture

See [the architecture overview](docs/architecture/overview.md). The only
Gradle subproject currently present is `:core`, which provides reusable analysis
models, analyzer contracts, execution, rules, JAR inspection, and dependency
analysis APIs.

### Development Setup

Install JDK 17, then run `gradlew.bat check` on Windows or `./gradlew check` on
Unix-like systems.

### Testing

The wrapper's `check` task compiles the Java module with compiler warnings
treated as errors and tests the runtime baseline, analysis models and
configuration, analyzer ordering and failure handling, timeouts, rule
processing, bounded JAR reading, and supported plugin descriptor analysis.
Dependency tests also cover graph resolution, missing dependencies, cycles,
duplicate classes, and plugin identity/version conflicts.

### Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Work is submitted from focused branches
to `develop`; direct commits to `main` and `develop` are not permitted.

### License

Licensed under the [Apache License, Version 2.0](LICENSE). See [NOTICE](NOTICE).

### Security Policy

See [SECURITY.md](SECURITY.md) for private vulnerability reporting guidance.

## 日本語

### プロジェクト概要

Minecraft Plugin Doctorは、Minecraftサーバープラグインの調査・診断を支援する開発者向けツールとして計画されています。現在は初期開発段階であり、Phase 0でリポジトリ基盤、Phase 1で再利用可能なCore解析エンジン、Phase 2で制限付きのローカルJAR解析とプラグインDescriptor解析、Phase 3で依存関係と競合の解析を実装しています。

### 機能

- **Implemented（実装済み）:** Gradle基盤、Java 17ツールチェーン、不変の解析結果・設定モデル、Analyzer登録・実行エンジン、順序付きルール処理、ログ・エラー報告、制限付きJARインベントリ、ManifestおよびBukkit/Paper/BungeeCord/Velocity Descriptor解析、依存グラフ、必須依存関係の不足・循環の検出、プラグイン識別子とクラスの重複検出、GitHub Actionsによる検証。
- **Planned（計画中）:** プラットフォーム互換性・バイトコード解析、レポート出力、製品インターフェース。利用者向けアプリケーションはまだありません。

ドキュメントでは機能の状態を **Planned**、**In Progress**、**Implemented**、**Experimental**、**Unsupported** で区別します。

### 対応プラットフォーム

Bukkit、Spigot、Paper、Folia、Purpur、Velocity、BungeeCordを解析対象として計画しています。各プラットフォームへの対応機能は未実装・未検証です。

### 必要環境

- JDK 17

### インストール

インストール可能な製品はまだありません。リポジトリのビルドには[開発環境](#開発環境)に記載したWrapperコマンドを実行してください。

### はじめに

CoreのAnalyzer APIと実行エンジンは実装済みです。Java APIからローカルJARを展開せずに検査し、クラス・リソースを列挙し、Manifestと対応するプラグインDescriptorを解析してFindingを返せます。複数のJARレポートを入力すると、依存グラフの作成、必須依存関係の不足・循環、重複クラス、プラグイン識別子・バージョンの競合を検出できます。利用者向けの解析ワークフローはまだありません。

### CLIの使い方

**Planned（計画中）。** CLI実行ファイルやコマンドはありません。

### Desktopアプリの使い方

**Planned（計画中）。** Desktopアプリはありません。

### 設定

Core解析設定はインメモリのJava APIとして実装済みです。製品設定ファイルの読み書きは**Planned（計画中）**です。

### AIの設定

**Planned（任意機能）。** AI連携およびプロバイダー設定は未実装です。

### レポート形式

**Planned（計画中）。** JAR解析APIはインメモリのレポートを返しますが、エクスポート形式やレポート生成機能は未実装です。

### GitHub Actions

CIワークフローは、`develop`宛てのPull Requestと`develop`へのpushでGradle検証を実行します。プラグインファイルの解析は行いません。

### アーキテクチャ

[アーキテクチャ概要](docs/architecture/overview.md)を参照してください。現在存在するGradleサブプロジェクトは`:core`のみで、解析モデル、Analyzer契約、実行エンジン、ルール処理、JAR検査、依存関係解析APIを提供します。

### 開発環境

JDK 17を用意し、Windowsでは`gradlew.bat check`、Unix系OSでは`./gradlew check`を実行してください。

### テスト

Gradle Wrapperの`check`タスクはJavaコンパイラー警告をエラーとして扱ってコンパイルし、ランタイム基準、解析モデル・設定、Analyzerの実行順序と失敗処理、タイムアウト、ルール処理、制限付きJAR読み込み、対応するプラグインDescriptor解析、依存グラフ、不足依存関係、循環、重複クラス、プラグイン識別子・バージョン競合をテストします。

### コントリビューション

[CONTRIBUTING.md](CONTRIBUTING.md)を参照してください。作業ブランチから`develop`へPull Requestを作成します。`main`および`develop`への直接コミットは禁止です。

### ライセンス

[Apache License 2.0](LICENSE)の下で公開します。[NOTICE](NOTICE)も参照してください。

### セキュリティポリシー

非公開の脆弱性報告方法は[SECURITY.md](SECURITY.md)を参照してください。
