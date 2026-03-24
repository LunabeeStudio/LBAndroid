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
