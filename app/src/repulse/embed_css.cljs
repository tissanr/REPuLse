(ns repulse.embed-css
  "Inline CSS for the embeddable <repulse-editor> Shadow DOM.
   Subset of main.css needed for the CodeMirror editor.")

(def EMBED_CSS
  (str
    ;; Reset
    "*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }\n"

    ;; Variables (dark theme)
    ":root {\n"
    "  --bg: #1a1a2e;\n"
    "  --bg2: #16213e;\n"
    "  --border: #0f3460;\n"
    "  --text: #e0e0e0;\n"
    "  --accent: #e94560;\n"
    "  --dim: #888;\n"
    "  --green: #4caf50;\n"
    "  --red: #f44336;\n"
    "  --font-mono: 'JetBrains Mono', 'Fira Code', 'Cascadia Code', monospace;\n"
    "}\n"

    ;; Host element
    ":host { display: block; background: var(--bg); border-radius: 4px; overflow: hidden; }\n"

    ;; Editor container
    ".editor-wrap {\n"
    "  height: 100%;\n"
    "  overflow: hidden;\n"
    "  font-family: var(--font-mono);\n"
    "}\n"

    ;; CodeMirror base
    ".editor-wrap .cm-editor {\n"
    "  height: 100%;\n"
    "  font-size: 14px;\n"
    "  background: var(--bg);\n"
    "}\n"

    ".cm-editor.cm-focused {\n"
    "  outline: none;\n"
    "}\n"

    ".cm-scroller { overflow: auto; }\n"

    ;; Rainbow delimiters
    ".rainbow-1 { color: #e06c75; }\n"
    ".rainbow-2 { color: #e5c07b; }\n"
    ".rainbow-3 { color: #98c379; }\n"
    ".rainbow-4 { color: #56b6c2; }\n"
    ".rainbow-5 { color: #c678dd; }\n"
    ".rainbow-6 { color: #61afef; }\n"

    ;; Active event highlighting
    ".active-event {\n"
    "  background-color: rgba(255, 200, 50, 0.35);\n"
    "  border-radius: 2px;\n"
    "  transition: background-color 0.08s ease-out;\n"
    "}\n"

    ;; Lint error squiggle
    ".cm-lintRange.cm-lintRange-error {\n"
    "  background-image: none !important;\n"
    "  border-bottom: 2px solid #e06c75;\n"
    "  padding-bottom: 0 !important;\n"
    "}\n"

    ;; Lint gutter
    ".cm-lint-marker-error {\n"
    "  color: #e06c75;\n"
    "}\n"

    ;; Hover tooltip
    ".repulse-hover-doc {\n"
    "  font-family: 'JetBrains Mono', 'Fira Code', monospace;\n"
    "  font-size: 13px;\n"
    "  max-width: 480px;\n"
    "  padding: 8px 12px;\n"
    "  background: #21252b;\n"
    "  border: 1px solid #3a3f4b;\n"
    "  border-radius: 4px;\n"
    "  color: #abb2bf;\n"
    "  line-height: 1.5;\n"
    "}\n"

    ".repulse-hover-sig {\n"
    "  color: #c678dd;\n"
    "  font-weight: 600;\n"
    "  margin-bottom: 4px;\n"
    "}\n"

    ".repulse-hover-desc {\n"
    "  margin-bottom: 4px;\n"
    "}\n"

    ".repulse-hover-example {\n"
    "  color: #98c379;\n"
    "  font-style: italic;\n"
    "  padding-top: 4px;\n"
    "  border-top: 1px solid #3a3f4b;\n"
    "}\n"

    ;; Insert buttons
    ".insert-plus-anchor {\n"
    "  --insert-btn-size: 14px;\n"
    "  display: inline-block;\n"
    "  width: 0;\n"
    "  line-height: 0;\n"
    "  position: relative;\n"
    "  overflow: visible;\n"
    "  vertical-align: top;\n"
    "}\n"

    ".insert-plus-btn {\n"
    "  --btn-color: 97, 175, 239;\n"
    "  width: var(--insert-btn-size);\n"
    "  height: var(--insert-btn-size);\n"
    "  padding: 0;\n"
    "  border: 1px solid rgba(var(--btn-color), 0.5);\n"
    "  border-radius: 999px;\n"
    "  background: rgba(24, 27, 34, 0.95);\n"
    "  color: rgba(var(--btn-color), 0.85);\n"
    "  display: inline-flex;\n"
    "  align-items: center;\n"
    "  justify-content: center;\n"
    "  opacity: 0.7;\n"
    "  cursor: pointer;\n"
    "  pointer-events: auto;\n"
    "  transition: opacity 0.1s ease, border-color 0.1s ease, color 0.1s ease;\n"
    "}\n"

    ".insert-plus-btn:hover {\n"
    "  opacity: 1;\n"
    "  color: rgb(var(--btn-color));\n"
    "  border-color: rgba(var(--btn-color), 0.9);\n"
    "}\n"

    ;; Dropdown
    ".insert-dropdown {\n"
    "  position: fixed;\n"
    "  z-index: 1000;\n"
    "  min-width: 300px;\n"
    "  max-width: min(420px, calc(100vw - 24px));\n"
    "  max-height: min(420px, calc(100vh - 24px));\n"
    "  overflow-y: auto;\n"
    "  padding: 8px;\n"
    "  background: #21252b;\n"
    "  border: 1px solid #3a3f4b;\n"
    "  border-radius: 8px;\n"
    "  box-shadow: 0 14px 36px rgba(0, 0, 0, 0.35);\n"
    "}\n"

    ".insert-category-header {\n"
    "  padding: 8px 10px 4px;\n"
    "  color: #5c6370;\n"
    "  font-size: 10px;\n"
    "  font-weight: 700;\n"
    "  letter-spacing: 0.08em;\n"
    "  text-transform: uppercase;\n"
    "}\n"

    ".insert-dropdown-item {\n"
    "  width: 100%;\n"
    "  border: 0;\n"
    "  border-radius: 6px;\n"
    "  background: transparent;\n"
    "  color: inherit;\n"
    "  padding: 8px 10px;\n"
    "  text-align: left;\n"
    "  cursor: pointer;\n"
    "  display: flex;\n"
    "  flex-direction: column;\n"
    "  gap: 2px;\n"
    "}\n"

    ".insert-dropdown-item:hover {\n"
    "  background: rgba(97, 175, 239, 0.12);\n"
    "}\n"

    ".insert-fn-name {\n"
    "  color: #61afef;\n"
    "  font-size: 13px;\n"
    "  font-weight: 600;\n"
    "}\n"

    ".insert-fn-detail {\n"
    "  color: #abb2bf;\n"
    "  font-size: 12px;\n"
    "  line-height: 1.35;\n"
    "}\n"))
