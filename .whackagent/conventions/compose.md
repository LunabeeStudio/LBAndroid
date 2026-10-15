# Kotlin — Compose

> whackagent convention module · lens: **style**. Only present when the project actually uses Jetpack Compose or Compose Multiplatform.

## Stateless screen, stateful route

Split each screen in two: a **route** that collects state from the ViewModel and wires callbacks, and a **stateless screen** that takes plain state + lambdas. Screen previewable and testable without a ViewModel.

```kotlin
@Composable
fun ProfileRoute(viewModel: ProfileViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileScreen(state = state, onRefresh = viewModel::refresh, onBack = onBack)
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) { /* ... */ }
```

## Parameters

Order: required params, then `modifier: Modifier = Modifier` as **first optional param**, then other optionals, then trailing `content` lambda.

- `modifier` applied to the **root** element only, once. Never pass it to a child as well.
- Params stable/immutable: `data class` of `val`s, `ImmutableList` or `@Immutable`/`@Stable` when needed. Never pass ViewModel, `MutableState`, or mutable collections down.
- Events as lambdas named `onX` (`onSubmit`, `onItemClick`).

## State hoisting

- State lives at lowest common owner that needs it. UI element state (`scroll`, `expanded`) in `remember`; survive config change / process death → `rememberSaveable`.
- Screen state from ViewModel. Composable never mutates ViewModel state directly — it calls an event lambda.
- No business logic, no I/O, no repository access in composables.

## Side effects

- `LaunchedEffect(key)` for suspend work tied to composition; keys = every value the effect reads that can change. `LaunchedEffect(Unit)` only when truly run-once.
- `DisposableEffect` for register/unregister pairs. `rememberCoroutineScope` for event-triggered launches.
- One-off events (navigation, snackbar) → consumed from state or a channel in a `LaunchedEffect`, never fired during composition.

## Test tags (targetable UI)

Every **interactive** or **asserted** element carries a stable `Modifier.testTag(...)` (plus `testTagsAsResourceId` at root when UI Automator / runtime tooling needs resource IDs). `wa-implementer` drives the built app by the UI tree; without tags it falls back to raw coordinates — brittle, breaks on layout change. Missing tag on an interactive element = style finding.

Rules:
- Tag **stable and semantic**, not positional: `"login.submitButton"`, not `"button2"`. Namespace by screen/feature.
- Set on the interactive element itself, not a wrapping container.
- Purely decorative elements do **not** need one — YAGNI.
- Tags defined as constants, so screen and test reference same source.
- Meaningful `contentDescription` on icons/images that convey info; `null` on decorative ones.

## Theming and resources

- Colors, typography, shapes, spacing from `MaterialTheme` or project design system tokens. No hardcoded `Color(0xFF…)` or `16.dp` magic numbers scattered in screens.
- User-facing strings from resources (`stringResource`, Compose Multiplatform resources). No hardcoded text.
- Reuse design system components before writing new ones.

## Previews

Every screen and reusable component has `@Preview` in its file (or project's preview convention). Multiple states → one preview per state, fed with fake state — never a real ViewModel.

```kotlin
@Preview
@Composable
private fun ProfileScreenLoadingPreview() {
    AppTheme { ProfileScreen(state = ProfileUiState(isLoading = true), onRefresh = {}, onBack = {}) }
}
```
