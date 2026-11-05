# Accessibility

## Rules every screen follows

- `contentDescription` (a string resource) on every icon-only control; `null` for decorative icons. Lint fails on missing descriptions.
- Touch targets at least 48 dp (`TravelTheme.spacing.minTouchTarget`).
- `semantics { heading() }` on section titles (`SectionScaffold` does this).
- State descriptions on toggles and selection (expand/collapse, "Added to plan").
- Live-region announcements for async results: `ErrorState` is a polite live region; `StreamingStatusAnnouncer` says "Hotels ready" as sections finish.
- Works at 200 % font scale: no fixed heights on text containers; lists scroll.
- No colour-only meaning: verification and status chips carry text or icons, not just colour.
- Reduce-motion: `rememberReduceMotion()`; shimmer becomes static.
- Contrast AA in light and dark.

## How to test

| Tool | How |
|---|---|
| TalkBack | Enable on the emulator or device; swipe through each screen, check order, labels, headings, announcements while a search streams |
| Accessibility Scanner | Install from Play (device) and run on each screen; fix touch target, contrast and label findings |
| Font scale | Settings -> Display -> Font size / Display size to the maximum; check clipping and reachability of actions |
| Compose tests | Assert semantics (`hasContentDescription`, `isHeading`, state descriptions) in Robolectric UI tests |
| Lint | `./gradlew lintLocalDebug` (a11y and i18n ids are errors, configured in `config/lint/lint.xml`) |
| Reduce motion | Developer options -> Animator duration scale off |

The `a11y-i18n-auditor` Claude agent (`.claude/agents/`) performs this checklist on a diff.
