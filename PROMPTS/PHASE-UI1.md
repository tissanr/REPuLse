# Phase UI1 — Theming & Settings Dialog

## Goal

Add a first-class **Settings dialog** to the host application and a **theme registry**
that repaints the entire UI instantly when the user picks a different theme from a
dropdown.

Themes swap the CSS custom properties declared on `:root` in `main.css` and
reconfigure the CodeMirror editor theme at the same time, so chrome and editor always
match. The selected theme persists across reloads via the D2 session.

Scope is intentionally tight: built-in named palettes only, no layout rearrangement,
no user-defined colour editors.

```
;; No Lisp surface change — this is a pure UI/presentation feature.
;; Before: the app is locked to the single hard-coded dark palette in main.css.
;; After: the user clicks the gear icon in the header, picks "Light" from
;; the Theme dropdown, and the entire app repaints in one frame.
;; The choice survives a page reload.
```

---

## Background

### CSS custom properties

`app/public/css/main.css` lines 3–13 declare nine CSS variables on `:root`:

```css
:root {
  --bg: #1a1a2e;
  --bg2: #16213e;
  --border: #0f3460;
  --text: #e0e0e0;
  --accent: #e94560;
  --dim: #888;
  --green: #4caf50;
  --red: #f44336;
  --font-mono: 'JetBrains Mono', 'Fira Code', 'Cascadia Code', monospace;
}
```

Every rule in the 1,389-line stylesheet references these variables consistently — there
are no stray hard-coded hex colours in layout or component rules. Calling
`document.documentElement.style.setProperty("--bg", value)` repaints every element
using that variable in one browser frame.

### CodeMirror editor theme

`app/src/repulse/ui/editor.cljs` imports `oneDark` from
`@codemirror/theme-one-dark` (line 13) and includes it directly in the extensions
arrays of both `make-cmd-editor` (line 121) and `make-editor` (line 149). To switch
the editor theme at runtime, the extension must be wrapped in a CodeMirror
`Compartment` so it can be reconfigured via a `StateEffect`.

### Modal pattern

`app/src/repulse/ui/snippet_submit_modal.cljs` shows the existing overlay pattern:
a module-private `visible?` atom, DOM-mutating `open!` / `close!` functions,
an overlay div with a keyboard listener for Escape, and an `init!` function called
from `app.cljs`. The settings dialog reuses this exact pattern.

### Session persistence (D2)

`app/src/repulse/session.cljs` — `build-session-snapshot` assembles the map that
gets JSON-serialised to `localStorage` under key `"repulse-session"`. Fields are
`:v`, `:editor`, `:bpm`, `:fx`, `:bank`, `:sources`, `:muted`, `:midi`. `load-session`
reads it back. Adding `:ui/theme` to the snapshot (as a string key in JSON, loaded back
as a keyword) is a non-breaking extension; missing field on load defaults to `:dark`.
`current-version` is `2` — no version bump needed for an additive field.

### app.cljs wiring

`app/src/repulse/app.cljs` owns the header DOM, manages all sub-module `init!` calls,
and handles global key listeners. The gear button goes in the header alongside the
existing play/stop controls and auth button.

---

## Implementation

### 1. New file: `app/src/repulse/themes.cljs`

This module owns the theme registry and the `apply-theme!` side-effecting function.
It must **not** depend on CodeMirror — the editor theme reconfiguration is handled by
a callback injected by `editor.cljs`.

```clojure
(ns repulse.themes)

(def themes
  {:dark          {:--bg "#1a1a2e" :--bg2 "#16213e" :--border "#0f3460"
                   :--text "#e0e0e0" :--accent "#e94560" :--dim "#888"
                   :--green "#4caf50" :--red "#f44336"
                   :editor-theme :one-dark}
   :light         {:--bg "#f5f5f0" :--bg2 "#e8e8e0" :--border "#c0c0b0"
                   :--text "#1a1a1a" :--accent "#c0392b" :--dim "#888"
                   :--green "#27ae60" :--red "#e74c3c"
                   :editor-theme :default-light}
   :high-contrast {:--bg "#000000" :--bg2 "#0a0a0a" :--border "#ffffff"
                   :--text "#ffffff" :--accent "#ffff00" :--dim "#aaaaaa"
                   :--green "#00ff00" :--red "#ff0000"
                   :editor-theme :one-dark}
   :solarized     {:--bg "#002b36" :--bg2 "#073642" :--border "#586e75"
                   :--text "#839496" :--accent "#d33682" :--dim "#657b83"
                   :--green "#859900" :--red "#dc322f"
                   :editor-theme :one-dark}
   :nord          {:--bg "#2e3440" :--bg2 "#3b4252" :--border "#4c566a"
                   :--text "#eceff4" :--accent "#bf616a" :--dim "#7a8190"
                   :--green "#a3be8c" :--red "#bf616a"
                   :editor-theme :one-dark}})

(def theme-labels
  {:dark "Dark (default)" :light "Light" :high-contrast "High Contrast"
   :solarized "Solarized Dark" :nord "Nord"})

(defonce current-theme (atom :dark))

;; Injected by editor.cljs after editor setup.
(defonce ^:private editor-theme-fn (atom nil))

(defn register-editor-theme-fn! [f]
  (reset! editor-theme-fn f))

(defn apply-theme! [theme-key]
  (when-let [palette (get themes (keyword theme-key))]
    (let [root (.-documentElement js/document)]
      (doseq [[k v] (dissoc palette :editor-theme)]
        (.setProperty (.-style root) (name k) v)))
    (when-let [f @editor-theme-fn]
      (f (:editor-theme palette)))
    (reset! current-theme (keyword theme-key))))
```

### 2. `app/src/repulse/ui/editor.cljs` — Compartment for the editor theme

Add `["@codemirror/state" :refer [... Compartment]]` to the require.

Create a module-level `Compartment`:

```clojure
(defonce ^:private theme-compartment (Compartment.))
```

In `make-editor`, replace the bare `oneDark` in the extensions array with:

```clojure
(.of theme-compartment oneDark)
```

Do the same in `make-cmd-editor`.

Export a function that reconfigures the compartment in both views:

```clojure
(defn set-editor-theme! [theme-key]
  (let [ext (if (= theme-key :default-light)
               (.theme EditorView #js {})  ; minimal/no theme for light
               oneDark)]
    (doseq [view [(deref editor-view) (deref cmd-view)]
            :when view]
      (.dispatch view
                 #js {:effects (.reconfigure theme-compartment ext)}))))
```

Call `(themes/register-editor-theme-fn! set-editor-theme!)` at the bottom of
`editor.cljs` after the function is defined, or from `app.cljs` after both modules
are loaded.

### 3. New file: `app/src/repulse/ui/settings_modal.cljs`

Follows the `snippet_submit_modal.cljs` pattern exactly.

```clojure
(ns repulse.ui.settings-modal
  (:require [repulse.themes :as themes]
            [repulse.session :as session]))

(defonce ^:private visible? (atom false))

(defn- el [id] (.getElementById js/document id))

(defn close! []
  (reset! visible? false)
  (when-let [overlay (el "settings-overlay")]
    (set! (.-display (.-style overlay)) "none")))

(defn open! []
  (reset! visible? true)
  (when-let [overlay (el "settings-overlay")]
    (set! (.-display (.-style overlay)) "flex")
    ;; Focus the select for keyboard accessibility
    (when-let [sel (el "settings-theme-select")]
      (.focus sel))))

(defn- on-theme-change! [e]
  (let [key (keyword (.. e -target -value))]
    (themes/apply-theme! key)
    (session/schedule-save!)))

(defn init! []
  (let [overlay (js/document.createElement "div")]
    (set! (.-id overlay) "settings-overlay")
    (set! (.-className overlay) "settings-overlay")
    (set! (.-innerHTML overlay)
          (str "<div class=\"settings-dialog\" id=\"settings-dialog\">"
               "<div class=\"settings-header\"><span>Settings</span>"
               "<button class=\"settings-close\" id=\"settings-close\">✕</button></div>"
               "<div class=\"settings-row\">"
               "<label for=\"settings-theme-select\">Theme</label>"
               "<select id=\"settings-theme-select\">"
               (apply str
                 (map (fn [[k label]]
                        (str "<option value=\"" (name k) "\">" label "</option>"))
                      themes/theme-labels))
               "</select></div></div>"))
    (.appendChild js/document.body overlay)
    ;; Overlay click outside dialog closes it
    (.addEventListener overlay "click"
      (fn [e]
        (when (= (.-target e) overlay)
          (close!))))
    ;; Escape key
    (.addEventListener js/document "keydown"
      (fn [e]
        (when (and @visible? (= (.-key e) "Escape"))
          (close!))))
    (when-let [close-btn (el "settings-close")]
      (.addEventListener close-btn "click" close!))
    (when-let [sel (el "settings-theme-select")]
      (.addEventListener sel "change" on-theme-change!))))

(defn sync-select! [theme-key]
  (when-let [sel (el "settings-theme-select")]
    (set! (.-value sel) (name theme-key))))
```

### 4. `app/src/repulse/session.cljs` — persist `:theme`

In `build-session-snapshot`, add:

```clojure
:theme (name @themes/current-theme)
```

(Requires `[repulse.themes :as themes]` in the namespace require.)

In `load-session`, the field arrives as `:theme` (a string). In `app.cljs`, after
loading the session, call:

```clojure
(when-let [theme-str (:theme session)]
  (themes/apply-theme! (keyword theme-str))
  (settings-modal/sync-select! (keyword theme-str)))
```

### 5. `app/src/repulse/app.cljs` — gear button + wiring

Add requires:
- `[repulse.themes :as themes]`
- `[repulse.ui.settings-modal :as settings-modal]`

In the header DOM setup, add a gear button next to the existing controls:

```clojure
(let [gear (js/document.createElement "button")]
  (set! (.-id gear) "settings-btn")
  (set! (.-className gear) "header-btn")
  (set! (.-title gear) "Settings")
  (set! (.-innerHTML gear) "⚙")
  (.addEventListener gear "click" settings-modal/open!)
  (.appendChild (el "header") gear))
```

Call `(settings-modal/init!)` in the `init!` sequence alongside the other modal inits.

After loading the session snapshot, apply the stored theme (see §4 above).

### 6. `app/public/css/main.css` — settings modal styles

Append a new section:

```css
/* ── Settings modal ──────────────────────────────── */
.settings-overlay {
  display: none;
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.6);
  justify-content: center; align-items: center;
  z-index: 1000;
}
.settings-dialog {
  background: var(--bg2);
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 16px;
  min-width: 280px;
}
.settings-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px;
  color: var(--accent);
}
.settings-close {
  background: none; border: none; color: var(--dim);
  cursor: pointer; font-size: 16px;
}
.settings-close:hover { color: var(--text); }
.settings-row {
  display: flex; justify-content: space-between; align-items: center;
  gap: 12px; padding: 4px 0;
}
.settings-row label { color: var(--text); }
.settings-row select {
  background: var(--bg); color: var(--text);
  border: 1px solid var(--border); border-radius: 2px;
  padding: 2px 6px; font-family: var(--font-mono); font-size: 12px;
}
#settings-btn {
  background: none; border: none; color: var(--dim);
  cursor: pointer; font-size: 16px; padding: 0 6px;
}
#settings-btn:hover { color: var(--text); }
```

No grammar or WASM build steps required for this phase.

---

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/themes.cljs` | **New** — theme registry, `apply-theme!`, `current-theme` atom, editor-theme callback |
| `app/src/repulse/ui/settings_modal.cljs` | **New** — settings dialog component, `init!`, `open!`, `close!`, `sync-select!` |
| `app/src/repulse/ui/editor.cljs` | Wrap editor themes in a `Compartment`; add `set-editor-theme!`; call `register-editor-theme-fn!` |
| `app/src/repulse/session.cljs` | Add `:theme` field to `build-session-snapshot`; require `repulse.themes` |
| `app/src/repulse/app.cljs` | Require new modules; add gear button to header; call `settings-modal/init!`; restore theme on load |
| `app/public/css/main.css` | Append settings modal styles; audit for any stray hard-coded hex in component rules |
| `docs/USAGE.md` | Add "Settings & themes" section |
| `README.md` | Mention the gear icon in the quick tour |
| `CLAUDE.md` | Mark Phase UI1 as ✓ delivered |

---

## Definition of done

- [ ] A gear icon (⚙) appears in the header; clicking it opens the Settings dialog
- [ ] ESC key and clicking outside the dialog both close it without side effects
- [ ] The theme `<select>` lists exactly five entries: Dark (default), Light, High Contrast, Solarized Dark, Nord
- [ ] Picking "Light" switches the chrome to a light palette and the CodeMirror editor loses `oneDark` styling in the same frame
- [ ] Picking "High Contrast" renders white text on black with yellow accent throughout all panels (header, context panel, dashboard, snippet panel)
- [ ] The selected theme persists: reload the page and the previously chosen theme is restored — the select reflects it and the UI matches
- [ ] Opening the Settings dialog when the theme is "Nord" shows "Nord" pre-selected in the dropdown
- [ ] No stray `#hex` colour values remain outside the CSS variable declarations in `main.css` (audit pass)
- [ ] All five themes have legible contrast for the `--text` / `--bg` pairing (checked visually)
- [ ] `npm run test` passes (no CLJS compilation errors in the new modules)
- [ ] `npx shadow-cljs compile app` passes without warnings for the new namespaces
- [ ] Existing features are unaffected: eval, playback, dashboard sliders, snippet panel, auth button, MIDI mapping all work on each theme
- [ ] The embed component (`<repulse-editor>`) is unaffected — it uses Shadow DOM and its own `theme` attribute; no regressions

---

## What NOT to do

- **No panel rearrangement or show/hide toggles** — moving panels around requires a larger layout refactor and is deferred to Phase UI2.
- **No user-defined theme editor** — custom colour pickers and CSS export belong in a follow-on phase; only named built-in palettes are in scope here.
- **No font selection** — `--font-mono` is intentionally excluded from theme switching to keep the scope minimal.
- **No extra settings fields** — the dialog in this phase contains only the Theme dropdown; future settings (default BPM, autosave interval, etc.) are additions to the dialog infrastructure and do not belong here.
- **No CodeMirror light theme packages** — the "Light" theme uses a plain empty EditorView theme (no extra npm package); avoid adding `@codemirror/theme-*` dependencies.
