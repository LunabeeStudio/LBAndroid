# Kotlin — Elegance (idiomatic Kotlin)

> whackagent convention module · lens: **elegance**. The "real Kotlin, not Java written in Kotlin" gate.

## Code speaks for itself

Bar: archi-clean, like senior Kotlin dev write. Names + structure carry meaning — reader get it, no comments. Need comment to explain *what* code do? Rename/restructure instead. Headline goal; all below serve it.

## Idiomatic Kotlin, not Java-in-Kotlin

Write to grain of language. Reject Java patterns when Kotlin offer better.

- **`val` over `var`.** Immutability default. Read-only collection types (`List`, `Map`) in APIs; mutable only local or private.
- **`data class` for models**, `@JvmInline value class` for wrapped primitives that prevent mix-ups (`UserId`, `Email`).
- **`sealed interface` / `sealed class` for closed state**, not booleans-and-flags or magic strings/ints. Model impossible states out of existence. `when` over sealed type exhaustive — no `else` branch hiding new cases.
- **Null-safety over sentinels.** No `-1` / `""` meaning "absent". Nullable type + `?.`, `?:`, `let`. No `!!` unless invariant is proven and obvious — `requireNotNull`/`checkNotNull` with message instead.
- **Collection transforms.** `map` / `filter` / `mapNotNull` / `associateBy` / `groupBy` / `firstOrNull` / `fold` over manual index loops and mutable accumulators.
- **Early return.** `?: return`, `require`, `check` at top; flatten happy path.
- **Extension functions** to add behavior to types you don't own — scoped to where they're used, not a global dump.
- **Default + named arguments** over overload chains and builders.
- **Scope functions sparingly.** `apply` for configuration, `let` for nullable, `also` for side effect. Nested scope functions or `it` shadowing = unreadable — use named locals.
- **No stringly-typed APIs.** Enums, sealed types, value classes, not strings passed around.
- **`object` for true singletons without state**, not for hiding global mutable state.

Reviewer see Java-style getter/setter, index loop, nullable-with-sentinel, `!!` without proof, boolean soup that should be sealed type — that finding.

## Advanced features (use sparingly)

Earn place only when cut real complexity.

- **Type-safe builders / DSLs** (`@DslMarker`) — when callers gain declarative syntax.
- **Delegation** (`by lazy`, `by map`, class delegation `by`) — factor repeated property or interface behavior.
- **`inline` + `reified`** — when generic type needed at runtime, or lambda overhead matter in hot path.
- **Operator overloading** — only when meaning is obvious from math/domain.

Rule: plain version first. Never to show off.

## Concurrency

Coroutines + Flow, structured concurrency.

- `suspend` functions for one-shot async; `Flow` for streams; `StateFlow` for observable state, `SharedFlow` for events when truly needed.
- Launch in lifecycle-bound scope (`viewModelScope`, injected `CoroutineScope`). **Never `GlobalScope`**, never `runBlocking` outside tests/main.
- **Inject dispatchers** (`CoroutineDispatcher` constructor param) — never hardcode `Dispatchers.IO` in logic you test.
- Main-safe suspend functions: switch with `withContext` inside the function that does blocking work, not at every call site.
- Never swallow `CancellationException`. `catch (e: Exception)` around suspend code rethrows it.
- No RxJava, callbacks, or `LiveData` in new code unless project is built on them.
- Shared mutable state behind `Mutex` or confined to one dispatcher — never unsynchronized `var` across coroutines.
