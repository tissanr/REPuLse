---
allowed-tools: Read, Write, Edit, Glob, Grep, Bash(git *), Bash(gh *), Bash(npm *), Bash(npx *), Bash(node *), Bash(mkdir *), Bash(rm *)
description: Bump the version, tag dev, and put a copy-paste test protocol for everything on dev that is not yet on main into the dev → main release PR, to work through in the Vercel dev preview
---

# Test protocol for the dev branch

Arguments: `$ARGUMENTS`

- *(empty)* → range `origin/main..origin/dev` (everything the next release would ship).
- `<ref>` → range `<ref>..origin/dev` (e.g. a release tag, or the SHA of the last tested state).
- `#123 #130` → only these PRs.
- `--no-verify` → skip step 5 (say so in the protocol header).
- `major` (or "Major-Version") → bump the major version in step 1b; `minor` → bump
  the minor version. **Default: bump the patch version** (the smallest step).
- `--no-tag` → skip step 1b (no version bump, no tag; say so in the header).

The result is the **`dev` → `main` release PR** (opened or updated by this skill) whose
description holds the protocol as a GitHub task list. The user works through it in the
Vercel preview of `dev` and ticks the boxes in the PR. No file is written into the
project (drafts go to the scratchpad).

Every test is a checkbox with a snippet they can paste straight into the editor
and a concrete expected outcome. Tests that are not REPuLse-Lisp (UI, keyboard, login,
snippets library, AI settings, CSP, export, embed, …) belong in it too, as click steps.
Nothing that changed behaviour may be missing just because it has no snippet.

Write the protocol in the user's language (German unless they invoked the skill in
English). Snippets, message texts and built-in names stay verbatim.

## Context

- Fetch: !`git fetch origin --prune --quiet && echo ok`
- dev head: !`git log -1 --format='%h %s (%cr)' origin/dev`
- Commits on dev not on main: !`git log --oneline --no-merges origin/main..origin/dev | wc -l`
- Merge commits in range: !`git log --merges --format='%h %s' origin/main..origin/dev`

## 1. Collect what changed

1. **PRs in range.** Take every PR number from the merge commits above
   (`Merge pull request #N`) and from squash commits (`… (#N)`) in
   `git log --format='%h %s' origin/main..origin/dev`. Ignore `Merge branch 'dev' into …`
   sync merges. For each PR:
   ```bash
   gh pr view N --repo tissanr/REPuLse --json number,title,body,files,mergedAt,headRefName
   ```
2. **Commits without a PR** (pushed straight to dev): list them with
   `git log --no-merges --format='%h %s' origin/main..origin/dev` and keep those not
   covered by a PR's commits (`gh pr view N --json commits`).
3. **Already on main?** Some fixes land on both branches through separate PRs (e.g. a
   security fix as #175 → main and #176 → dev; `git cherry` usually misses these because
   the patches differ). If a PR title says “(dev)” or a merged main PR has the same title
   or touches the same lines (`gh pr list --base main --state merged`), still
   test it (the preview runs dev), but tag the section “auch auf main”.
4. **Classify each change** by the files it touches:

   | Files | Kind of test |
   |---|---|
   | `packages/core`, `packages/lisp`, `app/src/repulse/env/builtins/`, `params`, `content/builtin_meta.edn` | Lisp snippet |
   | `packages/audio`, `app/public/worklet*.js`, `audio.cljs`, `fx/` | Snippet + listen check |
   | `app/src/repulse/ui/`, `editor.cljs`, `lisp-lang/` (grammar, completions, highlighting), `app/public/css`, `index.html` | Click/keyboard steps (+ snippet to trigger it) |
   | `api/`, `auth`, `snippets`, `ai` | Click steps in the deployed app (needs login / AI key — say so) |
   | `vercel.json` (CSP, headers), `embed.js`, `plugins/` | Steps + "DevTools console shows no CSP violation" |
   | `package.json` / lockfile only (dependabot) | No own tests; covered by the smoke regressions below — list the PR under *Nicht separat getestet* |
   | `docs/`, `PROMPTS/`, `ROADMAP.md`, `.claude/`, `.github/` only | No runtime change — list under *Nicht separat getestet* |

   Read the diff (`git diff origin/main...origin/dev -- <paths>`), not just the PR
   title: a "fix" PR often changes a message text, a keybinding or a default that needs
   its own check.

## 1b. Bump the version and tag the tested state

Every test round gets its own version, so a failure report can name it ("17 fails on
v0.1.2"). Do this before verifying, so the protocol tests exactly the tagged commit.

1. **Next version.** Take the higher of `package.json` on `origin/dev` and the newest
   `v*` tag (`git tag --list 'v*' --sort=-v:refname | head -1`), ignoring a suffix such as
   `-alpha`. Bump the **patch** digit (`0.1.0` → `0.1.1`) unless the arguments say
   `major` (`0.1.0` → `1.0.0`) or `minor` (`0.1.0` → `0.2.0`). Never pick a version
   whose tag already exists. If nothing changed since the newest tag
   (`git rev-list --count <tag>..origin/dev` is 0), don't bump: reuse that tag and say so.
2. **Bump on `dev` via PR** (`dev` requires a PR and green checks; never push to it):
   ```bash
   git switch -c chore/version-<version> origin/dev
   npm version <version> --no-git-tag-version   # package.json + package-lock.json
   git add package.json package-lock.json
   git commit -m "chore: bump version to <version>"
   git push -u origin chore/version-<version>
   gh pr create --base dev --title "chore: bump version to <version>" \
     --body "Version bump for the dev test round v<version>. The test protocol goes into the dev → main release PR."
   gh pr checks <n> --watch --fail-fast      # run in the background; ~10 min
   gh pr merge <n> --merge --delete-branch
   ```
   If a check fails, stop the version step: report it, leave the PR open, and continue
   the protocol against the current dev head with the header note *Version nicht
   erhöht (Bump-PR #n rot)*.
3. **Tag the merge commit** (annotated, on `origin/dev` after `git fetch`):
   ```bash
   git tag -a v<version> <merge-sha> -m "dev test round v<version>"
   git push origin v<version>
   ```
   No GitHub release — `/create-release` makes those from `main`; it finds the tag
   already there once `dev` is merged into `main` and reuses it.
4. From here on, "dev head" means this merge commit. Wait for its Vercel deployment
   (step 5.2) before verifying.

## 2. Reuse the PR test protocols

Most PRs end with `## Test protocol` (CLAUDE.md Rule 5). Extract that section from each
body and take its items over — but **check each against the current dev code**: a later
PR in the range may have renamed a built-in, changed a message or a default, or replaced
a demo sample. Fix or drop items that no longer hold, and note why under the item.
Drop setup lines that refer to localhost.

## 3. Fill the gaps

For every change that its PR protocol does not cover (or a PR without one, or a direct
commit), write new tests following Rule 5 in `CLAUDE.md`:

- Every new or changed built-in, parameter and effect → snippet test.
- Every new or changed error/warning message → a snippet that triggers it, with the exact
  text taken from the code (`grep` for it), as it appears in the output line.
- Every removed or renamed behaviour → a test showing the replacement (and the error
  for the old form, if there is one).
- UI/editor/API/CSP changes → numbered click steps: where to click or which keys (give
  both Linux/Windows and macOS keys), what appears where, exact labels from the code.
- A security fix → a concrete check that the hole is closed (e.g. a snippet tag with
  `<img src=x onerror=alert(1)>` shows as text, no alert), never "is secure".

Use only built-ins, samples, synth voices and demos that exist on `origin/dev`
(`docs/ai/builtins.json`, `app/src/repulse/content/`).

### Snippet rules (so they paste and run)

- Each snippet is **self-contained**: it evaluates on its own in a fresh editor. If it
  needs state (BPM, a track, a definition), put that in the same snippet.
- One-liners inline in backticks; anything longer in an indented ```` ```lisp ```` block
  under the checkbox (Markdown viewers give it a copy button).
- Say before each test whether to **replace** the editor content (default) or append.
- "Expected" is concrete: notes, hits per cycle, direction, seconds (default BPM 120 = 2 s
  per cycle), exact output text, what lights up. Never "works".

## 4. Always add the smoke regressions

Under *Regressionen*, besides at least two regressions near each touched area:

- Page load of the preview: no red errors, no CSP violations in the DevTools console.
- `(seq :bd :sd :bd :sd)` → kick/snare alternate, 4 hits per cycle (2 s).
- `(stop)` → playback stops cleanly.
- `(every 4 (fast 2) (seq :bd :sd :hh :oh))` → every 4th cycle twice as fast.
- A synth voice: `(->> (seq :c3 :e3 :g3) (synth :saw))` → 3-note arpeggio.
- If `api/` or deps changed: login works, a snippet from the library loads into the
  editor.

## 5. Verify the snippets (skip only with `--no-verify`)

Values must be real (Rule 5). Check them against **origin/dev**, not your branch:

1. **Event values (TEST1 harness).** In a scratch worktree of dev:
   ```bash
   git worktree add --detach "$SCRATCH/dev-check" origin/dev
   cd "$SCRATCH/dev-check" && npm install
   ```
   Write a throwaway Playwright spec there (never commit it) that opens
   `test-harness.html`, waits for `window.__REPULSE_TEST_READY__`, and for each snippet
   calls `window.__REPULSE_TEST__.evalToEvents(code, cycles)`; run it with
   `npx playwright test <spec>` (the config builds the harness). Copy note names, hit
   counts and parameter values from its output into "Expected". Remove the worktree
   afterwards (`git worktree remove --force`).
2. **Eval check on the real deployment.** Find the preview of the dev head:
   ```bash
   SHA=$(git rev-parse origin/dev)
   ID=$(gh api "repos/tissanr/REPuLse/deployments?sha=$SHA" -q '.[0].id')
   gh api "repos/tissanr/REPuLse/deployments/$ID/statuses" -q '.[0] | .state, .environment_url'
   ```
   If the state is not `success` yet, say so in the header and use the newest successful
   deployment of an older dev SHA, naming which commits it lacks. Open the URL in the
   browser pane (the user may need to sign in to Vercel there), then for each snippet set
   the editor content via `window.__REPULSE_EDITOR__` (a CodeMirror `EditorView`:
   `view.dispatch({changes:{from:0,to:view.state.doc.length,insert:code}})`), press
   Alt+Enter, read `#output` (class `output error` = failure), and evaluate `(stop)`
   after it. A snippet that is meant to error must show exactly the expected text; any
   other snippet must not error.

If a check fails, the snippet or expectation is wrong (fix it) **or dev has a bug**: keep
the test, mark it `⚠️ schlägt auf dev fehl:` with what you saw, and list it in the
summary. Do not silently drop it.

## 6. Put the protocol into the release PR

Write the draft to the scratchpad first, then publish it as the description of the
`dev` → `main` PR. This PR is where the round is tested and, once all boxes are ticked,
merged by a human (never merge it yourself).

```bash
gh pr list --repo tissanr/REPuLse --base main --head dev --state open --json number,url,body
```

- **No open PR** → `gh pr create --repo tissanr/REPuLse --base main --head dev
  --title "Release v<version>" --body-file <draft>` (with `--no-tag`:
  `"dev → main (<date>)"`).
- **Open PR** → replace its description with `gh pr edit <n> --body-file <draft>` and,
  if a version was bumped, set the title to `Release v<version>`. Before replacing, read
  the old body: carry over boxes the user already ticked (`- [x]`) for tests that are
  unchanged and still in range, and keep any text the user wrote above `## Test protocol`.

PR description structure (Rule 5: the protocol is the last section, followed only by the
attribution line):

````markdown
## Summary
- v<version> (tag `v<version>` on dev @ <short-sha>) — <n> PRs since main: #148, #150, …
- Preview: <deployment URL> (Vercel login required)
- Verified: harness ✅ / app build ✅ / deployment ✅ (or why not)

## Test protocol

Setup: Preview öffnen, Kopfhörer auf, warten bis die Samples geladen sind.
Jedes Snippet in den Editor kopieren (Inhalt ersetzen) und mit Alt+Enter
(macOS Option+Enter) auswerten; zwischen den Tests `(stop)`.

### PR #N — title
#### Neue Features
- [ ] **1. chop.** `(chop 8 (sound :breaks152 0))`
  Erwartet: …
#### Warnungen und Fehler
#### Ohne Code (UI, Tastatur, Login, …)
- [ ] **7. Kommentar umschalten.** Zeilen markieren, Strg+Shift+7 (macOS Cmd+Shift+7).
  Erwartet: jede Zeile beginnt mit `; `, zweites Drücken entfernt es.

### Regressionen
### Nicht separat getestet
- #173 chore(deps): … — nur Abhängigkeiten, durch Regressionen abgedeckt
- #177 docs: … — keine Laufzeitänderung
### Bekannte Probleme / ⚠️ auf dev fehlgeschlagen

🤖 Generated with [Claude Code](https://claude.com/claude-code)
````

Number the tests continuously through the whole protocol so the user can report "17
fails". Group by PR (oldest first) so a failure points at its PR; put shared regressions
at the end. Write `#123` references so GitHub links them.

**Size limit.** A PR description holds at most 65 536 characters. If the draft is longer,
keep Summary, setup, the regressions and the *Nicht separat getestet* list in the
description, and post the PR sections as PR comments (`gh pr comment <n> --body-file …`),
one comment per ≤ 60 000 characters, each starting with
`## Test protocol (Teil k/n)`. Their checkboxes are tickable too. Link the comments
from the description.

## 7. Report

Tell the user, briefly: the version and tag (or why none), the PR link, the preview URL,
how many tests (with snippet / without code), which PRs had no own protocol, and any ⚠️
failures found during verification. Clean up: remove the scratch worktree.
