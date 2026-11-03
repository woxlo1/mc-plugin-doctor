# Minecraft Plugin Doctor

[English](#english) | [日本語](#日本語)

## English

### Project Overview

Minecraft Plugin Doctor is a planned developer tool for inspecting and
diagnosing Minecraft server plugins. The repository is in pre-development;
Phases 0 and 1 establish the repository foundation and reusable core analysis
contracts and execution engine.

### Features

- **Implemented:** Gradle foundation, Java 17 toolchain, immutable analysis
  findings and configuration, analyzer registry and execution engine, ordered
  rule processing, logging/error reporting, and GitHub Actions verification.
- **Planned:** Plugin inspection and all product interfaces. No JAR analysis or
  user-facing application is available yet.

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

The core analyzer API and engine are implemented, but no plugin-specific
analyzers are available yet. There is no user-facing plugin analysis workflow.

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

**Planned.** No report generation is implemented.

### GitHub Actions

The CI workflow runs Gradle verification on pull requests targeting `develop`
and pushes to `develop`. It does not analyze plugin artifacts.

### Architecture

See [the architecture overview](docs/architecture/overview.md). The only
Gradle subproject currently present is `:core`, which provides reusable analysis
models, analyzer contracts, execution, and rules.

### Development Setup

Install JDK 17, then run `gradlew.bat check` on Windows or `./gradlew check` on
Unix-like systems.

### Testing

The wrapper's `check` task compiles the Java module with compiler warnings
treated as errors and tests the runtime baseline, analysis models and
configuration, analyzer ordering and failure handling, timeouts, and rule
processing.

### Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Work is submitted from focused branches
to `develop`; direct commits to `main` and `develop` are not permitted.

### License

Licensed under the [Apache License, Version 2.0](LICENSE). See [NOTICE](NOTICE).

### Security Policy

See [SECURITY.md](SECURITY.md) for private vulnerability reporting guidance.

## 日本語

### プロジェクト概要

Minecraft Plugin Doctorは、Minecraftサーバープラグインの調査・診断を支援する開発者向けツールとして計画されています。現在は開発前段階であり、Phase 0でリポジトリ基盤、Phase 1で再利用可能なCore解析契約と実行エンジンを整備しています。

### 機能

- **Implemented（実装済み）:** Gradle基盤、Java 17ツールチェーン、不変の解析結果・設定モデル、Analyzer登録・実行エンジン、順序付きルール処理、ログ・エラー報告、GitHub Actionsによる検証。
- **Planned（計画中）:** プラグイン解析および製品インターフェース。JAR解析や利用者向けアプリケーションはまだありません。

ドキュメントでは機能の状態を **Planned**、**In Progress**、**Implemented**、**Experimental**、**Unsupported** で区別します。

### 対応プラットフォーム

Bukkit、Spigot、Paper、Folia、Purpur、Velocity、BungeeCordを解析対象として計画しています。各プラットフォームへの対応機能は未実装・未検証です。

### 必要環境

- JDK 17

### インストール

インストール可能な製品はまだありません。リポジトリのビルドには[開発環境](#開発環境)に記載したWrapperコマンドを実行してください。

### はじめに

CoreのAnalyzer APIと実行エンジンは実装済みですが、プラグイン固有Analyzerはまだありません。利用者向けのプラグイン解析ワークフローはありません。

### CLIの使い方

**Planned（計画中）。** CLI実行ファイルやコマンドはありません。

### Desktopアプリの使い方

**Planned（計画中）。** Desktopアプリはありません。

### 設定

Core解析設定はインメモリのJava APIとして実装済みです。製品設定ファイルの読み書きは**Planned（計画中）**です。

### AIの設定

**Planned（任意機能）。** AI連携およびプロバイダー設定は未実装です。

### レポート形式

**Planned（計画中）。** レポート生成は未実装です。

### GitHub Actions

CIワークフローは、`develop`宛てのPull Requestと`develop`へのpushでGradle検証を実行します。プラグインファイルの解析は行いません。

### アーキテクチャ

[アーキテクチャ概要](docs/architecture/overview.md)を参照してください。現在存在するGradleサブプロジェクトは`:core`のみで、解析モデル、Analyzer契約、実行エンジン、ルール処理を提供します。

### 開発環境

JDK 17を用意し、Windowsでは`gradlew.bat check`、Unix系OSでは`./gradlew check`を実行してください。

### テスト

Gradle Wrapperの`check`タスクはJavaコンパイラー警告をエラーとして扱ってコンパイルし、ランタイム基準、解析モデル・設定、Analyzerの実行順序と失敗処理、タイムアウト、ルール処理をテストします。

### コントリビューション

[CONTRIBUTING.md](CONTRIBUTING.md)を参照してください。作業ブランチから`develop`へPull Requestを作成します。`main`および`develop`への直接コミットは禁止です。

### ライセンス

[Apache License 2.0](LICENSE)の下で公開します。[NOTICE](NOTICE)も参照してください。

### セキュリティポリシー

非公開の脆弱性報告方法は[SECURITY.md](SECURITY.md)を参照してください。
