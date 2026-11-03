# Contributing

## Development setup

- Install JDK 17.
- Use the Gradle wrapper: `gradlew.bat check` on Windows or `./gradlew check` on
  Unix-like systems.

## Branch and pull request workflow

- Create a focused work branch from `develop`.
- Do not commit directly to `main` or `develop`.
- Use concise Conventional Commit messages in English.
- Open a pull request from the work branch to `develop`.
- Include the tests, build, and static checks that were run, and report any
  checks that could not be run.

## Scope and implementation status

Keep changes limited to the approved phase. Do not describe planned or
placeholder behavior as implemented. Add or update documentation and tests for
behavior introduced by a change.
