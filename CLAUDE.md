# CLAUDE - project conventions

## Project Structure

Read the [README.md](README.md) file for more information about the project structure.

## Code conventions

1. Domain-driven design, naming and structure
2. Test-driven development - write tests first, then code
3. Clean code principles 
4. Strong separation of concerns and responsibility 
5. Functional-style programming whenever possible 
6. Avoid older imperative style of code 
7. Package-by-feature, not package-by-layer 
8. Refactor and simplify code whenever you can 
9. Always verify the plugin after new features are built, updated or removed, and when dependencies are updated (see [Build and test](#build-and-test))

## Build and test

The README's Requirements and Build sections cover setup: JDK 25 or newer, and the path to the local IntelliJ install the build compiles against. To verify a change:

1. `./gradlew domainTest` runs the domain unit tests in a plain JVM. JaCoCo measures coverage here.
2. `./gradlew test` runs the IntelliJ platform tests (`BasePlatformTestCase`).
3. `./gradlew clean buildPlugin` builds the plugin zip into `build/distributions/`.
4. `./gradlew verifyPlugin` runs the JetBrains Plugin Verifier against the IDE builds set in `build.gradle.kts`. The first run downloads those IDEs (several GB) and caches them.
5. `./gradlew liveReloadBrowserTest` runs the live-reload scripts (`ui/LiveReloadScripts.kt`) in a real Chromium through Playwright. It's opt-in and not part of `check`: run it whenever those scripts change. It needs Node.js 20+ and npm, and the first run downloads Playwright's Chromium.

Known issue: with the local IntelliJ IDEA 2026.2.3 (build 262.10968.63), every test in `./gradlew test` fails with `Cannot create extension (class=Z.Z.Z.Z.Z) [Plugin: com.intellij.modules.ultimate]`. The bug is in the IDE's bundled Ultimate plugin, not in this code, and `./gradlew check` fails for the same reason. Until an IDE update fixes it, verify with `domainTest`, `buildPlugin` and `verifyPlugin`, and report the platform tests as blocked by the IDE bug.

## Documentation

1. After a feature or fix is built, changed or removed, update CHANGELOG.md, README.md and ROADMAP.md. Verify the claims in the files with the current state of the source code and the plugins features.
2. Use the humanizer skill to clean up and simplify the generated text in the documentation and all comments in the source code.
3. Parked items go to [PARKED_ITEMS.md](PARKED_ITEMS.md), whatever the reason for parking them: what the item is, why it was parked, and what picking it up again would take. Remove a parked item from ROADMAP.md, and move it back to ROADMAP.md when the reason no longer holds.

## Commits and pull requests

Never add a `Co-Authored-By: Claude ...` trailer to a commit message or a "Generated with Claude Code" line to a pull request description.

## Method

1. Use the ROADMAP to select the next item to work on
2. Before a feature is built, ask an Opus 5.5 sub-agent with no context or history, xhigh effort and available security skills to review the security of both the planned feature and the security implications of adding the feature to the plugin. Give the agent the feature description and the existing code the feature will touch. The `security-review` skill only reviews changes already on the branch, and before the feature is built there are none.
3. If the security agent finds critical issues - stop the process, and recommend how to proceed: do not continue (feature is not added or built), fix the issues the security agent found, or park the feature for later in PARKED_ITEMS.md (see Documentation, step 3).
4. If the security agent does not find any issues, continue with planning the design of the implementation (step 5)
5. Plan the design of the feature and then present the design to the code owner for approval. When approved, plan and implement tests and finally implement the code.
6. When the feature is built and its tests pass, ask an Opus 5.5 sub-agent with no context or history and xhigh effort to review the code independently. Give it the feature description and the changes (`git diff`), and leave out your own assessment so the review isn't anchored on it. It should check correctness, security and the code conventions above.
7. Fix the issues the reviewer finds. If you disagree with a finding, present it to the code owner to decide.
8. When all tests are green, the review findings are resolved, and the plugin is verified by the build and the code owner, the round is done.
