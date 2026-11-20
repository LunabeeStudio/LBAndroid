---
issue: LBA-4
phase:
type: improvement
wiki: [[compose-ui]]
note: Depends on Renovate PR #402 (Compose BOM 2026.09.00 → 2026.11.01). Code only after it merges. #402 is not a Plane card, so no board relation.
created: 2026-11-20
---

## Context / Decisions

Source: Plane card LBA-4 (idea from https://developer.android.com/blog/posts/jetpack-compose-november-2026-release, section "Custom failure handling in tests").

**Goal.** `LbcComposeTest` (`compose/test/androidtest`) moves from its home-made failure capture to the native failure pipeline added in Compose UI test 1.13: `ComposeUiTestConfig(failurePolicy = TestFailurePolicy(screenshotCaptureMode, uiHierarchyCaptureMode, failureHandlers))`, package `androidx.compose.ui.test`. `CaptureMode` is nested in `TestFailurePolicy`, `TestFailureHandler` is a `fun interface` with `onTestFailed(context: FailureContext)`, and `FailureContext` exposes `error` and `artifacts: List<FailureArtifact>` (`type` = `FailureArtifact.Type.Screenshot` / `UiHierarchy`, `fileName`).

**What the API does (read from the reference pages and androidx.test sources).**
- A capture mode set to `Enabled` or `Disabled` overrides the suite-level runner args (`androidx.compose.ui.test.failure.isScreenshotCaptureEnabled`, `androidx.compose.ui.test.failure.isUiHierarchyCaptureEnabled`). `Unspecified` defers to them.
- Handlers run in order, synchronously on the test thread, **after teardown**: they cannot query nodes. An exception thrown by a handler is attached as suppressed to `FailureContext.error`.
- Artifacts go to AndroidX `PlatformTestStorage`. With no test service installed, `PlatformTestStorageRegistry` falls back to `FileTestStorage`, which writes into the `additionalTestOutputDir` runner arg. AGP pulls that folder into `build/outputs/connected_android_test_additional_output`. No extra setup is needed.
- Calls without a config now use a default `ComposeUiTestConfig` with `inputMode = InputMode.Touch`. That side effect comes with the BOM bump, not with this task.
- Not confirmed by the docs: whether an opt-in annotation is needed (the compiler will say), and whether Robolectric produces artifacts (checked during code, see below).

**Dependency.** The repo is on Compose BOM `2026.09.00`. The API needs `2026.11.01`, which Renovate PR #402 already bumps. This task does not bump the BOM: code starts after #402 merges.

**Decisions.**
1. The native capture replaces the home-made one. `onFailure` (screenshot through `LbcPrintRule` with `FailureSuffix`, plus the root semantics tree logged under `ON_FAILURE`) is removed, together with the `try/catch` in both `invoke` overloads.
2. `LbcComposeTest` gets an open `failurePolicy: TestFailurePolicy` property. Its default is `screenshotCaptureMode = Enabled`, `uiHierarchyCaptureMode = Enabled`, `failureHandlers = failureHandlers`. Default behavior stays "always captured on failure". A subclass overrides it to turn captures off. Because `Enabled` is explicit, the suite runner args cannot disable it: the KDoc says so.
3. Custom logic goes through an open `failureHandlers: List<TestFailureHandler>` property, empty by default, used by the default `failurePolicy`. There is no other hook.
4. Both `invoke` overloads replace `effectContext: CoroutineContext` with `config: ComposeUiTestConfig = ComposeUiTestConfig(failurePolicy = failurePolicy)`, passed to `runComposeUiTest` / `runAndroidComposeUiTest`. A caller that passes its own config owns its failure policy.
5. `LbcAndroidTestConstants.FailureSuffix` is removed: nothing uses it anymore.
6. Unchanged: `LbcPrintRule`, the `printRule` rule of `LbcComposeTest`, the `waitAndPrint…` / `printToCacheDir` helpers and their `_TIMEOUT` / `_ERROR` / `_MANY_ROOTS` suffixes. They are explicit prints, not failure capture. `robolectrictest` is unchanged unless the Robolectric check below says otherwise.
7. Robolectric check: during code, run one Robolectric test (in `compose/test/robolectrictest` or another JVM test source set that can run Compose under Robolectric) with a failing block and a recording handler. If it produces the artifacts, keep that test as a JVM regression guard. If it doesn't, drop the test and write in the `LbcComposeTest` KDoc that the captures only work on a device or emulator. Report the result in `## Implementation` either way.
8. Proof on device: an instrumented test in `demo-compose/src/androidTest`, next to `PrintRuleDemoTest`.
9. Release: `LBCANDROIDTEST_VERSION` 1.15.0 → 1.16.0. This is a narrow break (a removed hook, a removed constant, a replaced parameter), so it is a minor bump with `**break:**` lines in `CHANGELOG.MD`, plus the bom bump following the existing release-block format.

**Out of scope (YAGNI).** No artifact upload or reporting integration. No runner-arg wiring in this repo's Gradle files. No changes to `LbcPrintRule`.

## Acceptance criteria

- [ ] `LbcComposeTest` builds against Compose UI test 1.13 (BOM `2026.11.01`) with no `onFailure` function and no `try/catch` in either `invoke` overload.
- [ ] `LbcComposeTest` exposes open `failurePolicy: TestFailurePolicy` (default: screenshot and UI hierarchy captures `Enabled`, handlers = `failureHandlers`) and open `failureHandlers: List<TestFailureHandler>` (default empty), both with KDoc. The KDoc of `failurePolicy` says that runner args cannot override the explicit `Enabled`.
- [ ] Both `invoke` overloads take `config: ComposeUiTestConfig` (default `ComposeUiTestConfig(failurePolicy = failurePolicy)`) instead of `effectContext`, and pass it to `runComposeUiTest` / `runAndroidComposeUiTest`. The class KDoc example is updated.
- [ ] `LbcAndroidTestConstants.FailureSuffix` no longer exists.
- [ ] A new instrumented test in `demo-compose/src/androidTest` subclasses `LbcComposeTest`, overrides `failureHandlers` with a handler that records its `FailureContext`, runs `invoke { … }` with a block that fails, and catches the error with `assertFails`. It asserts that the handler was called once, that `context.error` is the thrown error, and that `context.artifacts` holds one `FailureArtifact.Type.Screenshot` and one `FailureArtifact.Type.UiHierarchy`. It passes on an emulator.
- [ ] A second case in the same test class, a subclass whose `failurePolicy` sets both capture modes to `Disabled`, sees a handler call with an empty `artifacts` list.
- [ ] `PrintRuleDemoTest` still passes unchanged.
- [ ] The Robolectric result (artifacts produced or not) is recorded in `## Implementation`. The matching outcome of decision 7 is in the code: a kept JVM test, or the device-only note in the KDoc.
- [ ] `LBCANDROIDTEST_VERSION` is `1.16.0`. `CHANGELOG.MD` has a new release block listing `Lbcandroidtest 1.16.0` and the bom, with `**break:**` lines for the removed `onFailure`, the removed `FailureSuffix` and `effectContext` → `config` in `invoke`, plus a line for the native failure captures and handlers.
- [ ] `./gradlew detekt` passes.

## Implementation

## Review

## Verification

## Feedback
