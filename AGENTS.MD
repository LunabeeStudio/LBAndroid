# AGENTS

## Commit Rules

- Use split semantic commits.
- Keep each commit focused on a single concern.
- Use Conventional Commit style prefixes such as `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, or `chore:`.

## Kotlin API Rules

- Add KDoc to public functions (except in demo apps)

## Verification Rules

- Run `./gradlew detekt -Pstudio.lunabee.detekt.skipDependencySorting` before finishing work when no `*.gradle*` or `*.toml` file changed.
- Run `./gradlew detekt` before finishing work when a `*.gradle*` or `*.toml` file changed.
- Fix detekt warnings instead of ignoring them.
- When validating changes against another local project, publish the updated artifacts first with `./gradlew publishToMavenLocal` so it can be found by other project.

## Release Rules

- Fill the changelog for each feature, following the latest changelog format already used in the repository.
- Bump the touched module version according to semantic versioning.
- Do not bump the same module version twice on the same branch. When additional work lands before release, fold it into the existing unreleased branch version instead of creating another bump.
- Reserve major version bumps for large API breaking changes.
- Also bump the minor version of modules that depend on a touched module when the dependency version must move transitively.
