---
# Agents config — written by setup, edit freely.
discussion_language: en        # language Claude talk to you in
primary_language: kotlin       # swift | kotlin | typescript | generic  (picks convention pack;
                               # other language → generic)
project_kind: package              # app | web | server | cli | package  (picks architecture module)
                               # app = mobile/desktop GUI; package = library/SDK

paths:                         # WHERE agents keep each kind of file. Skills refer as
                               # {backlog} {tasks} {wiki} {reports} {conventions} {worktrees} — never literal path.
                               # Relative path resolve from repo root (sibling repo: ../other/docs).
                               # Config versioned, shared → NEVER user-specific absolute path
                               # (/Users/<name>, /home/<name>, ~): breaks every other machine.
                               # Point at committed, human-browsable folder when team share them —
                               # `docs/wiki` obvious to teammates, hidden `.lunabee/agents/wiki` easy to miss.
                               # Missing key → default below, so old config still work.
  backlog: .lunabee/agents/BACKLOG.md
  tasks: .whackagent/tasks
  wiki: .lunabee/agents/wiki
  reports: .lunabee/agents/reports          # run reports — local, gitignored by setup
  conventions: .lunabee/agents/conventions  # convention modules copied here by setup
  worktrees: ../LBAndroid-worktrees  # task worktrees (branch.worktree, autopilot). <repo> = repo
                               # folder name. Resolve from MAIN checkout root, even when setup run
                               # from a worktree — keep relative. MUST sit OUTSIDE repo: nested
                               # checkout get indexed, grepped, globbed by builds.
                               # `.lunabee/agents/config.md` itself NOT configurable: it carry these
                               # paths, so must sit at known spot.
                               # Move path after setup → move files too; nothing back-fill.

backlog:
  provider: plane              # WHERE tickets live — providers/CONTRACT.md.
                               #   local  — {backlog} + task files, one agent (default).
                               #   github — GitHub Project + issues, many agents/worktrees.
                               #   plane  — Lunabee's Plane, run by the lb-board plugin (internal,
                               #     LunabeeStudio/Lunabee-Plane): .lunabee/board/lb-board holds board,
                               #     claims and hooks (Lunabee Plane board App). API key: env PLANE_API_KEY.
                               #   github, plane: settings in repo variables (lb-backlog provision /
                               #     /lb-board-setup), not here. Forces branch.per_task: true, branch.base =
                               #     close.target (named branch). Ignores close.strategy for tickets:
                               #     validate step marks PR ready, close step merges it on your yes.
  milestones: []               # OPEN release scopes (iteration, version), OLDEST FIRST. Local provider
                               # only — github read repo milestones. New task join LAST
                               # entry → new work never grow current/past scope. You own list:
                               # add entry to open next scope, remove when shipped (tasks keep
                               # their milestone:). Empty → no milestone.

vcs:
  provider: github             # WHERE code lives (branches, PRs, claims) under a non-local backlog.
                               #   github — only value (default). Missing → github.
                               # backlog.provider github implies it; local ignores it.

release:
  doc:                         # WHERE project's release flow is written — `path` or `path#Heading`
                               # (docs/RELEASING.md, {wiki}/release.md, CLAUDE.md#Releasing).
                               # release step follow it step by step, never invent flow.
                               # Empty → release step search markdown, else interview you and write it.

review:
  when: on_validation          # WHEN verifier run.
                               #   on_validation — once, at validate step: your green light say feature
                               #     match spec, and THAT fire review over whole diff (code + every
                               #     feedback round). Coding and feedback round stay fast; nothing
                               #     reviewed while still moving.
                               #   each_round — also after code step and after every feedback step fix.
                               #     Catch drift earlier, cost verifier round each time. validate step
                               #     still run final pass.
                               # Either way no task close unreviewed — close step refuse task the
                               # verifier never saw. autopilot ignore this key: it always run
                               # validate step itself after green runtime check.
  inline_micro_fixes: true     # feedback step may apply MICRO-fix itself instead of spawn
                               # implementer (~50k tokens context for one-liner). Bounded: ≤2 files,
                               # ≤~20 lines, no new file/type/folder, no layer or public-API change.
                               # Bigger, or any doubt → implementer.
                               # false → every change go through implementer, whatever size.
  autofix: true                # wa-code verify phase re-dispatch implementer until clean
  public_doc: true             # require doc on public API — flip false per company
  modules: [skill:lunabee-android:android-development, style.md, elegance.md, testing.md, architecture-global.md, architecture-package.md, compose.md]
                               # what single verifier load before judging. Setup write
                               # only relevant ones: UI module (swiftui.md, compose.md)
                               # only if project use that toolkit,
                               # architecture-package.md for non-app kinds, single <lang>.md for
                               # one-file packs. Paths relative to {conventions}.
                               # + apple.md when setup exported Xcode's Apple references
                               # into {conventions}/apple/ (Xcode 27+): index only, agents
                               # open one page per topic they touch.
                               # `skill:<plugin>:<skill>` = rulebook from another installed plugin
                               # (company guidelines), loaded by name on every machine; project rules
                               # on top go to overlay file listed after it. Order = load order, later
                               # wins. e.g. [skill:lunabee-android:android-development,
                               # android-development.md, architecture-global.md] (reference/paths.md).
                               # ONE verifier, not one per lens: isolated agent cost ~50k tokens
                               # context before reading a line, and every lens judge same diff
                               # against same rulebook — splitting pay twice, leave duplicate
                               # findings to dedupe. It sweep style, elegance, structure and
                               # correctness in one pass and report which ran. Exception you
                               # opt into: `focus` below.
                               # Legacy `categories: {conventions: [...], correctness: []}` read as
                               # union of its lists.
  focus:
    - name: concurrency
      modules: [skill:lunabee-android:kotlin-concurrency-review]
      when: 'suspend|Flow\b|Channel\b|\blaunch|\basync\b|Deferred|CoroutineScope|Dispatchers|withContext|flowOn|Mutex|Semaphore|stateIn|shareIn|collect|runBlocking|runInterruptible|runCatching|withTimeout|GlobalScope|launchWhen|LaunchedEffect|rememberCoroutineScope|produceState|snapshotFlow|runTest'
    - name: recomposition
      modules: [skill:lunabee-android:compose-recomposition-review]
      when: '@Composable|UiState|Presenter|Reducer|Mapper|remember|derivedStateOf|computedStateOf|Lazy(Column|Row|Vertical|Horizontal)|CompositionLocal|@Stable|@Immutable|onSizeChanged|onGloballyPositioned'
                               # FOCUSED verifiers — topic the single verifier keep missing
                               # (concurrency, recomposition…): long rulebook dilute attention.
                               # Each entry = one more verifier, own context, that topic only,
                               # its own modules only, spawned beside main one ONLY when `when`
                               # regex match diff's changed lines or file paths. Main one then
                               # leave topic to it. ~50k tokens per spawn — list only topics
                               # worth it. Entry:
                               #   - name: concurrency      # topic = finding tag
                               #     modules: [skill:lunabee-android:kotlin-concurrency-review]
                               #     when: 'suspend |Flow\b|launch|Dispatchers|Mutex'
                               # `modules` resolve like `modules` above (reference/paths.md).
                               # [] → single verifier, as before.

build:                         # how THIS project build — project win over plugin default
  command: ""                  # e.g. "make build", "./scripts/build.sh", "./gradlew assembleDebug".
                               # Empty → build tool detected in repo: XcodeBuildMCP (Xcode project),
                               # ./gradlew (Gradle), swift build (SwiftPM), package.json scripts,
                               # cargo, go, dotnet, mvn, make… — see implementer.
  test_command: ""             # e.g. "make test". Empty → same tool test task.

tools:
  - name: detekt
    run: ./gradlew detekt -Pstudio.lunabee.detekt.skipDependencySorting   # plain `./gradlew detekt` when *.gradle* or *.toml changed
    when: [code, validation]
                               # project tools agents run — lint, static analysis, format check.
                               # Commands, never hardcoded per language. Each entry:
                               #   - name: detekt
                               #     run: ./gradlew detekt                 # whole project; exit ≠ 0 = fail
                               #     fix: ./gradlew detekt --auto-correct  # optional: tool own fixer,
                               #                                           # implementer try it before hand fix
                               #     when: [code, validation]
                               # when — code: implementer run it after build + tests, every brick and
                               #   feedback round; failure in changed file = red build.
                               #   validation: validate step run it before verifier; failures join
                               #   autofix loop as `tool:<name>` findings.
                               # Failure only in file task never touched = pre-existing: reported,
                               # never fixed — not this task scope.
                               # Empty → no tool run.

verify:                        # runtime check — implementer drive app it built
  mode: off                    # WHO exercise app after green build:
                               #   autopilot — agent drive it in autopilot only (nobody there
                               #     to test); attended code step + feedback step stop at build + tests and
                               #     YOU validate by testing app yourself. Default for anything runnable.
                               #   always — agent drive it every run, attended or not.
                               #   off — agent never drive it; build + tests whole proof.
                               # Under `autopilot` and `off` implementer may STILL launch app when
                               # it can't write feature without seeing it run (reproduce bug, judge
                               # layout, follow nav flow). That implementation, not proof: it drive
                               # minimum it need and say so in NOTES.
                               # Legacy `enabled: true` / `false` read as `always` / `off`.
  platform: none               # WHAT gets driven, which tool (list allowed: [ios, android]):
                               #   ios     — XcodeBuildMCP (simulator), mobile-mcp (device)
                               #   android — mobile-mcp (emulator or device)
                               #   web     — browser automation MCP (Playwright, Chrome)
                               #   desktop — desktop/computer-use MCP when configured
                               #   server  — start it, hit endpoints (curl), read responses + logs
                               #   cli     — run binary with task inputs, check output + exit code
                               #   none    — library, nothing to drive; build + tests whole proof
                               # Legacy `both` read as [ios, android].
  target: local                # simulator | emulator | device | browser | local

commit:
  auto_commit_after_validation: false   # may Claude commit once YOU validate feature?
                               # (name kept for old configs: it gate the commit close step make)
  author_name: ""              # commits use this — never "Claude". Empty (default) → committer own
  author_email: ""             # `git config user.name` / `user.email` at commit time, never made-up.
                               # Config versioned, shared → never personal identity here: teammates'
                               # commits would carry it. Set only for shared identity (team bot).

branch:
  per_task: true               # code step work on own branch per task instead of current one
  worktree: true               # with per_task: each task branch checked out in own git worktree
                               #   at {worktrees}/<slug> — main checkout never switch branch, tasks
                               #   open side by side. Cost: cold build per worktree, open IDE there.
  prefix: "wa/"                # branch name: <prefix><slug> → wa/login-apple
  base: master                 # fork point: current | main | <branch name>
                               # GitHub provider: named branch, same as close.target
  sprint_prefix: "sprint/"     # task carrying `sprint:` branch off SPRINT branch, not base:
                               #   sprint/login-refacto ← created from base: on first task of sprint
                               #   feature/login-apple  ← forked from it, merged back by close step
                               # So task 3 of sprint see task 1 work — same screen, no blind conflict.
                               # Created by whoever need it first: code step (step 0) or autopilot
                               # wave setup. Empty string → sprint get no branch, tasks use base:.
  tracks: {}                   # long-shot features on own long-lived trunk beside base:
                               #   {server: develop_synchro} — key = milestone title, value = branch.
                               # Task in that milestone fork from + land on that branch instead of
                               # base:/close.target. New tasks never join it by default; agent
                               # suggests, you decide. Rules: reference/tracks.md. Needs per_task.
                               # You own list. Empty (default) → no track, flow unchanged.
  checkout_next: true          # after close step commit, hop onto next task branch (worktree mode:
                               #   create its worktree)
                               # (only when per_task AND commit.auto_commit_after_validation)
  test_branch: false           # with per_task: ≥2 coded tasks waiting your test on one base
                               #   (sprint branch or trunk) → merged into LOCAL throwaway branch
                               #   lb-test/<base> (worktree mode: own worktree; else checked out at end
                               #   of attended round, clean tree only), so you test them
                               #   in one launch. Rebuilt after each delivery. Never pushed, never
                               #   landed; conflicting task skipped. Rules: reference/test-branch.md.
                               #   false or missing → no test branch, test each task branch.
                               #   per_task off → ignored (setup writes false then).

close:                         # WHERE work land when close step finish a task. Task in a sprint
                               # always merge into its sprint branch first — this block say what
                               # happen to branch that has nowhere left to go (standalone task,
                               # or sprint branch itself once its last task close).
  strategy: nothing            # nothing — close step stop after commit, branch left alone.
                               #   YOU open the PR. Safest, and default.
                               # pr     — push branch + PR onto target: ready for review —
                               #   existing draft marked ready, else opened ready. Never merged.
                               #   Sprint task: PR onto its sprint branch, no local merge.
                               #   Outward-facing: close step ALWAYS confirm before, every time.
                               # merge  — merge branch into target: locally, no push.
                               # GitHub provider: ignored for tickets — validate step marks PR ready,
                               #   close step merges it on GitHub (squash) on your yes, every time.
  target: master               # where pr/merge land. Ignored by `nothing`.
  delete_branch: auto          # auto   — delete only once code live elsewhere (merged into sprint
                               #   branch, or merged into target). `pr` and `nothing` keep it:
                               #   PR need its branch, and so do you.
                               # always — delete after close whatever happen. Ask first when
                               #   branch unmerged — that throw work away.
                               # never  — close step never delete a branch.
                               # Worktree for the slug (autopilot leftover) removed with it.

pr:                            # HOW every PR agents open look — ticket draft, close step PR, sprint PR.
                               # Rules: reference/pull-requests.md. Release PR not: release doc own it.
  template: auto               # (key missing → none, old behavior)
                               # auto — repo PR template when present (.github/pull_request_template.md,
                               #   .github/PULL_REQUEST_TEMPLATE/…): sections filled from task file,
                               #   checkboxes ticked only when proved. <path> — that file. none — no template.
                               # agents part (Closes #n, criteria, status) always in own marked block;
                               # refresh rewrite only that block, human edits stay.
  title: "{title}"             # placeholders {title} {n} (issue number, GitHub) {slug}
                               # e.g. "[#{n}] {title}", "feat: {title}"
  labels: []                   # added at creation, e.g. [agents]. Label missing in repo → skipped.
                               # On top, every PR gets ≥1 label picked from repo's own labels
                               # (best fit: bug, feature, docs, area…) — never created.
  assignees: ["@me"]           # PR assignees at creation, on top of @me (always self-assigned)
  reviewers: []                # requested when PR go ready, never on draft. Users or org/team.
                               # Empty → none requested (CODEOWNERS still apply).
  doc: "AGENTS.MD#Workflow rules"  # PR rules keys can't say (changelog line, size limit) — `path` or
                               # `path#Heading` (CONTRIBUTING.md#Pull requests). Empty → none.

compress_wiki: true            # caveman-compress config + wiki pages to save re-read tokens
                               # (at setup and after every wiki step update; .original backups removed)
---

# Project config

Free-form project notes every skill keep in mind.
Edit frontmatter above to change behavior.

## Core rule — stop and ask

Outside autopilot, anything unclear, ambiguous, or blocked beyond task spec: **stop and ask**. Never guess scope.

**Every question come with recommended answer** — always, no exception. One line for pick, one line for why, plus alternative when real one exist. No basis to choose? Recommend most reversible option, say it guess. Bare question without proposal never acceptable: answering must be confirm-or-correct, not homework.

Inside autopilot: never ask (nobody watching) — freeze task with open question logged, move to next.
