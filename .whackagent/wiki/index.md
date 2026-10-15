# Wiki — Index

Living knowledge base for project. Pages link to each other + tasks via `[[wikilink]]` syntax (Obsidian-compatible). Keep current via `/wa-wiki`.

## Pages

- [[architecture]] — module layout (56 projects), path → `studio.lunabee.*` group ID mapping, lint-check projects, KMP/Android/JVM split, dependency layers, BOM scope, demo apps
- [[build-and-publishing]] — `buildSrc` vs `build-logic` plugins, `*_VERSION` constants, changelog/semver rules, SKIE iOS frameworks, JReleaser → Maven Central, detekt, TeamCity, snapshot tasks
- [[presenter]] — MVI presenter stack: submodules, presenter/reducer roles, reducer-only state/nav/side effects, KSP reducer factories, factory-generation ownership, Hilt (`presenter-hilt`, `presenter-hilt-ksp`) + Koin integrations, v1→v2 migration, navigation link
- [[synchronization]] — sync engine: `LBSyncOperator`, groups, managers, `syncOrJoin`, `withSyncLock`, DataStore/Room cursor stores, event triggers, remote + Parse↔Room managers, `checks` lint rule
- [[common-libraries]] — non-UI `common/` libs (logger, core, extension, loading, ktor, monitoring, test): platforms, entry points, invariants, inter-group deps
- [[compose-ui]] — Compose libs under `compose/` (core specs, image, foundation, theme, uifield, crop, haptic, accessibility, glance, test) + demo-compose coverage

## How to read

- Page = domain, decision, or subsystem — *why* + *shape*, not code copy.
- Tasks reference wiki pages in `wiki:` frontmatter; wiki pages can link back to tasks.
- Code = structural truth; wiki = human narrative on top — explains what source can't.