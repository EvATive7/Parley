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
