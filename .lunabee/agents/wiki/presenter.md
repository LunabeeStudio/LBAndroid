# Presenter stack

MVI-style screen driver for Compose: `ViewModel` presenter feed actions into reducer; reducer alone own state, navigation, side effects. KSP generate reducer factories (optionally Hilt `@Inject` ctor or Koin bindings).

Related: [[architecture]] · [[compose-ui]] · [[build-and-publishing]] · [[common-libraries]]

## Modules (`compose/presenter/`)

Published as `studio.lunabee.compose.presenter:<module>`; versions in `buildSrc/src/main/kotlin/AndroidConfig.kt` (`LBCPRESENTER_*`). See [[build-and-publishing]].

| Module | Kind | Purpose |
|---|---|---|
| `presenter` | Android Compose lib | Runtime: `LBPresenter`, reducers, `ReduceResult`, `LBPresenterContext`. Re-exports `presenter-annotations` (`api`). No dependency on `compose:core` / `compose:image`. |
| `presenter-annotations` | JVM | `@GenerateReducerFactory`, `@FactoryArg` (BINARY retention). |
| `presenter-ksp` | JVM, KSP processor | Generates `<Reducer>Factory` (+ `<Reducer>FactoryArgs`); hosts `FactoryOwningProcessorProvider` ownership contract + shared KSP option names (`KspOptions.kt`). |
| `presenter-hilt-ksp` | JVM, KSP processor | Pulls `presenter-ksp` (`api`). Owns factory generation by default, adds `@javax.inject.Inject` primary ctor. |
| `presenter-koin-ksp` | JVM, KSP processor | Pulls `presenter-ksp` (`api`): Koin module + optional `@Factory` annotation. |
| `presenter-hilt` | Android Compose lib | `PresentScreen` overloads (`hiltViewModel()`, explicit store owner + factory, assisted factory). `api`: `hilt-lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`. |
| `presenter-koin` | Android Compose lib | `PresentScreen<NavScope, Presenter>(navScope)`: `koinViewModel()` + `invoke`. `api`: Koin BOM + `koin-compose-viewmodel`. |
| `template/` | IDE file template | `CorePresenter.kt*` — presenter/reducer/state/action/navscope/screen/destination skeleton. |

`PresentScreen` is `inline` + `reified` in both DI modules → libs it calls exposed as `api`, else consumer can't compile inlined body.

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
- Other params **injected** into factory constructor. Qualifiers carried over: `@Named` (javax/jakarta/Koin, flavour recorded in `DiQualifier.Named.annotationClassName`) and `@Qualifier`-meta annotations copied **with arguments** (`DiQualifier.Typed.annotationSpec`); Koin `@Provided` too.
- `GeneratedFactoryDecorator` (DI processors) adds class / constructor / parameter annotations. Non-empty `constructorAnnotations` forces primary ctor even with zero injected params (Hilt needs it).
- `@FactoryArg` params → `<Name>ReducerFactoryArgs` data class; factory then exposes `create(context, factoryArgs)` + convenience `create(context, arg1, …)`, does **not** implement `LBSingleReducerFactory`.
- Without `@FactoryArg`, factory implements `LBSingleReducerFactory` → usable directly by `LBFactorySinglePresenter`.

## Factory generation ownership

One factory file per reducer, whichever processors sit on KSP classpath. `presenter-ksp/src/main/kotlin/studio/lunabee/compose/presenter/ksp/FactoryGenerationOwnership.kt`:

- DI processor providers implement `FactoryOwningProcessorProvider.ownsFactoryGeneration(annotateFactoryOption: Boolean?)` (`null` = option unset).
- Every processor discovers providers via `ServiceLoader` of `SymbolProcessorProvider` (shared KSP classloader), resolves same `FactoryGenerationOwnership`, compares by **class** (KSP instance ≠ discovered instance).
- Claim rules: Hilt claims unless option `false`; Koin claims only when option `true`.

| Outcome | Who generates | Report |
|---|---|---|
| `Base` (0 claimants) | `presenter-ksp`, no DI annotations | warn if `annotateFactory=true` (no DI processor found) |
| `Owned` (1) | owning DI processor, with its decorator | Hilt warns if another DI processor present (factories carry `@Inject` only) |
| `Ambiguous` (≥2, e.g. Hilt + Koin with `annotateFactory=true`) | nobody | **compile error** |
| `Unresolved` (service load failed) | nobody | **compile error** |

- `annotateFactory` value not `true`/`false` → compile error (reported once, by `presenter-ksp`).
- Errors/warnings for `Base`/`Ambiguous`/`Unresolved` raised only by `presenter-ksp` processor (always on classpath).

## Hilt integration

Apply only `ksp(presenter-hilt-ksp)` + `implementation(presenter-hilt)`. No KSP arg, no Dagger module to write.

- Generated factory gets `@javax.inject.Inject` primary ctor; qualifiers re-emitted (`@Named` keeps `jakarta.inject` flavour, any other `@Named` → `javax.inject.Named`; typed qualifiers copied whole). `@FactoryArg` still → `<Name>ReducerFactoryArgs`.
- Leave `annotateFactory` unset. `false` → factories without `@Inject` ctor (Hilt can't bind without hand-written `@Provides`) + warning.
- Presenter = plain `@HiltViewModel` injecting generated factory: `LBSinglePresenter` + `factory.create(context, …)` for `@FactoryArg`s, or `LBFactorySinglePresenter(reducerFactory)` when factory implements `LBSingleReducerFactory`.
- Hilt + Koin processors together, option unset → Hilt owns; Koin must bind factories via `generatedReducerFactoryModule`, not `@ComponentScan` (demo-compose runs this).

`PresentScreen` overloads (`compose/presenter/presenter-hilt/src/main/kotlin/studio/lunabee/compose/presenter/hilt/PresentScreen.kt`):

| Signature | Use |
|---|---|
| `PresentScreen<NavScope, Presenter>(navScope)` | Default: `hiltViewModel()`. |
| `PresentScreen(navScope, viewModelStoreOwner, factory, extras)` | Host not Hilt VM store owner (e.g. input method service): `viewModel(owner, factory, extras)`. `extras` defaults to owner's `defaultViewModelCreationExtras`, else `CreationExtras.Empty` → pass extras when presenter reads route args from `SavedStateHandle`. |
| `PresentScreen<NavScope, Presenter, AssistedFactory>(navScope, creationCallback)` | `@HiltViewModel(assistedFactory = …)` presenter needing runtime value only composition has. |

**Destroyed-lifecycle rule:** every Hilt overload returns early, rendering nothing, when `LocalLifecycleOwner` reached `DESTROYED` (`isLifecycleOwnerDestroyed()`, `@PublishedApi internal`). navigation-compose `NavHost` can recompose just-destroyed `NavBackStackEntry` while it leaves composition; reading its ViewModels then throws `IllegalStateException`. Destroyed entry never reactivates → skipping safe. Koin `PresentScreen` has no such guard.

## Koin generation

Apply only `ksp(presenter-koin-ksp)`. KSP args:

| Arg | Effect |
|---|---|
| `studio.lunabee.presenter.generateKoinModule=true` | Emits `GeneratedReducerFactoryModule.kt` with `val generatedReducerFactoryModule` (`factory { XFactory(get(), …) }`). |
| `studio.lunabee.presenter.koinModulePackage=<pkg>` | Package of module; missing → warning + common-package inference. |
| `studio.lunabee.presenter.annotateFactory=true` | Koin processor claims factory generation (see ownership), adds `@org.koin.core.annotation.Factory` for `@ComponentScan`. Fails compile if `presenter-hilt-ksp` also on classpath. |

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
- `presenter-ksp/src/test`, `presenter-koin-ksp/src/test`, `presenter-hilt-ksp/src/test` — JUnit tests on parser helpers, validator, file generators (golden source strings), Koin options, Hilt decorator (`HiltFactoryDecoratorTest`: `@Inject` ctor, jakarta `@Named`, typed qualifier args); `FactoryOwningProviderTest` resolves ownership with fake Koin-/Hilt-like providers. No full KSP compilation tests.
- `presenter-hilt` has no tests.
- `compose/navigation` has no tests.

## Demo (`demo-compose`)

`ksp(projects.compose.presenterHiltKsp)` **and** `ksp(projects.compose.presenterKoinKsp)`, args `generateKoinModule` + `koinModulePackage`, `annotateFactory` unset → Hilt owns generation (factories `@Inject`), Koin binds them via `generatedReducerFactoryModule`. Koin started in `DemoApp.kt` alongside Hilt.

| Sample (`demo-compose/src/main/kotlin/studio/lunabee/compose/demo/…`) | Shows |
|---|---|
| `presenter/simple/` | `LBFactorySinglePresenter` + generated factory, `useActivity`, cascading `emitUserAction` |
| `presenter/timer/` | `@FactoryArg currentTime`; Koin and Hilt `PresentScreen` variants injecting same generated `TimerReducerFactory` (`TimerInjectedParam` from Hilt `PresenterReducerFactoryModule`) |
| `presenter/pullToRefresh/` | Hand-written `@Inject` `LBSingleReducerFactory` with Hilt (`hiltViewModel()` direct) |
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
| KSP processor / validator / generator, `GeneratedFactoryDecorator` | `compose/presenter/presenter-ksp/src/main/kotlin/studio/lunabee/compose/presenter/ksp/` |
| `FactoryOwningProcessorProvider`, `FactoryGenerationOwnership` | `…/presenter-ksp/…/ksp/FactoryGenerationOwnership.kt` |
| `AnnotateFactoryOption`, `GenerateKoinModuleOption`, `booleanKspOption` | `…/presenter-ksp/…/ksp/KspOptions.kt` |
| Hilt processor + `HiltFactoryDecorator` | `compose/presenter/presenter-hilt-ksp/src/main/kotlin/studio/lunabee/compose/presenter/ksp/hilt/` |
| Koin processor + module generator | `compose/presenter/presenter-koin-ksp/src/main/kotlin/studio/lunabee/compose/presenter/ksp/koin/` |
| `PresentScreen` (Koin) | `compose/presenter/presenter-koin/src/main/kotlin/studio/lunabee/compose/presenter/koin/PresentScreen.kt` |
| `PresentScreen` (Hilt) | `compose/presenter/presenter-hilt/src/main/kotlin/studio/lunabee/compose/presenter/hilt/PresentScreen.kt` |
| `LbcNavHost`, `LbcDestination`, `NavigationHelper` | `compose/navigation/src/main/kotlin/studio/lunabee/compose/navigation/` |