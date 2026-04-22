(ns repulse.embed-css)

;; Minimal CSS for the Shadow DOM — editor styles only, no full-app chrome.
(def EMBED_CSS
  "
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

:host {
  display: block;
  --bg: #282c34;
  --text: #abb2bf;
  --font-mono: 'JetBrains Mono', 'Fira Code', 'Cascadia Code', monospace;
}

.embed-wrap {
  width: 100%;
  height: 100%;
  overflow: hidden;
  font-family: var(--font-mono);
  background: var(--bg);
}

.cm-editor {
  height: 100%;
  font-size: 14px;
}

.cm-editor.cm-focused {
  outline: none;
}

/* Rainbow delimiters */
.rainbow-1 { color: #e06c75; }
.rainbow-2 { color: #e5c07b; }
.rainbow-3 { color: #98c379; }
.rainbow-4 { color: #56b6c2; }
.rainbow-5 { color: #c678dd; }
.rainbow-6 { color: #61afef; }

/* Active code highlighting */
.active-event {
  background-color: rgba(255, 200, 50, 0.35);
  border-radius: 2px;
  transition: background-color 0.08s ease-out;
}

/* Lint error squiggle */
.cm-lintRange.cm-lintRange-error {
  background-image: none !important;
  border-bottom: 2px solid #e06c75;
  padding-bottom: 0 !important;
}

/* Hover doc tooltip */
.repulse-hover-doc {
  font-family: var(--font-mono);
  font-size: 13px;
  max-width: 480px;
  padding: 8px 12px;
  background: #21252b;
  border: 1px solid #3a3f4b;
  border-radius: 4px;
  color: #abb2bf;
  line-height: 1.5;
}

.repulse-hover-sig     { color: #c678dd; font-weight: 600; margin-bottom: 4px; }
.repulse-hover-desc    { margin-bottom: 4px; }
.repulse-hover-example {
  color: #98c379;
  font-style: italic;
  padding-top: 4px;
  border-top: 1px solid #3a3f4b;
}

/* Insert + buttons */
.insert-plus-anchor {
  --insert-btn-size: 14px;
  display: inline-block;
  width: 0;
  line-height: 0;
  position: relative;
  overflow: visible;
  vertical-align: top;
}
.insert-plus-anchor-wrap  { transform: translate(calc(-1 * var(--insert-btn-size) - 1px), -5px); }
.insert-plus-anchor-chain,
.insert-plus-anchor-top   { transform: translate(2px, -5px); }

.insert-plus-btn {
  --btn-color: 97, 175, 239;
  width: var(--insert-btn-size);
  height: var(--insert-btn-size);
  padding: 0;
  border: 1px solid rgba(var(--btn-color), 0.5);
  border-radius: 999px;
  background: rgba(24, 27, 34, 0.95);
  color: rgba(var(--btn-color), 0.85);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  opacity: 0.7;
  cursor: pointer;
  pointer-events: auto;
  transition: opacity 0.1s ease, border-color 0.1s ease, color 0.1s ease,
              background 0.1s ease, box-shadow 0.1s ease, transform 0.1s ease;
}
.insert-plus-anchor-wrap .insert-plus-btn { --btn-color: 198, 120, 221; }
.insert-plus-btn:hover,
.insert-plus-btn:focus-visible {
  opacity: 1;
  color: rgb(var(--btn-color));
  border-color: rgba(var(--btn-color), 0.9);
  background: rgba(30, 33, 42, 1);
  box-shadow: 0 0 0 2px rgba(var(--btn-color), 0.2), 0 2px 8px rgba(0,0,0,0.5);
  transform: scale(1.2);
  outline: none;
}
.insert-plus-btn svg { display: block; pointer-events: none; }

.insert-dropdown {
  position: fixed;
  z-index: 1000;
  min-width: 300px;
  max-width: min(420px, calc(100vw - 24px));
  max-height: min(420px, calc(100vh - 24px));
  overflow-y: auto;
  padding: 8px;
  background: #21252b;
  border: 1px solid #3a3f4b;
  border-radius: 8px;
  box-shadow: 0 14px 36px rgba(0,0,0,0.35);
}
.insert-category-header {
  padding: 8px 10px 4px;
  color: #5c6370;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.insert-dropdown-item {
  width: 100%;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: inherit;
  padding: 8px 10px;
  text-align: left;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.insert-dropdown-item:hover,
.insert-dropdown-item:focus-visible { background: rgba(97,175,239,0.12); outline: none; }
.insert-fn-name   { color: #61afef; font-size: 13px; font-weight: 600; }
.insert-fn-detail { color: #abb2bf; font-size: 12px; line-height: 1.35; }

/* Autoplay click-to-start overlay */
.embed-play-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(24, 27, 34, 0.72);
  cursor: pointer;
  z-index: 10;
  transition: background 0.15s ease;
}
.embed-play-overlay:hover { background: rgba(24, 27, 34, 0.55); }
.embed-play-btn {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: rgba(233, 69, 96, 0.9);
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(0,0,0,0.5);
  transition: transform 0.1s ease, background 0.1s ease;
  pointer-events: none;
}
.embed-play-overlay:hover .embed-play-btn { transform: scale(1.1); background: #e94560; }
.embed-play-btn svg { display: block; }
")
