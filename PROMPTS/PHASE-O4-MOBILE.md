# Phase O4 — Mobile Layout

## Goal

Make the existing REPuLse app usable on phones and tablets without changing the
language, audio engine, or desktop workflow.

## Scope

- Responsive app shell for narrow viewports.
- Touch-friendly transport controls, panel toggles, sliders, and snippet actions.
- Editor and command-bar sizing that avoids iOS auto-zoom.
- Context panel, timeline, snippets, and AI panel layout adjustments for mobile.
- Manual verification on iOS Safari and Android Chrome where available.

## Current Code Notes

- Main host styling lives in `app/public/css/main.css`.
- Timeline rendering uses canvas colors in `app/src/repulse/ui/timeline.cljs`.
- The embeddable component has separate styling in `app/src/repulse/embed_css.cljs`;
  only change it if the mobile issue affects embeds too.

## Definition Of Done

- [ ] App is usable at common phone widths without overlapping core controls.
- [ ] Primary touch targets are at least 44px where practical.
- [ ] Editor and command bar remain readable and editable with virtual keyboards.
- [ ] Panels can be opened/closed without trapping the user.
- [ ] Desktop layout is unchanged except for intentional responsive CSS.
- [ ] `npm run test` and `npx shadow-cljs compile app` pass.
