# Kotlin — Testing & fakes

> whackagent convention module · lens: **style**.

## Testing

- Framework = whatever project uses (JUnit 5, JUnit 4, `kotlin.test` in KMP `commonTest`). Never introduce a second one.
- One test class per unit under test (`UserRepositoryTest`), mirrored package and path in test source set.
- Test names describe behavior. Backtick names on JVM (`` `returns cached users when offline` ``); camelCase in KMP common code where backticks unsupported.
- Given / When / Then shape — setup, one action, assertions — separated by a blank line.
- One behavior per test. Several unrelated asserts → split.

```kotlin
class UserRepositoryTest {
    private val remote = FakeUserRemoteDataSource()
    private val repository = DefaultUserRepository(remote)

    @Test
    fun `returns users from remote`() = runTest {
        remote.users = listOf(User.fake(name = "Ada"))

        val users = repository.getUsers()

        assertEquals("Ada", users.single().name)
    }
}
```

## Coroutines and Flow

- `kotlinx-coroutines-test`: `runTest` for suspend tests; inject `StandardTestDispatcher` / `UnconfinedTestDispatcher` wherever production code takes a dispatcher.
- `Dispatchers.setMain(testDispatcher)` for ViewModel tests (a rule/extension when project has one), reset after.
- Flow assertions with Turbine when present (`flow.test { awaitItem() }`); else `first()` / `toList()` on a bounded flow. Never `Thread.sleep` or real delays.

## Fakes over mocks

- Hand-written fakes implementing the interface (`FakeUserRepository`) — in-memory, observable state, deterministic.
- Mock libraries (MockK, Mockito) only at true external boundaries you can't fake cheaply, or when project already standardizes on them.
- Reused fakes live in shared test fixtures (`testFixtures`, `:core:testing` module) — not copy-pasted per test.

## Model fakes

Model need test/preview instance → `fun Foo.Companion.fake(...)` (or top-level `fakeFoo(...)`) in test fixtures. Every parameter mirror a property and **has default value**, so `User.fake()` work bare and any field overridable.

```kotlin
fun User.Companion.fake(
    id: String = "user-1",
    name: String = "Ada Lovelace",
    isAdmin: Boolean = false,
): User = User(id = id, name = name, isAdmin = isAdmin)
```

## UI tests

- Compose: `createComposeRule()`, find nodes by `onNodeWithTag` (tags from `compose.md`) or semantics, not by display text that localization changes.
- Test the stateless screen with fake state; ViewModel logic tested separately in unit tests.
