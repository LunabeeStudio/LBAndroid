<!-- lb-board contract, installed by /lb-board-setup with its angle-bracket placeholders filled. Update it through
     the setup skill, together with .lunabee/board/lb-board. -->

# Board

This repository's tickets live on a Plane board: https://projects.lunabee.ovh/lunabee/projects/9263a518-9669-487d-97bb-42abd0b34cee/issues/. Code, branches and pull requests live on
GitHub. Work the way you like, by hand or with any agent. This file only says how a card moves, and what the
repository must show for it to move.

**Agents read this file before working on a ticket and follow it.**

## The one rule

Nobody drags a card to another column, human or agent. You may *claim* a ticket before working on it and
*release* it if you stop. Every other move comes from the repository: a spec pushed, a PR opened, a PR merged.
The "Lunabee Plane board" GitHub App applies these moves within seconds. Humans write cards and reorder them.

## States

| State | Meaning | Entered by |
|---|---|---|
| `todo` | the ticket exists | card created |
| `grilling` | **locked**: someone is writing its spec | `claim LBA-n grilling` |
| `grilled` | spec written, ready to code | a spec pushed on a branch naming the ticket |
| `coding` | **locked**: someone is changing its code | `claim LBA-n coding` |
| `review` | a PR is open | PR opened, or a push to it while a `coding` claim is held |
| `done` | the change landed | PR merged; every assignee removed |

`Canceled` means the ticket was dropped: it never landed and nobody can claim it.

The claims and specs are optional. A ticket you never claim and never spec still goes from `todo` to `review` when
its PR opens, and to `done` when it merges.

A PR closed without merging sends the card back:
- to `grilled` when its branch holds the spec;
- to `todo` otherwise.

Cards labeled `lb-ignore`, and cards in a column outside these states, are left alone.

## Naming the ticket

A branch or PR belongs to the ticket whose key it names, **anywhere and in any case**:
- `feature/LBA-12-login`
- `fix/lba-12`
- `LBA-12`

The board checks these places in order and uses the first one that names a ticket:

1. The branch name.
2. The PR title.
3. A card link in the PR body: `[LBA-12](https://projects.lunabee.ovh/lunabee/browse/LBA-12/)`.

One PR moves one ticket. A branch or title naming two tickets moves nothing, and the board says so on the PR.

## Claiming a ticket

A claim is a lock: one holder per ticket and phase. It moves the card, assigns you, and tells everyone you're on it.

```sh
.lunabee/board/lb-board claim LBA-12 coding     # or grilling, to write the spec first
.lunabee/board/lb-board release LBA-12 coding --reset-to todo --reason "parked"
```

In Claude Code, `/lb-board-claim` and `/lb-board-start` do the same.

What a claim needs:
- `gh` logged in;
- `PLANE_API_KEY` exported in your shell. Create the key in Plane, Profile settings → API tokens. Never put it in
  the repository.

When the claim fails:
- **Exit `3`:** someone else holds the ticket, or it is assigned to someone else. Pick another one.
- **Exit `4`:** the ticket isn't in a state you can claim from, or a blocker is still open. Pass `--blocked-ok` to
  code it anyway.

You can claim from these states:
- `grilling`: from `todo` or `grilled`.
- `coding`: from `todo`, `grilled` or `review`.

How a claim ends:
- A `grilling` claim ends when the spec is pushed.
- A `coding` claim ends when the PR opens or you push to it. Each later round claims `coding` again from `review`.
- If you stop, release the claim. `.lunabee/board/lb-board claims` lists the claims held; one with nothing new for
  24 h is stale.

## Posting a spec

The spec is a Markdown file named after the ticket, `.whackagent/tasks/LBA-12-<slug>.md`. Push it on a branch that
names the ticket. Write it in one go, then push: the push moves the card to `grilled` and drops your `grilling`
claim.

```markdown
# Login with Google

## Acceptance criteria

- [ ] the sign-in screen shows a Google button
- [ ] a new account lands on onboarding

## Context

What was decided and why (optional).
```

A spec written in the card description moves nothing: GitHub events are the only trigger.

`check` reports these cards.

## Feedback and run report

**Feedback rounds.** Review feedback can go in the spec, under `## Feedback`, one `### ` block per round.
- When a round is pushed to an open PR while a `coding` claim is held, the card links the new rounds.
- The PR gets a 📝 comment with their permalinks.

**Run report.** A PR body may carry a run report between `<!-- wa-report -->` and `<!-- /wa-report -->`.
- When the PR merges, the report is copied onto the card.
- whackagent writes one; anyone else may.

## Priority, sprints, milestones, dependencies

**Priority**
- Card order on the board is priority: the top card is next.
- Humans drag cards. `move LBA-12 --top|--bottom|--before LBA-3|--after LBA-3` does the same.

**Sprints**
- A sprint is a parent card labeled `lb-sprint`. Plane cycles are not used.
- Its tickets are the parent's children.
- Turn a ticket into a sprint with `split LBA-12`. Add tickets with `create --sprint <name>` or
  `set-field LBA-12 sprint <name>`.
- The sprint root moves to `Done` on its own when the sprint lands:
  - if sprints have a branch (`PLANE_SPRINT_PREFIX`), when that branch's PR merges;
  - otherwise, when its last ticket lands.
- A root left open with nothing to land is the one card a human moves: to `Done`, or to `Canceled` when nothing
  shipped.

**Milestones**
- A milestone is a Plane module.
- A ticket sits in one module at most. `set-field LBA-12 milestone <name>` moves it there.
- `create` puts new cards in the highest open module, unless that module is listed in `PLANE_TRACKS`.
- Humans create and close modules. `close-milestone <name>` closes one.

**Dependencies**
- A dependency is a Plane relation ("blocked by"): `depend LBA-12 --on LBA-3,LBA-4`.
- A `coding` claim refuses a ticket with an open blocker, unless `--blocked-ok` is passed.
- Relations are only ever added. A human removes one in Plane.

## Board CLI

`.lunabee/board/lb-board`, run from the repository root. It prints JSON.

| You want to | Run |
|---|---|
| see the board | `list [--state todo,grilled] [--mine] [--sprint <name>] [--milestone <name>] [--all]` |
| read one ticket | `get LBA-12` |
| create a card | `create --title "…" --summary "…" [--priority high] [--assignee <name>] [--sprint <name>] [--milestone <name>]` |
| reorder | `move LBA-12 --top\|--bottom\|--before LBA-3\|--after LBA-3` |
| sprint, milestone | `split LBA-12` · `set-field LBA-12 sprint\|milestone <value>` · `milestones` · `close-milestone <name>` |
| record a dependency | `depend LBA-12 --on LBA-3` |
| take / give back a ticket | `claim LBA-12 grilling\|coding`, then `release LBA-12 <phase> [--reset-to <state>]` |
| comment on a card | `comment LBA-12 "…"` |
| drop a ticket, on a human's word | `cancel LBA-12 --reason "…"` |
| see live claims | `claims` |
| check the board against the repository | `check` |
| repair a PR link (the App links PRs itself) | `link-pr LBA-12 <pr>` |

## When cards don't move

- **Missed events:** if the App's receiver is down, cards stay put and missed events are not replayed on their own.
  Deliveries are listed at https://hooks.lunabee.ovh/ (Lunabee sign-in), and a missed one can be redelivered.
- **No double writer:** Plane's own GitHub integration must stay off, or it would move the same cards too.
- **Drift:** `check` reports cards out of step with the repository, such as a merged PR whose card isn't `done`,
  or acceptance criteria in a `todo` card's description.
