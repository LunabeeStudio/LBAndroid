# Kotlin — Coding style

> whackagent convention module · lens: **style**. `wa-implementer` obeys it too. Base: official Kotlin coding conventions.

## One top-level class per file

One top-level `class` / `interface` / `object` / `enum class` per file. Filename match it (`UserRepository.kt` hold `UserRepository`).

Exceptions: sealed hierarchy with its subclasses may share file; small private helpers used only by main type may share file. File of only top-level functions/extensions → name after what they do (`StringExtensions.kt`), never `Utils.kt`.

```kotlin
// LoginUiState.kt
sealed interface LoginUiState {          // ✅ sealed parent + children, one file
    data object Idle : LoginUiState
    data class Error(val message: String) : LoginUiState
}
```

## Naming

- Classes/objects `UpperCamelCase`; functions, properties, locals `lowerCamelCase`.
- Constants (`const val`, top-level/`object` `val` holding deeply immutable data) `SCREAMING_SNAKE_CASE`.
- Backing property prefixed `_`: `private val _state` + `val state`.
- Packages lowercase, no underscores. Test names may use backticks.
- Acronyms: two letters uppercase (`IOStream`), longer camel (`HttpClient`).

## Explicit types on public API

Public/protected functions and properties declare return type — API contract must not depend on inference. Locals and private members may infer.

```kotlin
fun loadUsers(): Flow<List<User>> = repository.users()   // ✅ public, explicit
private fun format(user: User) = "${user.first} ${user.last}"  // ✅ private may infer
val state = MutableStateFlow(Idle)                       // ❌ public, inferred
```

## Member order

1. Property declarations and `init` blocks
2. Secondary constructors
3. Methods — public → internal → private; related methods grouped, not alphabetized
4. Nested classes
5. `companion object` last

```kotlin
class UserViewModel(
    private val repository: UserRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(UserUiState())   // 1 properties
    val state: StateFlow<UserUiState> = _state.asStateFlow()

    init { refresh() }                                      // 1 init

    fun refresh() { ... }                                   // 3 public
    private fun update(users: List<User>) { ... }           //   private last

    companion object { ... }                                // 5 last
}
```

## Expression bodies

Single-expression function → expression body (`= ...`). Multi-branch logic or side effects → block body.

## Comments

- No comments for obvious code.
- No comments when editing code after user feedback.
- KDoc (`/** */`) on all public API — classes, functions, properties. Use `@param`, `@return`, `@throws` only when they add info beyond the signature. *(Disable via `public_doc` toggle for teams not requiring it.)*

## File header

Only when project already uses one (license, copyright). Match existing header exactly; never invent one.

## Formatting

- 4-space indent. Follow project `.editorconfig`, ktlint, detekt, spotless config when present — tool wins over this file.
- Multi-line call or declaration: break after `(`, one param per line, `)` on own line. **Trailing commas** on multi-line declarations and call sites.
- Chained calls: break before `.`, one call per line when chain wraps.

```kotlin
fun createUser(
    name: String,
    email: String,
    isAdmin: Boolean = false,
): User {
    /* ... */
}
```

## Tests and fakes

- Test class `FooTest` for `Foo`, same package, mirrored path under test source set.
- Fakes named `FakeFooRepository`, implement the interface, live in test sources (or shared `testFixtures` / test module when reused).
- See `testing.md`.
