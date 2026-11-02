# Parley Agent Notes

- Keep the skills synchronized across `.agents/skills` and `.claude/skills` whenever one changes.
- To publish a release, use the `publish-a-release` skill.

## Build Variants

| Configuration | Release Variant | Debug Variant |
| :--- | :--- | :--- |
| **Application ID (Package Name)** | `io.github.evative7.parley` | `io.github.evative7.parley.debug` |
| **Application Name (App Label)** | `Parley` | `Parley Debug` |

Unless specified otherwise by the user, build and run actions use the debug variant.

## Release Tags

Release tags MUST be annotated tags (`git tag -a v0.3.0 -m "Release v0.3.0"`), not lightweight tags.

## Version Management

Git tag is the version source of truth. CI extracts `VERSION_NAME` and derives `VERSION_CODE` (formula: `major*10000 + minor*100 + patch`, e.g., `v0.3.0` → `300`) from the tag during release builds. Do not manually modify `versionCode` or `versionName` in `app/build.gradle.kts`.

## Config Versioning

The service settings have one format: `ConfigFile` (`app/src/main/java/io/github/evative7/parley/data/ConfigFile.kt`). It is both the exported/imported file and, encrypted whole, what the app stores internally. There is no second format to keep in sync.

- Every change to the config layout bumps `ConfigFile.VERSION` by exactly one. Adding, removing, renaming or moving a field, or changing what a value means, are all layout changes.
- Each bump adds one migration step to `ConfigFile.MIGRATIONS`, turning version `n` into `n + 1`. Released steps are never edited.
- Migration follows the version chain: a file of an older version is upgraded one step at a time until it reaches the current version (e.g. current is 3, input is 1: 1 → 2 → 3). Only the current layout has a reader; never add a direct "1 → 3" shortcut or version checks in the reader.
- Files newer than `VERSION` are refused (`TooNew`), never guessed at.
- Each new step gets a test that decodes a file of the previous version.
- Settings stored before the single config document existed (separate DataStore keys) are migrated once by `SettingsRepository` into the current version; that path is not part of the chain.
