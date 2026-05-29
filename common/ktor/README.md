# LBKtor libraries

This folder contains modules to help you configure a `HttpClient` with Ktor. It is currently split in 3 modules.

All modules are KMP compatible.

## lbktor-core

This module contains the following elements:
- `LBKtorExceptionHandler` to handle exception automatically.
- `LBKtorException` represents all exceptions that can be throw by above method.

> ℹ️ Methods describe here will only catch exception that should be handled. It will ignore for example `CancellationException` 
> that should be catch by the `CoroutineScope` itself.

It provides as `api` dependency the Ktor core dependency that can be use directly in your KMP module/app.

## lbktor-json

This module allows you to configure your client to accept and handle Json with Kotlinx Serialization. The dependency is provide as `api`.

`LBKtorJson` is a plugin that you can simply use like this:
```kotlin
private val httpClient: HttpClient = HttpClient {
    install(LBKtorJson) { 
        url = "https://dogapi.dog" // mandatory or throw an IllegalArgumentException
        prettyPrint = true // optional, default = true
        isLenient = true // optional, default = true
        ignoreUnknownKeys = true // optional, default = true
        explicitNulls = false // optional, default = false
    }
}
```

This plugin installs, under the hood, `DefaultRequest` plugin configured to accept `ContentType.Application.Json` and `ContentNegociation`.

## lbktor-kermit

This module allows you to configure your client with the Kermit Logger already available in our library.
```kotlin
private val httpClient: HttpClient = HttpClient {
    install(LBKtorKermit) {
        logLevel = LogLevel.All // optional, default = LogLevel.All
    }
}
```

This plugin installs, under the hood, `Logging` plugin configured with our Kermit Logger.

## Use it in your app.

To use it in your application (KMP or not), you need to add the following dependencies (depending on your need):
```libs.gradle.toml
lunabee-bom = { group = "studio.lunabee", name = "bom", version.ref = "lunabee-bom" }
lbktor-core = { group = "studio.lunabee.ktor", name = "ktor-core" }
lbktor-json = { group = "studio.lunabee.ktor", name = "ktor-json" }
lbktor-kermit = { group = "studio.lunabee.ktor", name = "ktor-kermit" }

ktor-client-android = { group = "io.ktor", name = "ktor-client-android", version.ref = "ktor" }
ktor-client-ios = { group = "io.ktor", name = "ktor-client-ios", version.ref = "ktor" }
```

Then in your build.gradle.kts file (for KMP for example):
```
commonMain.dependencies {
    implementation(project.dependencies.platform(libs.lunabee.bom))
    implementation(libs.lbktor.core) // <- LBKtorExceptionHandler + LBKtorException
    implementation(libs.lbktor.json) // <- LBKtorJson
    implementation(libs.lbktor.kermit) // <- LBKtorKermit
}
```

And, depending on the platform you target: 
```
androidMain.dependencies {
    implementation(libs.ktor.client.android)
}

iosMain.dependencies {
    implementation(libs.ktor.client.ios)
}
```

## Configure your client:
```kotlin
private val httpClient: HttpClient = HttpClient {
    install(LBKtorKermit)
    install(LBKtorExceptionHandler) {
        mapErr = { AppError(it) } // optional, return a LBKtorException by default
    }
    install(LBKtorJson) { url = "https://dogapi.dog" }
}
```

## Execute a request with default Ktor API:
```kotlin
httpClient.get(urlString = "api/v2/facts") {
    parameter("limit", 1)
}.body().facts.first()
```

## Handle errors

Error are handled by `LBKtorExceptionHandler`. Only expected exception are caught.
