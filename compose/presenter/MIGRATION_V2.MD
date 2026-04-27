Analyze this repository and migrate it from manual presenter reducer instantiation to the LBPresenter generated reducer-factory pattern.

Before editing:

- Read and follow any `AGENTS.md` / repo instructions you find.
- Limit scope to the presenter migration only. Do not copy unrelated product changes from reference branches.
- Identify every presenter/reducer pair still using manual reducer creation (`initReducer()` or `createReducer(...)` that directly
  instantiates the reducer).
- Library code is open-source at https://github.com/LunabeeStudio/LBAndroid

Target migration:

1. Update the presenter version entry in `libs.versions.toml` to the latest version (2.0.0+) published on Maven Central before changing
   code.
    - Check Maven Central for `studio.lunabee.compose.presenter:presenter`, `studio.lunabee.compose.presenter:presenter-koin`,
      `studio.lunabee.compose.presenter:presenter-ksp` and `studio.lunabee.compose.presenter:presenter-annotations`
    - If the project uses a shared presenter version alias, update that alias once and reuse it for every presenter dependency, except if
      you found more recent versions for specific artifacts
2. Upgrade each affected module to use LBPresenter reducer factory generation.
3. Add the KSP dependency for the presenter processor where needed.
4. If the project uses Koin, ask the user which reducer-factory registration mode they want. Before asking, detect the current
   setup by scanning the project's build files for the Koin K2 compiler plugin id `io.insert-koin.compiler.plugin` and any
   `@KoinApplication` / `@Module` / `@ComponentScan` / `@KoinViewModel` annotations, and present the detected mode as the
   default in the question. Let the user override the default.

   Option A — Koin compiler plugin (recommended when the project already applies `io.insert-koin.compiler.plugin` and uses
   `@Module` / `@ComponentScan`).
   - Add the KSP flag:
     `ksp {`
     `    arg("studio.lunabee.presenter.useKoinAnnotations", "true")`
     `}`
   - Ensure the project's existing `@Module @ComponentScan("ROOT_PACKAGE")` covers every package containing a
     `@GenerateReducerFactory`-annotated reducer.
   - Do NOT add `includes(generatedReducerFactoryModule)` — the aggregator is not generated in this mode.
   - Inject the generated factory directly as a `@Factory`-registered type. No `@Provided` is needed on the presenter
     constructor because the compiler plugin's compile-safety check sees the annotated factory.

   Option B — classic DSL module (recommended when the project is still on runtime Koin DSL, has no
   `io.insert-koin.compiler.plugin` applied, or mixes presenter-ksp factories with other hand-written DSL `module { }`
   declarations).
   - Add the KSP flags:
     `ksp {`
     `    arg("studio.lunabee.presenter.generateKoinModule", "true")`
     `    arg("studio.lunabee.presenter.koinModulePackage", "YOUR_MODULE_ROOT_PACKAGE")`
     `}`
   - In the project's `startKoin { ... }` / app module, add: `includes(generatedReducerFactoryModule)`
   - If several generated `generatedReducerFactoryModule` values collide, use import aliases.

5. Record the selected mode — you must report it in the final summary.
6. Annotate reducers that should get generated factories with `@GenerateReducerFactory`.
7. Mark every reducer constructor parameter that is not DI-provided and must be supplied by the presenter at creation time with
   one of the recognised "supplied at construction" annotations. presenter-ksp accepts any of these — pick whichever is most
   idiomatic for the project's DI stack and keep usage consistent within a module:
    - `@studio.lunabee.compose.presenter.FactoryArg` (framework-agnostic, no extra dependency)
    - `@org.koin.core.annotation.InjectedParam` (Koin-annotated projects)
    - `@dagger.assisted.Assisted` (Dagger / Hilt projects)

   Typical examples of params to annotate:
    - route / nav args from `SavedStateHandle`
    - callbacks / lambdas created in the presenter
    - presenter-owned values computed at runtime
   - put the annotation on the same line as the parameter
8. Migrate presenters using these rules:
    - If the reducer only needs DI dependencies plus `LBPresenterContext`, convert the presenter to `LBFactorySinglePresenter` and inject
      the generated reducer factory. ⚠️ presenter instanciate with koin param `parameterOf` must be migrate with `@FactoryArg`
    - If the reducer needs presenter-owned runtime values, keep `LBSinglePresenter`, override
      `createReducer(context: LBPresenterContext<Action>)`, and call the generated factory with `factoryArgs = ...`.
9. Replace old manual reducer creation patterns:
    - remove `initReducer()`
    - stop manually wiring `viewModelScope` / `::emitUserAction` into reducer constructors
    - use `context.coroutineScope` and `context.emitUserAction` via generated factories
10. Clean up presenter constructors, properties, and imports after the migration:
    - remove unused constructor params/properties that were only needed for manual reducer instantiation
    - remove unused imports such as `LBSingleReducer`, `viewModelScope`, or obsolete use cases/managers no longer used by the presenter
    - keep only dependencies still needed for `flows`, `getInitialState()`, `content`, or other presenter-owned logic
    - try to remove `@Suppress("LongParameterList")` on presenter constructors when the migration cleanup makes it unnecessary
11. Add KDoc on every presenter class to link the related presenter files:
    - if the presenter class has no KDoc, add:
      `/**`
      ` * NAMEPresenter`
      ` *`
      ` * @see [NAME]Reducer`
      ` * @see [NAME]UiState`
      ` * @see [NAME]Action`
      ` * @see [NAME]NavScope`
      ` */`
    - if the presenter class already has KDoc, keep the existing text and append the `@see` lines at the end of the KDoc block
12. Update DI registrations so presenters receive `reducerFactory = get()` when needed.
    - In **Option A** (Koin compiler plugin), do NOT add a DSL `includes(...)` for presenter factories — `@ComponentScan` handles
      registration. Just remove old presenter constructor arguments that the reducer factory migration made redundant.
    - In **Option B** (DSL aggregator), do not modify the Koin module beyond:
      `includes(generatedReducerFactoryModule)`
      plus removing old presenter constructor arguments that are no longer needed after reducer factory migration.
    - In **Option B**, if several generated Koin modules named `generatedReducerFactoryModule` are imported in the same file, use
      import aliases to avoid collisions.
13. If the repo has templates/snippets/sample presenter files that still generate the old `initReducer()` pattern, update them too.

Implementation guidance:

- Prefer small, repetitive-safe edits.
- Preserve existing style, naming, and DI conventions.
- When a multiline call supports named arguments, use named arguments.
- Do not introduce unrelated behavior changes.
- If a module is not Koin-based, pause and explain the mismatch instead of guessing how to apply the generated Koin module step.

Verification:

- Run a real Gradle build covering every touched module.
- Run detekt after the migration.
- If repo instructions specify an exact detekt command, use that exact command.
- Fix warnings/errors related to the migration instead of suppressing them.
- Report the files changed, the migration categories applied, and the exact verification commands/results.

In your final summary, include:

- which reducer-factory registration mode was selected (Option A — Koin compiler plugin, or Option B — DSL aggregator)
- which presenters were converted to `LBFactorySinglePresenter`
- which presenters stayed on `LBSinglePresenter` with generated `FactoryArgs`
- which reducers gained `@GenerateReducerFactory`
- which constructor params were marked `@FactoryArg`
- confirmation that presenter constructor unused params/properties were cleaned up
- build and detekt results
