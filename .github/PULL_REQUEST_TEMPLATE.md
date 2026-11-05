## What and why

<!-- One or two sentences. Link the issue or spec section. -->

## Checklist

- [ ] Tests written first and passing (`./gradlew spotlessCheck detekt lintLocalDebug testLocalDebugUnitTest assembleLocalDebug`)
- [ ] Module boundaries respected (features depend only on core:{ui,designsystem,data,domain,model,common}); shared changes are in their own `core: ...` commit
- [ ] Only API data is displayed (nothing invented; estimates labelled; booking via `BookingLinkButton`)
- [ ] Loading, empty and error-with-retry states exist
- [ ] Accessibility: content descriptions, 48 dp targets, headings, 200 % font scale, TalkBack checked
- [ ] Strings are resources and translated (`values-es`), plurals where counts appear
- [ ] Design-system tokens only (no hard-coded colours or dp)
- [ ] Screenshots attached: light and dark (and tablet if adaptive)
- [ ] Docs updated (`docs/`, `MEMORY.md` for decisions); `scripts/check-doc-links.sh` passes
- [ ] No secrets, keystores, `local.properties` or `google-services.json` in the diff
