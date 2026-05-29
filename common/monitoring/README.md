# Monitoring libraries

This folder contains modules to help you store and display network log.

All modules are KMP compatible, and UI is Compose Multiplatform!

## monitoring-okhttp

Record network calls from OkHttp. Use `OkHttpMonitoringInterceptor` as network interceptor on OkHttp client to feed monitoring.

## monitoring-ktor

Record network calls from ktor

## monitoring-core

Module use to share basic contracts and classes across all others modules.

## monitoring-room

Get a independent Room database to store your monitoring data.

You can simply use like this:
```kotlin
val monitoring: LBMonitoring = LBRoomMonitoring.get(
    context = applicationContext,
    dispatcher = Dispatchers.Default,
)
```

You can then use it with `monitoring-ktor` and `monitoring-ui`. This object should be injected by your application.

## monitoring-ui

Provide a UI to display your monitoring data, independently of your local source or your client.
You can simply use like this:
```kotlin
// For example, in your application class, where monitoring is a LBMonitoring instance.
LBMonitoringUi.init(monitoring = monitoring)

// Then, in a compose activity or as a route in your Compose NavHost, use:
setContent {
    LBMonitoringMainRoute(
        closeMonitoring = { },
    )
}
```

## Use it in your app.

To use it in your application (KMP or not), you need to add the following dependencies (depending on your need):
```libs.gradle.toml
lunabee-bom = { group = "studio.lunabee", name = "bom", version.ref = "lunabee-bom" }
monitoring-core = { group = "studio.lunabee.monitoring", name = "monitoring-core" }
monitoring-room = { group = "studio.lunabee.monitoring", name = "monitoring-room" }
monitoring-ui = { group = "studio.lunabee.monitoring", name = "monitoring-ui" }
monitoring-ktor = { group = "studio.lunabee.monitoring", name = "monitoring-ktor" }
monitoring-okhttp = { group = "studio.lunabee.monitoring", name = "monitoring-okhttp" }
```

## Integration with clients

### Ktor

Use `monitoring-ktor` and simply use the provided plugin with your instance of `LBMonitoring`
```kotlin
private val httpClient: HttpClient = HttpClient(Android) {
    install(LBKtorJson) { url = "https://dogapi.dog" }
    install(LBKtorExceptionHandler)
    install(LBKtorKermit)
    install(LBKtorMonitoring) {
        monitoring = myMonitoringInstance
    }
}
```

```libs.gradle.toml
lunabee-bom = { group = "studio.lunabee", name = "bom", version.ref = "lunabee-bom" }
monitoring-ktor = { group = "studio.lunabee.monitoring", name = "monitoring-ktor" }
```

