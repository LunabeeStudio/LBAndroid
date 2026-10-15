# Presenter stack

MVI-style screen driver for Compose: `ViewModel` presenter feed actions into reducer; reducer alone own state, navigation, side effects. KSP generate reducer factories (optionally Koin bindings).

Related: [[architecture]] · [[compose-ui]] · [[build-and-publishing]] · [[common-libraries]]

## Modules (`compose/presenter/`)

Published as `studio.lunabee.compose.presenter:<module>`; versions in `buildSrc/src/main/kotlin/AndroidConfig.kt` (`LBCPRESENTER_*`). See [[build-and-publishing]].

| Module | Kind | Purpose |
|---|---|---|
| `presenter` | Android Compose lib | Runtime: `LBPresenter`, reducers, `ReduceResult`, `LBPresenterContext`. Re-exports `presenter-annotations` (`api`). |
| `presenter-annotations` | JVM | `@GenerateReducerFactory`, `@FactoryArg` (BINARY retention). |
| `presenter-ksp` | JVM, KSP processor | Generates `<Reducer>Factory` (+ `<Reducer>FactoryArgs`). |
| `presenter-koin-ksp` | JVM, KSP processor | Extends `presenter-ksp` processor (pulls via `api`): Koin module + optional `@Factory` annotation. |
| `presenter-koin` | Android Compose lib | `PresentScreen<NavScope, Presenter>(navScope)`: `koinViewModel()` + `invoke`. |
| `template/` | IDE file template | `CorePresenter.kt*` — presenter/reducer/state/action/navscope/screen/destination skeleton. |

**Hilt:** no `presenter-hilt` module registered in `settings.gradle.kts` (`presenter-hilt*` folders hold stale `build/` output). Hilt projects use plain `@HiltViewModel` presenters with generated or hand-written factory (see demo).

## Roles

| Piece | Role |
|---|---|
| `UiState` | Immutable state implementing `PresenterUiState`; often carry UI callbacks calling `emitUserAction`. |
| `Action` | Sealed type of every event reducer handles (UI events + `flows` events). |
| `NavScope` | Interface of navigation lambdas, supplied by caller of `presenter(navScope)`. |
| `Screen` | Stateless composable rendering `UiState`; no business rules. |
| `LBPresenter` | `ViewModel`. Declares `flows`, `getInitialState()`, `content`, `getReducerByState()`; `invoke(navScope)` renders. |
| `LBSinglePresenter` | One reducer for whole lifecycle; override `createReducer(context)` (built lazily once). |
| `LBFactorySinglePresenter` | `createReducer` is `final`, delegated to injected `LBSingleReducerFactory`. |
| `LBReducer` | `reduce(actualState, action, performNavigation, useActivity) → ReduceResult`; `filterAction` / `filterUiState`. |
| `LBSingleReducer` | Reducer with filters always `true` — handles every action for one `UiState` type. |

## Invariants and runtime shape

- **Reducer only mutator.** All state changes from `reduce`; navigation via `performNavigation { … }`, side effects via `ReduceResult.sideEffect` (`state withSideEffect { … }` / `state.asResult()`), launched in reducer's `coroutineScope`.
- `useActivity` for activity-bound work only; replayed once, run only while lifecycle `STARTED` and activity not finishing/destroyed.
- Action sources = `flows` + unlimited channel fed by `emitUserAction`, merged in `LBReducer.collectReducer`. Side effects re-enter pipeline via `emitUserAction`.
- State exposed with `stateIn(viewModelScope, WhileSubscribed(5_000))`; `getInitialState()` must be fast, side-effect free.
- **Multi-state:** plain `LBPresenter.getReducerByState` swaps reducer when `UiState` *class* changes (demo `MultiStatePresenter`).
- **Navigation guard:** navigation rejected while another pending, or if screen already navigated and no longer `RESUMED` (anti double-tap, changelog 2.0.0).
- Reducer needs `coroutineScope` + `emitUserAction`; presenters provide via `LBPresenterContext` (`viewModelScope`, `::emitUserAction`).

## Route args (`SavedStateHandle`)

Presenter parses route args, not reducer: template does `savedStateHandle.toRoute<…Destination>()` in presenter constructor, then passes presenter-owned values to reducer in `createReducer` (as `@FactoryArg`). Keep `content` to rendering + callback wiring.

## Generated reducer factories (KSP)

- Annotate reducer with `@GenerateReducerFactory` → `<Name>ReducerFactory` in same package.
- Contract (enforced in `ReducerFactorySignatureValidator`): concrete class, **directly** extends `LBSingleReducer`, public/internal class + constructor, exactly one `coroutineScope: CoroutineScope` and one `emitUserAction: (Action) -> Unit`, no defaults, no varargs; `@FactoryArg` not allowed on those two nor on param named `context`. Generated visibility = stricter of class/constructor.
- Other params **injected** into factory constructor (DI qualifiers `@Named` javax/jakarta/koin and `@Qualifier`-meta annotations preserved; Koin `@Provided` too).
- `@FactoryArg` params → `<Name>ReducerFactoryArgs` data class; factory then exposes `create(context, factoryArgs)` + convenience `create(context, arg1, …)`, does **not** implement `LBSingleReducerFactory`.
- Without `@FactoryArg`, factory implements `LBSingleReducerFactory` → usable directly by `LBFactorySinglePresenter`.

## Koin generation

Apply only `ksp(presenter-koin-ksp)`. KSP args:

| Arg | Effect |
|---|---|
| `studio.lunabee.presenter.generateKoinModule=true` | Emits `GeneratedReducerFactoryModule.kt` with `val generatedReducerFactoryModule` (`factory { XFactory(get(), …) }`). |
| `studio.lunabee.presenter.koinModulePackage=<pkg>` | Package of module; missing → warning + common-package inference. |
| `studio.lunabee.presenter.annotateFactory=true` | Koin processor takes over factory generation, adds `@org.koin.core.annotation.Factory` for `@ComponentScan`. |

- Module generated only for single-platform compilations (KMP metadata compilations skip). Rebuilt from all source files each round with `Dependencies.ALL_FILES` to survive incremental KSP.
- Consumer: `includes(generatedReducerFactoryModule)` / `modules(generatedReducerFactoryModule)`, presenters get `reducerFactory = get()`.

## v1 → v2 migration (`compose/presenter/MIGRATION_V2.MD`)

Written as agent prompt for client repos. Key points:
- **Breaking:** `LBSinglePresenter.initReducer()` → `createReducer(context: LBPresenterContext<Action>)`; stop wiring `viewModelScope` / `::emitUserAction` by hand.
- DI-only reducers → `LBFactorySinglePresenter` + injected generated factory; reducers needing presenter values stay on `LBSinglePresenter`, call `factory.create(context, …)` with `@FactoryArg`s (incl. former Koin `parametersOf` args).
- Add `@GenerateReducerFactory`, KSP dep + args, `includes(generatedReducerFactoryModule)`; clean unused presenter ctor params; update templates.

## Relation to `compose/navigation`

`compose/navigation` (Navigation 3) does **not** depend on presenter modules; they meet in app. `LbcDestination<NavScope>` returns `LbcNavigationScreen<NavScope>`, builds `NavScope` from `NavigationHelper` (`navigate`, `modal`, `popBackStack(PopUpTo…)`, `popAllModals`); `LbcNavHost` renders entries (modals via `ModalSceneStrategy`) with per-entry ViewModel store. Presenter can implement `LbcNavigationScreen`, call `invoke(navScope)` in `Screen` (demo `TestPresenterPresenter`). See [[compose-ui]].

## Tests

- `compose/presenter/presenter/src/test/…/LBReducerTest.kt` — concurrent `collectReducer` with `runTest`.
- `…/LBSinglePresenterTest.kt` — Robolectric + `createComposeRule` + `LbcInjectComponentActivityRule` (`compose/test/robolectrictest`): `useActivity` chaining, `@FactoryArg`-style runtime args, presenter-computed values.
- `presenter-ksp/src/test` and `presenter-koin-ksp/src/test` — JUnit tests on parser helpers, validator, file generators (golden source strings), Koin options; no full KSP compilation tests.
- `compose/navigation` has no tests.

## Demo (`demo-compose`)

`ksp(projects.compose.presenterKoinKsp)` with `generateKoinModule` + `koinModulePackage`; Koin started in `DemoApp.kt` alongside Hilt.

| Sample (`demo-compose/src/main/kotlin/studio/lunabee/compose/demo/…`) | Shows |
|---|---|
| `presenter/simple/` | `LBFactorySinglePresenter` + generated factory, `useActivity`, cascading `emitUserAction` |
| `presenter/timer/` | `@FactoryArg currentTime`; Koin (`PresentScreen`) and Hilt variants of same reducer |
| `presenter/pullToRefresh/` | Hand-written `@Inject` `LBSingleReducerFactory` with Hilt |
| `presenter/multistate/` | Raw `LBPresenter` switching reducers per `UiState` class |
| `navigation/` | `LbcNavHost` + `LbcDestination`s, presenter as `LbcNavigationScreen`, `performNavigation` |

## Key types → file

| Type | Path |
|---|---|
| `LBPresenter`, `LBSimpleReducer` | `compose/presenter/presenter/src/main/kotlin/studio/lunabee/compose/presenter/LBPresenter.kt` |
| `LBSinglePresenter` | `…/presenter/LBSinglePresenter.kt` |
| `LBFactorySinglePresenter` | `…/presenter/LBFactorySinglePresenter.kt` |
| `LBReducer` / `LBSingleReducer` | `…/presenter/LBReducer.kt` / `LBSingleReducer.kt` |
| `LBSingleReducerFactory`, `LBPresenterContext` | `…/presenter/LBSingleReducerFactory.kt`, `LBPresenterContext.kt` |
| `ReduceResult`, `withSideEffect`, `asResult` | `…/presenter/ReduceResult.kt` |
| `@GenerateReducerFactory`, `@FactoryArg` | `compose/presenter/presenter-annotations/src/main/kotlin/studio/lunabee/compose/presenter/` |
| KSP processor / validator / generator | `compose/presenter/presenter-ksp/src/main/kotlin/studio/lunabee/compose/presenter/ksp/` |
| Koin processor + module generator | `compose/presenter/presenter-koin-ksp/src/main/kotlin/studio/lunabee/compose/presenter/ksp/koin/` |
| `PresentScreen` | `compose/presenter/presenter-koin/src/main/kotlin/studio/lunabee/compose/presenter/koin/PresentScreen.kt` |
| `LbcNavHost`, `LbcDestination`, `NavigationHelper` | `compose/navigation/src/main/kotlin/studio/lunabee/compose/navigation/` |