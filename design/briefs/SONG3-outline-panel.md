# Design brief: Outline panel (Phase SONG3)

This brief is for a UI designer (Claude Design). It describes the REPuLse screen as it is
today, what the existing right-hand **context panel** contains, and what the new
**outline panel** has to show and do. The open question is **where the outline panel
goes and how it looks**. Please propose a design that answers that.

---

## 1. The product in one paragraph

REPuLse is a browser-based live-coding instrument. A musician types code in a small
Lisp and hears it as music in real time:

```lisp
(def kick (seq :bd :_ :bd :_))
(track :drums (stack kick (seq :_ :sd)))
(track :bass  (seq :c2 :eb2 :g2))
(song
  (section :intro 4 [:drums])
  (section :verse 8 [:drums :bass]))
```

The audience is musicians performing or composing live on a **desktop browser**
(not mobile-first). The editor is the centre of everything. Every panel around it must
be glanceable during a performance and must never steal focus from the code.

Visual style today: dark, dense, monospaced, "terminal / synth hardware" feel, small
type (9–13 px), thin 1 px borders, colour used sparingly for meaning.

---

## 2. The screen today

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ [logo] REPuLse   tap  lib  ai  share            [sign in]      ▶ play  ●     │  top nav
├────────────────────────────────────────────────────────────┬─────────────────┤
│                                                            │ CONTEXT PANEL   │
│   EDITOR (CodeMirror)                                      │ ~195 px wide    │
│   takes all remaining width & height                       │ (168 px when    │
│                                                            │  window ≤760px) │
│                                                            │                 │
│                                                            │ scrolls         │
│                                                            │ vertically      │
├────────────────────────────────────────────────────────────┤                 │
│ [lib panel – hidden unless "lib" toggled; shares height]   │                 │
│ [ai panel  – hidden unless "ai" toggled; shares height]    │                 │
├────────────────────────────────────────────────────────────┤                 │
│ => ready — Alt+Enter or click play          (console out)  │                 │
│ > _                                         (command line) │                 │
├────────────────────────────────────────────────────────────┤                 │
│ ∿ wave ⊞ spec ◐ loud ║ beat ⊟ roll ▬ seg   (viz toolbar)   │                 │
│ :drums ▮▮ ▮  ▮▮ ▮   (per-track timeline / visualisation)   │                 │
│ :bass  ▬▬▬ ▬▬▬                                             │                 │
├────────────────────────────────────────────────────────────┤                 │
│                                        Alt+Enter to eval   │  status bar     │
└────────────────────────────────────────────────────────────┴─────────────────┘
```

Facts a designer should know:

- **Left of the editor there is nothing today.** The editor starts at the window edge
  (with its line-number gutter).
- The **lib** (snippet library) and **ai** (assistant chat) panels do **not** open as
  sidebars. They open *below* the editor, inside the same column, and split its height.
- The **context panel** is the only sidebar. It's always visible, fixed width, on the
  right, and scrolls on its own.
- Nothing is resizable by dragging today.

### Colour and type tokens in use

| Token | Value | Used for |
|---|---|---|
| `--bg` | `#131928` | app background |
| `--bg-ed` | `#0d1421` | editor background |
| `--bg-pn` | `#161d30` | panels |
| context panel bg | `#10172a` | right sidebar |
| `--bd` / `--border` | `#1e2a45` | borders |
| `--text` | `#abb2bf` | normal text |
| `--mid` | `#5a6a8a` | secondary text |
| `--dim` | `#3a4868` | disabled / quiet text |
| section titles | `#2a3a55`, 9 px, letter-spacing 0.14em, uppercase | "TRACKS", "FX" |
| `--accent` / `--red` | `#e94560` | brand pink, errors, recording |
| `--cyan` | `#56b6c2` | playing, song status |
| `--yellow` | `#e5c07b` | BPM |
| `--purple` | `#c678dd` | sample bank |
| `--green` | `#98c379` | OK states |
| font | JetBrains Mono / Fira Code / Cascadia Code | everything |

The editor syntax colours follow the One Dark palette (the colours above are taken from it).

---

## 3. What is in the context panel today (the "old" panel)

The context panel shows **live session state**: what is currently sounding, not what is
written in the code. It's rebuilt from runtime state on every animation frame where
something changed. It pauses re-rendering while the user drags one of its sliders.

Sections from top to bottom. A section only appears when it has content.

### 3.1 Status strip (always shown)

One wrapping row of small badges:

| Badge | Example | Colour | Meaning |
|---|---|---|---|
| BPM | `120 BPM` | yellow, bold, 13 px | current tempo |
| backend | `[wasm]` or `[js]` | dim, outlined box | which audio engine is running |
| bank | `AkaiLinn` | purple, bold | active drum-machine sample prefix (only if set) |
| transport | `● playing` / `○ stopped` | cyan / dim | play state |
| song | `▶ chorus · bar 13/24` or `▶ song · starting` | cyan, bold | only when a `song` is playing: current section name and bar position |
| recording | `● REC 12.0 / 50.0 s` or `● REC waiting` | pink, bold | only during a full-song audio export |

### 3.2 TRACKS

One block per named track, sorted alphabetically:

- **Header row:** icon + `:name` + status
  - icon `▶` normal, `■` muted, `★` solo (the only unmuted track)
  - status text, one of:
    - `(muted)`
    - `(solo)`
    - `(not in :verse)`: the track exists, but the currently playing song section doesn't use it
    - otherwise, short non-numeric params inline, e.g. `synth saw  bank tr909`
  - a muted track is visually dimmed
- **Slider rows** (unmuted tracks only), one per numeric parameter the track uses:
  `amp`, `pan`, `decay`, `attack`, `release`, `rate`, `begin`, `end`.
  Layout: `label · range slider · value`. Dragging a slider **rewrites the number in the
  code** live.
- **Per-track FX** (collapsible `<details>`, open by default): title `fx (2)`, then for
  each effect its name and its sliders/dropdowns (e.g. `filter` → `freq`, `q`;
  `amp-sim` → `gain`, `stages`, `tone`, `sag`, `mix`, `oversample`, `tonestack` dropdown).

The track block is the tallest part of the panel. With 4–6 tracks and a few effects it
easily fills a laptop screen height.

### 3.3 FX (master effects)

One row per active global effect: name + `off` if bypassed, otherwise its sliders and
dropdowns (same row style as above). Up to ~14 effect types exist (reverb, delay,
filter, chorus, phaser, tremolo, overdrive, distort, amp-sim, cab, bitcrusher,
sidechain, compressor, dattorro-reverb). Each has 1–7 controls.

### 3.4 MIDI

One row per MIDI CC mapping: `CC #74 → filter-freq`.

### 3.5 BUSES

One row per modulation/audio bus: `name · type` (e.g. `lfo1 · control`).

### 3.6 SOURCES

One row per loaded sample library, prefixed `♫`:
`github:tidalcycles/Dirt-Samples (217 banks)` or `freesound: rain (12)`.

### 3.7 BINDINGS

One row per user `def` that exists *in the running environment*: `name · type`, where
type is `pattern`, `fn`, `number`, `string`, `keyword` or `value`. Sorted
alphabetically. **No positions and no clicking.** It reflects evaluated state, not the
text.

> Note the overlap: BINDINGS (runtime, alphabetical, not clickable) and the new
> OUTLINE (source text, source order, clickable) both list `def`s. The design can keep
> both, merge them, or drop BINDINGS. Please give an opinion.

### Row styles shared by all sections

- section title: tiny uppercase dim label (9 px, `#2a3a55`)
- row: `name` in normal text, `type`/value right-aligned or after it in dim text
- slider row: label (dim) · native range input · value
- sections separated by a 1 px border; padding about 8 px 10 px

---

## 4. What the new outline panel must contain

### 4.1 Purpose

Song files get long: instruments and patterns at the top, the `song` arrangement at
the bottom, often 100–400 lines. The outline is a **table of contents of the code**.
You see the structure at a glance and jump anywhere with one click.

It reflects the **text in the editor**, not the running session. It updates while
typing (within ~300 ms) and **keeps working when the code has a syntax error** (it
shows what it can still recognise).

### 4.2 Entries

Only **top-level** forms, in **source order** (the order they appear in the file):

| Kind | From code | Displayed as | Has children |
|---|---|---|---|
| `def` | `(def kick …)` | `def  kick` | no |
| `defn` | `(defn humanize [p] …)` | `defn humanize` | no |
| `defsynth` | `(defsynth pad [freq] …)` | `synth pad` (or `defsynth`) | no |
| `track` | `(track :drums …)` | `track :drums` | no |
| `song` | `(song …)` | `song` | **yes: its `section`s** |
| `section` | `(section :verse 8 [:drums :bass])` inside `song` | `:verse` + extra info | no |

Each entry shows:

- **kind**: a short label or small icon. It must be distinguishable at a glance;
  colour-coding by kind is welcome.
- **name**
- **line number**: right-aligned, dim (e.g. `L42`)

Sections can show extra info if it fits: the **length in bars** (`8 bars`) and
**which tracks play** (`:drums :bass`). Treat this as optional, a design decision.

Example content (a typical mid-sized song, 15–25 entries; large ones 40+):

```
OUTLINE
  def    kick              L1
  def    snare             L2
  def    hats              L3
  defn   humanize          L5
  synth  pad               L9
  track  :drums           L14
  track  :bass            L20
  track  :keys            L27
  track  :pad             L33
▾ song                    L40
    :intro   4 bars       L41
    :verse   8 bars       L42
  ▶ :chorus  8 bars       L43   ← currently playing
    :verse   8 bars       L44
    :outro   4 bars       L45
```

### 4.3 States each entry can be in

| State | When | Needs a visual treatment |
|---|---|---|
| normal | default | yes |
| hover | mouse over | yes (it's clickable) |
| **cursor inside** | the editor cursor is inside this form | yes, a subtle "you are here" highlight, updated as the cursor moves |
| **playing section** | a song is playing and this `section` is sounding now | yes, distinct from "cursor inside" (play-head style, e.g. cyan ▶ like the status strip). Both can apply at once |
| playing track (optional) | this track is audible in the current section | optional: a dim/bright cue |
| muted track (optional) | the track is muted | optional: mirror the context panel's dimming |

### 4.4 Panel-level states

| State | What to show |
|---|---|
| empty buffer / no top-level forms | short hint, e.g. "No definitions yet — `(def name …)` and `(track :name …)` appear here" |
| syntax error somewhere | the outline still lists everything it can recognise. Optionally a small, quiet "partial" marker. **No red error banner** (the editor already shows red squiggles) |
| collapsed | the panel can be collapsed to give the editor full width. Collapsed state must be obvious and easy to reopen (like `lib` / `ai` toggles) |
| `song` collapsed | the `song` entry's sections can be folded with ▾/▸ |

### 4.5 Interactions

| Action | Result |
|---|---|
| click an entry | editor cursor moves to the start of that form and scrolls it into view. Focus goes back to the editor so the user can type immediately |
| click ▾/▸ on `song` | fold/unfold its sections |
| toggle button (top nav or panel header) | show/hide the whole outline panel |
| keyboard (nice-to-have) | arrow keys move through entries when the panel is focused; Enter jumps |

Related editor behaviour (no panel UI needed, but the panel should **advertise the
shortcuts**, e.g. in its header or a footer hint line):

| Shortcut | Does |
|---|---|
| **Cmd/Ctrl + click** on a name in code (`kick`, `:drums`) | go to its definition |
| **F12** with the cursor on a name | go to its definition |
| **Shift + F12** | go back to where you were before the jump |

Unknown names do nothing (no error message).

### 4.6 What it is not

- Not multi-file. A later phase (SONG4) adds multiple buffers. The design should leave
  room for that (e.g. a buffer name above the list, or groups per buffer) but must not
  require it now.
- Not an arranger or timeline editor. Entries are not draggable, and there's no
  rename/refactor.
- It doesn't change any code. It only reads it and moves the cursor.

---

## 5. The placement question (what the design should decide)

Options already considered (please judge them, or propose something better):

**A. New left sidebar.** The outline gets its own column to the left of the editor
(~180–220 px), collapsible.
- \+ outline-on-the-left is the convention in code editors (VS Code, IntelliJ)
- \+ the structure (left) and the live state (right) frame the code symmetrically
- − takes editor width on smaller laptops: with both sidebars, a 1280 px screen loses
  ~400 px

**B. Inside the existing right context panel**, as a new top or bottom section.
- \+ no new column, and the editor keeps its width
- − the context panel is already tall (tracks + sliders + FX) and only ~195 px wide.
  A 40-entry outline would push live controls off screen, or the reverse
- − mixes "what's in the text" with "what's sounding"

**C. Tabs in the right sidebar**: `Session | Outline`, one visible at a time.
- \+ no new column, and each view gets full height
- − during a performance you often want both at once (sliders *and* a jump to the chorus)

**D. Something else**, e.g. a compact horizontal "section bar" above the editor for the
song sections only, plus a list elsewhere for defs and tracks; or an overlay/popover
opened with a shortcut.

Constraints for any option:

- Desktop first. Must still work at **1280×800**. At **≤760 px** width a collapse is
  acceptable.
- Collapsing must give the editor its full width back.
- The live context-panel controls (sliders) must stay reachable while the outline is visible.
- Fits the existing visual language (section 2 tokens). No new fonts. Small type is fine.

---

## 6. What I'd like back from the design

1. The chosen placement (A/B/C/D) with a short rationale.
2. A full-screen mock at 1440×900 with a realistic song (use the example in 4.2) and
   the context panel populated (3–4 tracks with sliders, 2 master FX).
3. The same at 1280×800, plus the collapsed state.
4. Close-ups of the entry states: normal, hover, cursor-inside, playing section,
   cursor-inside + playing, muted track (if used).
5. Empty state and "partial / syntax error" state.
6. Where the shortcut hints live.
7. A recommendation on the BINDINGS overlap (keep / merge / drop).
8. Exact sizes (widths, row height, paddings) and colours as tokens or hex, so the
   implementation can follow it 1:1.
