# Design

Design sources for REPuLse's UI. They are reference material for phase specs in
`PROMPTS/`, not part of the app: nothing here is built, served or tested.

| Path | What |
|---|---|
| `briefs/` | Design briefs: what a UI has to show and do, written as input for a designer (e.g. Claude Design) |
| `outline-panel-v2/` | Claude Design prototype *Outline Panel v2* (outline, inspector, inline sliders, AI rail). Open `Outline Panel v2.html` in a browser. Used by SONG3 and WS1–WS4 |

Conventions:

- One folder per design, named after the design (not the phase), because one design
  can feed several phases.
- Keep the exported files unchanged, so a newer export can replace them one-to-one.
  Notes and decisions go into the phase spec, not into these files.
- Specs link here with a relative path, e.g. `../design/outline-panel-v2/`.
