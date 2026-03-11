# REPuLse — External Sample Repository Import (Phase 9)

## Context

REPuLse ships with the Dirt-Samples and Tidal Drum Machines collections loaded from
Strudel's CDN. For custom sounds, users currently have no way to add their own samples
without modifying source code.

Phase 9 adds `(samples! url)` — a single Lisp built-in that loads additional sample banks
at runtime from either a Strudel-compatible JSON manifest or a public GitHub repository
with audio files organised into sub-folders. The load is async (fire-and-forget); the
built-in returns a status string immediately so the footer gives feedback.

---

## Goal for this session

By the end of this session:

1. `(samples! "https://…/samples.json")` loads a Strudel-compatible JSON manifest and registers its banks
2. `(samples! "github:owner/repo")` auto-discovers audio files from a public GitHub repo via the public tree API, defaulting to the `main` branch with `master` fallback
3. `(samples! "github:owner/repo/branch")` targets a specific branch
4. `(sample-banks)` returns a sorted sequence of all currently registered bank names
5. Loaded banks are immediately usable as keywords: `(seq :my-kick :my-snare)`
6. Duplicate bank names: last loaded wins (existing `merge` behaviour on the registry atom)
7. All existing patterns and the built-in synthesis fallback continue to work

---

## Lisp syntax

### Manifest URL

Point at any Strudel-compatible JSON manifest:

```lisp
(samples! "https://raw.githubusercontent.com/user/repo/main/samples.json")
```

Expected JSON format (identical to Strudel's CDN manifests — the existing `parse-manifest`
function already handles this):

```json
{
  "_base": "https://raw.githubusercontent.com/user/repo/main/samples/",
  "kick":  ["kick1.wav", "kick2.wav"],
  "snare": ["snare1.wav", "snare2.wav"],
  "pad":   ["pad-c.wav", "pad-e.wav", "pad-g.wav"]
}
```

After loading: `:kick`, `:snare`, `:pad` become usable banks.

### GitHub shorthand

```lisp
; Auto-discover from the default branch (tries "main", falls back to "master")
(samples! "github:algorave-dave/samples")

; Target a specific branch
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

### Inspect loaded banks

```lisp
(sample-banks)   ; => ("bd" "hh" "kick" "pad" "snare" ...)
```

---

## Architecture

### `app/src/repulse/samples.cljs` — new functions

Add two functions. The existing `load-manifest!`, `parse-manifest`, `registry`, and
`get-buffer!` are unchanged.

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

**`load-external!`** — router: dispatches to `load-github!` or the existing `load-manifest!`:

```clojure
(defn load-external!
  "Load samples from url.
   Accepts: 'github:owner/repo' | 'github:owner/repo/branch' | any manifest JSON URL."
  [url]
  (if (clojure.string/starts-with? url "github:")
    (let [parts  (clojure.string/split (subs url 7) #"/")
          owner  (first parts)
          repo   (second parts)
          branch (nth parts 2 nil)]
      (if branch
        (load-github! owner repo branch)
        ;; No branch specified: try "main", fall back to "master" on failure
        (-> (load-github! owner repo "main")
            (.catch (fn [_] (load-github! owner repo "master"))))))
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

If a user owns a sample repo and wants a curated bank list (rather than auto-discovery),
they create a `samples.json` at the repo root:

```json
{
  "_base": "https://raw.githubusercontent.com/my-name/my-samples/main/",
  "kick":  ["kicks/kick1.wav", "kicks/kick2.wav"],
  "snare": ["snares/snare1.wav"],
  "ambient": ["textures/rain.wav", "textures/wind.wav"]
}
```

Then in REPuLse:

```lisp
(samples! "https://raw.githubusercontent.com/my-name/my-samples/main/samples.json")
```

This also works with any static hosting (Codeberg, Gitea, S3, GitHub Pages, Netlify).

---

## Repository structure changes

```
app/src/repulse/
└── samples.cljs     updated — load-github!, load-external!

packages/lisp/src/repulse/lisp/
└── eval.cljs        updated — samples!, sample-banks built-ins
```

---

## Definition of Done

- [ ] `(samples! "https://…/samples.json")` loads a Strudel-format manifest; banks become usable as keywords
- [ ] `(samples! "github:owner/repo")` auto-discovers audio, grouped by folder, registered as banks
- [ ] `(samples! "github:owner/repo/branch")` targets a specific branch
- [ ] Console shows `[REPuLse] loaded N banks from github:owner/repo` on success
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
