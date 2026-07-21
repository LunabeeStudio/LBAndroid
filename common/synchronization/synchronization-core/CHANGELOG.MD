# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

# Deprecated, please update the main Changelog

## [1.7.2] - 2020-01-21
## Changed
- `LBSyncManager.lastSyncDateSharedPrefKey` and `LBSyncManager.lastSuccessfulSyncDate` are now public.

## [1.7.1] - 2020-01-09
## Changed
- `LBSyncToken.changeStatusClosure` is now call synchronously on `LBSyncProcessStatus` changes, so 
the synchronize completion will not be call until every observers return.

## [1.7.0] - 2019-12-13
### Added
- `LBSyncApplication` use as mother class to refresh sync managers on lifecycle changes
### Deprecate
- `LBApplication` (replaced by `LBSyncApplication`)
- `LBLifecycleListener` (moved to module lb-core)

## [1.6.1] - 2019-11-28
### Fixed
- Use `LinkedHashSet` to store groups and sync managers instead of `HashSet` to ensure 
synchronization order. Also use explicit type `LinkedHashMap` to store groups instead of `MutableMap`.

## [1.6.0] - 2019-10-17
### Added
- This changelog

### Changed
- `LBSyncRefreshEvent` is now a sealed class. It takes a delay in millisecondes as param. This delay 
has to be reached to trigger the refresh. Default delay is 0 which means syncmanager always refresh on event.  
`APP_FOREGROUND` become `AppForeground`  
`INTERNET_IS_BACK` become `InternetIsBack`
- `SyncSuccessfully.lastSuccessfulSync` return a date <= as today's date.

[1.7.1]:https://github.com/LunabeeStudio/Libraries_Android/compare/lb-synchronization-1.7.2^...lb-synchronization-1.7.2
[1.7.1]:https://github.com/LunabeeStudio/Libraries_Android/compare/lb-synchronization-1.7.1^...lb-synchronization-1.7.1
[1.7.0]:https://github.com/LunabeeStudio/Libraries_Android/compare/lb-synchronization-1.7.0^...lb-synchronization-1.7.0
[1.6.1]:https://github.com/LunabeeStudio/Libraries_Android/compare/lb-synchronization-1.6.1^...lb-synchronization-1.6.1
[1.6.0]:https://github.com/LunabeeStudio/Libraries_Android/compare/lb-synchronization-1.6.0^...lb-synchronization-1.6.0
