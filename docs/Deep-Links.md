# Deep links

| Link | Use |
|---|---|
| `voyager://auth/callback?code=\|error=` | Return from social sign-in in the system browser |
| `https://<web host>/verify?token=` | Email verification (R3) |
| `https://<web host>/reset-password?token=` | Password reset (R3) |
| `https://<web host>/share/<token>` | Public share link (R6): plan, recap, savings, or an unknown kind |
| `voyager://share`, `alerts`, `plan`, `chat`, `nearby` | Existing in-app targets |

- The https filters are not `autoVerify` (needs `assetlinks.json` with the release cert fingerprint, owner task), so Android may show a chooser once. The app always offers "paste the link or code" as a fallback.
- `DeepLinkIntents.sanitize` rejects other hosts, long tokens and extra paths; it is unit-tested.
- Cold-start links wait for the session gate (`DeepLinkHold`).

## Share links (R6)

- `https://<web host>/share/<token>` has its own non-`autoVerify` `pathPrefix` filter. `DeepLinkIntents.sanitize` keeps it only when it is a VIEW intent, the exact host (no port, user info, fragment or query) and exactly one URL-safe token (1 to 128 characters); `/share/<token>/page`, `/card.png` and everything else is dropped. The app then opens `ShareRoute(token)` itself (`AccountLinks.route`), never through Navigation's deep-link extras.
- "Open a share link" (You tab) takes a pasted link of any host, a `travelplanner://share/<token>` link or a bare code (at least 8 URL-safe characters); only the token is used and it is requested from the configured backend.
- The viewer loads `GET /api/share/{token}` (public, works signed out) and shows by `kind`: `plan` (and a legacy token without `kind`) as the shared trip with "Save a copy" when signed in; `recap` and `savings` as the server's card (headline, count chips, month and year only when given, "Shared by" only with the sharer's `show_name`); any other `kind` as a plain link: "Open in browser" opens `<web host>/share/<token>` in a Custom Tab (http/https only; hidden when the build has no web host).
- A revoked or unknown token shows "This shared trip isn't available" (offline shows retry). Dates appear only when the server sent them (`dates` hidden removes them; month shows no day).
- Telemetry: `share_view` with `kind` (`plan`, `recap`, `savings`) once per successful load; an unknown kind sends nothing.
- Until the owner publishes `assetlinks.json` with the release certificate, Android may show a chooser for these links.
