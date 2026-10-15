<!-- board-contract: 2.2 -->
<!-- Installed by setup, its angle-bracket placeholders filled for this repository. Update through the setup PR, never by hand: the hooks,
     the board CLI and the PR template carry the same contract version. -->

# Board contract

This repository's backlog is a GitHub Project: https://github.com/orgs/LunabeeStudio/projects/445. Several developers and several AI agents share it, each with their own tools and work loop. This file is the only thing they all have to agree on: how a ticket moves, who may touch it, and what the repository must show at each step. How you do the work in between (your skill, your agent, by hand) is yours.

**Every agent reads this file before it works on an issue, and follows it.** The rules are written as MUST and NEVER on purpose; a workflow check reports every rule broken.

## The one rule

**Workers take tickets. The repository moves them.**

You never move a card on the board. You only *claim* a ticket (a lock) before working on it, and *release* it if you stop. Every other change of state comes from something that happened in the repository (a spec written, a PR opened, a PR merged) and is applied by the board workflow. The board says what the code says.

## What lives where

| Thing | Where |
|---|---|
| Ticket | a repository issue; its number is the ticket id (`#12`). Every issue is a ticket, unless labeled `wa-ignore` |
| State | the Project `Status` field (the columns) |
| Priority | card order on the Project, top = next |
| Size | the Project `Size` field: `quickwin`, `medium`, `large` |
| Spec | the issue body (`## Acceptance criteria`), or a task file `.whackagent/tasks/<n>-<slug>.md` on the ticket's branch |
| Lock | a claim, git ref `refs/wa-claims/<n>/<phase>`, created by the board CLI |
| Reservation | issue assignees: a ticket assigned to another account is not yours to take |
| Sprint | a parent issue labeled `wa-sprint`; its tickets are its sub-issues |
| Milestone | the repository's GitHub milestones (release scope) |
| Dependency | issue relationships ("blocked by") |
| Change | one PR per ticket, linked to the ticket |

## States

| State | Meaning | Entered by |
|---|---|---|
| `todo` | the ticket exists, no spec yet | issue opened |
| `grilling` | **locked**: one worker is writing its spec | `claim <n> grilling` |
| `grilled` | spec written, ready to code | workflow: spec found (see *Spec*) |
| `coding` | **locked**: one worker is changing its code | `claim <n> coding` |
| `review` | a PR is open, humans are on turn | workflow: PR opened, or a claimed round pushed to it |
| `done` | the change landed | workflow: PR merged |

Allowed claims: `grilling` from `todo` (first spec) or `grilled` (re-spec, when scope changed). `coding` from `grilled` (first round) or `review` (any later round). Anything else is refused.

In `review`, the PR tells who acts: **draft** = humans test it, **ready** = a human decides the merge.

Column names may differ on the Project; the board CLI always reports the six names above.

## Board CLI

`.github/board/wa-backlog` (run from the repository root; needs `gh` logged in with the `project` scope). It prints JSON on stdout and messages on stderr.

| You want to | Run |
|---|---|
| see the board | `.github/board/wa-backlog list [--state s,…] [--sprint x] [--milestone m]` |
| read one ticket (state, branch, blockers, claims) | `.github/board/wa-backlog get <n>` |
| create a ticket | `.github/board/wa-backlog create --title "…" --summary "…" [--size …] [--sprint …] [--note "…"]` |
| take a ticket | `.github/board/wa-backlog claim <n> grilling\|coding` |
| give it back | `.github/board/wa-backlog release <n> <phase> --reset-to <state> --reason "…"` |
| repair a PR's link to its ticket (the workflow links it on its own) | `.github/board/wa-backlog link-pr <n> <pr>` |
| record a dependency | `.github/board/wa-backlog depend <n> --on <x>[,<y>]` |
| set size, sprint, milestone | `.github/board/wa-backlog set-field <n> size\|sprint\|milestone <value>` |
| leave a note on the ticket | `.github/board/wa-backlog comment <n> "…"` |
| see live locks | `.github/board/wa-backlog claims` |

Exit codes: `0` ok · `1` error · `2` usage · **`3` taken** (someone else holds it, or it is assigned to another account; the owner is printed) · **`4` wrong state**. `3` and `4` are normal answers, not failures. Claiming a phase you already hold (same agent id) answers `0`.

## Working a ticket

### 1. Pick

Take the top ticket of the state you need (`todo` to spec, `grilled` to code) that is **unclaimed and not reserved**. Never pick a ticket whose `claims` is non-empty or whose `reserved` is true. Never pick a `grilled` ticket whose related blockers are still open, unless you stack on them (see *Dependencies*).

### 2. Claim

`.github/board/wa-backlog claim <n> <phase>` before you change anything: spec, branch, code, PR. Exit `3` → someone holds it: never retry, never steal; pick another ticket or tell your user who holds it. Exit `4` → it is not in a state you can claim: say which, stop.

A winning claim assigns your account to the issue. Leave that assignment alone.

### 3a. Spec (`grilling`)

- Write the spec in the **issue body**, below the original request, which you keep untouched:
  - `## Acceptance criteria` (required, non-empty): observable checks that mean "done", one per line.
  - `## Context / Decisions` (optional): what was decided and why.
- Write both sections in **one edit, at the end** (`gh issue edit <n> --body-file <file>`). The workflow moves the ticket to `grilled` on that edit and drops your claim. A half-written spec saved early would move it too soon. Only an edit by an account that can push to the repository counts: criteria written by anyone else are reported, until a maintainer edits the body.
- A task file on the ticket's branch (`.whackagent/tasks/<n>-<slug>.md`, frontmatter `issue: <n>`, non-empty `## Acceptance criteria`) is accepted too: pushing it has the same effect. That route needs the branch named `wa/<n>-<slug>`.
- Spec too big for one ticket → split it into new tickets in a sprint (see *Sprints*), with your user's agreement.
- You stop before the spec is written → `release <n> grilling --reset-to todo` (re-spec: `--reset-to grilled`).

### 3b. Code (`coding`)

- **Branch.** Reuse the ticket's branch when it has one (`get <n>` → `branch`). Otherwise create it linked to the issue: `gh issue develop <n> --name wa/<n>-<slug> --base <fork point>`. Another branch name is fine if your PR links the ticket.
- **Fork point** = the sprint branch `sprint/<sprint>` when the ticket is in a sprint, else `master` (or its track branch, when its milestone is a track).
- **One round = claim → work → commit → push.** Push **once, at the end** of the round: a push to a branch with an open PR ends the round and drops your claim.
- **First round** opens a **draft PR** (`gh pr create --draft`) whose body says `Closes #<n>`. Later rounds push to the same PR and refresh its body.
- **A round ends only with a PR or a release.** Local commits, or a branch pushed without a PR, keep your claim and the card in `coding`: nobody else can test or review that work yet. Stopping for longer than 24 h → open the draft PR, or release.
- **Nothing to push** at the end of a round → `release <n> coding --reset-to review` (PR open) or `--reset-to grilled` (no PR yet). Never leave a claim behind.
- **After each push**, check `gh pr view <pr> --json mergeable` (retry while `UNKNOWN`). `CONFLICTING` → GitHub runs no workflow on a conflicting PR, so your push moved nothing and your claim is still held: rebase your ticket's own commits onto the PR base, re-run the tests, `git push --force-with-lease`, check again.
- **Feedback on a ready PR** → `gh pr ready --undo` before your first commit, so nobody merges code that is moving.

### 4. Ready

`gh pr ready <pr>` means: *this PR is mergeable*. Mark ready only when every acceptance criterion is proven (checked in the PR body) or explicitly left to a human, and no check is failing. In an attended run, the human who tested says so. In an unattended run, mark ready only if your own verification proved every criterion it could run; otherwise leave the PR draft and say what is left. Request reviewers when the PR goes ready, never on a draft.

### 5. Merge

**A human decides every merge**: on GitHub, or by asking their agent to merge that PR in that session. An agent merges no PR its user did not name. When it merges:

- Claim `coding` first (from `review`), so no other round pushes while you merge. Exit `3` → stop.
- Merge only a **ready** PR whose checks are green, whose required reviews are in, and whose base is not another ticket's open branch (stacked: the PR below merges first).
- Use the merge style below: `.github/board/wa-backlog merge <pr>` for a ticket (squash; `--method merge` for a track landing). It merges that one PR through GitHub's async merge API, the only one that accepts a stacked PR, and refuses while a PR below it in the stack is open. Never `--admin`, never bypass branch protection.
- GitHub refuses (checks, reviews, conflict) → `release <n> coding --reset-to review --reason "…"` and say why.

Never merge a ticket branch locally. The workflow sets `done`, drops the claim and closes the issue on merge.

## Pull requests

- **One PR links exactly one ticket**: `Closes #<n>` in the body. The workflow adds the Development link from it, whatever the PR's base branch (GitHub links it on its own only for PRs onto the default branch).
- **The body follows the repository's PR template**, including its board sections:
  - `## Ticket`: `Closes #<n>`.
  - `## Acceptance criteria`: the ticket's current criteria as a checklist. Check `- [x]` only what a round proved; leave the rest `- [ ]` with a one-line reason.
  - `## Status`: one line saying what is owed next (`Draft — test it`, `Ready — review, then merge`).
  - A PR that is not a ticket's (sprint, track or sync PR) writes `Sprint #<parent>` or `n/a` under `## Ticket`, never `Closes`.
- **Refresh the body on every round that pushes**: a PR body describing superseded behavior misleads its reviewer. Keep text a human added.
- **No milestone on PRs**: the linked ticket carries it. **Labels**: pick from the repository's own; never create labels.

## Merge style

- **Ticket PRs land squashed.**
- **Sprint, track and sync PRs land with a merge commit, never squashed**: squashing loses the history every later rebase and sync relies on. Their body says so.
- Because tickets land squashed, the base never contains a ticket's original commits. **Diff and rebase only your ticket's own commits**: `git rebase --onto origin/<new base> <first own commit>^`. Never a plain `git rebase master` or a `master..HEAD` diff after a parent or stacked ticket landed.

## Sprints

- A sprint groups the tickets of one body of work: a parent issue labeled `wa-sprint`, sprint name = its title in kebab-case, tickets = its sub-issues. `set-field <n> sprint <name>` creates or joins it.
- With sprint branches, each ticket forks from `sprint/<name>` and its PR targets it. When every ticket landed, one sprint PR merges the sprint branch into `master` (merge commit); the workflow then closes the parent.
- A parent issue is not a ticket: never claim it, never code it. A ticket that already has a parent which is not a sprint can't join a sprint: say so, never detach it.

## Milestones

- **Humans own milestones**: they create, rename and close them. Workers never do, except closing one in a documented release flow on a human's yes.
- A new ticket joins the **highest open milestone** (version order of titles); `create` does it. Change a ticket's milestone only on a human's word.
- Milestones listed as tracks (long-lived trunks beside `master`) are never a default: a ticket joins one only on a human's word, and then forks from and lands on that track's branch.

## Dependencies

- Record them: `depend <n> --on <x>`. Read them: `get <n>` → `blocked_by`.
- A blocker is **related** when it shares the ticket's sprint, or its track milestone (`get` → `sprint`, `milestone`). An **unrelated** blocker (other sprint or track, or none) never stacks: work the ticket from its own base as usual and add the body line `Blocked by #<x>: merge order is yours`.
- Ticket with an open related blocker: wait, or **stack**: fork from the blocker's branch, PR base = that branch, then `.github/board/wa-backlog stack <pr> --on <blocker pr>` puts the PR in the blocker's GitHub stack. Stacks are linear: when the blocker already has a PR on top (exit `4`), the PR stays chained on the blocker's branch with the body line `Stacked on #<pr>: merge it first`.
- **Keep a stack linear.** GitHub refuses to merge a stack whose upper branch does not descend from the one below. A round that pushes to a ticket with PRs above it in its stack rebases each of them onto the new branch below: claim it `coding` first (exit `3` → leave it, say it needs a rebase), rebase only its own commits, push with `--force-with-lease`.
- After a stacked PR merges, GitHub retargets the PRs above it and rebases their branches itself: start your next round from the remote branch, never push an older local copy over it.
- **Never delete a branch by hand** while PRs are based on it: GitHub retargets them only when the branch is deleted by a merge. Deleting it later closes them.

## Locks

- Claims never expire: a spec interview may wait two days for an answer.
- `claims` flags a lock `stale` after 24 h with nothing new (age = the newer of the claim and the branch's last commit), and the check reports it on the ticket. **Only a human clears a stale lock** (`release <n> <phase> --reset-to <state> --reason "stale: …"`). Never release a lock you don't hold to take its ticket.

## Checks

The board workflow **reports** what breaks this contract, once per problem, as a comment on the ticket or PR. It never moves cards: fixing is up to the worker or a human. It reports:

- on a ticket, once a day on weekdays: a card in `grilling` or `coding` with no claim held; a card in `review` with no open PR; a linked PR open while the card sits in `todo` or `grilled`; a linked PR merged while the card is not `done`; a card in `grilled` with no spec; acceptance criteria in the issue body while the card sits in `todo`; a card in `done` on an open issue; a closed issue in a lock column; a claim held with nothing new for 24 h; an issue closed as completed whose card is not `done`. The comment turns to resolved once the problem is gone.
- on a PR, as it happens: a push while the ticket holds no `coding` claim; a PR linking several tickets.
- on a ticket, as it happens: acceptance criteria written by an account that can't push to the repository.

Read those comments when you take a ticket, and fix what they name.

## Never

- Move a card, or set a state, yourself.
- Work on a ticket you have not claimed, or one claimed by someone else, or reserved for another account.
- Merge a PR your user did not ask you to merge, merge with an admin override, or merge a ticket branch locally.
- Push in the middle of a round to a branch with an open PR.
- Create, rename or close milestones; create labels.
- Force-push without `--force-with-lease`, or force-push a branch whose ticket you have not claimed.
- Delete a branch other PRs are based on.

## Version

This file carries `board-contract: 2.2` in its first line. The board CLI, the board workflow and the PR template sections carry the same version. The CLI warns when this file's version differs from its own: update them together with the setup PR, never one file by hand.
