# AGENTS

## Commit Rules

- Use split semantic commits.
- Keep each commit focused on a single concern.
- Use Conventional Commit style prefixes such as `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, or `chore:`.

## Kotlin API Rules

- Add KDoc to public functions.

## Verification Rules

- Run `./gradlew detekt -Pstudio.lunabee.detekt.skipDependencySorting` before finishing work when no `*.gradle*` or `*.toml` file changed.
- Run `./gradlew detekt` before finishing work when a `*.gradle*` or `*.toml` file changed.
- Fix detekt warnings instead of ignoring them.

## Release Rules

- Fill the changelog for each feature, following the latest changelog format already used in the repository.
- Bump the touched module version according to semantic versioning.
- Reserve major version bumps for large API breaking changes.
- Also bump the minor version of modules that depend on a touched module when the dependency version must move transitively.
