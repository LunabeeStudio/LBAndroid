# Kotlin — Architecture (package / non-app)

> whackagent convention module · lens: **structure**. JVM server / library / CLI: module + package rules. Pair with `architecture-global.md` (platform-agnostic principles). Freer than app — not free-for-all.

No UI-layer mandate here (app-only). Surviving discipline:

## Rules (file tree / modules)

- **Group by feature/domain, never by type.** No top-level `models/`, `services/`, `utils/`, `helpers/` packages holding whole codebase. Feature/domain package own own types.
- **No flat tree.** More than ~5–7 files in package → add sub-packages. Pile of files in root package = finding.
- **Server layers**: routes/controllers → services → repositories. Routes parse + validate + map to domain, no business logic. Services hold business rules. Repositories own persistence.
- **No framework types in domain.** Ktor/Spring request/response types, ORM entities, serialization annotations stay at edges; domain models plain Kotlin. Map at boundaries.
- **Modules = independent responsibilities.** Split into Gradle modules when concerns genuinely independent (`core`, `api`, `persistence`). Dependencies point one direction; no cycles.
- **Public API surface intentional.** Libraries: `internal` by default, `public` only what consumers need — consider explicit API mode. Document public API (per `public_doc` toggle).
- **Tests mirror source tree.** `src/test/kotlin/` follow same package structure as `src/main/kotlin/`.
- **Extensions**: in file named after what they extend/do, local to feature needing them — shared only when truly cross-cutting.

## Example layout

```
src/main/kotlin/<package>/
├── <domain>/                     # one package per domain/feature
│   ├── <Domain>Routes.kt         # server: HTTP edge only
│   ├── <Domain>Service.kt
│   ├── <Domain>Repository.kt
│   ├── model/                    # domain-local value types
│   └── internal/                 # implementation detail, kept internal
├── <otherdomain>/
│   └── …
├── persistence/                  # a cohesive subsystem, only if it earns its place
└── Application.kt                # composition root: wiring, DI setup
src/test/kotlin/<package>/        # mirrors src/main/kotlin/
└── <domain>/<Domain>ServiceTest.kt
```

## Review checklist (architecture category — package-specific)

> Platform-agnostic checks (YAGNI/SOLID/DRY, composition, interface-driven, sealed-vs-interface, testability) live in `architecture-global.md`. Below = package-specific only.

- No flat dump of files in root package.
- Group by domain, not file type.
- Package nesting reflect size of each area; large areas get sub-packages.
- Server: no business logic in routes; no framework/ORM types in domain.
- Module boundaries coherent; dependency direction clean.
- `public` intentional and minimal; internals `internal`.
- Tests mirror source structure.

> **Project override.** This default for non-app Kotlin. If repo has own structural rules, edit this file in project's conventions dir (`paths.conventions`, default `.whackagent/conventions/`) to match — reviewer read project copy.
