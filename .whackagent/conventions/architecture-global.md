# Kotlin — Architecture (global)

> whackagent convention module · lens: **structure**. Platform-agnostic principles; the kind module (`architecture-app.md` / `architecture-package.md`) carries layers + file tree.

Architecture here = how feature **decomposed, made testable, kept modular** — not how code reads (that elegance module).

## YAGNI · SOLID · DRY

- **YAGNI** — build what task need now, nothing speculative. No param/interface/config with single caller and no near-term second use. Add generality when second real case arrive, not before.
- **SOLID** —
  - *Single responsibility*: one class, one reason to change. Class that fetch *and* parse *and* render = three classes.
  - *Open/closed*: extend via new implementations, not by growing `when` case-by-case across the codebase.
  - *Liskov*: implementations honor interface contract — no surprise throws or no-ops.
  - *Interface segregation*: small focused interfaces over one fat interface; client depend only on what it use.
  - *Dependency inversion*: depend on abstractions (`interface`), never concretions, at layer boundaries.
- **DRY** — no duplicated or re-implemented logic. Same block copy-pasted across files, or re-implementation of something already in codebase → factor it, reuse it.

## Design principles (general)

- **Composition over inheritance.** No `open` class hierarchies for domain logic. Classes `final` by default (Kotlin default — keep it). Data = `data class`, immutable. Dependencies = constructor params — never inherited.
- **Interface-driven boundaries.** Repositories, data sources, clocks, dispatchers, anything I/O → behind `interface`, concrete impl injected. This what make things swappable + testable (fakes).
- **Sealed type vs interface.** `sealed` only for fixed, closed one-of (UI state, domain results, errors — each subtype carry own data). Set open/extensible, or each case own behavior → plain `interface` + implementations.
- **Constructor injection.** Every dependency a constructor param, required or defaulted. Use whatever DI project already uses (Hilt, Koin, Dagger, manual) — never introduce a second one. **No service locator calls in business code** (`get()`, `inject()`, `KoinComponent`, static singletons) — only at composition root / framework entry points.
- **Errors as types for expected failures.** Sealed result/error types or `Result<T>` for failures caller must handle (network, validation, not found). Exceptions for programmer errors and truly unexpected states. Never `catch (e: Exception)` and return `null` silently.
- **Single responsibility, small files.** One class, one reason to change; files small + focused.
- **Testability by design.** Interface boundaries + constructor DI + injected dispatchers make fakes trivial. Class hard to test = design wrong.

## Review checklist (architecture category)

- **YAGNI**: no speculative abstraction/param/interface with single caller and no near-term use.
- **DRY**: no duplicated logic; no re-implementation of something already in codebase.
- **Single responsibility**: no class doing several unrelated jobs; small, focused units.
- **Composition, not inheritance**: no `open` domain hierarchies; deps injected.
- **Interface-driven**: collaborators across layers depended on via `interface`, injected through constructor — not instantiated inside, not fetched from a locator.
- **sealed vs interface**: sealed only for closed sets; open/extensible cases → interface + implementations.
- **Testability**: every unit reachable with fakes through its constructor; anything hard to test = design finding.
