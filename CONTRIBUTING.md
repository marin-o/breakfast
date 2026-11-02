# Contributing

Bug reports and ideas are welcome as issues.

If you send a pull request, you confirm it's yours to give and you license it under GPL-3.0. You also agree that the copyright holder may use your contribution under other terms, including commercial ones, such as a paid or closed version of the app. You keep the copyright in your own work.

Before you open one:

- Run `./gradlew testDebugUnitTest lintDebug`.
- Use Conventional Commits. `git config core.hooksPath .githooks` turns on the check.
- Reuse the shared components in `ui/components` rather than copying layout or logic.
- Follow [`DESIGN.md`](DESIGN.md) for anything visual.
