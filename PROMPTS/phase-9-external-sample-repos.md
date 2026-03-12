# REPuLse — External Sample Repository Import (Phase 9)

## Context

REPuLse ships with the Dirt-Samples and Tidal Drum Machines collections loaded from
Strudel's CDN. For custom sounds, users currently have no way to add their own samples
without modifying source code.

Phase 9 adds `(samples! url)` — a single Lisp built-in that loads additional sample banks
at runtime from a **Lisp manifest** (`.edn`), a **Strudel-compatible JSON manifest**, or
a **public GitHub repository** with audio files organised into sub-folders.
The load is async (fire-and-forget); the built-in returns a status string immediately
so the footer gives feedback.

---

## Goal for this session

By the end of this session:

1. `(samples! "https://…/samples.edn")` loads a REPuLse Lisp manifest and registers its banks
2. `(samples! "https://…/samples.json")` loads a Strudel-compatible JSON manifest and registers its banks
3. `(samples! "github:owner/repo")` auto-discovers audio files from a public GitHub repo via the public tree API, defaulting to the `main` branch with `master` fallback
4. `(samples! "github:owner/repo/branch")` targets a specific branch
5. `(sample-banks)` returns a sorted sequence of all currently registered bank names
6. Loaded banks are immediately usable as keywords: `(seq :my-kick :my-snare)`
7. Duplicate bank names: last loaded wins (existing `merge` behaviour on the registry atom)
8. All existing patterns and the built-in synthesis fallback continue to work

---

## Manifest formats

### REPuLse Lisp manifest (`.edn`)

The native format. A plain map literal — a superset of EDN — readable by the
existing `repulse.lisp.reader` without any extra dependencies.

```clojure
{:_base "https://raw.githubusercontent.com/user/repo/main/samples/"
 :kick  ["kick1.wav" "kick2.wav"]
 :snare ["snare1.wav" "snare2.wav"]
 :pad   ["pad-c.wav" "pad-e.wav" "pad-g.wav"]}
```

Rules:
- The map must be at the top level (no wrapping expression).
- `:_base` — the URL prefix prepended to every filename. **Required** when filenames
  are relative; omit if every filename is already an absolute URL.
- Every other key is a bank name. The key is a keyword (`:kick`) — `(name k)` strips
  the leading colon to produce the registered bank name (`"kick"`).
- Values are vectors of filename strings (relative to `:_base` if present, absolute otherwise).
- Commas are optional whitespace (they are skipped by the reader already).
- Inline comments with `;` are stripped by the reader, so annotated manifests work:

```clojure
{:_base "https://cdn.example.com/samples/"
 ; drums
 :kick  ["kick/01.wav" "kick/02.wav"]
 :snare ["snare/rimshot.wav" "snare/crack.wav"]
 ; melodic
 :bass  ["bass/low.wav" "bass/mid.wav" "bass/high.wav"]}
```

**Format detection:** the URL ends with `.edn` (case-insensitive). If the URL has no
extension or ends with `.json`, the JSON path is used instead.

---

### Strudel-compatible JSON manifest (`.json`)

The existing format, unchanged. Kept for compatibility with the Strudel ecosystem.

```json
{
  "_base": "https://raw.githubusercontent.com/user/repo/main/samples/",
  "kick":  ["kick1.wav", "kick2.wav"],
  "snare": ["snare1.wav", "snare2.wav"],
  "pad":   ["pad-c.wav", "pad-e.wav", "pad-g.wav"]
}
```

After loading: `:kick`, `:snare`, `:pad` become usable banks.

---

## Lisp syntax

```lisp
; Load a REPuLse Lisp manifest
(samples! "https://raw.githubusercontent.com/my-name/my-samples/main/samples.edn")

; Load a Strudel-compatible JSON manifest
(samples! "https://raw.githubusercontent.com/my-name/my-samples/main/samples.json")

; Auto-discover from the default branch (tries "main", falls back to "master")
(samples! "github:algorave-dave/samples")

; Target a specific branch
(samples! "github:algorave-dave/samples/my-branch")

; Inspect what's loaded
(sample-banks)   ; => ("bd" "hh" "kick" "pad" "snare" ...)
```

---

## GitHub shorthand

```lisp
(samples! "github:algorave-dave/samples")
(samples! "github:algorave-dave/samples/my-branch")
```

REPuLse calls the public GitHub tree API:

```
GET https://api.github.com/repos/{owner}/{repo}/git/trees/{branch}?recursive=1
```

Audio files (`.wav`, `.mp3`, `.ogg`, `.flac`, `.aiff`) are grouped by their **immediate
parent folder name**. Each folder becomes one bank. Files sitting directly in the repo root
are grouped under the repo name itself.

Raw file URLs are constructed as:

```
https://raw.githubusercontent.com/{owner}/{repo}/{branch}/{path}
```

---

## Architecture

### `app/src/repulse/samples.cljs` — new functions

The existing `load-manifest!`, `parse-manifest`, `registry`, and `get-buffer!` are
unchanged. Add the following.

**`parse-lisp-manifest`** — parses a REPuLse Lisp manifest string using the existing reader:

```clojure
(defn parse-lisp-manifest
  "Parse a REPuLse Lisp manifest string into a Strudel-style {bank-name [url ...]} map.
   Uses the existing reader — no new dependencies needed."
  [text]
  (let [result (reader/read-string text)]
    (when (map? result)
      (let [base  (get result :_base "")
            banks (dissoc result :_base)]
        (reduce-kv
          (fn [acc k v]
            (assoc acc (name k)
                       (mapv #(str base %) v)))
          {}
          banks)))))
```

`reader/read-string` returns a CLJS map with keyword keys and vector values — exactly
the structure we need. `(name :kick)` → `"kick"`.

**`load-lisp-manifest!`** — fetches and registers a `.edn` manifest:

```clojure
(defn load-lisp-manifest!
  "Fetch a REPuLse Lisp (.edn) manifest, parse it, and register the banks."
  [url]
  (-> (js/fetch url)
      (.then #(.text %))
      (.then (fn [text]
               (if-let [banks (parse-lisp-manifest text)]
                 (do (swap! registry merge banks)
                     (js/console.log (str "[REPuLse] loaded " (count banks)
                                          " banks from " url)))
                 (js/console.warn "[REPuLse] Lisp manifest parse failed:" url))))
      (.catch (fn [e]
                (js/console.warn "[REPuLse] Lisp manifest load failed:" url e)))))
```

**`load-github!`** — discovers audio via the GitHub tree API:

```clojure
(def ^:private AUDIO-EXTS #{"wav" "mp3" "ogg" "flac" "aiff"})

(defn- audio-ext? [path]
  (contains? AUDIO-EXTS
             (clojure.string/lower-case
               (last (clojure.string/split path #"\.")))))

(defn load-github!
  "Discover audio files in a public GitHub repo and register them as sample banks.
   Groups by immediate parent folder; files in the repo root go under repo-name."
  [owner repo branch]
  (let [api-url  (str "https://api.github.com/repos/" owner "/" repo
                      "/git/trees/" branch "?recursive=1")
        raw-base (str "https://raw.githubusercontent.com/"
                      owner "/" repo "/" branch "/")]
    (-> (js/fetch api-url)
        (.then #(.json %))
        (.then (fn [data]
                 (let [tree    (js->clj (.-tree data) :keywordize-keys true)
                       blobs   (filter #(and (= (:type %) "blob")
                                             (audio-ext? (:path %)))
                                       tree)
                       grouped (group-by
                                 (fn [{:keys [path]}]
                                   (let [parts (clojure.string/split path #"/")]
                                     (if (> (count parts) 1)
                                       (nth parts (- (count parts) 2))
                                       repo)))
                                 blobs)
                       banks   (reduce-kv
                                 (fn [acc folder files]
                                   (assoc acc folder
                                          (mapv #(str raw-base (:path %)) files)))
                                 {}
                                 grouped)]
                   (swap! registry merge banks)
                   (js/console.log (str "[REPuLse] loaded " (count banks)
                                        " banks from github:" owner "/" repo)))))
        (.catch (fn [e]
                  (js/console.warn "[REPuLse] GitHub load failed:" (str owner "/" repo) e))))))
```

**`load-external!`** — router that dispatches to the right loader based on URL shape:

```clojure
(defn- lisp-manifest-url? [url]
  (clojure.string/ends-with? (clojure.string/lower-case url) ".edn"))

(defn load-external!
  "Load samples from url.
   Accepts:
     'github:owner/repo'          — auto-discover, tries main then master
     'github:owner/repo/branch'   — auto-discover on specific branch
     'https://…/samples.edn'      — REPuLse Lisp manifest
     'https://…/samples.json'     — Strudel JSON manifest (default for all other URLs)"
  [url]
  (cond
    (clojure.string/starts-with? url "github:")
    (let [parts  (clojure.string/split (subs url 7) #"/")
          owner  (first parts)
          repo   (second parts)
          branch (nth parts 2 nil)]
      (if branch
        (load-github! owner repo branch)
        (-> (load-github! owner repo "main")
            (.catch (fn [_] (load-github! owner repo "master"))))))

    (lisp-manifest-url? url)
    (load-lisp-manifest! url)

    :else
    (load-manifest! url)))
```

### `packages/lisp/src/repulse/lisp/eval.cljs` — two new built-ins

In `make-env`, add alongside the existing sample/audio built-ins:

```clojure
"samples!"     (fn [url]
                 (samples/load-external! url)
                 (str "loading " url "…"))

"sample-banks" (fn []
                 (samples/bank-names))
```

`samples!` returns a status string immediately — the async load continues in the background.
The user sees feedback in the footer. No promise is surfaced into the Lisp layer.

### No changes to

- `packages/core/` — pure pattern engine, no sample awareness
- `packages/audio/` — WASM synthesis unaffected
- `app/src/repulse/audio.cljs` — scheduler unaffected
- The build system (`shadow-cljs.edn`, `package.json`, `wasm-pack`)

---

## Browser CORS notes

| Endpoint | CORS |
|---|---|
| `api.github.com` (public GET) | ✓ allowed |
| `raw.githubusercontent.com` (public files) | ✓ allowed |
| Strudel CDN (existing) | ✓ already works |
| User-hosted manifests | Must serve `Access-Control-Allow-Origin: *` (GitHub Pages, CDNs do this by default) |

---

## Rate limiting

The unauthenticated GitHub API allows **60 requests/hour per IP**. One `(samples! "github:…")`
call consumes exactly **1 API request** (the recursive tree endpoint). Buffer fetches hit
`raw.githubusercontent.com` directly, which has no documented rate limit for public repos.
This is fine for a live coding session.

---

## Hosting a custom manifest (user guide addition)

### Lisp manifest (recommended for REPuLse users)

Create `samples.edn` at the repo root and host it on GitHub (or any static host):

```clojure
{:_base "https://raw.githubusercontent.com/my-name/my-samples/main/"
 ; drums
 :kick    ["kicks/kick1.wav" "kicks/kick2.wav"]
 :snare   ["snares/snare1.wav"]
 ; textures
 :ambient ["textures/rain.wav" "textures/wind.wav"]}
```

```lisp
(samples! "https://raw.githubusercontent.com/my-name/my-samples/main/samples.edn")
```

### JSON manifest (Strudel-compatible)

Create `samples.json` at the repo root:

```json
{
  "_base": "https://raw.githubusercontent.com/my-name/my-samples/main/",
  "kick":  ["kicks/kick1.wav", "kicks/kick2.wav"],
  "snare": ["snares/snare1.wav"],
  "ambient": ["textures/rain.wav", "textures/wind.wav"]
}
```

```lisp
(samples! "https://raw.githubusercontent.com/my-name/my-samples/main/samples.json")
```

Both formats work with any static hosting: GitHub Pages, Codeberg, S3, Netlify.

---

## Repository structure changes

```
app/src/repulse/
└── samples.cljs     updated — parse-lisp-manifest, load-lisp-manifest!,
                               load-github!, load-external!

packages/lisp/src/repulse/lisp/
└── eval.cljs        updated — samples!, sample-banks built-ins
```

---

## Definition of Done

- [ ] `(samples! "https://…/samples.edn")` loads a REPuLse Lisp manifest; banks become usable as keywords
- [ ] `(samples! "https://…/samples.json")` loads a Strudel-format manifest; banks become usable as keywords
- [ ] `(samples! "github:owner/repo")` auto-discovers audio, grouped by folder, registered as banks
- [ ] `(samples! "github:owner/repo/branch")` targets a specific branch
- [ ] Console shows `[REPuLse] loaded N banks from …` on success for all three load paths
- [ ] `(sample-banks)` returns sorted list of all registered bank names
- [ ] After loading, `(seq :newly-loaded-bank)` plays samples
- [ ] Malformed URLs and network errors log a console warning; no exception surfaces to the editor footer
- [ ] `(stop)` still works; built-in synthesis fallback still works
- [ ] All existing core unit tests still pass

---

## What NOT to do in this phase

- No GitHub authentication / token support — public repos only
- No UI for browsing or previewing available banks
- No preloading / prefetching — lazy-load on first play (existing behaviour is unchanged)
- No recursive sub-folder grouping — only the immediate parent folder name becomes the bank name
- No `(unload-samples!)` or selective registry reset — append-only for this phase
- No changes to the default manifest list; Strudel CDN loads on startup as before
- No support for private repos, SSH URLs, or non-GitHub forges (Codeberg, Gitea) in the `github:` shorthand — use a full manifest URL instead
