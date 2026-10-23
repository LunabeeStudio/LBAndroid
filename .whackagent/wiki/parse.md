# Parse

KMP client for Parse Server REST API under `common/parse/`, shared by Lunabee Parse apps (Kokoriko, RunMotion). Built on `ktor-*` (see [[common-libraries]]). Module layout: [[architecture]]. Usage guide: `common/parse/README.md`.

## Why it exists

- Parse Android SDK = Android only (OkHttp + Bolts), Parse-Swift = iOS only, community Kotlin SDKs dead or JVM-only → nothing callable from KMP common code.
- Seeded from Kokoriko `Kokoriko_KMP/remote-ktor` (headers plugin, error mapping, Date/Pointer serializers, keyset pull, `FakeParseServer`), app-specific parts stripped. Ticket #372.
- App DTOs, class names, typed cloud functions stay in apps. Parse↔Room sync on top of it = separate future module, not here.

## Modules

| Module | Coordinates | Role |
|---|---|---|
| `parse-ktor` | `studio.lunabee.parse:parse-ktor` | client, errors, Parse types, endpoint helpers |
| `parse-ktor-test` | `studio.lunabee.parse:parse-ktor-test` | `FakeParseServer` + `ParseRequestAssertions` for consumer tests; `api(ktor-client-mock)` so mock never reaches main classpath |

- `lunabee.kmp-library-conventions` + `jvm()` → JVM + iosArm64 + iosSimulatorArm64. Android apps consume JVM artifact.
- Shared version `PARSE_KTOR_VERSION` (`PARSE_KTOR_TEST_VERSION` aliases it). Both in BOM.
- No HTTP engine bundled: consumer passes `HttpClientEngine`.
- Packages by feature: `studio.lunabee.parse.{client, error, model, query, function, file, user, installation}`; `studio.lunabee.parse.test` in test module.

## Shape

- `LBParseClient(engine, LBParseConfig, configure)` — `AutoCloseable`. `httpClient` = escape hatch for app-specific calls (one Parse-configured client per app). `configure` installs extra plugins (e.g. `LBKtorMonitoring`) without library depending on them.
- Headers: app id + client key always; `X-Parse-Session-Token` from `LBParseSessionTokenProvider` (null → absent); `X-Parse-Installation-Id` from optional `LBParseInstallationIdProvider`. `withoutSessionToken()` opts one request out. App owns token + installation id, library never stores them.
- Helpers = extension functions on `LBParseClient`: `query`, `pull` (keyset cursor `updatedAt|objectId`), `callFunction` (unwraps `result`), `uploadFile`, user auth (`signUp`, `logIn`, `logInAnonymously`, `requestPasswordReset` skip session token; `logOut`, `me` send it), installation create/update.
- `pull` next-page `where` = caller `where` + keyset `$or`; caller `where` already holding `$or` → both wrapped in `$and`, so caller filter survives page 2+.
- `ParseDateSerializer` = `{"__type":"Date","iso":…}`, ms precision, for custom Date fields only. Top-level `createdAt`/`updatedAt` come back as plain ISO strings → default `Instant` serializer.

## Errors and retry — invariants

- Sealed `LBParseException` (`Network`, `Parse(code, error, httpStatus)`, `Http(httpStatus, body)`, `Decoding`, `Unexpected`), extends `Exception`. `Parse.message` = Parse `error` text (RunMotion shows cloud-function errors by message). `ParseErrorCode` constants instead of magic numbers.
- `LBParseConfig.mapErr` converts to app error type (RunMotion `RMRemoteError`, Kokoriko `CoreError`); no invalid-session listener in library — apps react to 209 in `mapErr`.
- **Plugin order matters.** `ParseErrorMappingPlugin` installed first, then `LBKtorExceptionHandler`, `LBKtorJson`, `LBKtorKermit`, headers, `HttpRequestRetry` last:
  - Earlier-installed HttpSend interceptors = outer → retry innermost, sees raw `IOException` before handler wraps it.
  - ktor-core handler re-wraps non-`LBKtorException` thrown by its own `mapErr` as `Unexpected` → handler only maps `LBKtorException` → `LBParseException`; app `mapErr` applied by `ParseErrorMappingPlugin` on top.
  - Body decoding errors raised in Ktor's own phase before `Receive` → mapping plugin intercepts its own phase inserted before `Receive`, so `mapErr` also covers `Decoding`.
- Retry: only `IOException` (timeouts included), `maxRetries` (default 1, matches Parse SDK config), fixed 1 s delay, every method incl. POST. Responses never retried (default 5xx retry turned off). `maxRetries = 0` → no retry plugin.
- Cancellation never mapped.

## Testing

- MockEngine tests in `parse-ktor` `commonTest`, one file per package, run JVM + iOS simulator. Use `parse-ktor-test` itself (cyclic test dep accepted by Gradle KMP).
- `FakeParseServer`: real client on `MockEngine`, per-request handler (multi-call flows), thread-safe request log. Throw `IOException` from handler = network failure.
- No live Parse server test (CI has no secrets), no demo screen.
