/**
 * Base classes for REPuLse plugins.
 *
 * Extend VisualPlugin or EffectPlugin instead of writing a plain object so that:
 *   - The `type` property is set automatically
 *   - Default no-op implementations are provided for optional lifecycle methods
 *   - Abstract methods throw a clear error if not overridden
 *
 * Usage:
 *   import { EffectPlugin } from '/plugin-base.js';
 *   export default class Chorus extends EffectPlugin {
 *     constructor() { super({ name: "chorus" }); }
 *     createNodes(ctx)   { ... return { inputNode, outputNode }; }
 *     setParam(n, v)     { ... }
 *     bypass(on)         { ... }
 *     destroy()          { ... }
 *   }
 */

function abstractMethod(plugin, method) {
  throw new Error(`[REPuLse plugin "${plugin.name}"] ${method}() must be implemented`);
}

// ─── Visual plugin protocol ───────────────────────────────────────────────────

export class VisualPlugin {
  constructor({ name, version = "1.0.0" } = {}) {
    if (!name) throw new Error("VisualPlugin: name is required");
    this.type    = "visual";
    this.name    = name;
    this.version = version;
  }

  /** Called once on registration. Store host.analyser, host.audioCtx etc. */
  init(host) {}

  /** Append DOM into container and start the render loop. @abstract */
  mount(container) { abstractMethod(this, "mount"); }

  /** Stop the render loop and remove DOM elements. @abstract */
  unmount() { abstractMethod(this, "unmount"); }

  /** Full teardown. Defaults to unmount(). Override if you need extra cleanup. */
  destroy() { this.unmount(); }
}

// ─── Effect plugin protocol ───────────────────────────────────────────────────

export class EffectPlugin {
  constructor({ name, version = "1.0.0" } = {}) {
    if (!name) throw new Error("EffectPlugin: name is required");
    this.type    = "effect";
    this.name    = name;
    this.version = version;
  }

  /** Called once on registration. Start async work (e.g. addModule) here. */
  init(host) {}

  /**
   * Build the Web Audio sub-graph synchronously.
   * Must return { inputNode: AudioNode, outputNode: AudioNode }.
   * @abstract
   */
  createNodes(ctx) { abstractMethod(this, "createNodes"); }

  /**
   * Update a named parameter. Common names: "wet", "dry", "decay", "rate".
   * @abstract
   */
  setParam(name, value) { abstractMethod(this, "setParam"); }

  /**
   * Toggle bypass mode.
   *   on = true  → transparent pass-through (silence the wet path)
   *   on = false → restore wet signal
   * Default is a no-op. Override for proper dry/wet bypass.
   */
  bypass(on) {}

  /** Return current parameter values as { name: value }. */
  getParams() { return {}; }

  /** Disconnect all nodes and release references. @abstract */
  destroy() { abstractMethod(this, "destroy"); }
}
