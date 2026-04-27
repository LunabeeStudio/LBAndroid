# Presenter library

## Introduction

This directory contains the presenter stack used to drive a Compose screen with:
- a `PresenterUiState`
- a presenter extending `LBPresenter`
- a reducer extending `LBReducer`

The presenter owns screen wiring. The reducer owns state transitions, navigation calls and side effects.

## Recommended structure

For one screen, keep the following split:
- `UiState`: immutable state rendered by the screen
- `Action`: all events handled by the reducer
- `Screen`: pure Compose UI reading the `UiState`
- `Presenter`: creates the reducer, exposes `content` and declares extra `flows`
- `Reducer`: implements `reduce` and returns the next `UiState`

The recommended data flow is:
1. The screen emits UI events as `Action`.
2. The presenter forwards those actions with `emitUserAction`.
3. The reducer computes the next state and optional side effects.
4. The presenter renders the updated `UiState`.

## Presenter guidelines

### Marking presenter-supplied reducer parameters

`presenter-ksp` recognises any of the following annotations on a reducer constructor parameter as "supplied by the presenter at creation time" (i.e. not DI-resolved):

- `@studio.lunabee.compose.presenter.FactoryArg` (framework-agnostic, no extra dependency)
- `@org.koin.core.annotation.InjectedParam` (Koin-annotated projects)
- `@dagger.assisted.Assisted` (Dagger / Hilt projects)

JSR-330 doesn't cover this concept, so each DI stack ships its own. Use whichever is already on the module's classpath; the generated factory's `create(context, args)` shape is identical.

### Prefer `LBSinglePresenter` for a single-state screen

Use `LBSinglePresenter` when one reducer handles the whole screen lifecycle.

Recommended rules:
- keep `getInitialState()` fast and side-effect free
- use `flows` for non-UI action sources such as repository streams or startup events
- keep `content` focused on rendering and UI callback wiring
- compute route arguments or presenter-owned values in the presenter, then pass them to the reducer in `createReducer`

### Prefer `LBFactorySinglePresenter` when reducer creation can be delegated

Use `LBFactorySinglePresenter` when the reducer only needs:
- dependencies already provided by DI
- the `LBPresenterContext` supplied by the presenter runtime

This keeps reducer wiring explicit and lets you reuse generated or injected `LBSingleReducerFactory`.

### Keep the screen stateless

The screen should render from `UiState` and expose callbacks. Business rules should stay in the reducer, not in Composables.

## Reducer guidelines

Use `LBSingleReducer` when all actions are valid for the same `UiState` type.

Recommended rules:
- keep `reduce` as the single place where actions mutate state
- return navigation and side effects through `ReduceResult` instead of triggering them from the UI
- use `performNavigation` for navigation work
- use `useActivity` only for activity-bound operations that cannot be expressed elsewhere
- call `emitUserAction` from side effects only when a new action must re-enter the reducer pipeline

## File template

Start from the presenter template available in this directory:

- [template](template)

The template already shows the expected presenter shape with:
- `SavedStateHandle` route parsing
- `flows`
- `createReducer`
- `getInitialState`
- `content`

## Koin integration

If your project uses Koin, `presenter-koin` provides `PresentScreen` to resolve the presenter as a ViewModel and invoke it with the screen navigation scope.

### Registering generated reducer factories

`presenter-ksp` supports two mutually exclusive modes, picked via KSP options in the consuming module's `build.gradle.kts`:

- **Koin compiler plugin mode** (`studio.lunabee.presenter.useKoinAnnotations=true`) — generated factories are annotated with
  `@org.koin.core.annotation.Factory` and qualifiers are emitted as `@Named(...)` on the factory constructor, so Koin's K2
  compiler plugin (`io.insert-koin.compiler.plugin`) auto-registers them through an existing `@Module @ComponentScan("…")` that
  covers the reducer package. No `generatedReducerFactoryModule` is emitted and presenters do not need `@Provided` on the
  generated factory parameter.
- **DSL aggregator mode** (`studio.lunabee.presenter.generateKoinModule=true` + `studio.lunabee.presenter.koinModulePackage=…`)
  — emits a classic `generatedReducerFactoryModule: Module` that callers add via `startKoin { modules(generatedReducerFactoryModule) }`.
  Use this when the project has not adopted the Koin compiler plugin or relies on runtime DSL module composition.

See [MIGRATION_V2.MD](MIGRATION_V2.MD) for the full migration decision flow.
