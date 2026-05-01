# Phase PLUG1 — Drop-In Plugin Packages

## Goal

Let users install third-party REPuLse plugins by dragging a local plugin file
onto the app. The feature should build on the current plugin registry and
effect/visual plugin protocols, not replace them.

The first version is a **trusted local plugin** system: dropped plugins are
JavaScript modules that run in the app context after explicit user consent.
This is honest, useful, and matches the current Web Audio plugin architecture.

```text
my-delay.repulse-plugin.zip
├── repulse-plugin.json
├── main.js
├── assets/
│   └── impulse.wav
└── worklets/
    └── processor.js
```

---

## Background

REPuLse already has:

- `app/src/repulse/plugins.cljs` — registry and plugin protocol validation
- `app/src/repulse/plugin_loading.cljs` — remote `load-plugin` consent flow
- `app/public/plugin-base.js` — public JS base classes for authors
- `app/public/plugins/*.js` — built-in effect/visual plugins loaded as ES modules
- `app/src/repulse/fx.cljs` — inserts effect plugin nodes into the audio graph

Drag-and-drop should reuse this path:

1. load an ES module
2. validate the plugin object
3. register it with `plugins/register!`
4. mount it if visual, or add it to `fx` if effect

The new work is packaging, local file loading, asset URL resolution,
persistence, and UX.

---

## Plugin package format

### Manifest

Every packaged plugin contains `repulse-plugin.json`:

```json
{
  "id": "com.example.delay",
  "name": "Example Delay",
  "version": "1.0.0",
  "apiVersion": 1,
  "type": "effect",
  "entry": "main.js",
  "permissions": ["audio", "worklet"],
  "assets": ["assets/impulse.wav", "worklets/processor.js"]
}
```

Required fields:

- `id` — stable reverse-DNS or package-style identifier
- `name` — user-facing display name
- `version` — semver-ish string
- `apiVersion` — plugin API version, initially `1`
- `type` — `"effect"` or `"visual"`
- `entry` — ES module entry file inside the package

Optional fields:

- `permissions` — declarative list shown in the trust dialog
- `assets` — packaged assets exposed via `host.resolveAsset(path)`
- `description`, `author`, `homepage`, `license`

### Single-file fallback

For quick experiments, users may drop a single `.js` file. The loader infers:

```clojure
{:id generated-local-id
 :name filename-without-extension
 :version "0.0.0-local"
 :type inferred-from-exported-plugin
 :entry filename}
```

Single-file plugins are not portable packages. The UI should label them as
"local single-file plugin".

---

## Plugin module API

Prefer a factory export for new plugins:

```js
export default function createPlugin() {
  return {
    type: "effect",
    id: "com.example.delay",
    name: "Example Delay",
    version: "1.0.0",

    init(host) {},

    createNodes(ctx) {
      return { inputNode, outputNode };
    },

    setParam(name, value) {},
    bypass(on) {},
    getParams() { return {}; },
    destroy() {}
  };
}
```

For compatibility, the loader should also accept the current singleton object
default export:

```js
export default {
  type: "effect",
  name: "delay",
  createNodes(ctx) { /* ... */ }
};
```

The loader normalizes either shape into one plugin instance before validation.

---

## Host API

Dropped plugins receive a small explicit host object:

```js
{
  apiVersion: 1,
  audioCtx,
  analyser,
  getBpm(),
  onBpmChange(fn),
  resolveAsset(path),
  addWorkletModule(path),
  log(...args),
  warn(...args)
}
```

`resolveAsset(path)` returns a blob URL for a packaged asset. `addWorkletModule`
resolves through the package asset map and calls `audioCtx.audioWorklet.addModule`.

Even though v1 is not a hard sandbox, this host object documents the supported
surface and gives future sandboxed plugin types a clean contract.

---

## UX

### Drop target

- Dragging a `.js` or `.repulse-plugin.zip` over the app shows a clear drop state.
- Dropping opens an install dialog before executing plugin code.
- Unsupported files show a concise error and do not execute anything.

### Trust dialog

The dialog must state plainly:

- plugin name, id, version, author if available
- source: local file name
- type: effect or visual
- requested permissions
- warning: "Plugins run JavaScript in your session. Only install plugins from
  sources you trust."

Buttons:

- `Install`
- `Cancel`

### Plugin manager

Add a small user-plugin manager UI:

- list dropped plugins
- show enabled/disabled status
- enable
- disable
- reload from stored package
- remove
- show load errors

This can live in the existing context/sidebar area or a compact modal. It should
not block the main live-coding flow.

---

## Persistence

Use IndexedDB for plugin packages. Do not use localStorage for blobs.

Suggested record shape:

```clojure
{:id "com.example.delay"
 :name "Example Delay"
 :version "1.0.0"
 :type :effect
 :enabled? true
 :manifest {...}
 :files {"main.js" Blob
         "worklets/processor.js" Blob
         "assets/impulse.wav" Blob}
 :installed-at 1710000000000}
```

Startup behaviour:

- Load metadata for installed plugins.
- Do not execute persisted plugins silently until the user has approved restore
  for this browser origin, or until a setting explicitly enables auto-restore.
- Restore enabled plugins after built-in plugins so user plugins can replace
  built-ins by name if desired.

---

## Files to change

| File | Change |
|------|--------|
| `app/src/repulse/plugin_packages.cljs` | **New** — parse dropped files, manifests, zip packages, blob URLs |
| `app/src/repulse/plugin_store.cljs` | **New** — IndexedDB persistence for packages |
| `app/src/repulse/plugin_loading.cljs` | Add local blob-module loading and export normalization |
| `app/src/repulse/plugins.cljs` | Add validation for `id`, `apiVersion`, optional metadata; keep current protocols |
| `app/src/repulse/ui/plugin_manager.cljs` | **New** — install dialog and installed-plugin manager |
| `app/src/repulse/app.cljs` | Wire drag/drop listeners and plugin manager mount point |
| `app/public/plugin-base.js` | Document factory export and package manifest conventions |
| `docs/PLUGINS.md` | Author guide for package format, API, examples, security model |
| `docs/USAGE.md` | User instructions for installing/removing dropped plugins |
| `package.json` / `app/package.json` | Add a zip parser dependency only if needed |

If a zip parser dependency is added, prefer a small browser-compatible library
with no Node-only assumptions. A no-dependency MVP may support single-file `.js`
first, then package zip support in the same phase if time allows.

---

## Security model

PLUG1 is **trusted local execution**, not a sandbox.

Required safeguards:

- Never execute dropped code before user consent.
- Show a clear warning in the install dialog.
- Validate manifest before import.
- Validate plugin protocol after import.
- Catch load/init errors and keep the app usable.
- Allow disabling/removing plugins.
- Revoke blob URLs when unloading plugins.
- Do not auto-load persisted plugins without explicit restore consent.

Out-of-scope for PLUG1:

- Hard sandboxing for arbitrary effect plugins
- Plugin signing
- Marketplace review
- Running Web Audio graph plugins in an iframe
- Preventing malicious plugins from accessing same-page browser APIs

Future sandboxed plugin types can build on the manifest and host API introduced
here.

---

## Definition of done

- [ ] Dropping a single `.js` plugin asks for consent and installs it
- [ ] Dropping a `.repulse-plugin.zip` with `repulse-plugin.json` asks for consent and installs it
- [ ] Manifest validation rejects missing/invalid `id`, `name`, `version`, `apiVersion`, `type`, or `entry`
- [ ] Default export factory and default export object are both accepted
- [ ] Effect plugins are registered and available through existing `(fx ...)` workflows
- [ ] Visual plugins are registered and can mount/unmount
- [ ] Packaged assets are available through `host.resolveAsset`
- [ ] Worklet assets can be loaded through `host.addWorkletModule`
- [ ] Installed plugins persist in IndexedDB
- [ ] User can disable, enable, reload, and remove installed plugins
- [ ] Load failures are visible in the UI and logged to console without crashing the app
- [ ] Blob URLs are revoked when plugins unload or are removed
- [ ] Existing built-in plugin loading still works
- [ ] Existing `(load-plugin url)` consent flow still works
- [ ] `npm run test` passes
- [ ] Manual browser smoke test covers install, reload, disable, remove, and one effect plugin

---

## Out of scope

- No plugin marketplace
- No remote package registry
- No code signing
- No hard security sandbox
- No changes to REPuLse-Lisp plugin syntax beyond existing `(load-plugin)` and `(unload-plugin)`
- No migration of built-in plugins to CLJS

---

## Open questions

1. **Should auto-restore be opt-in globally or per plugin?** Recommendation:
   global restore prompt first, then per-plugin enable state.
2. **Should plugins be allowed to replace built-ins by name?** Recommendation:
   yes, with an explicit warning when the installed plugin name collides with an
   existing plugin.
3. **Should package extension be `.repulse-plugin.zip` or `.repulse-plugin`?**
   Recommendation: support both, document `.repulse-plugin.zip` because it is
   transparent and easy to inspect.
