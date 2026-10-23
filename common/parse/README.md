# Parse libraries

Kotlin Multiplatform client for the [Parse Server REST API](https://docs.parseplatform.org/rest/guide/), built on the
`ktor-*` modules. Targets JVM (Android apps use the JVM artifact), `iosArm64` and `iosSimulatorArm64`.

## parse-ktor

Package `studio.lunabee.parse`, split by feature: `client`, `error`, `model`, `query`, `function`, `file`, `user`,
`installation`.

### Setup

Add the BOM, then the modules. No HTTP engine is bundled: add the one of your platform.

```kotlin
implementation(platform("studio.lunabee:bom:<version>"))
implementation("studio.lunabee.parse:parse-ktor")
androidMain.dependencies { implementation("io.ktor:ktor-client-okhttp:<ktor>") } // or ktor-client-android
iosMain.dependencies { implementation("io.ktor:ktor-client-darwin:<ktor>") }
jvmMain.dependencies { implementation("io.ktor:ktor-client-java:<ktor>") }
testImplementation("studio.lunabee.parse:parse-ktor-test")
```

### Client

```kotlin
val parse = LBParseClient(
    engine = engine,
    config = LBParseConfig(
        serverUrl = "https://parse.example.com/parse", // mount path included
        applicationId = "app-id",
        clientKey = "client-key",
        sessionTokenProvider = { sessionStore.token() },    // null: no X-Parse-Session-Token header
        installationIdProvider = { installationStore.id() }, // optional, sent as X-Parse-Installation-Id
        maxRetries = 1,                                       // default, 0 disables retry
        logLevel = LBKtorKermit.LogLevel.None,                // default: requests carry credentials
        mapErr = { it },                                      // default: LBParseException as is
    ),
    configure = { install(LBKtorMonitoring) }, // extra plugins on the underlying HttpClient
)
```

- `X-Parse-Application-Id` and `X-Parse-Client-Key` are sent on every request.
- `parse.httpClient` is the configured Ktor `HttpClient` for calls the helpers do not cover. Paths are relative to
  `serverUrl`. `HttpRequestBuilder.withoutSessionToken()` opts one request out of the session token header.
- `LBParseClient` is `AutoCloseable`, closing it closes the engine.
- The app owns the session token and the installation id: the library never generates nor stores them.

### Errors

Every failure is an `LBParseException`, unless `mapErr` converts it:

| Case                                     | When                                                                            |
|------------------------------------------|---------------------------------------------------------------------------------|
| `Network(cause)`                         | `IOException`, including connect, socket and request timeouts                    |
| `Parse(code, error, httpStatus)`         | non-2xx response with a `{code, error}` body, `message` is `error`              |
| `Http(httpStatus, body)`                 | non-2xx response without a Parse body (e.g. a proxy HTML page)                  |
| `Decoding(cause)` / `Unexpected(cause)`  | body decoding failure / anything else                                           |

`ParseErrorCode` holds the Parse codes (`InvalidSessionToken = 209`, `ObjectNotFound = 101`, …).
Cancellation is never mapped.

```kotlin
mapErr = { e ->
    when {
        e is LBParseException.Parse && e.code == ParseErrorCode.InvalidSessionToken -> AppError.SessionExpired
        else -> AppError.Remote(e)
    }
}
```

### Retry

A request failing with an `IOException` is retried `maxRetries` times (default 1) after a fixed 1 s delay, for every
HTTP method, POST included. A response is never retried: a Parse error body, a 4xx or a 5xx is sent once. Only the final
failure surfaces, as `LBParseException.Network`.

### Helpers

All helpers are extension functions on `LBParseClient`.

- `query<T>(className, where, order, limit, keys, include)`: `GET classes/<className>`; `keys` and `include` are sent
  comma-joined when not empty. Overloads taking a `serializer` serve types known only as a type parameter.
- `pull<T : ParseObject, R>(className, where, cursor, keys, include, transform)`: pages of 100 sorted by `updatedAt,objectId` with a
  keyset cursor (`updatedAt|objectId`, never an offset). Pass `ParsePage.nextCursor` back until it is `null`.
  `pullWhere(updatedAfter) { … }` builds the `where`.
- `callFunction<T, B>(name, body)`: `POST functions/<name>`, returns the unwrapped `result`.
- `uploadFile(name, bytes, contentType: ContentType)`: `POST files/<name>`, returns a `ParseFile`.
- `user`: `signUp`, `logIn`, `logInAnonymously` (`authData.anonymous`), `requestPasswordReset` send no session token;
  `logOut` and `me` send it. They return a `ParseUser(objectId: ParseObjectId, sessionToken)`.
- `installation`: `createInstallation(fields)` (`POST installations`, returns its `ParseObjectId`) and
  `updateInstallation(objectId: ParseObjectId, fields)` (`PUT installations/<objectId>`).
- `model`: `ParsePointer` (its `objectId` is a `ParseObjectId` value class, as is every public object id),
  `ParseFile`, `ParseAccess` / `ParseACL`, and `ParseDateSerializer` (`{"__type": "Date", "iso": …}`, millisecond
  precision) for `Instant` fields: `@Serializable(with = ParseDateSerializer::class) val startsAt: Instant`.
  `createdAt` and `updatedAt` are plain ISO strings in Parse responses: declare them as `Instant` with the default
  serializer.

## parse-ktor-test

`FakeParseServer` runs a real `LBParseClient` on a Ktor `MockEngine` and records every request. Its `handler` answers
per request, so multi-call flows are testable. Throw an `IOException` from it to simulate a network failure.
`ParseRequestAssertions` checks method, path, headers, query and JSON body. It exposes `ktor-client-mock` as `api`.

```kotlin
val server = FakeParseServer(
    handler = { request ->
        when (request.url.encodedPath) {
            "/parse/login" -> respondParse("""{"objectId":"u1","sessionToken":"r:s1"}""")
            else -> respondParseError(code = ParseErrorCode.ObjectNotFound, error = "Not found")
        }
    },
)

val user = server.client.logIn(username = "ada", password = "secret")

assertRequest(server.lastRequest, HttpMethod.Post, "login", sessionToken = null)
assertJsonBody(server.lastRequest, """{"username":"ada","password":"secret"}""")
```
