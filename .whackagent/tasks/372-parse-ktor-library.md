---
issue: 372
phase: validated
wiki: "[[common-libraries]], [[architecture]], [[build-and-publishing]], [[parse]] (new)"
note: "Blocks LunabeeStudio/RunMotion_Android#1202. LunabeeStudio/Kokoriko#327 migrates Kokoriko onto it once published. Parse↔Room sync on parse-ktor is a separate LBAndroid ticket (to create)."
created: 2026-10-19
---

## Context / Decisions

spec: issue #372 body; RunMotion Shared KMP Data Layer plan (rev 109, phase 1), https://claude.ai/code/artifact/467dc172-770a-4bf9-a237-8ab027f8a67c

RunMotion moves its Parse remote layer from the Parse Android SDK to a shared KMP module. The generic Parse REST client goes into LBAndroid as `parse-ktor`, so Kokoriko, RunMotion and other Lunabee Parse apps share it. It is seeded from Kokoriko `Kokoriko_KMP/remote-ktor` (PR LunabeeStudio/Kokoriko#41: `CoreHttpClient`, `ParseHeadersPlugin`, `ParseErrorBody` / `ParseErrorMapping`, `ParseDateSerializer`, `ParsePointer`, `ParseFile`, `ParseAccess`, `ParseQuery` / `PullCursor`, `FakeParseServer` / `ParseRequestAssertions`). Kokoriko-specific code (`ParseClass`, DTOs, datasources, `CoreError`) stays out.

### Prior art survey

- parse-community/Parse-SDK-Android: Java, Android only, OkHttp + Bolts. No JVM or iOS. Its KMP plan (issue #1100, 2021) was never delivered. Does not fit.
- parse-community/Parse-Swift: iOS only, cannot be called from KMP common code. Does not fit.
- 8grams/parse-sdk-kotlin: JVM only, never published, its HTTP layer encodes `Map<String, Any>` with kotlinx.serialization and fails at runtime, no `{code, error}` mapping. Does not fit.
- Nikhil-z/ParseKt (mirror of L3K0V/ParseKt): Android only, Ktor 1.x, no license, abandoned in 2021. Does not fit.
- glendmaatita/parse-kotlin-sdk: empty repository. Does not fit.
- Kokoriko `remote-ktor`: KMP (Android, JVM, iOS) on LBAndroid `ktor-*`, well tested, but app-specific and lacks cloud functions, files, installations, signup and anonymous login. Used as the seed.
- Verdict: nothing reusable exists, so we build `parse-ktor` ourselves.

### Scope

- In scope: transport (client, headers, session token, installation id, error mapping, retry), Parse types, thin typed helpers for the standard Parse REST endpoints, and a published test harness.
- Out of scope: Parse↔Room sync on `parse-ktor` (`synchronization-parse-ktor-room` in the plan). It becomes a separate LBAndroid ticket that depends on this one, because it is another module group with its own version and it needs this API stable first.
- Out of scope: migrating Kokoriko. That is LunabeeStudio/Kokoriko#327 (milestone 0.10.0), done once `parse-ktor` 1.0.0 is on Maven Central.
- Out of scope: app DTOs, app class names and RunMotion's ~56 typed cloud functions. They stay in the apps.
- Out of scope: generating or storing the session token or the installation id. The app owns both.

### Modules and coordinates

- `common/parse/parse-ktor`, registered with `addCommonModule("parse/parse-ktor")`, published as `studio.lunabee.parse:parse-ktor`, accessor `projects.parseKtor`.
- `common/parse/parse-ktor-test`, published as `studio.lunabee.parse:parse-ktor-test`, accessor `projects.parseKtorTest`.
- Versions in `AndroidConfig.kt`: `PARSE_KTOR_VERSION = "1.0.0"` and `PARSE_KTOR_TEST_VERSION = PARSE_KTOR_VERSION`.
- Both modules use `lunabee.kmp-library-conventions` with `jvm()`, so they target JVM, `iosArm64` and `iosSimulatorArm64`, like `ktor-*` and `synchronization-remote`. Android apps consume the JVM artifact.
- Both modules are added to the BOM with `addJvmModule`, and the BOM version is bumped.
- `parse-ktor` builds on `ktor-core` (`LBKtorExceptionHandler`), `ktor-json` (`LBKtorJson`) and `ktor-kermit` (`LBKtorKermit`). It bundles no HTTP engine: the consumer passes an `HttpClientEngine`.
- `parse-ktor-test` exposes `api(parse-ktor)` and `api(ktor-client-mock)`, so `ktor-client-mock` never reaches consumers' main classpath.

### Public API

- `LBParseClient(engine, config: LBParseConfig, configure: HttpClientConfig<*>.() -> Unit = {})` is `AutoCloseable`. It exposes `httpClient` as an escape hatch for app-specific calls (for example the RunMotion activity server), so apps never build a second Parse-configured client.
- `LBParseConfig` holds `serverUrl`, `applicationId`, `clientKey`, `sessionTokenProvider`, `installationIdProvider` (optional, default `null`), `maxRetries` (default `1`), `logLevel` (default `LogLevel.None`) and `mapErr: suspend (LBParseException) -> Throwable` (default identity).
- `configure` lets apps install extra plugins, such as `LBKtorMonitoring`, without the library depending on them.
- `LBParseSessionTokenProvider` is a `fun interface` with `suspend fun sessionToken(): String?`. A `null` return sends no `X-Parse-Session-Token` header.
- `LBParseInstallationIdProvider` is a `fun interface` with `suspend fun installationId(): String?`. A non-null value is sent as `X-Parse-Installation-Id` on every request, as the Parse Android SDK does, so sessions keep their installation link.
- `X-Parse-Application-Id` and `X-Parse-Client-Key` are sent on every request.
- `HttpRequestBuilder.withoutSessionToken()` opts a single request out of the session token header.
- Helpers are extension functions on `LBParseClient`, grouped in feature packages: `studio.lunabee.parse.{client, error, model, query, function, file, user, installation}`. Implementation details stay `internal`. No flat package.
  - `model`: `ParsePointer`, the `ParseDate` serializer (`{"__type": "Date", "iso": …}`, millisecond precision), `ParseFile`, `ParseACL`.
  - `query`: `query`, `pull` and the keyset `PullCursor` (`updatedAt|objectId`), ported from Kokoriko.
  - `function`: `callFunction<T>(name, body)`, which unwraps Parse's `{"result": …}` envelope.
  - `file`: `uploadFile(name, bytes, contentType)`, which returns a `ParseFile`.
  - `user`: `signUp`, `logIn`, `logInAnonymously` (`authData.anonymous`), `logOut`, `me`, `requestPasswordReset`. Every call except `logOut` and `me` skips the session token.
  - `installation`: create and update on `/installations`.

### Errors

- Sealed `LBParseException`, mapped from `LBKtorException` inside `LBKtorExceptionHandler`. `mapErr` converts it to the app's type (`RMRemoteError` in RunMotion, `CoreError` in Kokoriko).
  - `Network(cause)`: from `LBKtorException.IO`, which includes connect, socket and request timeouts.
  - `Parse(code, error, httpStatus)`: a non-2xx response with a decodable `{code, error}` body. Its `message` is the `error` text, because RunMotion shows cloud-function business errors by their message.
  - `Http(httpStatus, body)`: a non-2xx response without a Parse body, for example a 502 HTML page from a proxy.
  - `Decoding(cause)` and `Unexpected(cause)`: from the same-named `LBKtorException` cases.
- No raw exception leaks out of the client. Cancellation still propagates untouched.
- `ParseErrorCode` holds named constants (`ConnectionFailed = 100`, `ObjectNotFound = 101`, `InvalidSessionToken = 209`, …) so apps branch on `code` without magic numbers.
- No invalid-session listener in the library. RunMotion handles 209 in its own #1194, and Kokoriko rebuilds its listener in `mapErr`.

### Retry

- Ktor `HttpRequestRetry` with `retryOnExceptionIf(maxRetries) { _, cause -> cause is IOException }` and a fixed 1 s delay. No retry based on the response, so a Parse error body, a 4xx or a 5xx is never retried.
- It applies to every HTTP method, POST included, to match the Parse SDK's `Parse.Configuration.maxRetries(1)` that RunMotion runs today. `maxRetries = 0` disables retry.
- The retry sees the raw `IOException` before `LBKtorExceptionHandler` wraps it. Only the final failure surfaces, as `LBParseException.Network`.

### Test harness (`parse-ktor-test`)

- `FakeParseServer` builds a real `LBParseClient` on a `MockEngine` and records every request. It answers through a per-request handler `(HttpRequestData) -> response`, with helpers to answer a Parse JSON body or a `{code, error}` error, so multi-call flows (signup, then login, then `me`) are testable. This replaces Kokoriko's single canned response.
- `ParseRequestAssertions`: `assertRequest(method, path, sessionToken)`, `assertQuery(where, order, limit)` and `assertJsonBody(expected)`.
- `parse-ktor`'s own tests use this artifact.

### Proof and docs

- MockEngine tests in `commonTest`, one test file per feature package, mirroring the source tree. They run on JVM and the iOS simulator.
- No live Parse server test (it would need secrets that CI does not have), and no `demo-compose` screen (there is no Parse server to call).
- `common/parse/README.md` covers setup, client config, each helper, error mapping and the test harness.
- KDoc on every public declaration.
- A `CHANGELOG.MD` block for `parse-ktor` and `parse-ktor-test` 1.0.0 and the BOM bump.
- A new wiki page `[[parse]]`, linked from `[[common-libraries]]` and `[[architecture]]`.

## Acceptance criteria

1. `./gradlew :parse-ktor:build :parse-ktor-test:build` succeeds, including the JVM and iOS simulator test tasks.
2. `./gradlew detekt` passes (plain run, because Gradle files and the version catalog change).
3. `./gradlew :parse-ktor:PrintCoordinates` prints `studio.lunabee.parse:parse-ktor:1.0.0`, and `:parse-ktor-test:PrintCoordinates` prints `studio.lunabee.parse:parse-ktor-test:1.0.0`.
4. `./gradlew publishList` lists both modules, and the BOM declares constraints for both.
5. Every request carries `X-Parse-Application-Id` and `X-Parse-Client-Key`.
6. When the session token provider returns a token, the request carries `X-Parse-Session-Token`. When it returns `null`, the header is absent. A request built with `withoutSessionToken()` never carries it.
7. `signUp`, `logIn`, `logInAnonymously` and `requestPasswordReset` send no session token. `logOut` and `me` send it.
8. When the installation id provider returns a value, every request carries `X-Parse-Installation-Id`. Without a provider, the header is absent.
9. A non-2xx response with body `{"code": 141, "error": "Coach not found"}` throws `LBParseException.Parse` with `code == 141` and `message == "Coach not found"`.
10. A non-2xx response with a non-JSON body throws `LBParseException.Http` with the HTTP status.
11. An `IOException` on send throws `LBParseException.Network`. A timeout also throws `LBParseException.Network`.
12. With `mapErr` set, the client throws the mapped type instead of `LBParseException`.
13. With the default config, a request that fails with `IOException` once and then succeeds returns the success after exactly 2 requests.
14. A request that fails with `IOException` twice throws `LBParseException.Network` after exactly 2 requests.
15. A 5xx response, a 4xx response or a Parse error body is sent exactly once (no retry).
16. With `maxRetries = 0`, an `IOException` request is sent exactly once.
17. `callFunction("hello", body)` sends `POST functions/hello` with the JSON body and returns the decoded value of `result`.
18. `uploadFile("avatar.jpg", bytes, "image/jpeg")` sends `POST files/avatar.jpg` with the raw bytes and that content type, and returns a `ParseFile` with the server's `name` and `url`.
19. `logInAnonymously()` sends `POST users` with `authData.anonymous.id` and returns the session token.
20. `logIn`, `signUp`, `logOut`, `me` and `requestPasswordReset` hit `POST login`, `POST users`, `POST logout`, `GET users/me` and `POST requestPasswordReset`.
21. The installation helper creates with `POST installations` and updates with `PUT installations/<objectId>`.
22. `ParseDate` serializes to and parses from `{"__type": "Date", "iso": "…"}` with millisecond precision, and `ParsePointer` serializes to `{"__type": "Pointer", "className": …, "objectId": …}`.
23. `pull` pages with the keyset cursor: the next page's `where` combines `updatedAt` and `objectId` with `$or`, as in Kokoriko.
24. A consumer test can use `FakeParseServer` from `parse-ktor-test` to answer different responses per request path and assert method, path, headers and JSON body with `ParseRequestAssertions`.
25. `common/parse/README.md`, KDoc on all public API, the `CHANGELOG.MD` block and the `[[parse]]` wiki page exist.

## Implementation

Autopilot round, 4 bricks, one implementer.

1. Scaffold + transport. `parse/parse-ktor` and `parse/parse-ktor-test` are registered in `settings.gradle.kts`. `AndroidConfig.kt` gets `PARSE_KTOR_VERSION = "1.0.0"` and `PARSE_KTOR_TEST_VERSION`, and `PLATFORM_VERSION` goes to `4.17.0`. `libs.versions.toml` gets `ktorClientMock`. The BOM gets `addJvmModule` for both modules. Package `client`: `LBParseClient`, `LBParseConfig`, the two provider interfaces, `ParseHeadersPlugin` (with `withoutSessionToken()`) and `ParseErrorMappingPlugin`. Package `error`: `LBParseException`, `ParseErrorCode`, `ParseErrorBody` and `ParseErrorMapping`. `parse-ktor-test` holds `FakeParseServer` and `ParseRequestAssertions`.
2. Parse types + query. Package `model`: `ParsePointer`, `ParseDateSerializer` (on `kotlin.time.Instant`), `ParseFile`, `ParseAccess` and `ParseACL`. Package `query`: `ParseObject`, `ParsePage`, `ParseQueryPage`, `PullCursor` and `ParseQuery` (`query`, `pull`, `pullWhere`).
3. Endpoint helpers. `function/ParseCloudFunction.kt` holds `callFunction`. `file/ParseFileUpload.kt` holds `uploadFile`. `user/ParseUserAuth.kt` holds `signUp`, `logIn`, `logInAnonymously`, `logOut`, `me` and `requestPasswordReset`, and `user/ParseUser.kt` holds the result type. `installation/ParseInstallationEndpoints.kt` holds `createInstallation` and `updateInstallation`.
4. Docs + release. `common/parse/README.md`, a `CHANGELOG.MD` block, two rows in the root `README.md` and a KDoc pass.

Implementation decisions:

- Plugin install order: `ParseErrorMappingPlugin` first, then `LBKtorExceptionHandler`, `LBKtorJson`, `LBKtorKermit` and the headers plugin, and `HttpRequestRetry` last. HttpSend interceptors installed earlier are the outer ones, so the retry is innermost and sees the raw `IOException` before it is wrapped.
- `HttpRequestRetry` retries 5xx responses by default. `retryIf(maxRetries) { _, _ -> false }` turns that off, so only `IOException` is retried. With `maxRetries = 0` the retry plugin is not installed.
- ktor-core's `LBKtorExceptionHandler` wraps any non-`LBKtorException` thrown by its `mapErr` as `Unexpected` and maps it again. So the handler only maps `LBKtorException` to `LBParseException`, and `ParseErrorMappingPlugin` applies `LBParseConfig.mapErr` on top. ktor-core is unchanged.
- The inline reified helpers use internals, so `PullCursor`, `ParseQueryPage` and `ParseFunctionResult` are `@PublishedApi internal`.
- The installation helpers take a `JsonObject`, so the library does not guess the installation schema. `ParseUser` exposes only `objectId` and `sessionToken`.
- `PLATFORM_VERSION` 4.17.0 is the same bump as open PR #370. Whichever PR lands second folds into that unreleased BOM bump (autopilot assumption).

## Review

### validation (autopilot) — 2026-10-19

Lenses: style ✓ · elegance ✓ · structure ✓ · correctness ✓. Tools: plain `./gradlew detekt` passes. Verdict: clean after 2 autofix rounds.

Round 1 had 15 findings (4 major, 11 minor), all fixed by autofix:

- major: `LBParseConfig.mapErr` was skipped for body decoding failures, because Ktor's `HttpCallValidator` raises them in its own phase before `Receive`. `ParseErrorMappingPlugin` now intercepts its own phase inserted before `Receive`. Two new tests cover `Decoding` with and without `mapErr`.
- major: the `pull` keyset `$or` overwrote a caller `$or` in `where` on pages 2 and later. Both are now wrapped in `$and`, and a test covers it.
- major: `kotlinx-serialization-json` is now `api`, because `JsonObject` is part of the public API.
- major: the README example put `ParseDateSerializer` on `createdAt`, but Parse returns `createdAt` and `updatedAt` as plain ISO strings. The example and the KDoc are fixed, and the query test fixture now uses a plain ISO `updatedAt`.
- minor: `respondParseError` builds JSON with `buildJsonObject`, and the `FakeParseServer` request log is thread-safe (`AtomicReference`).
- minor: the unused content-negotiation and json-serialization dependencies are removed, and the trailing-slash `serverUrl` test is real.
- minor: named arguments on continuation lines, `__type` is a class-body property in `ParsePointer` and `ParseFile` with `@property` KDoc, and `LBParseException` extends `Exception`.
- minor: `uploadFile` takes a Ktor `ContentType`, and `assertJsonBody(request, expected)` takes the request first like the other assertions.

Round 2 had 2 minor style findings: the formatting of the `$and` branch in `PullCursor`, and the import order in `FakeParseServer`. Both were fixed. Round 3 is clean.

## Verification

- `./gradlew :parse-ktor:build :parse-ktor-test:build` succeeds: 48 tests on jvmTest and 48 on iosSimulatorArm64Test, 0 failures (criterion 1).
- Plain `./gradlew detekt` passes with no findings (criterion 2).
- `PrintCoordinates` prints `studio.lunabee.parse:parse-ktor:1.0.0` and `studio.lunabee.parse:parse-ktor-test:1.0.0` (criterion 3).
- `publishList` lists both modules, and the BOM pom (`:bom:generatePomFileForBomPublication`) has constraints for `parse-ktor`, `parse-ktor-jvm`, `parse-ktor-test` and `parse-ktor-test-jvm` (criterion 4).
- MockEngine tests through `FakeParseServer` prove criteria 5 to 24 (client, error, model, query, function, file, user and installation test files).
- The `[[parse]]` wiki page exists, linked from `[[index]]`, `[[architecture]]` and `[[common-libraries]]` (criterion 25, wiki part).
- There is no runtime check, because this is a library (`verify.mode: off`). No live Parse server was called.

## Feedback
