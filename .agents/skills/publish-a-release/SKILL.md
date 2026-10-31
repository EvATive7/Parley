---
name: publish-a-release
description: "Cut a Parley release end-to-end: make sure master is clean, pushed and builds, create and push an annotated version tag, wait for the CI-created GitHub Release, then write and publish multilingual Release Notes."
---

# Publish a Release

Use this skill to release a new version of Parley: create and push an annotated version tag, wait for the remote CI to create the GitHub Release, then write and publish user-facing Release Notes for the new tag. If the user specifies a version, use it; otherwise infer it from the changes.

## Project facts

- Single Git repository; releases are cut from `master`. Remote: `origin` (https://github.com/EvATive7/Parley).
- The Git tag is the only source of the version. CI (`.github/workflows/release.yml`) derives `versionName` from the tag and `versionCode` as `major*10000 + minor*100 + patch`. Never edit `versionCode` or `versionName` in `app/build.gradle.kts`.
- Pushing a `v*` tag runs the workflow: job `create-release` creates the GitHub Release at once (with an auto-generated body); job `build` then runs `testDebugUnitTest assembleRelease` and uploads `app-release.apk` to it.
- User-facing languages: the README exists in English, 简体中文, 繁體中文, 日本語, 한국어, Français, Deutsch, Español (`README.md`, `README.zh-CN.md`, `README.zh-TW.md`, `README.ja.md`, `README.ko.md`, `README.fr.md`, `README.de.md`, `README.es.md`). Release Notes use the same languages in the same order.
- Debug-only features (anything behind `BuildConfig.DEBUG`, such as the simulated speech and translation services) never reach users of the release APK.

## Scope and safety requirements

- Update the body of the GitHub Release for the target tag only after the version differences have been fully reviewed.
- Do not modify the title, draft or prerelease status, assets, or target commit.
- Tags MUST be annotated (`git tag -a`), never lightweight.
- Do not move or delete a tag that has been pushed. If something went wrong after pushing, stop and report instead.

## Workflow

**IMPORTANT: AFTER COMPLETING EACH OF THE FOLLOWING STEPS, YOU MUST RE‑READ THIS SKILL FILE TO ENSURE OPERATIONAL ACCURACY.**

### 1. Ensure master is releasable

Before tagging, the repository MUST be in a releasable state.

1. Confirm the current branch is `master`. If the work to release is on another branch, stop and ask whether to merge it first.
2. Confirm the working tree is clean — no uncommitted changes to tracked files. If there are any, stop and report.
3. Fetch `origin` with tags. If `origin/master` has commits that local `master` lacks, stop and report. If local `master` has unpushed commits, push them.
4. Check that every README language still describes the released behavior correctly (features, FAQ, privacy). If a user-visible behavior changed since the last tag and a README contradicts it or omits a notable new feature, update all eight READMEs consistently, commit, and push before tagging.
5. Run `./gradlew testDebugUnitTest assembleRelease` locally — the same tasks CI runs. If it fails, stop and report; a pushed tag cannot be taken back.

### 2. Create and push the annotated tag

1. Determine the new version. If the user specified a version or a bump policy, use it. Otherwise infer it from the latest tag (`git tag -l --sort=-v:refname`) and the changes being released, following semantic versioning: new user-facing features bump the minor version, fixes only bump the patch version.
2. Create an annotated tag:
   ```
   git tag -a v<version> -m "Release v<version>"
   ```
3. Push the tag: `git push origin v<version>`.

### 3. Write the Release Notes

Right after pushing the tag, draft the notes first — the CI runs in the background while you write.

1. Synchronize remote tags, then determine the comparison range: the target tag is the new `v<version>`; the baseline is the tag created immediately before it. Confirm both resolve.
2. Fully inspect the commits and file differences between the two tags, and deeply think about the relevant implementation. Do not rely solely on commit titles. For every potentially user-facing change, focus on UI text (`app/src/main/res/values*/strings.xml`), user workflows, settings behavior, translation and recognition behavior, face-to-face mode, documentation for supported behavior, and user-visible error handling.
3. Write the Release Notes draft to a temporary Markdown file outside the repository, following the [template](references/release-notes-template.md) and [example](references/release-notes-example.md) and the content rules below.

- Organize the content according to the actual changes in this release, using the [Release Note template](references/release-notes-template.md) and [Release Note example](references/release-notes-example.md) as references. The notes MUST include every language in order: English first, then 简体中文, 繁體中文, 日本語, 한국어, Français, Deutsch, Español inside the collapsed section.
- Include only externally observable changes and describe specific capabilities or outcomes from the user's perspective: new capabilities, improvements to the user experience, and fixes for user-visible issues. Exclude refactoring, tests, CI, packaging, dependencies, internal architecture, debug-only features, and other engineering changes unless they directly affect the user experience. Keep each item concise and specific. Do not include commit IDs, PR numbers, file names, module names, or implementation details.
- **When the same feature undergoes multiple iterations or follow-up corrections within the version, merge all related commits into a single entry describing only the final net effect — do not expose intermediate development steps or superseded states.** A layout tried and then reverted within the release is not a change at all.
- A fix belongs under Fixes only if the problem existed in a previous release. A problem introduced and fixed within this release is not mentioned.
- Use the app's own UI wording for buttons, settings, and modes in each language (for example "face-to-face mode" / "面对面模式"), taken from the strings files and READMEs.
- The meaning MUST be equivalent in every language and expressed naturally in each one. Product names, service names, established UI labels, file extensions, and technical terms that aid understanding may remain untranslated.
- Include only categories with qualifying content. If a category has no relevant changes, omit both its heading and content in every language. Do not use placeholder text or add internal changes merely to populate a category.
- End the notes with the compare link, outside the collapsed section: `**Full Changelog**: https://github.com/EvATive7/Parley/compare/v<previous>...v<version>`.

### 4. Wait for the CI-created Release

1. Poll the remote CI for the Release with `gh release view v<version>` until it exists. Stop polling as soon as the Release appears — publishing notes only needs the Release, not the completed build.

### 5. Publish and verify

1. Inspect the draft you wrote in step 3 before publishing.
2. Use `gh release edit v<version> --notes-file <draft>` to update only the body of the Release.
3. Read the target Release to confirm that its published body matches the expected content, then delete the temporary Markdown file.
4. Check the workflow run for the tag (`gh run list --workflow release.yml`). If the `build` job has finished, confirm it succeeded and that `app-release.apk` is attached; if it is still running, say so. If it failed, report the failing step.
5. Report the comparison range, Release link, build status, and a brief summary of the published user-facing changes.
