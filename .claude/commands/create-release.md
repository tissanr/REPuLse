---
allowed-tools: Bash(git *), Bash(gh *), Bash(npm *), Read, Glob, Grep, AskUserQuestion
description: Create a GitHub release — drafts notes from delivered phases, bumps the version in package.json via PR, tags the release
---

## Context

- Latest releases: !`gh release list --limit 5`
- Latest release tag: !`gh release list --limit 1 --json tagName --jq '.[0].tagName'`
- Latest release date: !`gh release list --limit 1 --json publishedAt --jq '.[0].publishedAt'`
- Latest tag ref: !`git describe --tags --abbrev=0`
- package.json version on this branch: !`npm pkg get version`
- Delivered phases (ROADMAP.md): !`grep -E "^## Phase .*✅ \*delivered\*" ROADMAP.md`
- Arguments provided by user: $ARGUMENTS

## Your task

You are creating a GitHub release for REPuLse. Follow these steps carefully.

### Step 1 — Determine the release title

If `$ARGUMENTS` contains a title (anything quoted or a descriptive string), use it.
Otherwise, ask the user: **"What should the release title be?"**

### Step 2 — Determine release type

If `$ARGUMENTS` explicitly mentions "alpha", use `alpha`.
If `$ARGUMENTS` explicitly mentions "beta", use `beta`.
If neither is mentioned, ask:
> "Is this a **regular release**, **alpha**, or **beta**?"

- Regular release: no suffix — tag is just `v<major>.<minor>.<patch>`
- Alpha: tag suffix `-alpha`
- Beta: tag suffix `-beta`

### Step 3 — Determine the version number

Parse the latest release tag (e.g. `v0.1.0-alpha` → major=0, minor=1, patch=0).

If `$ARGUMENTS` contains an explicit version (e.g. "v0.2.0", "0.2"), use that.

Otherwise ask:
> "The latest release is `<tag>`. Should I bump the **minor** version (→ `v0.X.0`) or is this a **major** release (→ `v1.0.0`)?"

- Minor bump: increment the minor digit, reset patch to 0.
- Major bump: increment the major digit, reset minor and patch to 0.

Construct the tag as:
- Regular: `v<major>.<minor>.<patch>` (e.g. `v0.2.0`)
- Alpha: `v<major>.<minor>.<patch>-alpha` (e.g. `v0.2.0-alpha`)
- Beta: `v<major>.<minor>.<patch>-beta` (e.g. `v0.2.0-beta`)

### Step 4 — Identify new phases since last release

Run `git log --oneline <latest-tag>..HEAD` (substituting the actual tag from context) to get commits since the last release. Cross-reference with the delivered phases in ROADMAP.md to identify which phases were delivered in this release cycle. If the commit log is thin or unclear, include all delivered phases grouped clearly, noting that this is a cumulative list.

### Step 5 — Draft release notes

Draft release notes in the same style as the v0.1.0-alpha release:

```
> One-sentence pitch for REPuLse and what this release represents.

---

**<Group heading>**

- **Phase X** — Description
- **Phase Y** — Description

**<Group heading>**

- ...

---

> **Note:** The editor uses the **oneDark** theme in this release. [Omit this line once Phase UI1 is delivered.]
```

Group phases into logical sections (Core engine, Pattern language, Audio & effects, Editor & UI, Persistence & sharing, Community & backend, MIDI & I/O, Visuals, Infrastructure). Only include groups that have at least one phase in this release.

### Step 6 — Confirm and create

Show the user:
- Proposed tag: `v0.x.0-alpha`
- Title: the release title
- The full draft notes

Then ask: **"Shall I create this release with the above details?"**

Wait for confirmation ("yes", "go ahead", "do it", etc.) before changing anything.

### Step 7 — Get the version onto `main`

Releases are cut from `main`. Changes reach it only as `dev` → `main` PRs, which a human
merges (CLAUDE.md, "Phase workflow"; the `main` ruleset blocks every other update). The
version in `package.json` must match the tag without the leading `v` (tag `v0.2.0-alpha` →
version `0.2.0-alpha`).

Run `git fetch origin dev main`, then read `git show origin/main:package.json` and
`git show origin/dev:package.json`, and take the first matching case:

1. **`main` already has the version** → continue with Step 8.
2. **`dev` has it, `main` does not** → the release PR is missing or unmerged. If there is no
   open `dev` → `main` PR, open one:
   `gh pr create --base main --head dev --title "Release <tag>" --body "<draft notes from Step 5>"`.
   Stop: return the PR URL and ask the user to merge it, then run `/create-release` again.
3. **Neither has it** → open the version-bump PR against `dev`:

   ```bash
   git switch -c chore/release-<tag> origin/dev
   npm version <version> --no-git-tag-version   # updates package.json and package-lock.json
   git add package.json package-lock.json
   git commit -m "chore: bump version to <version>"
   git push -u origin chore/release-<tag>
   gh pr create --base dev --title "chore: bump version to <version>" --body "Version bump for release <tag>."
   ```

   Stop: return the PR URL. Once it is merged, run `/create-release` again (→ case 2).

Never tag before the version is on `main`.

### Step 8 — Create the release

```bash
gh release create <tag> --target main --title "<title>" --notes "<notes>"
```

Return the release URL when done.
